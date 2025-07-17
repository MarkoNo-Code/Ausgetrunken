package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("wine_id") val wineId: String,
    @SerialName("subscription_type") val subscriptionType: String, // MONTHLY, QUARTERLY, YEARLY
    @SerialName("quantity") val quantity: Int,
    @SerialName("next_delivery_date") val nextDeliveryDate: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)