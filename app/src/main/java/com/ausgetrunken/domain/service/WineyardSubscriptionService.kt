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
        val subscription = subscriptionRepository.getSubscription(userId, wineyardId)
        return subscription?.isActive == true
    }
}