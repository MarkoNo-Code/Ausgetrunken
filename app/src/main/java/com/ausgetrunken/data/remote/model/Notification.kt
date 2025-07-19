package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String,
    @SerialName("wineyard_id") val wineyardId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("notification_type") val notificationType: String,
    val title: String,
    val message: String,
    @SerialName("wine_id") val wineId: String? = null,
    @SerialName("created_at") val createdAt: String
)