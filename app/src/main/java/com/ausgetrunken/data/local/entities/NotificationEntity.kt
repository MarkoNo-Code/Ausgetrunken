package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val wineyardId: String,
    val senderId: String,
    val notificationType: NotificationType,
    val title: String,
    val message: String,
    val wineId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class NotificationType {
    LOW_STOCK,
    CRITICAL_STOCK,
    NEW_WINE,
    NEW_RELEASE,
    SPECIAL_OFFER,
    GENERAL
}