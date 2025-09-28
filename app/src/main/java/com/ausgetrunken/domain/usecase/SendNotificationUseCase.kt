package com.ausgetrunken.domain.usecase

import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.model.NotificationResult
import com.ausgetrunken.domain.service.NotificationService

class SendNotificationUseCase(
    private val notificationService: NotificationService
) {
    
    suspend fun sendLowStockNotification(wineryId: String, wine: WineEntity): NotificationResult {
        return try {
            val result = notificationService.sendLowStockNotification(wineryId, wine)
            
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
    
    suspend fun sendCriticalStockNotification(wineryId: String, wine: WineEntity): NotificationResult {
        return try {
            val result = notificationService.sendCriticalStockNotification(wineryId, wine)
            
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
    
    suspend fun sendLowStockNotificationsForWinery(wineryId: String): NotificationResult {
        return try {
            val result = notificationService.sendLowStockNotificationsForWinery(wineryId)
            
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
        wineryId: String,
        title: String,
        message: String,
        notificationType: NotificationType,
        wineId: String? = null
    ): NotificationResult {
        return try {
            val result = notificationService.sendCustomNotification(
                wineryId = wineryId,
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