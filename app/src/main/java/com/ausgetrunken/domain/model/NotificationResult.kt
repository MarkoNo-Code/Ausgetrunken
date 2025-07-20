package com.ausgetrunken.domain.model

data class NotificationResult(
    val success: Boolean,
    val sentCount: Int,
    val message: String
)