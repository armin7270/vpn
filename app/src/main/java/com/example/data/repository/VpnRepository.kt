package com.example.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.database.VpnDao
import com.example.data.database.VpnServerEntity
import com.example.data.database.VpnSubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class VpnRepository(private val vpnDao: VpnDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val allServersFlow: Flow<List<VpnServerEntity>> = vpnDao.getAllServersFlow()
    val activeServerFlow: Flow<VpnServerEntity?> = vpnDao.getActiveServerFlow()
    val allSubscriptionsFlow: Flow<List<VpnSubscriptionEntity>> = vpnDao.getAllSubscriptionsFlow()

    suspend fun getActiveServer(): VpnServerEntity? = vpnDao.getActiveServer()

    suspend fun setActiveServer(id: Int) = withContext(Dispatchers.IO) {
        vpnDao.setActiveServer(id)
    }

    suspend fun deleteServer(id: Int) = withContext(Dispatchers.IO) {
        vpnDao.deleteServerById(id)
    }

    suspend fun addManualServer(server: VpnServerEntity) = withContext(Dispatchers.IO) {
        vpnDao.insertServer(server)
    }

    suspend fun addSubscription(name: String, url: String) = withContext(Dispatchers.IO) {
        val sub = VpnSubscriptionEntity(url = url, name = name, lastUpdated = System.currentTimeMillis())
        vpnDao.insertSubscription(sub)
        refreshSubscription(url)
    }

    suspend fun deleteSubscription(url: String) = withContext(Dispatchers.IO) {
        vpnDao.deleteSubscriptionByUrl(url)
        vpnDao.deleteServersBySubscription(url)
    }

    suspend fun refreshAllSubscriptions() = withContext(Dispatchers.IO) {
        val subs = vpnDao.getAllSubscriptions()
        for (sub in subs) {
            try {
                refreshSubscription(sub.url)
            } catch (e: Exception) {
                Log.e("VpnRepository", "Error updating subscription: ${sub.url}", e)
            }
        }
    }

    suspend fun refreshSubscription(url: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to fetch subscription: code ${response.code}")
                val bodyText = response.body?.string() ?: ""
                val decodedText = decodeBase64Safe(bodyText)
                
                // Parse lines
                val servers = mutableListOf<VpnServerEntity>()
                val lines = decodedText.split(Regex("[\\n\\r]+"))
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val parsed = parseVpnUri(trimmed, isCustom = false, subscriptionUrl = url)
                    if (parsed != null) {
                        servers.add(parsed)
                    }
                }

                if (servers.isNotEmpty()) {
                    // Delete old ones for this sub and insert new ones
                    vpnDao.deleteServersBySubscription(url)
                    vpnDao.insertServers(servers)
                    
                    // Mark subscription as updated
                    val currentSub = vpnDao.getAllSubscriptions().find { it.url == url }
                    if (currentSub != null) {
                        vpnDao.insertSubscription(currentSub.copy(lastUpdated = System.currentTimeMillis()))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VpnRepository", "Failed refreshSubscription $url", e)
            throw e
        }
    }

    // Ping check using raw TCP socket connection (highly responsive and real-world VPN diagnostic)
    suspend fun checkServerPing(serverId: Int, host: String, port: Int): Int = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var ping = -1
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500) // 2.5s timeout
                ping = (System.currentTimeMillis() - start).toInt()
            }
        } catch (e: Exception) {
            Log.d("VpnRepository", "Ping failed for $host:$port -> ${e.localizedMessage}")
        }
        vpnDao.updateServerPing(serverId, ping)
        ping
    }

    suspend fun autoSelectBestServer() = withContext(Dispatchers.IO) {
        val servers = vpnDao.getAllServers()
        if (servers.isEmpty()) return@withContext

        // Ping all servers and find the lowest ping server
        var bestId = -1
        var lowestPing = Int.MAX_VALUE

        for (server in servers) {
            val p = checkServerPing(server.id, server.address, server.port)
            if (p in 1 until lowestPing) {
                lowestPing = p
                bestId = server.id
            }
        }

        if (bestId != -1) {
            setActiveServer(bestId)
        } else if (servers.isNotEmpty()) {
            setActiveServer(servers.first().id)
        }
    }

    // Decodes base64 with support for missing padding, newlines, and URL-safe variants
    private fun decodeBase64Safe(input: String): String {
        val trimmed = input.trim().replace("\r", "").replace("\n", "")
        if (trimmed.isEmpty()) return ""
        return try {
            // Check if it is a list of plain lines (starts with protocol prefix like vmess://)
            if (trimmed.startsWith("vmess://") || trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
                return input // already decoded/plain text lines
            }
            val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
            String(decodedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                val decodedBytes = Base64.decode(trimmed, Base64.URL_SAFE or Base64.NO_PADDING)
                String(decodedBytes, StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                // If it looks like base64 but fails, return empty, or return input as fallback
                input
            }
        }
    }

    fun parseVpnUri(uri: String, isCustom: Boolean = true, subscriptionUrl: String? = null): VpnServerEntity? {
        val trimmed = uri.trim()
        try {
            if (trimmed.startsWith("vmess://")) {
                val base64Json = trimmed.substring("vmess://".length)
                val jsonString = String(Base64.decode(base64Json, Base64.DEFAULT), StandardCharsets.UTF_8)
                val json = JSONObject(jsonString)
                
                val name = json.optString("ps", "VMess Node")
                val address = json.optString("add", "")
                val port = json.optInt("port", 443)
                val id = json.optString("id", "")
                val sni = json.optString("sni", "")
                val security = if (json.optString("tls", "").isNotEmpty()) "tls" else "none"

                if (address.isEmpty() || id.isEmpty()) return null

                return VpnServerEntity(
                    name = name,
                    protocol = "VMESS",
                    address = address,
                    port = port,
                    uuidOrPassword = id,
                    sni = sni,
                    security = security,
                    isCustom = isCustom,
                    subscriptionUrl = subscriptionUrl
                )
            } else if (trimmed.startsWith("vless://")) {
                // vless://uuid@host:port?query#name
                val indexAtom = trimmed.indexOf("@")
                if (indexAtom == -1) return null
                val uuid = trimmed.substring("vless://".length, indexAtom)
                
                val indexSlash = trimmed.indexOf("/", indexAtom)
                val indexQuest = trimmed.indexOf("?", indexAtom)
                val indexHash = trimmed.indexOf("#", indexAtom)

                val hostPortEnd = when {
                    indexQuest != -1 -> indexQuest
                    indexHash != -1 -> indexHash
                    indexSlash != -1 -> indexSlash
                    else -> trimmed.length
                }

                val hostPort = trimmed.substring(indexAtom + 1, hostPortEnd)
                val hostPortParts = hostPort.split(":")
                if (hostPortParts.size < 2) return null
                val address = hostPortParts[0]
                val port = hostPortParts[1].toIntOrNull() ?: 443

                var name = "VLESS Node"
                if (indexHash != -1) {
                    name = URLDecoder.decode(trimmed.substring(indexHash + 1), "UTF-8")
                }

                var sni = ""
                var publicKey = ""
                var flow = ""
                var security = "none"

                if (indexQuest != -1) {
                    val queryEnd = if (indexHash != -1) indexHash else trimmed.length
                    val query = trimmed.substring(indexQuest + 1, queryEnd)
                    val params = query.split("&")
                    for (param in params) {
                        val kv = param.split("=")
                        if (kv.size == 2) {
                            val key = kv[0].lowercase()
                            val value = URLDecoder.decode(kv[1], "UTF-8")
                            when (key) {
                                "sni" -> sni = value
                                "pbk" -> publicKey = value
                                "flow" -> flow = value
                                "security" -> security = value
                            }
                        }
                    }
                }

                return VpnServerEntity(
                    name = name,
                    protocol = "VLESS",
                    address = address,
                    port = port,
                    uuidOrPassword = uuid,
                    sni = sni,
                    publicKey = publicKey,
                    flow = flow,
                    security = security,
                    isCustom = isCustom,
                    subscriptionUrl = subscriptionUrl
                )
            } else if (trimmed.startsWith("trojan://")) {
                // trojan://password@host:port?query#name
                val indexAtom = trimmed.indexOf("@")
                if (indexAtom == -1) return null
                val password = trimmed.substring("trojan://".length, indexAtom)

                val indexSlash = trimmed.indexOf("/", indexAtom)
                val indexQuest = trimmed.indexOf("?", indexAtom)
                val indexHash = trimmed.indexOf("#", indexAtom)

                val hostPortEnd = when {
                    indexQuest != -1 -> indexQuest
                    indexHash != -1 -> indexHash
                    indexSlash != -1 -> indexSlash
                    else -> trimmed.length
                }

                val hostPort = trimmed.substring(indexAtom + 1, hostPortEnd)
                val hostPortParts = hostPort.split(":")
                if (hostPortParts.size < 2) return null
                val address = hostPortParts[0]
                val port = hostPortParts[1].toIntOrNull() ?: 443

                var name = "Trojan Node"
                if (indexHash != -1) {
                    name = URLDecoder.decode(trimmed.substring(indexHash + 1), "UTF-8")
                }

                var sni = ""
                var security = "none"

                if (indexQuest != -1) {
                    val queryEnd = if (indexHash != -1) indexHash else trimmed.length
                    val query = trimmed.substring(indexQuest + 1, queryEnd)
                    val params = query.split("&")
                    for (param in params) {
                        val kv = param.split("=")
                        if (kv.size == 2) {
                            val key = kv[0].lowercase()
                            val value = URLDecoder.decode(kv[1], "UTF-8")
                            when (key) {
                                "sni" -> sni = value
                                "security" -> security = value
                            }
                        }
                    }
                }

                return VpnServerEntity(
                    name = name,
                    protocol = "TROJAN",
                    address = address,
                    port = port,
                    uuidOrPassword = password,
                    sni = sni,
                    security = security,
                    isCustom = isCustom,
                    subscriptionUrl = subscriptionUrl
                )
            }
        } catch (e: Exception) {
            Log.e("VpnRepository", "Error parsing URI $uri", e)
        }
        return null
    }

    // Settings helpers
    suspend fun saveSetting(key: String, value: String) {
        vpnDao.insertSetting(com.example.data.database.SettingEntity(key, value))
    }

    suspend fun getSetting(key: String): String? {
        return vpnDao.getSettingValue(key)
    }
}
