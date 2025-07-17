package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "wines",
    foreignKeys = [
        ForeignKey(
            entity = WineyardEntity::class,
            parentColumns = ["id"],
            childColumns = ["wineyardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WineEntity(
    @PrimaryKey val id: String,
    val wineyardId: String,
    val name: String,
    val description: String,
    val wineType: WineType,
    val vintage: Int,
    val price: Double,
    val discountedPrice: Double? = null,
    val stockQuantity: Int,
    val lowStockThreshold: Int = 20,
    val photos: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class WineType {
    RED,
    WHITE,
    ROSE,
    SPARKLING,
    DESSERT,
    FORTIFIED
}