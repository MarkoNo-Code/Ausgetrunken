package com.ausgetrunken.data.repository

import com.ausgetrunken.auth.SupabaseAuthRepository
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
    private val postgrest: Postgrest,
    authRepository: SupabaseAuthRepository
) : BaseRepository(authRepository) {
    
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
        return execute {
            println("🔄 WineyardSubscriptionRepository: Starting subscription process for user: $userId, wineyard: $wineyardId")
            
            // CRITICAL FIX: Check Supabase directly for ANY existing subscription (active or inactive)
            // Local database only stores active subscriptions, so we need to check the source of truth
            println("🔍 WineyardSubscriptionRepository: Checking Supabase for any existing subscription...")
            val existingSupabaseSubscriptions = try {
                postgrest.from("wineyard_subscriptions")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("wineyard_id", wineyardId)
                            // Don't filter by is_active - we want ANY subscription
                        }
                    }
                    .decodeList<WineyardSubscription>()
            } catch (e: Exception) {
                println("❌ WineyardSubscriptionRepository: Failed to check Supabase: ${e.message}")
                emptyList()
            }
            
            println("📊 WineyardSubscriptionRepository: Found ${existingSupabaseSubscriptions.size} existing subscriptions in Supabase")
            
            if (existingSupabaseSubscriptions.isNotEmpty()) {
                val existingSubscription = existingSupabaseSubscriptions.first()
                println("🔍 WineyardSubscriptionRepository: Existing subscription found - ID: ${existingSubscription.id}, Active: ${existingSubscription.isActive}")
                
                if (existingSubscription.isActive) {
                    println("⚠️ WineyardSubscriptionRepository: Subscription is already active")
                    return@execute Result.failure(Exception("Already subscribed to this wineyard"))
                }
                
                // Reactivate existing subscription in Supabase
                println("🔄 WineyardSubscriptionRepository: Reactivating existing subscription in Supabase...")
                postgrest.from("wineyard_subscriptions")
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
                println("💾 WineyardSubscriptionRepository: Updating local database...")
                val localSubscription = WineyardSubscriptionEntity(
                    id = existingSubscription.id,
                    userId = existingSubscription.userId,
                    wineyardId = existingSubscription.wineyardId,
                    isActive = true,
                    lowStockNotifications = existingSubscription.lowStockNotifications,
                    newReleaseNotifications = existingSubscription.newReleaseNotifications,
                    specialOfferNotifications = existingSubscription.specialOfferNotifications,
                    generalNotifications = existingSubscription.generalNotifications,
                    createdAt = existingSubscription.createdAt.toLongOrNull() ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                wineyardSubscriptionDao.insertSubscription(localSubscription)
                
                println("✅ WineyardSubscriptionRepository: Successfully reactivated existing subscription")
                return@execute Result.success(localSubscription)
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
            
            // Insert locally first
            wineyardSubscriptionDao.insertSubscription(subscription)
            
            try {
                // Insert in Supabase
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
                
                postgrest.from("wineyard_subscriptions").insert(supabaseData)
                Result.success(subscription)
            } catch (supabaseError: Exception) {
                // Rollback local insertion
                wineyardSubscriptionDao.deleteSubscription(userId, wineyardId)
                Result.failure(Exception("Failed to save subscription to Supabase: ${supabaseError.message}", supabaseError))
            }
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
                throw supabaseError
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
                ?: throw Exception("Subscription not found")
            
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
            println("🔄 WineyardSubscriptionRepository: Starting full sync for user: $userId")
            
            val response = postgrest.from("wineyard_subscriptions")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<WineyardSubscription>()
            
            println("📊 WineyardSubscriptionRepository: Found ${response.size} subscriptions in Supabase")
            
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
            
            // CRITICAL FIX: Clear existing local subscriptions first, then insert fresh data
            // This ensures local database exactly matches Supabase state
            println("🗑️ WineyardSubscriptionRepository: Clearing existing local subscriptions for user: $userId")
            val existingSubscriptions = wineyardSubscriptionDao.getUserSubscriptions(userId)
            existingSubscriptions.forEach { existing ->
                println("🗑️ Deleting local subscription: ${existing.id} for wineyard: ${existing.wineyardId}")
                wineyardSubscriptionDao.deleteSubscription(existing)
            }
            
            // Insert fresh data from Supabase
            println("💾 WineyardSubscriptionRepository: Inserting ${subscriptions.size} fresh subscriptions from Supabase")
            subscriptions.forEach { subscription ->
                println("💾 Inserting subscription: ${subscription.id} for wineyard: ${subscription.wineyardId} (active: ${subscription.isActive})")
                wineyardSubscriptionDao.insertSubscription(subscription)
            }
            
            println("✅ WineyardSubscriptionRepository: Full sync completed successfully")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WineyardSubscriptionRepository: Sync failed: ${e.message}")
            e.printStackTrace()
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
     * Excludes wineyard owners from the count to prevent self-subscriptions
     */
    suspend fun getActiveSubscriptionsForWineyardFromSupabase(wineyardId: String): Result<List<WineyardSubscriptionEntity>> {
        return try {
            println("🔄 WineyardSubscriptionRepository: Fetching real-time subscribers for wineyard: $wineyardId")
            
            // Get subscriptions and filter out wineyard owner if needed
            val response = postgrest.from("wineyard_subscriptions")
                .select {
                    filter {
                        eq("wineyard_id", wineyardId)
                        eq("is_active", true) // Only fetch active subscriptions
                    }
                }
                .decodeList<WineyardSubscription>()
            
            // Get the wineyard owner ID to filter out owner subscriptions
            val wineyardOwner = try {
                postgrest.from("wineyards")
                    .select {
                        filter {
                            eq("id", wineyardId)
                        }
                    }
                    .decodeSingle<Map<String, String>>()
            } catch (e: Exception) {
                println("⚠️ WineyardSubscriptionRepository: Could not fetch wineyard owner: ${e.message}")
                null
            }
            
            val ownerId = wineyardOwner?.get("owner_id")
            println("🔍 WineyardSubscriptionRepository: Wineyard owner ID: $ownerId")
            
            // Filter out owner subscriptions in code since the query syntax is complex
            val filteredResponse = if (ownerId != null) {
                response.filter { it.userId != ownerId }
            } else {
                response
            }
            
            val subscriptions = filteredResponse.map { remote ->
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
            
            println("✅ WineyardSubscriptionRepository: Found ${subscriptions.size} active subscribers for wineyard $wineyardId (excluding owner)")
            Result.success(subscriptions)
        } catch (e: Exception) {
            println("❌ WineyardSubscriptionRepository: Failed to fetch wineyard subscribers: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}