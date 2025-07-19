package com.ausgetrunken.data.repository

import com.ausgetrunken.data.local.dao.WineyardSubscriptionDao
import com.ausgetrunken.data.local.entities.WineyardSubscriptionEntity
import com.ausgetrunken.data.remote.model.WineyardSubscription
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class WineyardSubscriptionRepository(
    private val wineyardSubscriptionDao: WineyardSubscriptionDao,
    private val postgrest: Postgrest
) {
    
    fun getUserSubscriptionsFlow(userId: String): Flow<List<WineyardSubscriptionEntity>> =
        wineyardSubscriptionDao.getUserSubscriptionsFlow(userId)
    
    suspend fun getUserSubscriptions(userId: String): List<WineyardSubscriptionEntity> =
        wineyardSubscriptionDao.getUserSubscriptions(userId)
    
    fun getSubscriptionFlow(userId: String, wineyardId: String): Flow<WineyardSubscriptionEntity?> =
        wineyardSubscriptionDao.getSubscriptionFlow(userId, wineyardId)
    
    suspend fun getSubscription(userId: String, wineyardId: String): WineyardSubscriptionEntity? =
        wineyardSubscriptionDao.getSubscription(userId, wineyardId)
    
    fun getSubscriberCountFlow(wineyardId: String): Flow<Int> =
        wineyardSubscriptionDao.getSubscriberCountFlow(wineyardId)
    
    suspend fun getSubscriberCount(wineyardId: String): Int =
        wineyardSubscriptionDao.getSubscriberCount(wineyardId)
    
    suspend fun subscribeToWineyard(userId: String, wineyardId: String): Result<WineyardSubscriptionEntity> {
        return try {
            // Check if subscription already exists
            val existingSubscription = wineyardSubscriptionDao.getSubscription(userId, wineyardId)
            
            if (existingSubscription != null) {
                if (existingSubscription.isActive) {
                    return Result.failure(Exception("Already subscribed to this wineyard"))
                } else {
                    // Reactivate existing subscription
                    wineyardSubscriptionDao.activateSubscription(userId, wineyardId)
                    
                    // Update in Supabase
                    postgrest.from("wineyard_subscriptions")
                        .update(
                            buildJsonObject {
                                put("is_active", true)
                                put("updated_at", System.currentTimeMillis().toString())
                            }
                        ) {
                            filter {
                                eq("user_id", userId)
                                eq("wineyard_id", wineyardId)
                            }
                        }
                    
                    val updatedSubscription = existingSubscription.copy(
                        isActive = true,
                        updatedAt = System.currentTimeMillis()
                    )
                    return Result.success(updatedSubscription)
                }
            }
            
            // Create new subscription
            val subscriptionId = UUID.randomUUID().toString()
            val subscription = WineyardSubscriptionEntity(
                id = subscriptionId,
                userId = userId,
                wineyardId = wineyardId,
                isActive = true,
                lowStockNotifications = true,
                newReleaseNotifications = true,
                specialOfferNotifications = true,
                generalNotifications = true
            )
            
            // Insert locally
            wineyardSubscriptionDao.insertSubscription(subscription)
            
            // Insert in Supabase
            postgrest.from("wineyard_subscriptions")
                .insert(
                    buildJsonObject {
                        put("id", subscriptionId)
                        put("user_id", userId)
                        put("wineyard_id", wineyardId)
                        put("is_active", true)
                        put("low_stock_notifications", true)
                        put("new_release_notifications", true)
                        put("special_offer_notifications", true)
                        put("general_notifications", true)
                        put("created_at", subscription.createdAt.toString())
                        put("updated_at", subscription.updatedAt.toString())
                    }
                )
            
            Result.success(subscription)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unsubscribeFromWineyard(userId: String, wineyardId: String): Result<Unit> {
        return try {
            // Deactivate locally
            wineyardSubscriptionDao.deactivateSubscription(userId, wineyardId)
            
            // Update in Supabase
            postgrest.from("wineyard_subscriptions")
                .update(
                    buildJsonObject {
                        put("is_active", false)
                        put("updated_at", System.currentTimeMillis().toString())
                    }
                ) {
                    filter {
                        eq("user_id", userId)
                        eq("wineyard_id", wineyardId)
                    }
                }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateNotificationPreferences(
        userId: String,
        wineyardId: String,
        lowStock: Boolean,
        newRelease: Boolean,
        specialOffer: Boolean,
        general: Boolean
    ): Result<Unit> {
        return try {
            val existingSubscription = wineyardSubscriptionDao.getSubscription(userId, wineyardId)
                ?: return Result.failure(Exception("Subscription not found"))
            
            val updatedSubscription = existingSubscription.copy(
                lowStockNotifications = lowStock,
                newReleaseNotifications = newRelease,
                specialOfferNotifications = specialOffer,
                generalNotifications = general,
                updatedAt = System.currentTimeMillis()
            )
            
            // Update locally
            wineyardSubscriptionDao.updateSubscription(updatedSubscription)
            
            // Update in Supabase
            postgrest.from("wineyard_subscriptions")
                .update(
                    buildJsonObject {
                        put("low_stock_notifications", lowStock)
                        put("new_release_notifications", newRelease)
                        put("special_offer_notifications", specialOffer)
                        put("general_notifications", general)
                        put("updated_at", updatedSubscription.updatedAt.toString())
                    }
                ) {
                    filter {
                        eq("user_id", userId)
                        eq("wineyard_id", wineyardId)
                    }
                }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun syncSubscriptionsFromSupabase(userId: String): Result<List<WineyardSubscriptionEntity>> {
        return try {
            val response = postgrest.from("wineyard_subscriptions")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<WineyardSubscription>()
            
            val subscriptions = response.map { remote ->
                WineyardSubscriptionEntity(
                    id = remote.id,
                    userId = remote.userId,
                    wineyardId = remote.wineyardId,
                    isActive = remote.isActive,
                    lowStockNotifications = remote.lowStockNotifications,
                    newReleaseNotifications = remote.newReleaseNotifications,
                    specialOfferNotifications = remote.specialOfferNotifications,
                    generalNotifications = remote.generalNotifications,
                    createdAt = remote.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = remote.updatedAt.toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            
            // Update local database
            subscriptions.forEach { subscription ->
                wineyardSubscriptionDao.insertSubscription(subscription)
            }
            
            Result.success(subscriptions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}