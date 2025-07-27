package com.ausgetrunken.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.notifications.FCMTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authService: AuthService,
    private val fcmTokenManager: FCMTokenManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    init {
        checkAuthState()
    }
    
    private fun checkAuthState() {
        viewModelScope.launch {
            println("🚀 AuthViewModel: Starting authentication check")
            
            // Ensure splash screen shows for at least 2 seconds for the animation
            val startTime = System.currentTimeMillis()
            
            authService.restoreSession()
                .onSuccess { user ->
                    println("🔄 AuthViewModel: RestoreSession result - user = ${user?.email ?: "NULL"}")
                    if (user != null) {
                        println("✅ AuthViewModel: User authenticated, getting user type...")
                        // User is authenticated, get their type
                        authService.checkUserType(user.id)
                            .onSuccess { userType ->
                                println("✅ AuthViewModel: User type = $userType")
                                
                                // Update FCM token for the restored session user
                                fcmTokenManager.updateTokenForUser(user.id)
                                
                                // Ensure minimum display time for animation
                                val elapsedTime = System.currentTimeMillis() - startTime
                                val minDisplayTime = 2500L // 2.5 seconds to see the full wine filling animation
                                val remainingTime = maxOf(0, minDisplayTime - elapsedTime)
                                
                                if (remainingTime > 0) {
                                    println("⏱️ AuthViewModel: Delaying for ${remainingTime}ms to show animation")
                                    kotlinx.coroutines.delay(remainingTime)
                                }
                                
                                _uiState.value = _uiState.value.copy(
                                    isCheckingSession = false,
                                    isAuthenticated = true,
                                    isLoginSuccessful = true,
                                    userType = userType
                                )
                                println("✅ AuthViewModel: Navigation should happen now")
                            }
                            .onFailure { error ->
                                println("❌ AuthViewModel: Failed to get user type: ${error.message}")
                                
                                // Ensure minimum display time for animation
                                val elapsedTime = System.currentTimeMillis() - startTime
                                val minDisplayTime = 2500L
                                val remainingTime = maxOf(0, minDisplayTime - elapsedTime)
                                
                                if (remainingTime > 0) {
                                    kotlinx.coroutines.delay(remainingTime)
                                }
                                
                                // Failed to get user type, show login screen
                                _uiState.value = _uiState.value.copy(
                                    isCheckingSession = false,
                                    isAuthenticated = false,
                                    errorMessage = "Failed to get user type: ${error.message}"
                                )
                            }
                    } else {
                        println("❌ AuthViewModel: No valid session found, showing login")
                        
                        // Ensure minimum display time for animation
                        val elapsedTime = System.currentTimeMillis() - startTime
                        val minDisplayTime = 2500L
                        val remainingTime = maxOf(0, minDisplayTime - elapsedTime)
                        
                        if (remainingTime > 0) {
                            kotlinx.coroutines.delay(remainingTime)
                        }
                        
                        // No valid session found
                        _uiState.value = _uiState.value.copy(
                            isCheckingSession = false,
                            isAuthenticated = false
                        )
                    }
                }
                .onFailure { error ->
                    println("❌ AuthViewModel: Session restoration failed: ${error.message}")
                    
                    // Ensure minimum display time for animation
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val minDisplayTime = 2500L
                    val remainingTime = maxOf(0, minDisplayTime - elapsedTime)
                    
                    if (remainingTime > 0) {
                        kotlinx.coroutines.delay(remainingTime)
                    }
                    
                    // Check for different types of session errors
                    val errorMessage = error.message ?: ""
                    when {
                        // Valid session but no UserInfo - extract user data and proceed
                        errorMessage.startsWith("VALID_SESSION_NO_USER:") -> {
                            println("✅ AuthViewModel: Valid session without UserInfo, extracting data...")
                            val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                            if (parts.size >= 2) {
                                val userId = parts[0]
                                val userEmail = parts[1]
                                println("✅ AuthViewModel: Extracted userId: $userId, email: $userEmail")
                                
                                // Determine user type and navigate accordingly
                                authService.checkUserType(userId)
                                    .onSuccess { userType ->
                                        println("✅ AuthViewModel: User type determined: $userType")
                                        
                                        // Update FCM token for restored session
                                        println("🔧 AuthViewModel: Updating FCM token for valid session - user: $userId")
                                        fcmTokenManager.updateTokenForUser(userId)
                                        
                                        _uiState.value = _uiState.value.copy(
                                            isCheckingSession = false,
                                            isAuthenticated = true,
                                            userType = userType
                                        )
                                        println("✅ AuthViewModel: Authentication successful with valid session")
                                    }
                                    .onFailure { typeError ->
                                        println("❌ AuthViewModel: Failed to determine user type: ${typeError.message}")
                                        _uiState.value = _uiState.value.copy(
                                            isCheckingSession = false,
                                            isAuthenticated = false,
                                            errorMessage = "Authentication failed. Please try again."
                                        )
                                    }
                            } else {
                                println("❌ AuthViewModel: Invalid VALID_SESSION_NO_USER format: $errorMessage")
                                _uiState.value = _uiState.value.copy(
                                    isCheckingSession = false,
                                    isAuthenticated = false,
                                    errorMessage = "Authentication failed. Please try again."
                                )
                            }
                        }
                        // Flagged account - show dialog
                        errorMessage.startsWith("FLAGGED_ACCOUNT:") -> {
                            val flaggedMessage = errorMessage.removePrefix("FLAGGED_ACCOUNT:")
                            _uiState.value = _uiState.value.copy(
                                isCheckingSession = false,
                                isAuthenticated = false,
                                showFlaggedAccountDialog = true,
                                flaggedAccountMessage = flaggedMessage
                            )
                        }
                        // Session invalidation messages - show as snackbar
                        errorMessage.startsWith("SESSION_INVALIDATED:") -> {
                            val sessionMessage = errorMessage.removePrefix("SESSION_INVALIDATED:")
                            _uiState.value = _uiState.value.copy(
                                isCheckingSession = false,
                                isAuthenticated = false,
                                errorMessage = sessionMessage
                            )
                        }
                        errorMessage.startsWith("SESSION_EXPIRED:") -> {
                            val sessionMessage = errorMessage.removePrefix("SESSION_EXPIRED:")
                            _uiState.value = _uiState.value.copy(
                                isCheckingSession = false,
                                isAuthenticated = false,
                                errorMessage = sessionMessage
                            )
                        }
                        errorMessage.startsWith("SESSION_INVALID:") -> {
                            val sessionMessage = errorMessage.removePrefix("SESSION_INVALID:")
                            _uiState.value = _uiState.value.copy(
                                isCheckingSession = false,
                                isAuthenticated = false,
                                errorMessage = sessionMessage
                            )
                        }
                        errorMessage.startsWith("SESSION_TERMINATED:") -> {
                            val sessionMessage = errorMessage.removePrefix("SESSION_TERMINATED:")
                            _uiState.value = _uiState.value.copy(
                                isCheckingSession = false,
                                isAuthenticated = false,
                                errorMessage = sessionMessage
                            )
                        }
                        // Regular session restoration failure - just show login without error
                        else -> {
                            _uiState.value = _uiState.value.copy(
                                isCheckingSession = false,
                                isAuthenticated = false
                            )
                        }
                    }
                }
        }
    }
    
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
    
    fun dismissFlaggedAccountDialog() {
        _uiState.value = _uiState.value.copy(
            showFlaggedAccountDialog = false,
            flaggedAccountMessage = null
        )
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
                            // Update FCM token for the logged-in user
                            fcmTokenManager.updateTokenForUser(user.id)
                            
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