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
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("flagged_for_deletion") val flaggedForDeletion: Boolean = false,
    @SerialName("deletion_flagged_at") val deletionFlaggedAt: String? = null,
    @SerialName("deletion_type") val deletionType: String? = null, // "USER" or "ADMIN"
    @SerialName("current_session_id") val currentSessionId: String? = null,
    @SerialName("session_created_at") val sessionCreatedAt: String? = null,
    @SerialName("last_session_activity") val lastSessionActivity: String? = null
)