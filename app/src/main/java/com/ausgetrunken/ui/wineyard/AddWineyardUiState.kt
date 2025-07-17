package com.ausgetrunken.ui.wineyard

import com.ausgetrunken.data.local.entities.WineType

data class AddWineyardUiState(
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val selectedImages: List<String> = emptyList(),
    val wines: List<WineFormData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val isValidForm: Boolean = false,
    val navigateBackWithSuccess: String? = null
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && 
                description.isNotBlank() && 
                address.isNotBlank() && 
                !isLoading && 
                !isSubmitting
}

data class WineFormData(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val wineType: WineType = WineType.RED,
    val vintage: String = "",
    val price: String = "",
    val discountedPrice: String = "",
    val stockQuantity: String = "",
    val lowStockThreshold: String = "20",
    val photos: List<String> = emptyList()
) {
    val isValid: Boolean
        get() = name.isNotBlank() && 
                description.isNotBlank() && 
                vintage.isNotBlank() && 
                price.isNotBlank() && 
                stockQuantity.isNotBlank() &&
                vintage.toIntOrNull() != null &&
                price.toDoubleOrNull() != null &&
                stockQuantity.toIntOrNull() != null
}