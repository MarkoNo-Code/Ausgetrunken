package com.ausgetrunken.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val userType: UserType,
    val profileCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserType {
    WINEYARD_OWNER,
    CUSTOMER
}