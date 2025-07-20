package com.ausgetrunken.data.repository

import com.ausgetrunken.data.local.dao.WineyardSubscriptionDao
import com.ausgetrunken.data.local.entities.WineyardSubscriptionEntity
import com.ausgetrunken.data.remote.model.WineyardSubscription
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.time.Instant

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
    
    suspend fun getActiveSubscriptionsForWineyard(wineyardId: String): List<WineyardSubscriptionEntity> =
        wineyardSubscriptionDao.getWineyardSubscriptions(wineyardId)
    
    suspend fun subscribeToWineyard(userId: String, wineyardId: String): Result<WineyardSubscriptionEntity> {
        return try {
            println("🔄 WineyardSubscriptionRepository: Starting subscription process")
            println("👤 User ID: $userId")
            println("🍷 Wineyard ID: $wineyardId")
            
            // Check if subscription already exists - check both Supabase and local
            println("🔍 Checking for existing subscription in Supabase...")
            val supabaseSubscriptions = try {
                postgrest.from("wineyard_subscriptions")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("wineyard_id", wineyardId)
                        }
                    }
                    .decodeList<WineyardSubscription>()
            } catch (e: Exception) {
                println("⚠️ Failed to check Supabase, will check local only: ${e.message}")
                emptyList()
            }
            
            var existingSubscription = wineyardSubscriptionDao.getSubscription(userId, wineyardId)
            
            // If we have Supabase data but no local data, sync it
            if (supabaseSubscriptions.isNotEmpty() && existingSubscription == null) {
                val supabaseSubscription = supabaseSubscriptions.first()
                println("🔄 Found subscription in Supabase but not locally, syncing...")
                val localSub = WineyardSubscriptionEntity(
                    id = supabaseSubscription.id,
                    userId = supabaseSubscription.userId,
                    wineyardId = supabaseSubscription.wineyardId,
                    isActive = supabaseSubscription.isActive,
                    lowStockNotifications = supabaseSubscription.lowStockNotifications,
                    newReleaseNotifications = supabaseSubscription.newReleaseNotifications,
                    specialOfferNotifications = supabaseSubscription.specialOfferNotifications,
                    generalNotifications = supabaseSubscription.generalNotifications,
                    createdAt = supabaseSubscription.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = supabaseSubscription.updatedAt.toLongOrNull() ?: System.currentTimeMillis()
                )
                wineyardSubscriptionDao.insertSubscription(localSub)
                existingSubscription = localSub
                println("✅ Synced subscription from Supabase to local database")
            }
            
            println("🔍 Final subscription check: ${if (existingSubscription != null) "Found (active: ${existingSubscription.isActive})" else "Not found"}")
            
            if (existingSubscription != null) {
                if (existingSubscription.isActive) {
                    println("⚠️ Already subscribed to this wineyard")
                    return Result.failure(Exception("Already subscribed to this wineyard"))
                } else {
                    println("🔄 Reactivating existing subscription")
                    // Reactivate existing subscription
                    wineyardSubscriptionDao.activateSubscription(userId, wineyardId)
                    
                    try {
                        println("🌐 Updating subscription in Supabase...")
                        // Update in Supabase
                        postgrest.from("wineyard_subscriptions")
                            .update(
                                buildJsonObject {
                                    put("is_active", true)
                                    put("updated_at", Instant.now().toString())
                                }
                            ) {
                                filter {
                                    eq("user_id", userId)
                                    eq("wineyard_id", wineyardId)
                                }
                            }
                        println("✅ Supabase update successful")
                    } catch (supabaseError: Exception) {
                        println("❌ Supabase update failed: ${supabaseError.message}")
                        supabaseError.printStackTrace()
                        // Rollback local change
                        wineyardSubscriptionDao.deactivateSubscription(userId, wineyardId)
                        return Result.failure(supabaseError)
                    }
                    
                    val updatedSubscription = existingSubscription.copy(
                        isActive = true,
                        updatedAt = System.currentTimeMillis()
                    )
                    println("✅ Subscription reactivated successfully")
                    return Result.success(updatedSubscription)
                }
            }
            
            // Create new subscription
            val subscriptionId = UUID.randomUUID().toString()
            println("🆕 Creating new subscription with ID: $subscriptionId")
            
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
            println("💾 Inserting subscription into local database...")
            wineyardSubscriptionDao.insertSubscription(subscription)
            println("✅ Local database insert successful")
            
            try {
                println("🌐 Inserting subscription into Supabase...")
                val supabaseData = buildJsonObject {
                    put("id", subscriptionId)
                    put("user_id", userId)
                    put("wineyard_id", wineyardId)
                    put("is_active", true)
                    put("low_stock_notifications", true)
                    put("new_release_notifications", true)
                    put("special_offer_notifications", true)
                    put("general_notifications", true)
                    put("created_at", Instant.ofEpochMilli(subscription.createdAt).toString())
                    put("updated_at", Instant.ofEpochMilli(subscription.updatedAt).toString())
                }
                
                println("📤 Supabase data payload: $supabaseData")
                
                // Insert in Supabase
                val supabaseResult = postgrest.from("wineyard_subscriptions")
                    .insert(supabaseData)
                
                println("✅ Supabase insert successful")
                println("🎉 Subscription created successfully!")
            } catch (supabaseError: Exception) {
                println("❌ CRITICAL: Supabase insert failed: ${supabaseError.message}")
                println("🔄 Rolling back local database insertion...")
                supabaseError.printStackTrace()
                
                // Rollback local insertion
                try {
                    wineyardSubscriptionDao.deleteSubscription(userId, wineyardId)
                    println("✅ Local rollback successful")
                } catch (rollbackError: Exception) {
                    println("❌ CRITICAL: Local rollback failed: ${rollbackError.message}")
                    rollbackError.printStackTrace()
                }
                
                return Result.failure(Exception("Failed to save subscription to Supabase: ${supabaseError.message}", supabaseError))
            }
            
            Result.success(subscription)
        } catch (e: Exception) {
            println("❌ UNEXPECTED ERROR in subscribeToWineyard: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun unsubscribeFromWineyard(userId: String, wineyardId: String): Result<Unit> {
        return try {
            println("🔄 WineyardSubscriptionRepository: Starting unsubscribe process")
            println("👤 User ID: $userId")
            println("🍷 Wineyard ID: $wineyardId")
            
            // Check if subscription exists and is active - check Supabase first for real-time data
            println("🔍 Checking subscription status in Supabase...")
            val supabaseSubscriptions = try {
                postgrest.from("wineyard_subscriptions")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("wineyard_id", wineyardId)
                            eq("is_active", true)
                        }
                    }
                    .decodeList<WineyardSubscription>()
            } catch (e: Exception) {
                println("⚠️ Failed to check Supabase, falling back to local: ${e.message}")
                emptyList()
            }
            
            if (supabaseSubscriptions.isNotEmpty()) {
                println("✅ Found active subscription in Supabase, proceeding with unsubscribe")
            } else {
                // Fallback to local check
                val existingSubscription = wineyardSubscriptionDao.getSubscription(userId, wineyardId)
                if (existingSubscription?.isActive != true) {
                    println("⚠️ No active subscription found in Supabase or local database")
                    return Result.failure(Exception("No active subscription found"))
                } else {
                    println("✅ Found active subscription in local database, proceeding with unsubscribe")
                }
            }
            
            // Deactivate locally
            println("💾 Deactivating subscription in local database...")
            wineyardSubscriptionDao.deactivateSubscription(userId, wineyardId)
            println("✅ Local database deactivation successful")
            
            try {
                println("🌐 Updating subscription status in Supabase...")
                // Update in Supabase
                postgrest.from("wineyard_subscriptions")
                    .update(
                        buildJsonObject {
                            put("is_active", false)
                            put("updated_at", Instant.now().toString())
                        }
                    ) {
                        filter {
                            eq("user_id", userId)
                            eq("wineyard_id", wineyardId)
                        }
                    }
                println("✅ Supabase update successful")
                println("🎉 Unsubscribed successfully!")
            } catch (supabaseError: Exception) {
                println("❌ Supabase update failed: ${supabaseError.message}")
                supabaseError.printStackTrace()
                
                // Rollback local change
                println("🔄 Rolling back local deactivation...")
                wineyardSubscriptionDao.activateSubscription(userId, wineyardId)
                return Result.failure(supabaseError)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ UNEXPECTED ERROR in unsubscribeFromWineyard: ${e.message}")
            e.printStackTrace()
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
                        put("updated_at", Instant.ofEpochMilli(updatedSubscription.updatedAt).toString())
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
    
    /**
     * Fetches user subscriptions directly from Supabase (real-time, no local cache)
     * Use this for subscription screens to ensure cross-device synchronization
     */
    suspend fun getUserSubscriptionsFromSupabase(userId: String): Result<List<WineyardSubscriptionEntity>> {
        return try {
            println("🔄 WineyardSubscriptionRepository: Fetching real-time subscriptions for user: $userId")
            
            val response = postgrest.from("wineyard_subscriptions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true) // Only fetch active subscriptions
                    }
                }
                .decodeList<WineyardSubscription>()
            
            println("📊 WineyardSubscriptionRepository: Found ${response.size} active subscriptions")
            
            val subscriptions = response.map { remote ->
                println("🔗 WineyardSubscriptionRepository: Processing subscription to wineyard: ${remote.wineyardId}")
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
            
            println("✅ WineyardSubscriptionRepository: Real-time fetch completed successfully")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WineyardSubscriptionRepository: Real-time fetch failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Fetches all active subscriptions for a wineyard directly from Supabase
     * Use this for notification center to get real-time subscriber count
     */
    suspend fun getActiveSubscriptionsForWineyardFromSupabase(wineyardId: String): Result<List<WineyardSubscriptionEntity>> {
        return try {
            println("🔄 WineyardSubscriptionRepository: Fetching real-time subscribers for wineyard: $wineyardId")
            
            val response = postgrest.from("wineyard_subscriptions")
                .select {
                    filter {
                        eq("wineyard_id", wineyardId)
                        eq("is_active", true) // Only fetch active subscriptions
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
            
            println("✅ WineyardSubscriptionRepository: Found ${subscriptions.size} active subscribers for wineyard $wineyardId")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WineyardSubscriptionRepository: Failed to fetch wineyard subscribers: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}