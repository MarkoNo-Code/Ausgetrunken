package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WineyardSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WineyardSubscriptionDao {
    
    @Query("SELECT * FROM wineyard_subscriptions WHERE userId = :userId AND isActive = 1")
    fun getUserSubscriptionsFlow(userId: String): Flow<List<WineyardSubscriptionEntity>>
    
    @Query("SELECT * FROM wineyard_subscriptions WHERE userId = :userId AND isActive = 1")
    suspend fun getUserSubscriptions(userId: String): List<WineyardSubscriptionEntity>
    
    @Query("SELECT * FROM wineyard_subscriptions WHERE wineyardId = :wineyardId AND isActive = 1")
    suspend fun getWineyardSubscriptions(wineyardId: String): List<WineyardSubscriptionEntity>
    
    @Query("SELECT * FROM wineyard_subscriptions WHERE userId = :userId AND wineyardId = :wineyardId LIMIT 1")
    suspend fun getSubscription(userId: String, wineyardId: String): WineyardSubscriptionEntity?
    
    @Query("SELECT * FROM wineyard_subscriptions WHERE userId = :userId AND wineyardId = :wineyardId LIMIT 1")
    fun getSubscriptionFlow(userId: String, wineyardId: String): Flow<WineyardSubscriptionEntity?>
    
    @Query("SELECT COUNT(*) FROM wineyard_subscriptions WHERE wineyardId = :wineyardId AND isActive = 1")
    suspend fun getSubscriberCount(wineyardId: String): Int
    
    @Query("SELECT COUNT(*) FROM wineyard_subscriptions WHERE wineyardId = :wineyardId AND isActive = 1")
    fun getSubscriberCountFlow(wineyardId: String): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: WineyardSubscriptionEntity)
    
    @Update
    suspend fun updateSubscription(subscription: WineyardSubscriptionEntity)
    
    @Delete
    suspend fun deleteSubscription(subscription: WineyardSubscriptionEntity)
    
    @Query("DELETE FROM wineyard_subscriptions WHERE userId = :userId AND wineyardId = :wineyardId")
    suspend fun deleteSubscription(userId: String, wineyardId: String)
    
    @Query("UPDATE wineyard_subscriptions SET isActive = 0, updatedAt = :timestamp WHERE userId = :userId AND wineyardId = :wineyardId")
    suspend fun deactivateSubscription(userId: String, wineyardId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE wineyard_subscriptions SET isActive = 1, updatedAt = :timestamp WHERE userId = :userId AND wineyardId = :wineyardId")
    suspend fun activateSubscription(userId: String, wineyardId: String, timestamp: Long = System.currentTimeMillis())
}