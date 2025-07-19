package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDelivery(
    val id: String,
    @SerialName("notification_id") val notificationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("delivered_at") val deliveredAt: String,
    @SerialName("read_at") val readAt: String? = null
)