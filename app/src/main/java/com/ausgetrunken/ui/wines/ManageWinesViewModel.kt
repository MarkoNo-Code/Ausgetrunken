package com.ausgetrunken.ui.wines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.service.WineService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManageWinesViewModel(
    private val wineService: WineService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ManageWinesUiState())
    val uiState: StateFlow<ManageWinesUiState> = _uiState.asStateFlow()
    
    fun loadWines(wineyardId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        // TEMPORARY FIX: Use the same test wineyard ID that wines are created with
        val testWineyardId = "00000000-0000-0000-0000-000000000001"
        // Using test wineyard ID temporarily
        
        viewModelScope.launch {
            try {
                val wines = wineService.getWinesByWineyardFromSupabase(testWineyardId)
                _uiState.update { 
                    it.copy(
                        wines = wines,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (exception: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Failed to load wines"
                    )
                }
            }
        }
    }
    
    fun deleteWine(wineId: String) {
        _uiState.update { 
            it.copy(
                deletingWineIds = it.deletingWineIds + wineId
            )
        }
        
        viewModelScope.launch {
            wineService.deleteWine(wineId)
                .onSuccess {
                    _uiState.update { 
                        it.copy(
                            wines = it.wines.filter { wine -> wine.id != wineId },
                            deletingWineIds = it.deletingWineIds - wineId
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update { 
                        it.copy(
                            deletingWineIds = it.deletingWineIds - wineId,
                            errorMessage = exception.message ?: "Failed to delete wine"
                        )
                    }
                }
        }
    }
    
    fun refreshWines() {
        val currentWineyardId = "00000000-0000-0000-0000-000000000001" // Same test ID
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                val wines = wineService.getWinesByWineyardFromSupabase(currentWineyardId)
                _uiState.update { 
                    it.copy(
                        wines = wines,
                        isRefreshing = false,
                        errorMessage = null
                    )
                }
            } catch (exception: Exception) {
                _uiState.update { 
                    it.copy(
                        isRefreshing = false,
                        errorMessage = exception.message ?: "Failed to refresh wines"
                    )
                }
            }
        }
    }
    
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

data class ManageWinesUiState(
    val wines: List<WineEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val deletingWineIds: Set<String> = emptySet(),
    val errorMessage: String? = null
)