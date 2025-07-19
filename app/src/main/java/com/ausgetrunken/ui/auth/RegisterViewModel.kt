package com.ausgetrunken.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.domain.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    
    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword)
    }
    
    fun updateUserType(userType: UserType) {
        _uiState.value = _uiState.value.copy(selectedUserType = userType)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun register() {
        val currentState = _uiState.value
        
        if (currentState.email.isBlank() || currentState.password.isBlank() || currentState.confirmPassword.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please fill in all fields")
            return
        }
        
        // Email format validation
        if (!isValidEmail(currentState.email)) {
            _uiState.value = currentState.copy(errorMessage = "Please enter a valid email address")
            return
        }
        
        if (currentState.password != currentState.confirmPassword) {
            _uiState.value = currentState.copy(errorMessage = "Passwords do not match")
            return
        }
        
        if (currentState.password.length < 6) {
            _uiState.value = currentState.copy(errorMessage = "Password must be at least 6 characters")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
            
            authService.signUp(currentState.email, currentState.password, currentState.selectedUserType)
                .onSuccess { _ ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isRegistrationSuccessful = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = "Registration failed: ${error.message ?: "Unknown error"}"
                    )
                }
        }
    }
    
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}