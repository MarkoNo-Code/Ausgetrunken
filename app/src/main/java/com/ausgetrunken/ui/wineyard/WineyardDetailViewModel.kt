package com.ausgetrunken.ui.wineyard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineyardRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class WineyardDetailViewModel(
    private val wineyardService: WineyardService,
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val subscriptionService: WineyardSubscriptionService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(WineyardDetailUiState())
    val uiState: StateFlow<WineyardDetailUiState> = _uiState.asStateFlow()
    
    fun loadWineyard(wineyardId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Get current user to check permissions
            authService.getCurrentUser().collect { currentUser ->
                if (currentUser != null) {
                    val userFlow = userRepository.getUserById(currentUser.id)
                    val wineyardFlow = wineyardService.getWineyardById(wineyardId)
                    
                    combine(userFlow, wineyardFlow) { user, wineyard ->
                        val canEdit = user?.userType == UserType.WINEYARD_OWNER
                        _uiState.value = _uiState.value.copy(
                            wineyard = wineyard,
                            canEdit = canEdit,
                            isLoading = false
                        )
                        
                        // Load subscription status for customer users
                        if (user?.userType == UserType.CUSTOMER && wineyard != null) {
                            loadSubscriptionStatus(currentUser.id, wineyard.id)
                        }
                    }.collect {}
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "User not authenticated"
                    )
                }
            }
        }
    }
    
    fun toggleEdit() {
        if (_uiState.value.canEdit) {
            _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
        }
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
        if (!_uiState.value.canEdit) return
        
        _uiState.value.wineyard?.let { wineyard ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isUpdating = true)
                
                val updatedWineyard = wineyard.copy(updatedAt = System.currentTimeMillis())
                
                wineyardService.updateWineyard(updatedWineyard)
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
    
    fun deleteWineyard() {
        if (!_uiState.value.canEdit) return
        
        _uiState.value.wineyard?.let { wineyard ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isDeleting = true)
                
                wineyardService.deleteWineyard(wineyard.id)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isDeleting = false,
                            navigateBackAfterDelete = true
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isDeleting = false,
                            errorMessage = "Failed to delete wineyard: ${error.message}"
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
    
    private fun loadSubscriptionStatus(userId: String, wineyardId: String) {
        viewModelScope.launch {
            try {
                val isSubscribed = subscriptionService.isSubscribed(userId, wineyardId)
                _uiState.value = _uiState.value.copy(isSubscribed = isSubscribed)
            } catch (e: Exception) {
                // Handle subscription loading error silently
            }
        }
    }
    
    fun toggleWineyardSubscription() {
        viewModelScope.launch {
            try {
                val currentUser = authService.getCurrentUser().firstOrNull()
                val userId = currentUser?.id ?: return@launch
                val wineyardId = _uiState.value.wineyard?.id ?: return@launch
                
                // Add loading state
                _uiState.value = _uiState.value.copy(isSubscriptionLoading = true)
                
                val isCurrentlySubscribed = _uiState.value.isSubscribed
                
                if (isCurrentlySubscribed) {
                    subscriptionService.unsubscribeFromWineyard(userId, wineyardId)
                    _uiState.value = _uiState.value.copy(isSubscribed = false)
                } else {
                    subscriptionService.subscribeToWineyard(userId, wineyardId)
                    _uiState.value = _uiState.value.copy(isSubscribed = true)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update subscription: ${e.message}"
                )
            } finally {
                // Remove loading state
                _uiState.value = _uiState.value.copy(isSubscriptionLoading = false)
            }
        }
    }
}