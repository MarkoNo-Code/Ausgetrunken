package com.ausgetrunken.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.notifications.FCMTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val authService: AuthService,
    private val fcmTokenManager: FCMTokenManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()
    
    private var authCheckInProgress = false
    
    init {
        println("🔍 SplashViewModel: ViewModel initialized, starting auth check")
        checkAuthState()
    }
    
    // Add method to manually trigger auth check (for navigation resets)
    fun recheckAuthState() {
        println("🔄 SplashViewModel: Manual auth recheck requested")
        checkAuthState()
    }
    
    private fun checkAuthState() {
        if (authCheckInProgress) {
            println("⚠️ SplashViewModel: Auth check already in progress, skipping")
            return
        }
        
        authCheckInProgress = true
        println("🚀 SplashViewModel: Starting authentication check")
        
        viewModelScope.launch {
            try {
                println("🚀 SplashViewModel: DEBUG - About to call authService.restoreSession()")
                authService.restoreSession()
                    .onSuccess { user ->
                        println("✅ SplashViewModel: RestoreSession SUCCESS - user = ${user?.email ?: "NULL"}")
                        println("✅ SplashViewModel: User details: id=${user?.id}, email=${user?.email}")
                        if (user != null) {
                            println("✅ SplashViewModel: User authenticated, getting user type...")
                            // User is authenticated, get their type
                            authService.checkUserType(user.id)
                                .onSuccess { userType ->
                                    println("✅ SplashViewModel: User type = $userType")
                                    
                                    // Update FCM token for restored session to ensure notifications work
                                    println("🔧 SplashViewModel: Updating FCM token for restored session - user: ${user.id}")
                                    fcmTokenManager.updateTokenForUser(user.id)
                                    
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
                            println("❌ SplashViewModel: RestoreSession returned NULL - no valid session found")
                            println("❌ SplashViewModel: Navigating to login screen")
                            // No valid session found
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isAuthenticated = false
                            )
                        }
                    }
                    .onFailure { error ->
                        println("❌ SplashViewModel: RestoreSession FAILED with error: ${error.message}")
                        println("❌ SplashViewModel: Error type: ${error.javaClass.simpleName}")
                        
                        // Check for different types of session errors
                        val errorMessage = error.message ?: ""
                        
                        // Handle special case where session is valid but we don't have Supabase UserInfo
                        if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                            println("✅ SplashViewModel: Session is valid but no UserInfo - extracting user data")
                            val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                            if (parts.size >= 2) {
                                val userId = parts[0]
                                val email = parts[1]
                                println("✅ SplashViewModel: Extracted userId: $userId, email: $email")
                                
                                // Get user type for valid session
                                authService.checkUserType(userId)
                                    .onSuccess { userType ->
                                        println("✅ SplashViewModel: User type = $userType (from valid session)")
                                        
                                        // Update FCM token for restored session
                                        println("🔧 SplashViewModel: Updating FCM token for valid session - user: $userId")
                                        fcmTokenManager.updateTokenForUser(userId)
                                        
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            isAuthenticated = true,
                                            userType = userType
                                        )
                                        println("✅ SplashViewModel: Navigation should happen now (valid session path)")
                                    }
                                    .onFailure { typeError ->
                                        println("❌ SplashViewModel: Failed to get user type for valid session: ${typeError.message}")
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            isAuthenticated = false,
                                            errorMessage = "Failed to get user type: ${typeError.message}"
                                        )
                                    }
                                return@launch
                            }
                        }
                        
                        error.printStackTrace()
                        val displayMessage = when {
                            // Flagged account - show message to user
                            errorMessage.startsWith("FLAGGED_ACCOUNT:") -> {
                                errorMessage.removePrefix("FLAGGED_ACCOUNT:")
                            }
                            // Session invalidated by another device
                            errorMessage.startsWith("SESSION_INVALIDATED:") -> {
                                errorMessage.removePrefix("SESSION_INVALIDATED:")
                            }
                            // Session expired
                            errorMessage.startsWith("SESSION_EXPIRED:") -> {
                                errorMessage.removePrefix("SESSION_EXPIRED:")
                            }
                            // Session invalid
                            errorMessage.startsWith("SESSION_INVALID:") -> {
                                errorMessage.removePrefix("SESSION_INVALID:")
                            }
                            // Session terminated
                            errorMessage.startsWith("SESSION_TERMINATED:") -> {
                                errorMessage.removePrefix("SESSION_TERMINATED:")
                            }
                            // Don't show generic errors
                            else -> null
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAuthenticated = false,
                            errorMessage = displayMessage // Will be passed to AuthScreen if not null
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