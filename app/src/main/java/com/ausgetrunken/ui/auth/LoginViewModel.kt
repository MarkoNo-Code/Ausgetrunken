package com.ausgetrunken.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.notifications.FCMTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authService: AuthService,
    private val fcmTokenManager: FCMTokenManager
) : ViewModel() {
    
    // Start with empty fields - email will be set from navigation if needed
    private val _uiState = MutableStateFlow(
        LoginUiState(
            email = "",
            password = ""
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
    
    fun getCurrentEmail(): String {
        return _uiState.value.email
    }
    
    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please fill in all fields")
            return
        }
        
        viewModelScope.launch {
            // CRITICAL: Reset login success state before attempting new login
            println("🔄 LoginViewModel: Resetting login state before new attempt")
            _uiState.value = currentState.copy(
                isLoading = true, 
                errorMessage = null,
                isLoginSuccessful = false, // Reset this flag
                userType = null // Reset user type as well
            )
            
            println("🔍 LoginViewModel: Starting login for email: ${currentState.email}")
            authService.signIn(currentState.email, currentState.password)
                .onSuccess { user ->
                    println("✅ LoginViewModel: Login successful for user: ${user.id}")
                    println("✅ LoginViewModel: User email: ${user.email}")
                    
                    authService.checkUserType(user.id)
                        .onSuccess { userType ->
                            println("✅ LoginViewModel: User type determined: $userType")
                            
                            // Update FCM token for the logged-in user
                            println("🔧 LoginViewModel: Triggering FCM token update for user: ${user.id}")
                            fcmTokenManager.updateTokenForUser(user.id)
                            
                            println("🚀 LoginViewModel: Setting login success state - navigating to: $userType")
                            _uiState.value = currentState.copy(
                                isLoading = false,
                                isLoginSuccessful = true,
                                userType = userType
                            )
                            println("✅ LoginViewModel: Login state updated successfully")
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