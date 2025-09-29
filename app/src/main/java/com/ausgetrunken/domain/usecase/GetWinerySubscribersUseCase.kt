package com.ausgetrunken.domain.usecase

import com.ausgetrunken.domain.model.SubscriberInfo
import com.ausgetrunken.domain.service.WinerySubscriptionService

class GetWinerySubscribersUseCase(
    private val winerySubscriptionService: WinerySubscriptionService
) {
    
    suspend operator fun invoke(wineryId: String): SubscriberInfo {
        return try {
            println("🔍 GetWinerySubscribersUseCase: Fetching subscribers for winery: $wineryId")
            
            // First try to get real-time data from Supabase for accurate count
            val supabaseResult = winerySubscriptionService.getActiveSubscriptionsForWineryFromSupabase(wineryId)
            
            val subscriptions = supabaseResult.getOrElse { error ->
                // Removed println: "⚠️ GetWinerySubscribersUseCase: Supabase fetch failed, falling back to local: ${error.message}"
                // Fallback to local database
                winerySubscriptionService.getActiveSubscriptionsForWinery(wineryId)
            }
            
            val totalSubscribers = subscriptions.size
            val lowStockSubscribers = subscriptions.count { it.lowStockNotifications }
            val generalSubscribers = subscriptions.count { it.generalNotifications }
            val newReleaseSubscribers = subscriptions.count { it.newReleaseNotifications }
            val specialOfferSubscribers = subscriptions.count { it.specialOfferNotifications }
            
            println("📊 GetWinerySubscribersUseCase: Found $totalSubscribers total subscribers")
            // Removed println: "   - Low stock: $lowStockSubscribers"
            // Removed println: "   - General: $generalSubscribers"
            // Removed println: "   - New release: $newReleaseSubscribers"
            // Removed println: "   - Special offers: $specialOfferSubscribers"
            
            SubscriberInfo(
                totalSubscribers = totalSubscribers,
                lowStockSubscribers = lowStockSubscribers,
                generalSubscribers = generalSubscribers,
                newReleaseSubscribers = newReleaseSubscribers,
                specialOfferSubscribers = specialOfferSubscribers
            )
        } catch (e: Exception) {
            // Removed println: "❌ GetWinerySubscribersUseCase: Error fetching subscribers: ${e.message}"
            e.printStackTrace()
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