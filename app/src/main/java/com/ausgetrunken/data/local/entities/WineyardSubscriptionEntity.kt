package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "wineyard_subscriptions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["wineyardId"]),
        Index(value = ["userId", "wineyardId"], unique = true)
    ]
)
data class WineyardSubscriptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val wineyardId: String,
    val isActive: Boolean = true,
    val lowStockNotifications: Boolean = true,
    val newReleaseNotifications: Boolean = true,
    val specialOfferNotifications: Boolean = true,
    val generalNotifications: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)