package com.ausgetrunken.ui.wineyard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.domain.usecase.GetWineyardByIdUseCase
import com.ausgetrunken.domain.usecase.UpdateWineyardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WineyardDetailViewModel(
    private val getWineyardByIdUseCase: GetWineyardByIdUseCase,
    private val updateWineyardUseCase: UpdateWineyardUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(WineyardDetailUiState())
    val uiState: StateFlow<WineyardDetailUiState> = _uiState.asStateFlow()
    
    fun loadWineyard(wineyardId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            getWineyardByIdUseCase(wineyardId).collect { wineyard ->
                _uiState.value = _uiState.value.copy(
                    wineyard = wineyard,
                    isLoading = false
                )
            }
        }
    }
    
    fun toggleEdit() {
        _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
    }
    
    fun updateWineyardName(name: String) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(name = name)
            )
        }
    }
    
    fun updateWineyardDescription(description: String) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(description = description)
            )
        }
    }
    
    fun updateWineyardAddress(address: String) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(address = address)
            )
        }
    }
    
    fun updateWineyardLocation(latitude: Double, longitude: Double) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(latitude = latitude, longitude = longitude)
            )
        }
    }
    
    fun addPhoto(photoUrl: String) {
        _uiState.value.wineyard?.let { wineyard ->
            val updatedPhotos = wineyard.photos + photoUrl
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(photos = updatedPhotos)
            )
        }
    }
    
    fun removePhoto(photoUrl: String) {
        _uiState.value.wineyard?.let { wineyard ->
            val updatedPhotos = wineyard.photos.filter { it != photoUrl }
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(photos = updatedPhotos)
            )
        }
    }
    
    fun saveWineyard() {
        _uiState.value.wineyard?.let { wineyard ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isUpdating = true)
                
                val updatedWineyard = wineyard.copy(updatedAt = System.currentTimeMillis())
                
                updateWineyardUseCase(updatedWineyard)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            wineyard = updatedWineyard,
                            isUpdating = false,
                            isEditing = false
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            errorMessage = "Failed to update wineyard: ${error.message}"
                        )
                    }
            }
        }
    }
    
    fun showImagePicker() {
        _uiState.value = _uiState.value.copy(showImagePicker = true)
    }
    
    fun hideImagePicker() {
        _uiState.value = _uiState.value.copy(showImagePicker = false)
    }
    
    fun showLocationPicker() {
        _uiState.value = _uiState.value.copy(showLocationPicker = true)
    }
    
    fun hideLocationPicker() {
        _uiState.value = _uiState.value.copy(showLocationPicker = false)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}