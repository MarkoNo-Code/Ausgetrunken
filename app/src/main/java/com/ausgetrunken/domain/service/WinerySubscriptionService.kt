package com.ausgetrunken.domain.service

import com.ausgetrunken.data.local.entities.WinerySubscriptionEntity
import com.ausgetrunken.data.repository.WinerySubscriptionRepository
import kotlinx.coroutines.flow.Flow

class WinerySubscriptionService(
    private val subscriptionRepository: WinerySubscriptionRepository
) {
    
    fun getUserSubscriptions(userId: String): Flow<List<WinerySubscriptionEntity>> =
        subscriptionRepository.getUserSubscriptionsFlow(userId)
    
    fun getSubscription(userId: String, wineryId: String): Flow<WinerySubscriptionEntity?> =
        subscriptionRepository.getSubscriptionFlow(userId, wineryId)
    
    fun getSubscriberCount(wineryId: String): Flow<Int> =
        subscriptionRepository.getSubscriberCountFlow(wineryId)
    
    suspend fun subscribeToWinery(userId: String, wineryId: String): Result<WinerySubscriptionEntity> =
        subscriptionRepository.subscribeToWinery(userId, wineryId)
    
    suspend fun unsubscribeFromWinery(userId: String, wineryId: String): Result<Unit> =
        subscriptionRepository.unsubscribeFromWinery(userId, wineryId)
    
    suspend fun updateNotificationPreferences(
        userId: String,
        wineryId: String,
        lowStock: Boolean,
        newRelease: Boolean,
        specialOffer: Boolean,
        general: Boolean
    ): Result<Unit> = subscriptionRepository.updateNotificationPreferences(
        userId, wineryId, lowStock, newRelease, specialOffer, general
    )
    
    suspend fun syncSubscriptions(userId: String): Result<List<WinerySubscriptionEntity>> =
        subscriptionRepository.syncSubscriptionsFromSupabase(userId)
    
    suspend fun isSubscribed(userId: String, wineryId: String): Boolean {
        return try {
            println("🔍 WinerySubscriptionService: Checking subscription status for user $userId, winery $wineryId")
            
            // First try to get real-time data from Supabase
            println("🌐 WinerySubscriptionService: Attempting Supabase query...")
            val supabaseResult = subscriptionRepository.getUserSubscriptionsFromSupabase(userId)
            supabaseResult.onSuccess { subscriptions ->
                println("✅ WinerySubscriptionService: Supabase query succeeded with ${subscriptions.size} subscriptions")
                subscriptions.forEach { sub ->
                    println("   📝 Subscription: wineryId=${sub.wineryId}, isActive=${sub.isActive}")
                }
                val isSubscribed = subscriptions.any { it.wineryId == wineryId && it.isActive }
                println("🎯 WinerySubscriptionService: Supabase check result for winery $wineryId: $isSubscribed")
                return isSubscribed
            }.onFailure { error ->
                println("❌ WinerySubscriptionService: Supabase query failed: ${error.message}")
                println("📱 WinerySubscriptionService: Falling back to local database...")
            }
            
            // Fallback to local database
            println("💾 WinerySubscriptionService: Querying local database...")
            val subscription = subscriptionRepository.getSubscription(userId, wineryId)
            val isSubscribed = subscription?.isActive == true
            println("💾 WinerySubscriptionService: Local subscription found: $subscription")
            println("🎯 WinerySubscriptionService: Local check result for winery $wineryId: $isSubscribed")
            
            // CRITICAL DEBUG: If local says subscribed but Supabase failed, we have a sync issue
            if (isSubscribed) {
                println("🚨 WinerySubscriptionService: LOCAL DATABASE HAS STALE DATA!")
                println("🚨 Local says subscribed but Supabase query failed - this is the root cause!")
            }
            
            isSubscribed
        } catch (e: Exception) {
            println("❌ WinerySubscriptionService: Error checking subscription: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    suspend fun getActiveSubscriptionsForWinery(wineryId: String): List<WinerySubscriptionEntity> =
        subscriptionRepository.getActiveSubscriptionsForWinery(wineryId)
    
    /**
     * Fetches all active subscriptions for a winery directly from Supabase
     * Use this for notification center to get real-time subscriber count
     */
    suspend fun getActiveSubscriptionsForWineryFromSupabase(wineryId: String): Result<List<WinerySubscriptionEntity>> =
        subscriptionRepository.getActiveSubscriptionsForWineryFromSupabase(wineryId)
    
    /**
     * Fetches user subscriptions directly from Supabase (real-time, no local cache)
     * Use this for subscription screens to ensure cross-device synchronization
     */
    suspend fun getUserSubscriptionsFromSupabase(userId: String): Result<List<WinerySubscriptionEntity>> =
        subscriptionRepository.getUserSubscriptionsFromSupabase(userId)
}