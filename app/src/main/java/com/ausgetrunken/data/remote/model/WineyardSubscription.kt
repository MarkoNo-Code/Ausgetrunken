package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WineyardSubscription(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("wineyard_id") val wineyardId: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("low_stock_notifications") val lowStockNotifications: Boolean,
    @SerialName("new_release_notifications") val newReleaseNotifications: Boolean,
    @SerialName("special_offer_notifications") val specialOfferNotifications: Boolean,
    @SerialName("general_notifications") val generalNotifications: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)