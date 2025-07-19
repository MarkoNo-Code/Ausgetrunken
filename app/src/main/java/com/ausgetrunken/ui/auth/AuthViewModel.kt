package com.ausgetrunken.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.domain.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    fun switchMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            errorMessage = null,
            successMessage = null,
            confirmPassword = "" // Clear confirm password when switching modes
        )
    }
    
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
    
    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    fun setInitialEmail(email: String) {
        if (email.isNotBlank()) {
            _uiState.value = _uiState.value.copy(email = email)
        }
    }
    
    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please fill in all fields")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null, successMessage = null)
            
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
                                errorMessage = "Failed to determine user type: ${error.message ?: "Unknown error"}"
                            )
                        }
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = "Login failed: ${error.message ?: "Unknown error"}"
                    )
                }
        }
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
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null, successMessage = null)
            
            authService.signUp(currentState.email, currentState.password, currentState.selectedUserType)
                .onSuccess { _ ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isRegistrationSuccessful = true,
                        successMessage = "Account created successfully! You can now sign in with your credentials.",
                        mode = AuthMode.LOGIN, // Switch to login mode
                        confirmPassword = "" // Clear confirm password
                    )
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = "${error.message ?: "Unknown error"}"
                    )
                }
        }
    }
    
    private fun isValidEmail(email: String): Boolean {
        // Use Android's built-in email validation pattern which properly handles + signs
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }
}