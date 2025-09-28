package com.ausgetrunken.ui.customer

import com.ausgetrunken.data.local.entities.WineryEntity
import com.ausgetrunken.data.local.entities.WineEntity

data class CustomerLandingUiState(
    val isLoading: Boolean = false,
    val currentTab: CustomerTab = CustomerTab.WINERIES,
    val wineries: List<WineryEntity> = emptyList(),
    val wines: List<WineEntity> = emptyList(),
    val currentWineryPage: Int = 0,
    val currentWinePage: Int = 0,
    val hasMoreWineries: Boolean = true,
    val hasMoreWines: Boolean = true,
    val errorMessage: String? = null,
    val subscribedWineryIds: Set<String> = emptySet(),
    val subscriptionLoadingIds: Set<String> = emptySet(),
    val isSubscriptionDataLoading: Boolean = true // Initially loading subscription data
)

enum class CustomerTab {
    WINERIES,
    WINES
}