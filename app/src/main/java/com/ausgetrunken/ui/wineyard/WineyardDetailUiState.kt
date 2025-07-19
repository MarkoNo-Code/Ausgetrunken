package com.ausgetrunken.ui.wineyard

import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.local.entities.WineEntity

data class WineyardDetailUiState(
    val wineyard: WineyardEntity? = null,
    val wines: List<WineEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
    val showImagePicker: Boolean = false,
    val showLocationPicker: Boolean = false,
    val navigateBackAfterDelete: Boolean = false,
    val canEdit: Boolean = false,
    val isSubscribed: Boolean = false,
    val isSubscriptionLoading: Boolean = false
)