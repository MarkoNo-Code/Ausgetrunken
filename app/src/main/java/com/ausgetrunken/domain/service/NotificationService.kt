package com.ausgetrunken.domain.service

import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.repository.NotificationRepository
import com.ausgetrunken.domain.repository.NotificationSendResult

class NotificationService(
    private val notificationRepository: NotificationRepository
) {
    
    suspend fun sendLowStockNotification(
        wineryId: String,
        wine: WineEntity
    ): NotificationSendResult {
        val title = "Low Stock Alert"
        val message = "${wine.name} has only ${wine.stockQuantity} bottles left - grab yours now!"
        
        return notificationRepository.sendNotification(
            wineryId = wineryId,
            notificationType = NotificationType.LOW_STOCK,
            title = title,
            message = message,
            wineId = wine.id
        )
    }
    
    suspend fun sendCriticalStockNotification(
        wineryId: String,
        wine: WineEntity
    ): NotificationSendResult {
        val title = "Critical Stock Alert!"
        val message = "Ausgetrunken - ${wine.name} is almost gone! Last chance before it's 'ausgetrunken'! Only ${wine.stockQuantity} bottles remaining."
        
        return notificationRepository.sendNotification(
            wineryId = wineryId,
            notificationType = NotificationType.CRITICAL_STOCK,
            title = title,
            message = message,
            wineId = wine.id
        )
    }
    
    suspend fun sendLowStockNotificationsForWinery(wineryId: String): NotificationSendResult {
        return notificationRepository.sendLowStockNotificationsForWinery(wineryId)
    }
    
    suspend fun sendCustomNotification(
        wineryId: String,
        title: String,
        message: String,
        notificationType: NotificationType,
        wineId: String? = null
    ): NotificationSendResult {
        return notificationRepository.sendNotification(
            wineryId = wineryId,
            notificationType = notificationType,
            title = title,
            message = message,
            wineId = wineId
        )
    }
    
    suspend fun updateUserFcmToken(userId: String, fcmToken: String) {
        notificationRepository.updateUserFcmToken(userId, fcmToken)
    }
    
    suspend fun clearUserFcmToken(userId: String) {
        notificationRepository.clearUserFcmToken(userId)
    }
    
    suspend fun getUserFcmToken(userId: String): String? {
        return notificationRepository.getUserFcmToken(userId)
    }
}