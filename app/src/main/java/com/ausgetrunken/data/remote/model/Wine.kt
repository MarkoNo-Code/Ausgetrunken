package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Wine(
    @SerialName("id") val id: String,
    @SerialName("wineyard_id") val wineyardId: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("wine_type") val wineType: String,
    @SerialName("vintage") val vintage: Int,
    @SerialName("price") val price: Double,
    @SerialName("discounted_price") val discountedPrice: Double? = null,
    @SerialName("stock_quantity") val stockQuantity: Int,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int,
    @SerialName("photos") val photos: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)