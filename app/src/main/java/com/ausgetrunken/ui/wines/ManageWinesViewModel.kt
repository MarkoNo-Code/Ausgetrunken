package com.ausgetrunken.ui.wines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManageWinesViewModel(
    private val wineService: WineService,
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val wineyardService: WineyardService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ManageWinesUiState())
    val uiState: StateFlow<ManageWinesUiState> = _uiState.asStateFlow()
    
    fun loadWines(wineyardId: String) {
        _uiState.update { it.copy(wineyardId = wineyardId, isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            // Get current user to check permissions
            authService.getCurrentUser().collect currentUserCollect@{ currentUser ->
                if (currentUser != null) {
                    val userFlow = userRepository.getUserById(currentUser.id)
                    
                    userFlow.collect userCollect@{ user ->
                        val isWineyardOwner = user?.userType == UserType.WINEYARD_OWNER
                        
                        if (!isWineyardOwner) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    canEdit = false,
                                    errorMessage = "Access denied: Only wineyard owners can manage wines"
                                )
                            }
                            return@userCollect
                        }
                        
                        // Validate wineyard ownership for security
                        val isOwner = wineyardService.validateWineyardOwnership(currentUser.id, wineyardId)
                        
                        if (!isOwner) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    canEdit = false,
                                    errorMessage = "Access denied: You can only manage wines for your own wineyards"
                                )
                            }
                            return@userCollect
                        }
                        
                        try {
                            val wines = wineService.getWinesByWineyardFromSupabase(wineyardId)
                            _uiState.update { 
                                it.copy(
                                    wines = wines,
                                    canEdit = true,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }
                        } catch (exception: Exception) {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    canEdit = false,
                                    errorMessage = exception.message ?: "Failed to load wines"
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "User not authenticated"
                        )
                    }
                }
            }
        }
    }
    
    fun deleteWine(wineId: String) {
        if (!_uiState.value.canEdit) return
        
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
        val currentWineyardId = _uiState.value.wineyardId
        if (currentWineyardId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "No wineyard selected") }
            return
        }
        
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
    val wineyardId: String = "",
    val wines: List<WineEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val deletingWineIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val canEdit: Boolean = false
)