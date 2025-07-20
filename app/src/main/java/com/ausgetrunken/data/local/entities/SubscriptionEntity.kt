package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

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
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["wineId"])
    ]
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val wineId: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)