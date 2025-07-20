package com.ausgetrunken.domain.model

data class SubscriberInfo(
    val totalSubscribers: Int,
    val lowStockSubscribers: Int,
    val generalSubscribers: Int,
    val newReleaseSubscribers: Int,
    val specialOfferSubscribers: Int
)