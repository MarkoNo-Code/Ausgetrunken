package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Wine(
    @SerialName("id") val id: String,
    @SerialName("winery_id") val wineryId: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("wine_type") val wineType: String,
    @SerialName("vintage") val vintage: Int,
    @SerialName("alcohol_content") val alcoholContent: Double? = null,
    @SerialName("price") val price: Double,
    @SerialName("discounted_price") val discountedPrice: Double? = null,
    @SerialName("stock_quantity") val stockQuantity: Int,
    @SerialName("full_stock_quantity") val fullStockQuantity: Int? = null,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("grape_variety") val grapeVariety: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)