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
                        // Session restoration failed
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = false,
                            errorMessage = "Session restoration failed: ${error.message}"
                        )
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