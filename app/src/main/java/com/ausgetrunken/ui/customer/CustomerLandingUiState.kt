package com.ausgetrunken.ui.customer

import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.local.entities.WineEntity

data class CustomerLandingUiState(
    val isLoading: Boolean = false,
    val currentTab: CustomerTab = CustomerTab.WINEYARDS,
    val wineyards: List<WineyardEntity> = emptyList(),
    val wines: List<WineEntity> = emptyList(),
    val currentWineyardPage: Int = 0,
    val currentWinePage: Int = 0,
    val hasMoreWineyards: Boolean = true,
    val hasMoreWines: Boolean = true,
    val errorMessage: String? = null
)

enum class CustomerTab {
    WINEYARDS,
    WINES
}