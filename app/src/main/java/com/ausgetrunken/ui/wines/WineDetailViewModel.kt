package com.ausgetrunken.ui.wines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WinePhotoService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WineDetailViewModel(
    private val wineService: WineService,
    private val winePhotoService: WinePhotoService
) : ViewModel() {

    private val _uiState = MutableStateFlow(WineDetailUiState())
    val uiState: StateFlow<WineDetailUiState> = _uiState.asStateFlow()

    // Wine photos flow
    val winePhotos = MutableStateFlow<List<String>>(emptyList())
    
    fun loadWine(wineId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            wineService.getWineById(wineId)
                .catch { exception ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Failed to load wine"
                        )
                    }
                }
                .collect { wine ->
                    _uiState.update {
                        it.copy(
                            wine = wine,
                            isLoading = false,
                            errorMessage = null
                        )
                    }

                    // Load wine photos if wine exists
                    wine?.let { loadWinePhotos(it.id) }
                }
        }
    }

    private fun loadWinePhotos(wineId: String) {
        viewModelScope.launch {
            winePhotoService.getWinePhotosWithStatus(wineId).collect { photosWithStatus ->
                val photosPaths = photosWithStatus.map { it.localPath }
                winePhotos.value = photosPaths
                println("✅ WineDetailViewModel: Loaded ${photosPaths.size} photos for wine $wineId")
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

data class WineDetailUiState(
    val wine: WineEntity? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)