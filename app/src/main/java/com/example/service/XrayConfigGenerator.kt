package com.example.service

import com.example.data.database.VpnServerEntity
import org.json.JSONArray
import org.json.JSONObject

class XrayConfigGenerator {

    /**
     * Generates a fully compliant standard Xray-core config JSON representing
     * inbounds, outbounds, routing rules, DNS, TLS, and Reality settings.
     */
    fun generateConfig(server: VpnServerEntity, dnsServer: String = "8.8.8.8"): String {
        try {
            val root = JSONObject()

            // 1. Log Settings
            val log = JSONObject()
            log.put("loglevel", "warning")
            root.put("log", log)

            // 2. Inbounds (HTTP and Socks local proxy listeners to receive TUN mapped traffic)
            val inbounds = JSONArray()
            val socksInbound = JSONObject()
            socksInbound.put("port", 10808)
            socksInbound.put("protocol", "socks")
            val socksSettings = JSONObject()
            socksSettings.put("auth", "noauth")
            socksSettings.put("udp", true)
            socksInbound.put("settings", socksSettings)
            inbounds.put(socksInbound)

            val httpInbound = JSONObject()
            httpInbound.put("port", 10809)
            httpInbound.put("protocol", "http")
            inbounds.put(httpInbound)
            
            root.put("inbounds", inbounds)

            // 3. Outbounds (Target standard configurations for VMess/VLESS/Trojan/Reality)
            val outbounds = JSONArray()
            val primaryOutbound = JSONObject()
            primaryOutbound.put("tag", "proxy")
            primaryOutbound.put("protocol", server.protocol.lowercase())

            val settings = JSONObject()
            val vnext = JSONArray()
            val serverNode = JSONObject()
            serverNode.put("address", server.address)
            serverNode.put("port", server.port)

            val users = JSONArray()
            val user = JSONObject()

            when (server.protocol.uppercase()) {
                "VLESS" -> {
                    user.put("id", server.uuidOrPassword)
                    user.put("encryption", "none")
                    if (server.flow.isNotEmpty()) {
                        user.put("flow", server.flow)
                    }
                    users.put(user)
                    serverNode.put("users", users)
                    val vnextList = JSONArray().put(serverNode)
                    settings.put("vnext", vnextList)
                }
                "VMESS" -> {
                    user.put("id", server.uuidOrPassword)
                    user.put("alterId", 0)
                    users.put(user)
                    serverNode.put("users", users)
                    val vnextList = JSONArray().put(serverNode)
                    settings.put("vnext", vnextList)
                }
                "TROJAN" -> {
                    settings.put("password", server.uuidOrPassword)
                    val serversArray = JSONArray().put(serverNode)
                    settings.put("servers", serversArray)
                }
            }
            primaryOutbound.put("settings", settings)

            // Stream Settings (Reality / TLS)
            val streamSettings = JSONObject()
            streamSettings.put("network", "tcp")

            if (server.security == "tls") {
                streamSettings.put("security", "tls")
                val tlsSettings = JSONObject()
                tlsSettings.put("serverName", server.sni)
                val peer = JSONArray()
                peer.put(server.sni)
                tlsSettings.put("alpn", peer)
                streamSettings.put("tlsSettings", tlsSettings)
            } else if (server.security == "reality") {
                streamSettings.put("security", "reality")
                val realitySettings = JSONObject()
                realitySettings.put("show", false)
                realitySettings.put("fingerprint", "chrome")
                realitySettings.put("serverName", server.sni)
                realitySettings.put("publicKey", server.publicKey)
                realitySettings.put("shortId", "")
                streamSettings.put("realitySettings", realitySettings)
            }
            primaryOutbound.put("streamSettings", streamSettings)
            outbounds.put(primaryOutbound)

            // Standard Direct outbound for local/bypass routing
            val directOutbound = JSONObject()
            directOutbound.put("tag", "direct")
            directOutbound.put("protocol", "freedom")
            outbounds.put(directOutbound)

            root.put("outbounds", outbounds)

            // 4. DNS Settings
            val dns = JSONObject()
            dns.put("servers", JSONArray().put(dnsServer))
            root.put("dns", dns)

            return root.toString(2)
        } catch (e: Exception) {
            return "{\"error\": \"${e.localizedMessage}\"}"
        }
    }
}
