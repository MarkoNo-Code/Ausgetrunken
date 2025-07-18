package com.ausgetrunken.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authService: AuthService
) : ViewModel() {
    
    // Pre-fill with test credentials for faster testing
    private val _uiState = MutableStateFlow(
        LoginUiState(
            email = "marko.nonninger@gmail.com",
            password = "123456"
        )
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please fill in all fields")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
            
            authService.signIn(currentState.email, currentState.password)
                .onSuccess { user ->
                    authService.checkUserType(user.id)
                        .onSuccess { userType ->
                            _uiState.value = currentState.copy(
                                isLoading = false,
                                isLoginSuccessful = true,
                                userType = userType
                            )
                        }
                        .onFailure { error ->
                            _uiState.value = currentState.copy(
                                isLoading = false,
                                errorMessage = "Failed to get user type: ${error.message}"
                            )
                        }
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = "Login failed: ${error.message}"
                    )
                }
        }
    }
}