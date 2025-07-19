package com.ausgetrunken.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomerProfileViewModel(
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CustomerProfileUiState())
    val uiState: StateFlow<CustomerProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                authService.getCurrentUser().collect { user ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = user
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
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
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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