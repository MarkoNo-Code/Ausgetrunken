package com.ausgetrunken.ui.winery

import com.ausgetrunken.data.local.entities.WineryEntity
import com.ausgetrunken.data.local.entities.WineEntity

data class WineryDetailUiState(
    val winery: WineryEntity? = null,
    val wines: List<WineEntity> = emptyList(),
    val photos: List<String> = emptyList(), // Photo paths for UI consumption
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
    val showImagePicker: Boolean = false,
    val showLocationPicker: Boolean = false,
    val navigateBackAfterDelete: Boolean = false,
    val navigateBackAfterSave: Boolean = false,
    val canEdit: Boolean = false,
    val isSubscribed: Boolean = false,
    val isSubscriptionLoading: Boolean = false
)