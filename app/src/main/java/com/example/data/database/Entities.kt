package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_servers")
data class VpnServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val protocol: String, // VLESS, TROJAN, VMESS
    val address: String,
    val port: Int,
    val uuidOrPassword: String,
    val sni: String = "",
    val publicKey: String = "",
    val flow: String = "",
    val security: String = "none", // none, tls, reality
    val ping: Int = -1,
    val isCustom: Boolean = false,
    val subscriptionUrl: String? = null,
    val isActive: Boolean = false
)

@Entity(tableName = "vpn_subscriptions")
data class VpnSubscriptionEntity(
    @PrimaryKey val url: String,
    val name: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
