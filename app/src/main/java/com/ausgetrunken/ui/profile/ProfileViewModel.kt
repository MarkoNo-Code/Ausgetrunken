package com.ausgetrunken.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineyardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: SupabaseAuthRepository,
    private val wineyardService: WineyardService,
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val currentUser = authRepository.currentUser
            if (currentUser != null) {
                try {
                    // Load user info
                    val userEmail = currentUser.email ?: ""
                    val userName = currentUser.userMetadata?.get("full_name")?.toString() ?: "Wineyard Owner"
                    val profilePictureUrl = currentUser.userMetadata?.get("avatar_url")?.toString()
                    
                    wineyardService.getWineyardsByOwner(currentUser.id).collect { wineyards ->
                        _uiState.value = _uiState.value.copy(
                            wineyards = wineyards,
                            canAddMoreWineyards = wineyards.size < 5,
                            userName = userName,
                            userEmail = userEmail,
                            profilePictureUrl = profilePictureUrl,
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load profile: ${e.message}"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "User not authenticated"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun refreshProfile() {
        loadUserProfile()
    }
    
    fun showProfilePicturePicker() {
        _uiState.value = _uiState.value.copy(showProfilePicturePicker = true)
    }
    
    fun hideProfilePicturePicker() {
        _uiState.value = _uiState.value.copy(showProfilePicturePicker = false)
    }
    
    fun updateProfilePicture(imageUrl: String) {
        _uiState.value = _uiState.value.copy(
            profilePictureUrl = imageUrl,
            showProfilePicturePicker = false
        )
        // TODO: Upload to storage and update user metadata
    }
    
    fun logout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoggingOut = true, errorMessage = null)
                
                authService.signOut()
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isLoggingOut = false,
                            logoutSuccess = true
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoggingOut = false,
                            errorMessage = "Failed to logout: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
    }
    
    fun showDeleteAccountDialog() {
        _uiState.value = _uiState.value.copy(showDeleteAccountDialog = true)
    }
    
    fun hideDeleteAccountDialog() {
        _uiState.value = _uiState.value.copy(showDeleteAccountDialog = false)
    }
    
    fun deleteAccount() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = true,
                    showDeleteAccountDialog = false,
                    errorMessage = null
                )
                
                authService.deleteAccount()
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isDeletingAccount = false,
                            deleteAccountSuccess = true
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isDeletingAccount = false,
                            errorMessage = "Failed to delete account: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = false,
                    errorMessage = "Unexpected error during account deletion: ${e.message}"
                )
            }
        }
    }
}