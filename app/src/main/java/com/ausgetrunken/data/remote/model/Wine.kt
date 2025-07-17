package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Wine(
    @SerialName("id") val id: String,
    @SerialName("wineyard_id") val wineyardId: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("wine_type") val wineType: String, // RED, WHITE, ROSE, SPARKLING
    @SerialName("vintage") val vintage: Int? = null,
    @SerialName("alcohol_content") val alcoholContent: Double? = null,
    @SerialName("price") val price: Double,
    @SerialName("stock_quantity") val stockQuantity: Int,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("grape_variety") val grapeVariety: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)