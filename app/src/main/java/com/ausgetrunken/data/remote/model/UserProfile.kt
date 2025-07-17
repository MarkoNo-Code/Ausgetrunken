package com.ausgetrunken.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("user_type") val userType: String,
    @SerialName("profile_completed") val profileCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("address") val address: String? = null
)