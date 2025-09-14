package com.ausgetrunken.ui.wineyard

data class AddWineyardUiState(
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val selectedImages: List<String> = emptyList(),
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