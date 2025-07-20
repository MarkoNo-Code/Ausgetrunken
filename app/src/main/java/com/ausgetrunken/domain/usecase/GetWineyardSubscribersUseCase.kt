package com.ausgetrunken.domain.usecase

import com.ausgetrunken.domain.model.SubscriberInfo
import com.ausgetrunken.domain.service.WineyardSubscriptionService

class GetWineyardSubscribersUseCase(
    private val wineyardSubscriptionService: WineyardSubscriptionService
) {
    
    suspend operator fun invoke(wineyardId: String): SubscriberInfo {
        return try {
            println("🔍 GetWineyardSubscribersUseCase: Fetching subscribers for wineyard: $wineyardId")
            
            // First try to get real-time data from Supabase for accurate count
            val supabaseResult = wineyardSubscriptionService.getActiveSubscriptionsForWineyardFromSupabase(wineyardId)
            
            val subscriptions = supabaseResult.getOrElse { error ->
                println("⚠️ GetWineyardSubscribersUseCase: Supabase fetch failed, falling back to local: ${error.message}")
                // Fallback to local database
                wineyardSubscriptionService.getActiveSubscriptionsForWineyard(wineyardId)
            }
            
            val totalSubscribers = subscriptions.size
            val lowStockSubscribers = subscriptions.count { it.lowStockNotifications }
            val generalSubscribers = subscriptions.count { it.generalNotifications }
            val newReleaseSubscribers = subscriptions.count { it.newReleaseNotifications }
            val specialOfferSubscribers = subscriptions.count { it.specialOfferNotifications }
            
            println("📊 GetWineyardSubscribersUseCase: Found $totalSubscribers total subscribers")
            println("   - Low stock: $lowStockSubscribers")
            println("   - General: $generalSubscribers")
            println("   - New release: $newReleaseSubscribers")
            println("   - Special offers: $specialOfferSubscribers")
            
            SubscriberInfo(
                totalSubscribers = totalSubscribers,
                lowStockSubscribers = lowStockSubscribers,
                generalSubscribers = generalSubscribers,
                newReleaseSubscribers = newReleaseSubscribers,
                specialOfferSubscribers = specialOfferSubscribers
            )
        } catch (e: Exception) {
            println("❌ GetWineyardSubscribersUseCase: Error fetching subscribers: ${e.message}")
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