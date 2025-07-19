package com.ausgetrunken.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()
    
    init {
        checkAuthState()
    }
    
    private fun checkAuthState() {
        viewModelScope.launch {
            try {
                println("🚀 SplashViewModel: Starting authentication check")
                authService.restoreSession()
                    .onSuccess { user ->
                        println("🔄 SplashViewModel: RestoreSessionUseCase result - user = ${user?.email ?: "NULL"}")
                        if (user != null) {
                            println("✅ SplashViewModel: User authenticated, getting user type...")
                            // User is authenticated, get their type
                            authService.checkUserType(user.id)
                                .onSuccess { userType ->
                                    println("✅ SplashViewModel: User type = $userType")
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        isAuthenticated = true,
                                        userType = userType
                                    )
                                    println("✅ SplashViewModel: Navigation to profile should happen now")
                                }
                                .onFailure { error ->
                                    println("❌ SplashViewModel: Failed to get user type: ${error.message}")
                                    // Failed to get user type, but user is authenticated
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        isAuthenticated = true,
                                        errorMessage = "Failed to get user type: ${error.message}"
                                    )
                                }
                        } else {
                            println("❌ SplashViewModel: No valid session found, navigating to login")
                            // No valid session found
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isAuthenticated = false
                            )
                        }
                    }
                    .onFailure { error ->
                        println("❌ SplashViewModel: Session restoration failed: ${error.message}")
                        
                        // Check if this is a flagged account error
                        val errorMessage = error.message ?: ""
                        if (errorMessage.startsWith("FLAGGED_ACCOUNT:")) {
                            // Extract the actual message and pass it to the auth screen
                            val flaggedMessage = errorMessage.removePrefix("FLAGGED_ACCOUNT:")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isAuthenticated = false,
                                errorMessage = flaggedMessage // This will be passed to AuthScreen
                            )
                        } else {
                            // Regular session restoration failure
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isAuthenticated = false,
                                errorMessage = null // Don't show regular session errors on login screen
                            )
                        }
                    }
            } catch (e: Exception) {
                println("❌ SplashViewModel: Unexpected error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
    }
}