package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE userId = :userId AND isActive = 1")
    fun getActiveSubscriptionsByUser(userId: String): Flow<List<SubscriptionEntity>>
    
    @Query("SELECT * FROM subscriptions WHERE wineId = :wineId AND isActive = 1")
    suspend fun getActiveSubscriptionsForWine(wineId: String): List<SubscriptionEntity>
    
    @Query("SELECT * FROM subscriptions WHERE userId = :userId AND wineId = :wineId")
    suspend fun getSubscription(userId: String, wineId: String): SubscriptionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)
    
    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)
    
    @Query("UPDATE subscriptions SET isActive = :isActive WHERE userId = :userId AND wineId = :wineId")
    suspend fun updateSubscriptionStatus(userId: String, wineId: String, isActive: Boolean)
    
    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)
}