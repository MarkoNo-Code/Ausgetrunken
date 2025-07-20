package com.ausgetrunken.domain.repository

import com.ausgetrunken.data.local.entities.NotificationType

interface NotificationRepository {
    
    suspend fun sendNotification(
        wineyardId: String,
        notificationType: NotificationType,
        title: String,
        message: String,
        wineId: String? = null
    ): NotificationSendResult
    
    suspend fun sendLowStockNotificationsForWineyard(wineyardId: String): NotificationSendResult
    
    suspend fun updateUserFcmToken(userId: String, fcmToken: String)
    
    suspend fun getUserFcmToken(userId: String): String?
}

data class NotificationSendResult(
    val success: Boolean,
    val sentCount: Int,
    val message: String,
    val failedCount: Int = 0
)