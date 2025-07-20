package com.ausgetrunken.domain.service

import com.ausgetrunken.data.local.entities.WineyardSubscriptionEntity
import com.ausgetrunken.data.repository.WineyardSubscriptionRepository
import kotlinx.coroutines.flow.Flow

class WineyardSubscriptionService(
    private val subscriptionRepository: WineyardSubscriptionRepository
) {
    
    fun getUserSubscriptions(userId: String): Flow<List<WineyardSubscriptionEntity>> =
        subscriptionRepository.getUserSubscriptionsFlow(userId)
    
    fun getSubscription(userId: String, wineyardId: String): Flow<WineyardSubscriptionEntity?> =
        subscriptionRepository.getSubscriptionFlow(userId, wineyardId)
    
    fun getSubscriberCount(wineyardId: String): Flow<Int> =
        subscriptionRepository.getSubscriberCountFlow(wineyardId)
    
    suspend fun subscribeToWineyard(userId: String, wineyardId: String): Result<WineyardSubscriptionEntity> =
        subscriptionRepository.subscribeToWineyard(userId, wineyardId)
    
    suspend fun unsubscribeFromWineyard(userId: String, wineyardId: String): Result<Unit> =
        subscriptionRepository.unsubscribeFromWineyard(userId, wineyardId)
    
    suspend fun updateNotificationPreferences(
        userId: String,
        wineyardId: String,
        lowStock: Boolean,
        newRelease: Boolean,
        specialOffer: Boolean,
        general: Boolean
    ): Result<Unit> = subscriptionRepository.updateNotificationPreferences(
        userId, wineyardId, lowStock, newRelease, specialOffer, general
    )
    
    suspend fun syncSubscriptions(userId: String): Result<List<WineyardSubscriptionEntity>> =
        subscriptionRepository.syncSubscriptionsFromSupabase(userId)
    
    suspend fun isSubscribed(userId: String, wineyardId: String): Boolean {
        return try {
            println("🔍 WineyardSubscriptionService: Checking subscription status for user $userId, wineyard $wineyardId")
            
            // First try to get real-time data from Supabase
            val supabaseResult = subscriptionRepository.getUserSubscriptionsFromSupabase(userId)
            supabaseResult.onSuccess { subscriptions ->
                val isSubscribed = subscriptions.any { it.wineyardId == wineyardId && it.isActive }
                println("✅ WineyardSubscriptionService: Supabase check - isSubscribed: $isSubscribed")
                return isSubscribed
            }.onFailure { error ->
                println("⚠️ WineyardSubscriptionService: Supabase check failed, falling back to local: ${error.message}")
            }
            
            // Fallback to local database
            val subscription = subscriptionRepository.getSubscription(userId, wineyardId)
            val isSubscribed = subscription?.isActive == true
            println("💾 WineyardSubscriptionService: Local check - isSubscribed: $isSubscribed")
            isSubscribed
        } catch (e: Exception) {
            println("❌ WineyardSubscriptionService: Error checking subscription: ${e.message}")
            false
        }
    }
    
    suspend fun getActiveSubscriptionsForWineyard(wineyardId: String): List<WineyardSubscriptionEntity> =
        subscriptionRepository.getActiveSubscriptionsForWineyard(wineyardId)
    
    /**
     * Fetches user subscriptions directly from Supabase (real-time, no local cache)
     * Use this for subscription screens to ensure cross-device synchronization
     */
    suspend fun getUserSubscriptionsFromSupabase(userId: String): Result<List<WineyardSubscriptionEntity>> =
        subscriptionRepository.getUserSubscriptionsFromSupabase(userId)
}