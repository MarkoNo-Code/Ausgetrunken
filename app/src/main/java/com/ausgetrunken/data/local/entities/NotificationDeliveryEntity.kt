package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_deliveries")
data class NotificationDeliveryEntity(
    @PrimaryKey val id: String,
    val notificationId: String,
    val userId: String,
    val deliveredAt: Long = System.currentTimeMillis(),
    val readAt: Long? = null
)