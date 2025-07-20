package com.ausgetrunken.domain.usecase

import com.ausgetrunken.domain.model.SubscriberInfo
import com.ausgetrunken.domain.service.WineyardSubscriptionService

class GetWineyardSubscribersUseCase(
    private val wineyardSubscriptionService: WineyardSubscriptionService
) {
    
    suspend operator fun invoke(wineyardId: String): SubscriberInfo {
        return try {
            val subscriptions = wineyardSubscriptionService.getActiveSubscriptionsForWineyard(wineyardId)
            
            val totalSubscribers = subscriptions.size
            val lowStockSubscribers = subscriptions.count { it.lowStockNotifications }
            val generalSubscribers = subscriptions.count { it.generalNotifications }
            val newReleaseSubscribers = subscriptions.count { it.newReleaseNotifications }
            val specialOfferSubscribers = subscriptions.count { it.specialOfferNotifications }
            
            SubscriberInfo(
                totalSubscribers = totalSubscribers,
                lowStockSubscribers = lowStockSubscribers,
                generalSubscribers = generalSubscribers,
                newReleaseSubscribers = newReleaseSubscribers,
                specialOfferSubscribers = specialOfferSubscribers
            )
        } catch (e: Exception) {
            SubscriberInfo(
                totalSubscribers = 0,
                lowStockSubscribers = 0,
                generalSubscribers = 0,
                newReleaseSubscribers = 0,
                specialOfferSubscribers = 0
            )
        }
    }
}