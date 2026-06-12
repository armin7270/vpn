package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDao {
    // Servers
    @Query("SELECT * FROM vpn_servers ORDER BY id DESC")
    fun getAllServersFlow(): Flow<List<VpnServerEntity>>

    @Query("SELECT * FROM vpn_servers")
    suspend fun getAllServers(): List<VpnServerEntity>

    @Query("SELECT * FROM vpn_servers WHERE isActive = 1 LIMIT 1")
    fun getActiveServerFlow(): Flow<VpnServerEntity?>

    @Query("SELECT * FROM vpn_servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): VpnServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: VpnServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<VpnServerEntity>)

    @Query("DELETE FROM vpn_servers WHERE id = :id")
    suspend fun deleteServerById(id: Int)

    @Query("DELETE FROM vpn_servers WHERE subscriptionUrl = :url")
    suspend fun deleteServersBySubscription(url: String)

    @Query("UPDATE vpn_servers SET isActive = 0")
    suspend fun deactivateAllServers()

    @Transaction
    suspend fun setActiveServer(serverId: Int) {
        deactivateAllServers()
        updateServerActiveStatus(serverId, true)
    }

    @Query("UPDATE vpn_servers SET isActive = :isActive WHERE id = :id")
    suspend fun updateServerActiveStatus(id: Int, isActive: Boolean)

    @Query("UPDATE vpn_servers SET ping = :ping WHERE id = :id")
    suspend fun updateServerPing(id: Int, ping: Int)

    @Query("DELETE FROM vpn_servers WHERE isCustom = 0")
    suspend fun clearSubscriptionServers()

    // Subscriptions
    @Query("SELECT * FROM vpn_subscriptions")
    fun getAllSubscriptionsFlow(): Flow<List<VpnSubscriptionEntity>>

    @Query("SELECT * FROM vpn_subscriptions")
    suspend fun getAllSubscriptions(): List<VpnSubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: VpnSubscriptionEntity)

    @Query("DELETE FROM vpn_subscriptions WHERE url = :url")
    suspend fun deleteSubscriptionByUrl(url: String)

    // Settings (Key-Value persistence)
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}
