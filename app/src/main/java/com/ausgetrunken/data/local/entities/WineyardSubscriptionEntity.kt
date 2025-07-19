package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wineyard_subscriptions")
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