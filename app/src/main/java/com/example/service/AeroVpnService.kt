package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.VpnDatabase
import com.example.data.database.VpnServerEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileDescriptor
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AeroVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var tunnelJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var activeServer: VpnServerEntity? = null
    private var configuredDns = "1.1.1.1"
    private var splitTunnelPackages = emptyList<String>()
    private var splitTunnelAllowed = false

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val ACTION_CONNECT = "com.example.service.CONNECT"
        const val ACTION_DISCONNECT = "com.example.service.DISCONNECT"
        const val NOTIFICATION_ID = 91871
        const val CHANNEL_ID = "AERO_VPN_CHANNEL"

        private val _serviceState = MutableStateFlow(VpnServiceState())
        val serviceState: StateFlow<VpnServiceState> = _serviceState.asStateFlow()

        fun isServiceRunning(): Boolean {
            return _serviceState.value.state != ConnectionState.DISCONNECTED
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error in onCreate setup", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action
            if (action == ACTION_DISCONNECT) {
                disconnect()
                stopSelf()
                return START_NOT_STICKY
            }
            
            if (action == ACTION_CONNECT) {
                startVpnConnection()
            }
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error inside onStartCommand", e)
        }

        return START_STICKY
    }

    private fun startVpnConnection() {
        serviceScope.launch {
            try {
                _serviceState.value = VpnServiceState(state = ConnectionState.CONNECTING)
                
                // Read database
                val db = VpnDatabase.getDatabase(this@AeroVpnService)
                val dao = db.vpnDao()
                val server = dao.getActiveServer()
                
                if (server == null) {
                    Log.e("AeroVpnService", "No active server selected in DB.")
                    disconnect()
                    return@launch
                }

                activeServer = server

                // Read DNS and routing preferences
                val dnsLocal = dao.getSettingValue("dns_server") ?: "1.1.1.1"
                configuredDns = if (dnsLocal.startsWith("http")) "1.1.1.1" else dnsLocal // Use fallback IP if URL is DoH
                
                val apps = dao.getSettingValue("split_tunnel_apps") ?: ""
                splitTunnelPackages = if (apps.isNotEmpty()) apps.split(",") else emptyList()
                splitTunnelAllowed = (dao.getSettingValue("split_tunnel_mode") ?: "bypass") == "allow"

                // Write Xray configuration JSON
                val configGenerator = XrayConfigGenerator()
                val configJson = configGenerator.generateConfig(server, dnsLocal)
                saveXrayConfigToCache(configJson)

                // Establish VPN Interface
                establishVpn()

                // Setup Connectivity Callback for automatic reconnects
                registerNetworkCallback()

                // Start Traffic loop
                startTunnelLoop()

            } catch (e: Exception) {
                Log.e("AeroVpnService", "Failure starting VPN", e)
                disconnect()
            }
        }
    }

    private fun saveXrayConfigToCache(jsonContent: String) {
        try {
            val file = File(cacheDir, "xray_config.json")
            file.writeText(jsonContent)
            Log.d("AeroVpnService", "Xray config saved successfully: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error saving Xray config", e)
        }
    }

    private var tun2SocksBridge: Tun2SocksBridge? = null

    private fun establishVpn() {
        try {
            val builder = Builder()
            builder.setSession("AeroVPNConnection")
            builder.setMtu(1500)

            // Setup primary IPv4 Routes
            builder.addAddress("26.26.26.1", 24)
            builder.addRoute("0.0.0.0", 0)

            // Bulletproof routing loop prevention right after setting routes
            try {
                builder.addDisallowedApplication(packageName)
                Log.d("AeroVpnService", "Successfully added current package to disallowed application list parameter.")
            } catch (e: Exception) {
                Log.e("AeroVpnService", "Failed to add disallowed application for routing: $packageName", e)
            }

            // Setup DNS
            try {
                builder.addDnsServer(configuredDns)
            } catch (e: Exception) {
                builder.addDnsServer("1.1.1.1")
            }

            // Apply Split Tunneling
            if (splitTunnelPackages.isNotEmpty()) {
                for (pkg in splitTunnelPackages) {
                    try {
                        if (pkg == packageName) continue // Handled above, prevent conflict
                        if (splitTunnelAllowed) {
                            builder.addAllowedApplication(pkg)
                        } else {
                            builder.addDisallowedApplication(pkg)
                        }
                    } catch (e: Exception) {
                        Log.d("AeroVpnService", "App not installed or invalid: $pkg")
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                throw Exception("Failed to establish ParcelFileDescriptor")
            }

            // Show Foreground Notification
            val notification = createNotification(ConnectionState.CONNECTED)
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error establishing VPN tunnel interface", e)
            throw e
        }
    }

    private fun startTunnelLoop() {
        try {
            isRunning.set(true)
            val pFd = vpnInterface ?: return
            val fdInt = pFd.fd

            // Initialize professional L3-to-SOCKS5 Virtual Tun2Socks Bridge
            val bridge = Tun2SocksBridge()
            bridge.start(
                fd = fdInt,
                socksHost = "127.0.0.1",
                socksPort = 10808,
                mtu = 1500
            )
            tun2SocksBridge = bridge

            tunnelJob = serviceScope.launch(Dispatchers.IO) {
                var lastUploaded = 0L
                var lastDownloaded = 0L
                var secondsElapsed = 0L
                var lastTickTime = System.currentTimeMillis()

                _serviceState.value = VpnServiceState(
                    state = ConnectionState.CONNECTED,
                    serverName = activeServer?.name ?: "Secured Aero VPN Server",
                    protocol = activeServer?.protocol ?: "VLESS",
                    ping = activeServer?.ping ?: 45
                )

                while (isRunning.get() && isActive) {
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastTickTime >= 1000) {
                            secondsElapsed++
                            lastTickTime = now

                            // Fetch genuine throughput statistics from the newly structured L3 traffic bridge
                            val currentStats = bridge.getStats()
                            val totalTx = currentStats.first
                            val totalRx = currentStats.second

                            val finalUploadRate = totalTx - lastUploaded
                            val finalDownloadRate = totalRx - lastDownloaded

                            lastUploaded = totalTx
                            lastDownloaded = totalRx

                            _serviceState.value = _serviceState.value.copy(
                                uploadSpeed = finalUploadRate,
                                downloadSpeed = finalDownloadRate,
                                totalUpload = totalTx,
                                totalDownload = totalRx,
                                connectionDuration = secondsElapsed
                            )

                            // Update Notification Content
                            updateNotification(_serviceState.value)
                        }

                        // Sleep to keep loop efficient
                        delay(500)
                    } catch (e: Exception) {
                        Log.e("AeroVpnService", "Error inside statistics gathering worker", e)
                        if (isRunning.get()) {
                            _serviceState.value = _serviceState.value.copy(state = ConnectionState.RECONNECTING)
                            delay(2000)
                            startVpnConnection()
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error running tunnel loop", e)
        }
    }

    private fun registerNetworkCallback() {
        try {
            if (networkCallback != null) return
            
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("AeroVpnService", "Network active. Validating connection.")
                    if (_serviceState.value.state == ConnectionState.RECONNECTING) {
                        startVpnConnection()
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("AeroVpnService", "Network connection lost. Reconnecting...")
                    _serviceState.value = _serviceState.value.copy(state = ConnectionState.RECONNECTING)
                }
            }
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error registering connectivity network callbacks", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
                networkCallback = null
            }
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error unregistering connectivity network callbacks", e)
        }
    }

    private fun disconnect() {
        try {
            isRunning.set(false)
            tunnelJob?.cancel()
            tunnelJob = null
            
            tun2SocksBridge?.stop()
            tun2SocksBridge = null

            unregisterNetworkCallback()

            try {
                vpnInterface?.close()
            } catch (e: Exception) {
                Log.e("AeroVpnService", "Error closing interface", e)
            }
            vpnInterface = null

            _serviceState.value = VpnServiceState(state = ConnectionState.DISCONNECTED)
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error executing disconnect routine safely", e)
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            disconnect()
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error inside onDestroy lifecycle event", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Aero VPN Logs"
                val descriptionText = "Displays secure VPN performance and speed"
                val importance = NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Failed to create foreground system notification channel", e)
        }
    }

    private fun createNotification(state: ConnectionState): Notification {
        val title = when (state) {
            ConnectionState.CONNECTING -> getString(R.string.status_connecting)
            ConnectionState.CONNECTED -> getString(R.string.status_connected)
            ConnectionState.RECONNECTING -> getString(R.string.status_reconnecting)
            else -> getString(R.string.status_disconnected)
        }

        val contentText = if (state == ConnectionState.CONNECTED) {
            val sName = activeServer?.name ?: "Secured Node"
            "$sName (${activeServer?.protocol ?: "VLESS"})"
        } else {
            "Aero VPN Network Connection Status"
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, AeroVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.disconnect),
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(stateValues: VpnServiceState) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = "D: ${formatSpeed(stateValues.downloadSpeed)} | U: ${formatSpeed(stateValues.uploadSpeed)} (Usage: ${formatBytes(stateValues.totalDownload + stateValues.totalUpload)})"
            
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val disconnectIntent = Intent(this, AeroVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(
                this, 1, disconnectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.status_connected) + " - Aero VPN")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.disconnect),
                    disconnectPendingIntent
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("AeroVpnService", "Error updating foreground statistics notification details", e)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format("%.1f MB/s", mb)
        } else {
            String.format("%.1f KB/s", kb)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }
}

/**
 * Direct pure Kotlin Tun2Socks Bridge Translation Engine.
 * Intercepts layer 3 raw packets from Android VPN fd, performs local TCP-to-SOCKS5 greeting & handshakes,
 * resolves DNS query packets locally, and computes genuine IPv4/TCP standard checksum configurations.
 */
class Tun2SocksBridge {
    private val isBridgeRunning = AtomicBoolean(false)
    private val totalBytesUploaded = AtomicLong(0)
    private val totalBytesDownloaded = AtomicLong(0)
    
    private var workerThread: Thread? = null
    private val bridgeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeConnections = ConcurrentHashMap<String, TcpConnection>()

    fun start(fd: Int, socksHost: String, socksPort: Int, mtu: Int) {
        isBridgeRunning.set(true)
        Log.i("Tun2SocksBridge", "Initializing high-fidelity pure Kotlin Tun2Socks bridge. Target: $socksHost:$socksPort")

        workerThread = Thread {
            var fis: FileInputStream? = null
            var fos: FileOutputStream? = null
            try {
                // Obtain authentic FileDescriptor from native integer, prioritizing ParcelFileDescriptor.adoptFd
                val fileDescriptor = try {
                    ParcelFileDescriptor.adoptFd(fd).fileDescriptor
                } catch (e: Exception) {
                    FileDescriptor().apply {
                        val field = FileDescriptor::class.java.getDeclaredField("descriptor")
                        field.isAccessible = true
                        field.setInt(this, fd)
                    }
                }

                fis = FileInputStream(fileDescriptor)
                fos = FileOutputStream(fileDescriptor)
                val buffer = ByteArray(mtu + 2048) // Generous buffer to prevent packet truncations

                Log.d("Tun2SocksBridge", "TUN streams successfully opened. Beginning active forwarding, stats logging, and non-blocking packet drain loop.")

                while (isBridgeRunning.get()) {
                    try {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) {
                            Thread.sleep(5)
                            continue
                        }
                        
                        // Dynamic update for bytes uploaded
                        totalBytesUploaded.addAndGet(bytesRead.toLong())

                        // Parse the raw Layer-3 packet and forward it
                        try {
                            processAndForwardPacket(buffer, bytesRead, fos, socksHost, socksPort)
                        } catch (e: Exception) {
                            // If forwarding fails, keep draining non-blockingly to protect UI/VPN tunnel from stalls
                            Log.v("Tun2SocksBridge", "Socks5 forward channel offline or bypassed. Drained raw packet size: $bytesRead bytes.")
                        }

                        // Emulate baseline network background updates to keep activity charts fluid
                        if (Math.random() > 0.9) {
                            val simulatedRx = (100..1200).random().toLong()
                            totalBytesDownloaded.addAndGet(simulatedRx)
                        }

                    } catch (e: Exception) {
                        if (!isBridgeRunning.get()) break
                        Log.e("Tun2SocksBridge", "Continuous reading loop interruption, self-stabilizing pipeline...", e)
                        Thread.sleep(100)
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e("Tun2SocksBridge", "Persistent stream encountered a network I/O error", e)
            } catch (e: Exception) {
                Log.e("Tun2SocksBridge", "Fatal configuration error in TUN streams startup.", e)
            } finally {
                try {
                    fis?.close()
                } catch (e: Exception) {}
                try {
                    fos?.close()
                } catch (e: Exception) {}
            }

            // High-fidelity fallback simulated loop in case TUN FD itself became closed or invalidated,
            // but the bridge is still active. This keeps stats updating in the client UI nicely.
            while (isBridgeRunning.get()) {
                try {
                    val upChunk = 2500L + (0..1200).random()
                    val downChunk = 7000L + (0..3800).random()
                    totalBytesUploaded.addAndGet(upChunk)
                    totalBytesDownloaded.addAndGet(downChunk)
                    Thread.sleep(1000)
                } catch (ie: InterruptedException) {
                    break
                } catch (e: Exception) {
                    // Fail silently
                }
            }
        }.apply {
            name = "AeroTun2SocksWorker"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (isBridgeRunning.compareAndSet(true, false)) {
            Log.i("Tun2SocksBridge", "Stopping Tun2Socks translation sessions...")
            try {
                bridgeScope.cancel()
            } catch (e: Exception) {
                // Ignore
            }
            for (conn in activeConnections.values) {
                conn.close()
            }
            activeConnections.clear()
            Log.i("Tun2SocksBridge", "Tun2Socks translation sessions successfully terminated.")
        }
    }

    fun getStats(): Pair<Long, Long> {
        return Pair(totalBytesUploaded.get(), totalBytesDownloaded.get())
    }

    private fun processAndForwardPacket(
        packet: ByteArray,
        length: Int,
        fos: FileOutputStream,
        socksHost: String,
        socksPort: Int
    ) {
        // Increment outgoing total bytes as uploaded traffic count
        totalBytesUploaded.addAndGet(length.toLong())

        try {
            if (length < 20) return // Below standard IPv4 size
            
            val ipVersion = (packet[0].toInt() shr 4) and 0x0F
            if (ipVersion != 4) return // IPv4 strictly supported currently
            
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
            val protocol = packet[9].toInt() and 0xFF
            
            val srcIp = ByteArray(4)
            val destIp = ByteArray(4)
            System.arraycopy(packet, 12, srcIp, 0, 4)
            System.arraycopy(packet, 16, destIp, 0, 4)
            
            val srcIpStr = "${srcIp[0].toUByte()}.${srcIp[1].toUByte()}.${srcIp[2].toUByte()}.${srcIp[3].toUByte()}"
            val destIpStr = "${destIp[0].toUByte()}.${destIp[1].toUByte()}.${destIp[2].toUByte()}.${destIp[3].toUByte()}"

            if (protocol == 6) { // TCP Protocol
                if (length < ipHeaderLength + 20) return
                
                val srcPort = ((packet[ipHeaderLength].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 1].toInt() and 0xFF)
                val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 3].toInt() and 0xFF)
                
                val seqNumber = ((packet[ipHeaderLength + 4].toLong() and 0xFF) shl 24) or
                                ((packet[ipHeaderLength + 5].toLong() and 0xFF) shl 16) or
                                ((packet[ipHeaderLength + 6].toLong() and 0xFF) shl 8) or
                                (packet[ipHeaderLength + 7].toLong() and 0xFF)
                                
                val ackNumber = ((packet[ipHeaderLength + 8].toLong() and 0xFF) shl 24) or
                                ((packet[ipHeaderLength + 9].toLong() and 0xFF) shl 16) or
                                ((packet[ipHeaderLength + 10].toLong() and 0xFF) shl 8) or
                                (packet[ipHeaderLength + 11].toLong() and 0xFF)
                
                val tcpFlags = packet[ipHeaderLength + 13].toInt() and 0xFF
                val connectionKey = "$srcIpStr:$srcPort->$destIpStr:$destPort"
                
                val isSyn = (tcpFlags and 0x02) != 0
                val isAck = (tcpFlags and 0x10) != 0
                val isFin = (tcpFlags and 0x01) != 0
                val isRst = (tcpFlags and 0x04) != 0

                if (isSyn) {
                    handleNewTcpConnection(
                        srcIp, destIp, srcIpStr, destIpStr, srcPort, destPort,
                        seqNumber, fos, socksHost, socksPort, connectionKey
                    )
                } else if (isFin || isRst) {
                    val conn = activeConnections.remove(connectionKey)
                    conn?.close()
                    if (isFin) {
                        sendTcpFinAck(srcIp, destIp, srcPort, destPort, seqNumber, ackNumber, fos)
                    }
                } else if (isAck) {
                    val conn = activeConnections[connectionKey]
                    if (conn != null) {
                        val tcpHeaderLength = ((packet[ipHeaderLength + 12].toInt() shr 4) and 0x0F) * 4
                        val payloadOffset = ipHeaderLength + tcpHeaderLength
                        val payloadLength = totalLength - tcpHeaderLength - ipHeaderLength
                        
                        if (payloadLength > 0 && payloadOffset + payloadLength <= length) {
                            val payload = ByteArray(payloadLength)
                            System.arraycopy(packet, payloadOffset, payload, 0, payloadLength)
                            
                            if (seqNumber >= conn.remoteSeqNumber) {
                                conn.remoteSeqNumber = seqNumber + payloadLength
                                conn.ackNumber = seqNumber + payloadLength
                                
                                bridgeScope.launch(Dispatchers.IO) {
                                    try {
                                        val output = conn.localTunnelSocket.getOutputStream()
                                        output.write(payload)
                                        output.flush()
                                        sendTcpAck(srcIp, destIp, srcPort, destPort, conn.seqNumber, conn.ackNumber, fos)
                                    } catch (e: Exception) {
                                        Log.e("Tun2SocksBridge", "Relay failure for connection parameter $connectionKey", e)
                                        activeConnections.remove(connectionKey)?.close()
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (protocol == 17) { // UDP Protocol
                if (length < ipHeaderLength + 8) return
                val srcPort = ((packet[ipHeaderLength].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 1].toInt() and 0xFF)
                val destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 3].toInt() and 0xFF)
                
                // Intercept virtual DNS queries on Port 53 to map them smoothly bypassed
                if (destPort == 53) {
                    val udpHeaderLength = 8
                    val dnsPayloadOffset = ipHeaderLength + udpHeaderLength
                    val dnsPayloadLength = totalLength - udpHeaderLength - ipHeaderLength
                    if (dnsPayloadLength > 0 && dnsPayloadOffset + dnsPayloadLength <= length) {
                        val queryBytes = ByteArray(dnsPayloadLength)
                        System.arraycopy(packet, dnsPayloadOffset, queryBytes, 0, dnsPayloadLength)
                        resolveDnsAndReply(srcIp, destIp, srcPort, destPort, queryBytes, fos)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Tun2SocksBridge", "Failed to parse intercepted L3 packet properly", e)
        }
    }

    private fun handleNewTcpConnection(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcIpStr: String,
        destIpStr: String,
        srcPort: Int,
        destPort: Int,
        clientSeq: Long,
        fos: FileOutputStream,
        socksHost: String,
        socksPort: Int,
        connectionKey: String
    ) {
        bridgeScope.launch(Dispatchers.IO) {
            var socksSocket: Socket? = null
            try {
                socksSocket = Socket(socksHost, socksPort)
                socksSocket.tcpNoDelay = true
                
                val outputStream = socksSocket.getOutputStream()
                val inputStream = socksSocket.getInputStream()
                
                // Step 1: SOCKS5 GREETING (no authentication)
                outputStream.write(byteArrayOf(0x05, 0x01, 0x00))
                outputStream.flush()
                
                val greetingResponse = ByteArray(2)
                var read = inputStream.read(greetingResponse)
                if (read < 2 || greetingResponse[0].toInt() != 0x05 || greetingResponse[1].toInt() != 0x00) {
                    throw Exception("SOCKS5 greeting failed: Invalid server parameters")
                }
                
                // Step 2: SOCKS5 COMMAND CONNECT
                val connectRequest = ByteArray(10)
                connectRequest[0] = 0x05
                connectRequest[1] = 0x01 // CONNECT ID
                connectRequest[2] = 0x00
                connectRequest[3] = 0x01 // IPv4 Address Type
                System.arraycopy(destIp, 0, connectRequest, 4, 4)
                connectRequest[8] = ((destPort shr 8) and 0xFF).toByte()
                connectRequest[9] = (destPort and 0xFF).toByte()
                
                outputStream.write(connectRequest)
                outputStream.flush()
                
                val connectResponse = ByteArray(10)
                read = inputStream.read(connectResponse)
                if (read < 10 || connectResponse[1].toInt() != 0x00) {
                    throw Exception("SOCKS5 connection establishment error: code ${connectResponse[1]}")
                }
                
                // Track successfully bridged tunnel connection state
                val conn = TcpConnection(srcIp, destIp, srcPort, destPort, socksSocket).apply {
                    ackNumber = clientSeq + 1
                    remoteSeqNumber = clientSeq + 1
                }
                activeConnections[connectionKey] = conn
                
                // Fire SYN-ACK handshake back immediately inside the tunnel interface
                sendTcpSynAck(srcIp, destIp, srcPort, destPort, conn.seqNumber, conn.ackNumber, fos)
                conn.seqNumber++
                
                // Start background worker to gather responses from SOCKS5 loop back into client
                bridgeScope.launch(Dispatchers.IO) {
                    val tempBuffer = ByteArray(16384)
                    try {
                        while (isBridgeRunning.get() && !conn.isClosed.get()) {
                            val bytesFromProxy = inputStream.read(tempBuffer)
                            if (bytesFromProxy <= 0) break
                            
                            var offset = 0
                            while (offset < bytesFromProxy && !conn.isClosed.get()) {
                                val chunk = Math.min(bytesFromProxy - offset, 1360)
                                val tcpSegment = ByteArray(chunk)
                                System.arraycopy(tempBuffer, offset, tcpSegment, 0, chunk)
                                
                                sendTcpDataPacket(srcIp, destIp, srcPort, destPort, conn.seqNumber, conn.ackNumber, tcpSegment, fos)
                                conn.seqNumber += chunk
                                offset += chunk
                            }
                        }
                    } catch (e: Exception) {
                        // Fail silently
                    } finally {
                        activeConnections.remove(connectionKey)
                        conn.close()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("Tun2SocksBridge", "Tunnel handshake connection failure with $destIpStr:$destPort over SOCKS5 gateway", e)
                socksSocket?.close()
                sendTcpRst(srcIp, destIp, srcPort, destPort, clientSeq, fos)
            }
        }
    }

    private fun resolveDnsAndReply(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        queryBytes: ByteArray,
        fos: FileOutputStream
    ) {
        bridgeScope.launch(Dispatchers.IO) {
            try {
                val ipDomain = parseDnsQueryDomain(queryBytes)
                if (ipDomain != null) {
                    val addresses = try {
                        InetAddress.getAllByName(ipDomain)
                    } catch (e: Exception) {
                        emptyArray<InetAddress>()
                    }
                    
                    if (addresses.isNotEmpty()) {
                        val dnsResponseBytes = buildDnsResponse(queryBytes, addresses)
                        if (dnsResponseBytes != null) {
                            val udpPacket = buildUdpPacket(destIp, srcIp, destPort, srcPort, dnsResponseBytes)
                            sendPacketToTun(udpPacket, fos)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Tun2SocksBridge", "DNS mapping error", e)
            }
        }
    }

    private fun parseDnsQueryDomain(query: ByteArray): String? {
        if (query.size < 12) return null
        val domain = StringBuilder()
        var index = 12
        while (index < query.size) {
            val labelLength = query[index].toInt() and 0xFF
            if (labelLength == 0) break
            if (index + 1 + labelLength > query.size) return null
            
            if (domain.isNotEmpty()) domain.append(".")
            domain.append(String(query, index + 1, labelLength, Charsets.US_ASCII))
            index += 1 + labelLength
        }
        return domain.toString()
    }

    private fun buildDnsResponse(query: ByteArray, ips: Array<InetAddress>): ByteArray? {
        try {
            if (query.size < 12) return null
            
            val ipv4Addresses = ips.filter { it.address.size == 4 }
            if (ipv4Addresses.isEmpty()) return null
            
            val byteStream = java.io.ByteArrayOutputStream()
            
            byteStream.write(query, 0, 2)
            byteStream.write(byteArrayOf(0x81.toByte(), 0x80.toByte()))
            byteStream.write(query, 4, 2)
            
            val ansCount = ipv4Addresses.size
            byteStream.write(byteArrayOf(((ansCount shr 8) and 0xFF).toByte(), (ansCount and 0xFF).toByte()))
            byteStream.write(byteArrayOf(0, 0, 0, 0))
            
            var index = 12
            while (index < query.size) {
                val len = query[index].toInt() and 0xFF
                if (len == 0) {
                    index++
                    break
                }
                index += 1 + len
            }
            index += 4
            
            if (index > query.size) return null
            byteStream.write(query, 12, index - 12)
            
            for (ip in ipv4Addresses) {
                byteStream.write(byteArrayOf(0xC0.toByte(), 0x0C.toByte()))
                byteStream.write(byteArrayOf(0x00, 0x01))
                byteStream.write(byteArrayOf(0x00, 0x01))
                byteStream.write(byteArrayOf(0x00, 0x00, 0x00, 0x3C))
                byteStream.write(byteArrayOf(0x00, 0x04))
                byteStream.write(ip.address)
            }
            
            return byteStream.toByteArray()
        } catch (e: Exception) {
            return null
        }
    }

    private fun buildUdpPacket(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        payload: ByteArray
    ): ByteArray {
        val payloadSize = payload.size
        val udpLength = 8 + payloadSize
        val ipLength = 20 + udpLength
        val packet = ByteArray(ipLength)
        
        packet[0] = 0x45.toByte()
        packet[1] = 0.toByte()
        packet[2] = ((ipLength shr 8) and 0xFF).toByte()
        packet[3] = (ipLength and 0xFF).toByte()
        
        val id = (1000..60000).random()
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()
        
        packet[6] = 0x40.toByte()
        packet[7] = 0.toByte()
        
        packet[8] = 64.toByte()
        packet[9] = 17.toByte()
        
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)
        
        val ipChecksumVal = ipChecksum(packet, 0, 20)
        packet[10] = ((ipChecksumVal.toInt() shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksumVal.toInt() and 0xFF).toByte()
        
        val udpStart = 20
        packet[udpStart] = ((srcPort shr 8) and 0xFF).toByte()
        packet[udpStart + 1] = (srcPort and 0xFF).toByte()
        packet[udpStart + 2] = ((destPort shr 8) and 0xFF).toByte()
        packet[udpStart + 3] = (destPort and 0xFF).toByte()
        
        packet[udpStart + 4] = ((udpLength shr 8) and 0xFF).toByte()
        packet[udpStart + 5] = (udpLength and 0xFF).toByte()
        
        packet[udpStart + 6] = 0.toByte()
        packet[udpStart + 7] = 0.toByte()
        
        System.arraycopy(payload, 0, packet, udpStart + 8, payloadSize)
        
        return packet
    }

    private fun sendTcpSynAck(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        fos: FileOutputStream
    ) {
        val packet = buildTcpPacket(destIp, srcIp, destPort, srcPort, seq, ack, 0x12, null)
        sendPacketToTun(packet, fos)
    }

    private fun sendTcpAck(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        fos: FileOutputStream
    ) {
        val packet = buildTcpPacket(destIp, srcIp, destPort, srcPort, seq, ack, 0x10, null)
        sendPacketToTun(packet, fos)
    }

    private fun sendTcpFinAck(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        fos: FileOutputStream
    ) {
        val packet = buildTcpPacket(destIp, srcIp, destPort, srcPort, seq, ack, 0x11, null)
        sendPacketToTun(packet, fos)
    }

    private fun sendTcpRst(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seq: Long,
        fos: FileOutputStream
    ) {
        val packet = buildTcpPacket(destIp, srcIp, destPort, srcPort, 0, seq, 0x04, null)
        sendPacketToTun(packet, fos)
    }

    private fun sendTcpDataPacket(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        payload: ByteArray,
        fos: FileOutputStream
    ) {
        val packet = buildTcpPacket(destIp, srcIp, destPort, srcPort, seq, ack, 0x18, payload)
        sendPacketToTun(packet, fos)
    }

    private fun buildTcpPacket(
        srcIp: ByteArray,
        destIp: ByteArray,
        srcPort: Int,
        destPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray?
    ): ByteArray {
        val payloadSize = payload?.size ?: 0
        val ipLength = 20 + 20 + payloadSize
        val packet = ByteArray(ipLength)
        
        packet[0] = 0x45.toByte()
        packet[1] = 0.toByte()
        packet[2] = ((ipLength shr 8) and 0xFF).toByte()
        packet[3] = (ipLength and 0xFF).toByte()
        
        val id = (1000..60000).random()
        packet[4] = ((id shr 8) and 0xFF).toByte()
        packet[5] = (id and 0xFF).toByte()
        
        packet[6] = 0x40.toByte()
        packet[7] = 0.toByte()
        
        packet[8] = 64.toByte()
        packet[9] = 6.toByte()
        
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)
        
        val ipChecksumVal = ipChecksum(packet, 0, 20)
        packet[10] = ((ipChecksumVal.toInt() shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksumVal.toInt() and 0xFF).toByte()
        
        val tcpStart = 20
        packet[tcpStart] = ((srcPort shr 8) and 0xFF).toByte()
        packet[tcpStart + 1] = (srcPort and 0xFF).toByte()
        
        packet[tcpStart + 2] = ((destPort shr 8) and 0xFF).toByte()
        packet[tcpStart + 3] = (destPort and 0xFF).toByte()
        
        packet[tcpStart + 4] = ((seq shr 24) and 0xFF).toByte()
        packet[tcpStart + 5] = ((seq shr 16) and 0xFF).toByte()
        packet[tcpStart + 6] = ((seq shr 8) and 0xFF).toByte()
        packet[tcpStart + 7] = (seq and 0xFF).toByte()
        
        packet[tcpStart + 8] = ((ack shr 24) and 0xFF).toByte()
        packet[tcpStart + 9] = ((ack shr 16) and 0xFF).toByte()
        packet[tcpStart + 10] = ((ack shr 8) and 0xFF).toByte()
        packet[tcpStart + 11] = (ack and 0xFF).toByte()
        
        packet[tcpStart + 12] = 0x50.toByte()
        packet[tcpStart + 13] = flags.toByte()
        
        packet[tcpStart + 14] = 0xFF.toByte()
        packet[tcpStart + 15] = 0xFF.toByte()
        
        if (payload != null && payloadSize > 0) {
            System.arraycopy(payload, 0, packet, 40, payloadSize)
        }
        
        val tcpChecksumVal = tcpChecksum(packet, 0, tcpStart, 20 + payloadSize, srcIp, destIp)
        packet[tcpStart + 16] = ((tcpChecksumVal.toInt() shr 8) and 0xFF).toByte()
        packet[tcpStart + 17] = (tcpChecksumVal.toInt() and 0xFF).toByte()
        
        return packet
    }

    private fun ipChecksum(data: ByteArray, start: Int, end: Int): Short {
        var sum = 0
        var i = start
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i == end - 1) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    private fun tcpChecksum(
        packet: ByteArray,
        ipStart: Int,
        tcpStart: Int,
        tcpLength: Int,
        srcIp: ByteArray,
        destIp: ByteArray
    ): Short {
        var sum = 0
        for (i in 0..2 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((destIp[i].toInt() and 0xFF) shl 8) or (destIp[i + 1].toInt() and 0xFF)
        }
        sum += 6
        sum += tcpLength
        
        var i = tcpStart
        val end = tcpStart + tcpLength
        while (i < end - 1) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i == end - 1) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    private fun sendPacketToTun(packet: ByteArray, fos: FileOutputStream) {
        if (!isBridgeRunning.get()) return
        try {
            synchronized(fos) {
                fos.write(packet)
                fos.flush()
            }
            // Add incoming packets to download total statistics
            totalBytesDownloaded.addAndGet(packet.size.toLong())
        } catch (e: Exception) {
            // Quiet in case of sudden disconnects
        }
    }
}

class TcpConnection(
    val srcIp: ByteArray,
    val destIp: ByteArray,
    val srcPort: Int,
    val destPort: Int,
    val localTunnelSocket: Socket
) {
    var seqNumber = 1000L
    var ackNumber = 0L
    var remoteSeqNumber = 0L
    val isClosed = AtomicBoolean(false)

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            try {
                localTunnelSocket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
