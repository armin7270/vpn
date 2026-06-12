package com.example.service

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class VpnServiceState(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val serverName: String = "",
    val protocol: String = "",
    val downloadSpeed: Long = 0, // bytes/sec
    val uploadSpeed: Long = 0,   // bytes/sec
    val totalDownload: Long = 0, // total bytes
    val totalUpload: Long = 0,   // total bytes
    val ping: Int = -1,
    val connectionDuration: Long = 0 // in seconds
)
