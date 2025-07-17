package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WineEntity::class,
            parentColumns = ["id"],
            childColumns = ["wineId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val wineId: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)