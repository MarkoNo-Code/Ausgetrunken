package com.ausgetrunken.ui.wineyard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.local.entities.WineType
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.repository.WineyardRepository
import com.ausgetrunken.domain.usecase.CreateWineyardUseCase
import com.ausgetrunken.domain.usecase.CreateWineUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AddWineyardNavigationEvent {
    data class NavigateBackWithSuccess(val wineyardId: String) : AddWineyardNavigationEvent()
}

class AddWineyardViewModel(
    private val createWineyardUseCase: CreateWineyardUseCase,
    private val createWineUseCase: CreateWineUseCase,
    private val authRepository: SupabaseAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddWineyardUiState())
    val uiState: StateFlow<AddWineyardUiState> = _uiState.asStateFlow()
    
    private val _navigationEvent = Channel<AddWineyardNavigationEvent>(capacity = Channel.CONFLATED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
        validateForm()
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
        validateForm()
    }

    fun onAddressChanged(address: String) {
        _uiState.value = _uiState.value.copy(address = address)
        validateForm()
    }

    fun onLocationChanged(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            latitude = latitude,
            longitude = longitude
        )
    }

    fun onImageAdded(imageUri: String) {
        val currentImages = _uiState.value.selectedImages
        _uiState.value = _uiState.value.copy(
            selectedImages = currentImages + imageUri
        )
    }

    fun onImageRemoved(imageUri: String) {
        val currentImages = _uiState.value.selectedImages
        _uiState.value = _uiState.value.copy(
            selectedImages = currentImages - imageUri
        )
    }

    fun addWine() {
        val currentWines = _uiState.value.wines
        val newWine = WineFormData(id = UUID.randomUUID().toString())
        _uiState.value = _uiState.value.copy(wines = currentWines + newWine)
    }

    fun removeWine(wineId: String) {
        val currentWines = _uiState.value.wines
        _uiState.value = _uiState.value.copy(
            wines = currentWines.filter { it.id != wineId }
        )
    }

    fun updateWine(wineId: String, updatedWine: WineFormData) {
        val currentWines = _uiState.value.wines
        val updatedWines = currentWines.map { wine ->
            if (wine.id == wineId) updatedWine else wine
        }
        _uiState.value = _uiState.value.copy(wines = updatedWines)
        validateForm()
    }

    fun onWineNameChanged(wineId: String, name: String) {
        updateWineField(wineId) { it.copy(name = name) }
    }

    fun onWineDescriptionChanged(wineId: String, description: String) {
        updateWineField(wineId) { it.copy(description = description) }
    }

    fun onWineTypeChanged(wineId: String, wineType: WineType) {
        updateWineField(wineId) { it.copy(wineType = wineType) }
    }

    fun onWineVintageChanged(wineId: String, vintage: String) {
        updateWineField(wineId) { it.copy(vintage = vintage) }
    }

    fun onWinePriceChanged(wineId: String, price: String) {
        updateWineField(wineId) { it.copy(price = price) }
    }

    fun onWineDiscountedPriceChanged(wineId: String, discountedPrice: String) {
        updateWineField(wineId) { it.copy(discountedPrice = discountedPrice) }
    }

    fun onWineStockChanged(wineId: String, stock: String) {
        updateWineField(wineId) { it.copy(stockQuantity = stock) }
    }

    fun onWineLowStockThresholdChanged(wineId: String, threshold: String) {
        updateWineField(wineId) { it.copy(lowStockThreshold = threshold) }
    }

    private fun updateWineField(wineId: String, update: (WineFormData) -> WineFormData) {
        val currentWines = _uiState.value.wines
        val updatedWines = currentWines.map { wine ->
            if (wine.id == wineId) update(wine) else wine
        }
        _uiState.value = _uiState.value.copy(wines = updatedWines)
        validateForm()
    }

    private fun validateForm() {
        val state = _uiState.value
        val isValid = state.name.isNotBlank() &&
                state.description.isNotBlank() &&
                state.address.isNotBlank() &&
                state.wines.all { it.isValid }

        _uiState.value = state.copy(isValidForm = isValid)
    }

    fun submitWineyard() {
        if (!_uiState.value.canSubmit) {
            println("🔥 AddWineyardViewModel: Cannot submit - form not valid")
            return
        }

        println("🔥 AddWineyardViewModel: Starting wineyard submission...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)

            try {
                val currentUser = authRepository.currentUser
                println("🔥 AddWineyardViewModel: Current user: ${currentUser?.id}")
                if (currentUser == null) {
                    println("🔥 AddWineyardViewModel: ERROR - User not authenticated")
                    _uiState.value = _uiState.value.copy(
                        error = "User not authenticated",
                        isSubmitting = false
                    )
                    return@launch
                }

                val state = _uiState.value
                val wineyardId = UUID.randomUUID().toString()

                // Create wineyard entity
                val wineyardEntity = WineyardEntity(
                    id = wineyardId,
                    ownerId = currentUser.id,
                    name = state.name,
                    description = state.description,
                    address = state.address,
                    latitude = state.latitude ?: 0.0,
                    longitude = state.longitude ?: 0.0,
                    photos = state.selectedImages
                )

                // Create wineyard
                println("🔥 AddWineyardViewModel: Creating wineyard entity: $wineyardEntity")
                val result = createWineyardUseCase(wineyardEntity)
                println("🔥 AddWineyardViewModel: Create result - isSuccess: ${result.isSuccess}")
                
                if (result.isSuccess) {
                    // Create wines if any
                    state.wines.forEach { wineForm ->
                        if (wineForm.isValid) {
                            val wineEntity = WineEntity(
                                id = UUID.randomUUID().toString(),
                                wineyardId = wineyardId,
                                name = wineForm.name,
                                description = wineForm.description,
                                wineType = wineForm.wineType,
                                vintage = wineForm.vintage.toInt(),
                                price = wineForm.price.toDouble(),
                                discountedPrice = wineForm.discountedPrice.toDoubleOrNull(),
                                stockQuantity = wineForm.stockQuantity.toInt(),
                                lowStockThreshold = wineForm.lowStockThreshold.toIntOrNull() ?: 20,
                                photos = wineForm.photos
                            )
                            createWineUseCase(wineEntity)
                        }
                    }

                    println("AddWineyardViewModel: Wineyard created successfully, wineyardId: $wineyardId")
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = null
                    )
                    
                    // Send navigation event through channel
                    _navigationEvent.trySend(AddWineyardNavigationEvent.NavigateBackWithSuccess(wineyardId))
                } else {
                    println("🔥 AddWineyardViewModel: ERROR - Create failed: ${result.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "Failed to create wineyard",
                        isSubmitting = false
                    )
                }
            } catch (e: Exception) {
                println("🔥 AddWineyardViewModel: EXCEPTION - ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error occurred",
                    isSubmitting = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearNavigationFlag() {
        _uiState.value = _uiState.value.copy(navigateBackWithSuccess = null)
    }
    
    fun onNavigateBackWithSuccess(onNavigateBack: (String) -> Unit) {
        // Clear any form state if needed
        // Then trigger navigation
        // This will be called from the screen when navigation event is received
    }
}