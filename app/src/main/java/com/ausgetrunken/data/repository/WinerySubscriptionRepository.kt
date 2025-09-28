package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.dao.WinerySubscriptionDao
import com.ausgetrunken.data.local.entities.WinerySubscriptionEntity
import com.ausgetrunken.data.remote.model.WinerySubscription
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.time.Instant

class WinerySubscriptionRepository(
    private val winerySubscriptionDao: WinerySubscriptionDao,
    private val postgrest: Postgrest,
    authRepository: SupabaseAuthRepository
) : BaseRepository(authRepository) {
    
    fun getUserSubscriptionsFlow(userId: String): Flow<List<WinerySubscriptionEntity>> =
        winerySubscriptionDao.getUserSubscriptionsFlow(userId)
    
    suspend fun getUserSubscriptions(userId: String): List<WinerySubscriptionEntity> =
        winerySubscriptionDao.getUserSubscriptions(userId)
    
    fun getSubscriptionFlow(userId: String, wineryId: String): Flow<WinerySubscriptionEntity?> =
        winerySubscriptionDao.getSubscriptionFlow(userId, wineryId)
    
    suspend fun getSubscription(userId: String, wineryId: String): WinerySubscriptionEntity? =
        winerySubscriptionDao.getSubscription(userId, wineryId)
    
    fun getSubscriberCountFlow(wineryId: String): Flow<Int> =
        winerySubscriptionDao.getSubscriberCountFlow(wineryId)
    
    suspend fun getSubscriberCount(wineryId: String): Int =
        winerySubscriptionDao.getSubscriberCount(wineryId)
    
    suspend fun getActiveSubscriptionsForWinery(wineryId: String): List<WinerySubscriptionEntity> =
        winerySubscriptionDao.getWinerySubscriptions(wineryId)
    
    suspend fun subscribeToWinery(userId: String, wineryId: String): Result<WinerySubscriptionEntity> {
        return execute {
            println("🔄 WinerySubscriptionRepository: Starting subscription process for user: $userId, winery: $wineryId")
            
            // CRITICAL FIX: Check Supabase directly for ANY existing subscription (active or inactive)
            // Local database only stores active subscriptions, so we need to check the source of truth
            println("🔍 WinerySubscriptionRepository: Checking Supabase for any existing subscription...")
            val existingSupabaseSubscriptions = try {
                postgrest.from("winery_subscriptions")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("winery_id", wineryId)
                            // Don't filter by is_active - we want ANY subscription
                        }
                    }
                    .decodeList<WinerySubscription>()
            } catch (e: Exception) {
                println("❌ WinerySubscriptionRepository: Failed to check Supabase: ${e.message}")
                emptyList()
            }
            
            println("📊 WinerySubscriptionRepository: Found ${existingSupabaseSubscriptions.size} existing subscriptions in Supabase")
            
            if (existingSupabaseSubscriptions.isNotEmpty()) {
                val existingSubscription = existingSupabaseSubscriptions.first()
                println("🔍 WinerySubscriptionRepository: Existing subscription found - ID: ${existingSubscription.id}, Active: ${existingSubscription.isActive}")
                
                if (existingSubscription.isActive) {
                    println("⚠️ WinerySubscriptionRepository: Subscription is already active")
                    return@execute Result.failure(Exception("Already subscribed to this winery"))
                }
                
                // Reactivate existing subscription in Supabase
                println("🔄 WinerySubscriptionRepository: Reactivating existing subscription in Supabase...")
                postgrest.from("winery_subscriptions")
                    .update(
                        buildJsonObject {
                            put("is_active", true)
                            put("updated_at", Instant.now().toString())
                        }
                    ) {
                        filter {
                            eq("id", existingSubscription.id)
                        }
                    }
                
                // Update local database
                println("💾 WinerySubscriptionRepository: Updating local database...")
                val localSubscription = WinerySubscriptionEntity(
                    id = existingSubscription.id,
                    userId = existingSubscription.userId,
                    wineryId = existingSubscription.wineryId,
                    isActive = true,
                    lowStockNotifications = existingSubscription.lowStockNotifications,
                    newReleaseNotifications = existingSubscription.newReleaseNotifications,
                    specialOfferNotifications = existingSubscription.specialOfferNotifications,
                    generalNotifications = existingSubscription.generalNotifications,
                    createdAt = existingSubscription.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                winerySubscriptionDao.insertSubscription(localSubscription)
                
                println("✅ WinerySubscriptionRepository: Successfully reactivated existing subscription")
                return@execute Result.success(localSubscription)
            }
            
            // Create new subscription
            val subscriptionId = UUID.randomUUID().toString()
            val subscription = WinerySubscriptionEntity(
                id = subscriptionId,
                userId = userId,
                wineryId = wineryId,
                isActive = true,
                lowStockNotifications = true,
                newReleaseNotifications = true,
                specialOfferNotifications = true,
                generalNotifications = true
            )
            
            // Insert locally first
            winerySubscriptionDao.insertSubscription(subscription)
            
            try {
                // Insert in Supabase
                val supabaseData = buildJsonObject {
                    put("id", subscriptionId)
                    put("user_id", userId)
                    put("winery_id", wineryId)
                    put("is_active", true)
                    put("low_stock_notifications", true)
                    put("new_release_notifications", true)
                    put("special_offer_notifications", true)
                    put("general_notifications", true)
                    put("created_at", Instant.ofEpochMilli(subscription.createdAt).toString())
                    put("updated_at", Instant.ofEpochMilli(subscription.updatedAt).toString())
                }
                
                postgrest.from("winery_subscriptions").insert(supabaseData)
                Result.success(subscription)
            } catch (supabaseError: Exception) {
                // Rollback local insertion
                winerySubscriptionDao.deleteSubscription(userId, wineryId)
                Result.failure(Exception("Failed to save subscription to Supabase: ${supabaseError.message}", supabaseError))
            }
        }
    }
    
    suspend fun unsubscribeFromWinery(userId: String, wineryId: String): Result<Unit> {
        return try {
            println("🔄 WinerySubscriptionRepository: Starting unsubscribe process")
            println("👤 User ID: $userId")
            println("🍷 Winery ID: $wineryId")
            
            // Check if subscription exists and is active - check Supabase first for real-time data
            println("🔍 Checking subscription status in Supabase...")
            val supabaseSubscriptions = try {
                postgrest.from("winery_subscriptions")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("winery_id", wineryId)
                            eq("is_active", true)
                        }
                    }
                    .decodeList<WinerySubscription>()
            } catch (e: Exception) {
                println("⚠️ Failed to check Supabase, falling back to local: ${e.message}")
                emptyList()
            }
            
            if (supabaseSubscriptions.isNotEmpty()) {
                println("✅ Found active subscription in Supabase, proceeding with unsubscribe")
            } else {
                // Fallback to local check
                val existingSubscription = winerySubscriptionDao.getSubscription(userId, wineryId)
                if (existingSubscription?.isActive != true) {
                    println("⚠️ No active subscription found in Supabase or local database")
                    return Result.failure(Exception("No active subscription found"))
                } else {
                    println("✅ Found active subscription in local database, proceeding with unsubscribe")
                }
            }
            
            // Deactivate locally
            println("💾 Deactivating subscription in local database...")
            winerySubscriptionDao.deactivateSubscription(userId, wineryId)
            println("✅ Local database deactivation successful")
            
            try {
                println("🌐 Updating subscription status in Supabase...")
                // Update in Supabase
                postgrest.from("winery_subscriptions")
                    .update(
                        buildJsonObject {
                            put("is_active", false)
                            put("updated_at", Instant.now().toString())
                        }
                    ) {
                        filter {
                            eq("user_id", userId)
                            eq("winery_id", wineryId)
                        }
                    }
                println("✅ Supabase update successful")
                println("🎉 Unsubscribed successfully!")
            } catch (supabaseError: Exception) {
                println("❌ Supabase update failed: ${supabaseError.message}")
                supabaseError.printStackTrace()
                
                // Rollback local change
                println("🔄 Rolling back local deactivation...")
                winerySubscriptionDao.activateSubscription(userId, wineryId)
                throw supabaseError
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("❌ UNEXPECTED ERROR in unsubscribeFromWinery: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun updateNotificationPreferences(
        userId: String,
        wineryId: String,
        lowStock: Boolean,
        newRelease: Boolean,
        specialOffer: Boolean,
        general: Boolean
    ): Result<Unit> {
        return try {
            val existingSubscription = winerySubscriptionDao.getSubscription(userId, wineryId)
                ?: throw Exception("Subscription not found")
            
            val updatedSubscription = existingSubscription.copy(
                lowStockNotifications = lowStock,
                newReleaseNotifications = newRelease,
                specialOfferNotifications = specialOffer,
                generalNotifications = general,
                updatedAt = System.currentTimeMillis()
            )
            
            // Update locally
            winerySubscriptionDao.updateSubscription(updatedSubscription)
            
            // Update in Supabase
            postgrest.from("winery_subscriptions")
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
                        eq("winery_id", wineryId)
                    }
                }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun syncSubscriptionsFromSupabase(userId: String): Result<List<WinerySubscriptionEntity>> {
        return try {
            println("🔄 WinerySubscriptionRepository: Starting full sync for user: $userId")
            
            val response = postgrest.from("winery_subscriptions")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<WinerySubscription>()
            
            println("📊 WinerySubscriptionRepository: Found ${response.size} subscriptions in Supabase")
            
            val subscriptions = response.map { remote ->
                WinerySubscriptionEntity(
                    id = remote.id,
                    userId = remote.userId,
                    wineryId = remote.wineryId,
                    isActive = remote.isActive,
                    lowStockNotifications = remote.lowStockNotifications,
                    newReleaseNotifications = remote.newReleaseNotifications,
                    specialOfferNotifications = remote.specialOfferNotifications,
                    generalNotifications = remote.generalNotifications,
                    createdAt = remote.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = remote.updatedAt.toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            
            // CRITICAL FIX: Clear existing local subscriptions first, then insert fresh data
            // This ensures local database exactly matches Supabase state
            println("🗑️ WinerySubscriptionRepository: Clearing existing local subscriptions for user: $userId")
            val existingSubscriptions = winerySubscriptionDao.getUserSubscriptions(userId)
            existingSubscriptions.forEach { existing ->
                println("🗑️ Deleting local subscription: ${existing.id} for winery: ${existing.wineryId}")
                winerySubscriptionDao.deleteSubscription(existing)
            }
            
            // Insert fresh data from Supabase
            println("💾 WinerySubscriptionRepository: Inserting ${subscriptions.size} fresh subscriptions from Supabase")
            subscriptions.forEach { subscription ->
                println("💾 Inserting subscription: ${subscription.id} for winery: ${subscription.wineryId} (active: ${subscription.isActive})")
                winerySubscriptionDao.insertSubscription(subscription)
            }
            
            println("✅ WinerySubscriptionRepository: Full sync completed successfully")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WinerySubscriptionRepository: Sync failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Fetches user subscriptions directly from Supabase (real-time, no local cache)
     * Use this for subscription screens to ensure cross-device synchronization
     */
    suspend fun getUserSubscriptionsFromSupabase(userId: String): Result<List<WinerySubscriptionEntity>> {
        return try {
            println("🔄 WinerySubscriptionRepository: Fetching real-time subscriptions for user: $userId")
            
            val response = postgrest.from("winery_subscriptions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_active", true) // Only fetch active subscriptions
                    }
                }
                .decodeList<WinerySubscription>()
            
            println("📊 WinerySubscriptionRepository: Found ${response.size} active subscriptions")
            
            val subscriptions = response.map { remote ->
                println("🔗 WinerySubscriptionRepository: Processing subscription to winery: ${remote.wineryId}")
                WinerySubscriptionEntity(
                    id = remote.id,
                    userId = remote.userId,
                    wineryId = remote.wineryId,
                    isActive = remote.isActive,
                    lowStockNotifications = remote.lowStockNotifications,
                    newReleaseNotifications = remote.newReleaseNotifications,
                    specialOfferNotifications = remote.specialOfferNotifications,
                    generalNotifications = remote.generalNotifications,
                    createdAt = remote.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = remote.updatedAt.toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            
            println("✅ WinerySubscriptionRepository: Real-time fetch completed successfully")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WinerySubscriptionRepository: Real-time fetch failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Fetches all active subscriptions for a winery directly from Supabase
     * Use this for notification center to get real-time subscriber count
     * Excludes winery owners from the count to prevent self-subscriptions
     */
    suspend fun getActiveSubscriptionsForWineryFromSupabase(wineryId: String): Result<List<WinerySubscriptionEntity>> {
        return try {
            println("🔄 WinerySubscriptionRepository: Fetching real-time subscribers for winery: $wineryId")
            
            // Get subscriptions and filter out winery owner if needed
            val response = postgrest.from("winery_subscriptions")
                .select {
                    filter {
                        eq("winery_id", wineryId)
                        eq("is_active", true) // Only fetch active subscriptions
                    }
                }
                .decodeList<WinerySubscription>()
            
            // Get the winery owner ID to filter out owner subscriptions
            val wineryOwner = try {
                postgrest.from("wineries")
                    .select {
                        filter {
                            eq("id", wineryId)
                        }
                    }
                    .decodeSingle<Map<String, String>>()
            } catch (e: Exception) {
                println("⚠️ WinerySubscriptionRepository: Could not fetch winery owner: ${e.message}")
                null
            }
            
            val ownerId = wineryOwner?.get("owner_id")
            println("🔍 WinerySubscriptionRepository: Winery owner ID: $ownerId")
            
            // Filter out owner subscriptions in code since the query syntax is complex
            val filteredResponse = if (ownerId != null) {
                response.filter { it.userId != ownerId }
            } else {
                response
            }
            
            val subscriptions = filteredResponse.map { remote ->
                WinerySubscriptionEntity(
                    id = remote.id,
                    userId = remote.userId,
                    wineryId = remote.wineryId,
                    isActive = remote.isActive,
                    lowStockNotifications = remote.lowStockNotifications,
                    newReleaseNotifications = remote.newReleaseNotifications,
                    specialOfferNotifications = remote.specialOfferNotifications,
                    generalNotifications = remote.generalNotifications,
                    createdAt = remote.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = remote.updatedAt.toLongOrNull() ?: System.currentTimeMillis()
                )
            }
            
            println("✅ WinerySubscriptionRepository: Found ${subscriptions.size} active subscribers for winery $wineryId (excluding owner)")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WinerySubscriptionRepository: Failed to fetch winery subscribers: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}