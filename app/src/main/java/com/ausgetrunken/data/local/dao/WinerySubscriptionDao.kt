package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.WinerySubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WinerySubscriptionDao {
    
    @Query("SELECT * FROM winery_subscriptions WHERE userId = :userId AND isActive = 1")
    fun getUserSubscriptionsFlow(userId: String): Flow<List<WinerySubscriptionEntity>>
    
    @Query("SELECT * FROM winery_subscriptions WHERE userId = :userId AND isActive = 1")
    suspend fun getUserSubscriptions(userId: String): List<WinerySubscriptionEntity>
    
    @Query("SELECT * FROM winery_subscriptions WHERE wineryId = :wineryId AND isActive = 1")
    suspend fun getWinerySubscriptions(wineryId: String): List<WinerySubscriptionEntity>
    
    @Query("SELECT * FROM winery_subscriptions WHERE userId = :userId AND wineryId = :wineryId AND isActive = 1 LIMIT 1")
    suspend fun getSubscription(userId: String, wineryId: String): WinerySubscriptionEntity?
    
    @Query("SELECT * FROM winery_subscriptions WHERE userId = :userId AND wineryId = :wineryId AND isActive = 1 LIMIT 1")
    fun getSubscriptionFlow(userId: String, wineryId: String): Flow<WinerySubscriptionEntity?>
    
    @Query("SELECT COUNT(*) FROM winery_subscriptions WHERE wineryId = :wineryId AND isActive = 1")
    suspend fun getSubscriberCount(wineryId: String): Int
    
    @Query("SELECT COUNT(*) FROM winery_subscriptions WHERE wineryId = :wineryId AND isActive = 1")
    fun getSubscriberCountFlow(wineryId: String): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: WinerySubscriptionEntity)
    
    @Update
    suspend fun updateSubscription(subscription: WinerySubscriptionEntity)
    
    @Delete
    suspend fun deleteSubscription(subscription: WinerySubscriptionEntity)
    
    @Query("DELETE FROM winery_subscriptions WHERE userId = :userId AND wineryId = :wineryId")
    suspend fun deleteSubscription(userId: String, wineryId: String)
    
    @Query("UPDATE winery_subscriptions SET isActive = 0, updatedAt = :timestamp WHERE userId = :userId AND wineryId = :wineryId")
    suspend fun deactivateSubscription(userId: String, wineryId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE winery_subscriptions SET isActive = 1, updatedAt = :timestamp WHERE userId = :userId AND wineryId = :wineryId")
    suspend fun activateSubscription(userId: String, wineryId: String, timestamp: Long = System.currentTimeMillis())
}