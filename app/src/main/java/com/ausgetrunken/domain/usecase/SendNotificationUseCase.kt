package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.model.NotificationResult
import com.ausgetrunken.domain.service.NotificationService

class SendNotificationUseCase(
    private val notificationService: NotificationService
) {
    
    suspend fun sendLowStockNotification(wineyardId: String, wine: WineEntity): NotificationResult {
        return try {
            val result = notificationService.sendLowStockNotification(wineyardId, wine)
            
            NotificationResult(
                success = result.success,
                sentCount = result.sentCount,
                message = result.message
            )
        } catch (e: Exception) {
            NotificationResult(
                success = false,
                sentCount = 0,
                message = e.message ?: "Failed to send notification"
            )
        }
    }
    
    suspend fun sendCriticalStockNotification(wineyardId: String, wine: WineEntity): NotificationResult {
        return try {
            val result = notificationService.sendCriticalStockNotification(wineyardId, wine)
            
            NotificationResult(
                success = result.success,
                sentCount = result.sentCount,
                message = result.message
            )
        } catch (e: Exception) {
            NotificationResult(
                success = false,
                sentCount = 0,
                message = e.message ?: "Failed to send critical notification"
            )
        }
    }
    
    suspend fun sendLowStockNotificationsForWineyard(wineyardId: String): NotificationResult {
        return try {
            val result = notificationService.sendLowStockNotificationsForWineyard(wineyardId)
            
            NotificationResult(
                success = result.success,
                sentCount = result.sentCount,
                message = result.message
            )
        } catch (e: Exception) {
            NotificationResult(
                success = false,
                sentCount = 0,
                message = e.message ?: "Failed to send bulk notifications"
            )
        }
    }
    
    suspend fun sendCustomNotification(
        wineyardId: String,
        title: String,
        message: String,
        notificationType: NotificationType,
        wineId: String? = null
    ): NotificationResult {
        return try {
            val result = notificationService.sendCustomNotification(
                wineyardId = wineyardId,
                title = title,
                message = message,
                notificationType = notificationType,
                wineId = wineId
            )
            
            NotificationResult(
                success = result.success,
                sentCount = result.sentCount,
                message = result.message
            )
        } catch (e: Exception) {
            NotificationResult(
                success = false,
                sentCount = 0,
                message = e.message ?: "Failed to send custom notification"
            )
        }
    }
}