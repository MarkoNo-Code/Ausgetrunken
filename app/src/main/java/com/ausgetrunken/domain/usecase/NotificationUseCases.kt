package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.dao.SubscriptionDao
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.notifications.NotificationService

class CheckLowStockNotificationsUseCase(
    private val getLowStockWinesUseCase: GetLowStockWinesUseCase,
    private val subscriptionDao: SubscriptionDao,
    private val notificationService: NotificationService
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            val lowStockWines = getLowStockWinesUseCase()
            
            lowStockWines.forEach { wine ->
                val subscriptions = subscriptionDao.getActiveSubscriptionsForWine(wine.id)
                subscriptions.forEach { subscription ->
                    notificationService.sendLowStockNotification(
                        userId = subscription.userId,
                        wineName = wine.name,
                        stockQuantity = wine.stockQuantity
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SendStockAlertToOwnerUseCase(
    private val notificationService: NotificationService
) {
    suspend operator fun invoke(ownerId: String, wine: WineEntity): Result<Unit> {
        return try {
            notificationService.sendOwnerStockAlert(
                ownerId = ownerId,
                wineName = wine.name,
                stockQuantity = wine.stockQuantity,
                threshold = wine.lowStockThreshold
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}