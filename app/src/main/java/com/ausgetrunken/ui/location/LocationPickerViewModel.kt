package com.ausgetrunken.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.common.AppResult
import com.ausgetrunken.domain.service.WineyardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationPickerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUpdating: Boolean = false
)

class LocationPickerViewModel(
    private val wineyardService: WineyardService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LocationPickerUiState())
    val uiState: StateFlow<LocationPickerUiState> = _uiState.asStateFlow()
    
    fun updateWineyardLocation(
        wineyardId: String,
        latitude: Double,
        longitude: Double,
        address: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)
            
            try {
                val result = wineyardService.updateWineyardLocation(
                    wineyardId = wineyardId,
                    latitude = latitude,
                    longitude = longitude,
                    address = address ?: ""
                )
                
                when (result) {
                    is AppResult.Success -> {
                        _uiState.value = _uiState.value.copy(isUpdating = false)
                        // Success handled by navigation
                    }
                    is AppResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            errorMessage = result.error.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}