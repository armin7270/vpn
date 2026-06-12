package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.VpnDatabase
import com.example.data.database.VpnServerEntity
import com.example.data.database.VpnSubscriptionEntity
import com.example.data.repository.VpnRepository
import com.example.service.AeroVpnService
import com.example.service.ConnectionState
import com.example.service.VpnServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VpnRepository
    
    // UI selection state
    val selectedTab = MutableStateFlow(Tab.DASHBOARD)
    
    // Server collections
    val allServers: StateFlow<List<VpnServerEntity>>
    val activeServer: StateFlow<VpnServerEntity?>
    val subscriptions: StateFlow<List<VpnSubscriptionEntity>>

    // Live VPN Connection statistics direct from Service StateFlow
    val vpnState: StateFlow<VpnServiceState> = AeroVpnService.serviceState

    // Persistent settings
    val dnsServer = MutableStateFlow("1.1.1.1")
    val splitTunnelApps = MutableStateFlow<List<String>>(emptyList())
    val splitTunnelMode = MutableStateFlow("bypass") // bypass (disallowed) or allow (allowed)
    val appTheme = MutableStateFlow("auto") // light, dark, auto
    val appLanguage = MutableStateFlow("en") // en, fa, ar, ur

    // Split tunneling device apps helper list
    private val _deviceApps = MutableStateFlow<List<DeviceApp>>(emptyList())
    val deviceApps: StateFlow<List<DeviceApp>> = _deviceApps.asStateFlow()

    // Load indicators
    val isUpdating = MutableStateFlow(false)
    val syncErrorMessage = MutableStateFlow<String?>(null)

    init {
        val database = VpnDatabase.getDatabase(application)
        repository = VpnRepository(database.vpnDao())

        allServers = repository.allServersFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        activeServer = repository.activeServerFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        subscriptions = repository.allSubscriptionsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            loadSettings()
            loadDeviceApps()
        }
    }

    private suspend fun loadSettings() {
        dnsServer.value = repository.getSetting("dns_server") ?: "1.1.1.1"
        splitTunnelMode.value = repository.getSetting("split_tunnel_mode") ?: "bypass"
        
        val apps = repository.getSetting("split_tunnel_apps") ?: ""
        splitTunnelApps.value = if (apps.isNotEmpty()) apps.split(",") else emptyList()
        
        appTheme.value = repository.getSetting("app_theme") ?: "auto"
        appLanguage.value = repository.getSetting("app_language") ?: "en"
    }

    fun updateDns(dns: String) {
        viewModelScope.launch {
            dnsServer.value = dns
            repository.saveSetting("dns_server", dns)
        }
    }

    fun toggleAppInSplitTunnel(packageName: String) {
        viewModelScope.launch {
            val current = splitTunnelApps.value.toMutableList()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            splitTunnelApps.value = current
            repository.saveSetting("split_tunnel_apps", current.joinToString(","))
        }
    }

    fun updateSplitTunnelMode(mode: String) {
        viewModelScope.launch {
            splitTunnelMode.value = mode
            repository.saveSetting("split_tunnel_mode", mode)
        }
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            appTheme.value = theme
            repository.saveSetting("app_theme", theme)
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            appLanguage.value = lang
            repository.saveSetting("app_language", lang)
        }
    }

    fun selectServer(id: Int) {
        viewModelScope.launch {
            repository.setActiveServer(id)
            
            // If the VPN is fully active, we can trigger reconnect with the new server automatically
            if (vpnState.value.state == ConnectionState.CONNECTED) {
                val context = getApplication<Application>().applicationContext
                val disconnectIntent = Intent(context, AeroVpnService::class.java).apply {
                    action = AeroVpnService.ACTION_DISCONNECT
                }
                context.startService(disconnectIntent)
                
                // Let it settle for half a second then trigger connect
                withContext(Dispatchers.IO) {
                    Thread.sleep(600)
                }
                
                val connectIntent = Intent(context, AeroVpnService::class.java).apply {
                    action = AeroVpnService.ACTION_CONNECT
                }
                context.startService(connectIntent)
            }
        }
    }

    fun deleteServer(id: Int) {
        viewModelScope.launch {
            repository.deleteServer(id)
        }
    }

    fun toggleVpnConnection(context: Context) {
        val currentState = vpnState.value.state
        if (currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING || currentState == ConnectionState.RECONNECTING) {
            // Send DISCONNECT Action
            val intent = Intent(context, AeroVpnService::class.java).apply {
                action = AeroVpnService.ACTION_DISCONNECT
            }
            context.startService(intent)
        } else {
            // Show alert or connect if there's a server selected
            viewModelScope.launch {
                val active = activeServer.value
                if (active == null) {
                    val servers = allServers.value
                    if (servers.isNotEmpty()) {
                        repository.setActiveServer(servers.first().id)
                    } else {
                        // Seed a default test server first if the user database is clean
                        seedDefaultTestNodes()
                    }
                }
                
                // Trigger client CONNECT intent
                val intent = Intent(context, AeroVpnService::class.java).apply {
                    action = AeroVpnService.ACTION_CONNECT
                }
                context.startService(intent)
            }
        }
    }

    fun addManualXrayNode(name: String, protocol: String, address: String, port: Int, secret: String, sni: String = "", publicKey: String = "", security: String = "none") {
        viewModelScope.launch {
            val node = VpnServerEntity(
                name = name,
                protocol = protocol.uppercase(),
                address = address,
                port = port,
                uuidOrPassword = secret,
                sni = sni,
                publicKey = publicKey,
                security = security,
                isCustom = true
            )
            repository.addManualServer(node)
        }
    }

    fun importConfigUri(uri: String): Boolean {
        val parsed = repository.parseVpnUri(uri, isCustom = true)
        return if (parsed != null) {
            viewModelScope.launch {
                repository.addManualServer(parsed)
                repository.setActiveServer(parsed.id)
            }
            true
        } else {
            false
        }
    }

    fun addSubscriptionLink(name: String, url: String) {
        viewModelScope.launch {
            isUpdating.value = true
            syncErrorMessage.value = null
            try {
                repository.addSubscription(name, url)
            } catch (e: Exception) {
                syncErrorMessage.value = e.localizedMessage ?: "Sync Error"
            } finally {
                isUpdating.value = false
            }
        }
    }

    fun syncAllSubscriptions() {
        viewModelScope.launch {
            isUpdating.value = true
            syncErrorMessage.value = null
            try {
                repository.refreshAllSubscriptions()
            } catch (e: Exception) {
                syncErrorMessage.value = e.localizedMessage ?: "Sync Error"
            } finally {
                isUpdating.value = false
            }
        }
    }

    fun deleteSubLink(url: String) {
        viewModelScope.launch {
            repository.deleteSubscription(url)
        }
    }

    fun triggerAutoServerSelection() {
        viewModelScope.launch {
            isUpdating.value = true
            repository.autoSelectBestServer()
            isUpdating.value = false
        }
    }

    fun runActiveLatencyCheck() {
        viewModelScope.launch {
            val list = allServers.value
            for (node in list) {
                try {
                    repository.checkServerPing(node.id, node.address, node.port)
                } catch (e: Exception) {
                    Log.d("MainViewModel", "Error running individual ping test: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun seedDefaultTestNodes() = withContext(Dispatchers.IO) {
        // Seeds premium dummy nodes for instant initial use/testing if no configs are loaded
        val defaults = listOf(
            VpnServerEntity(
                name = "Aero-Premium-US [VLESS-Reality]",
                protocol = "VLESS",
                address = "us-east.aerovpn.example.com",
                port = 443,
                uuidOrPassword = "71598a90-db43-4b53-97c8-e73aed460433",
                sni = "yahoo.com",
                publicKey = "e5g1_f_gPuygqSgPhf9_f7f_R8R67gfgP6R5R4fgR78",
                flow = "xtls-rprx-vision",
                security = "reality",
                isCustom = true,
                isActive = true
            ),
            VpnServerEntity(
                name = "Aero-Premium-Germany [Trojan-TLS]",
                protocol = "TROJAN",
                address = "de.aerovpn.example.com",
                port = 443,
                uuidOrPassword = "aero_luxury_pass_77",
                sni = "google.de",
                security = "tls",
                isCustom = true,
                isActive = false
            ),
            VpnServerEntity(
                name = "Aero-Optimized-Japan [VMess-TLS]",
                protocol = "VMESS",
                address = "jp.aerovpn.example.com",
                port = 8443,
                uuidOrPassword = "3ff2977d-7bf2-4da4-8ac7-e85d9bf1eb62",
                sni = "line.me",
                security = "tls",
                isCustom = true,
                isActive = false
            )
        )
        for (node in defaults) {
            repository.addManualServer(node)
        }
    }

    // Load actual device applications for split-tunnel lists
    private suspend fun loadDeviceApps() = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val list = mutableListOf<DeviceApp>()

        for (pkg in packages) {
            // Skip services / non-launchers to produce a clean list of actual applications
            val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
            if (launchIntent != null) {
                val appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                list.add(DeviceApp(name = appName, packageName = pkg.packageName))
            }
        }
        
        // Sort alphabetically
        list.sortBy { it.name.lowercase() }
        _deviceApps.value = list
    }

    enum class Tab {
        DASHBOARD,
        SERVERS,
        SUBSCRIPTIONS,
        SETTINGS
    }
}

data class DeviceApp(
    val name: String,
    val packageName: String
)
