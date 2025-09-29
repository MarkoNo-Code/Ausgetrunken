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
    
    // REMOVED: Automatic session check in init was causing infinite loops
    // AuthScreen should only handle login/register, not session restoration
    // Session restoration is handled by SplashScreen
    
    // Remove automatic auth check from init - only check when explicitly called
    // This prevents infinite loops when AuthViewModel is recreated during navigation
    
    private fun checkAuthState() {
        viewModelScope.launch {
            println("🚀 AuthViewModel: Starting authentication check")
            
            // REMOVED: No more animation timing needed in AuthViewModel
            
            authService.restoreSession()
                .onSuccess { user ->
                    println("🔄 AuthViewModel: RestoreSession result - user = ${user?.email ?: "NULL"}")
                    if (user != null) {
                        // Removed println: "✅ AuthViewModel: User authenticated, getting user type..."
                        // User is authenticated, get their type
                        authService.checkUserType(user.id)
                            .onSuccess { userType ->
                                // Removed println: "✅ AuthViewModel: User type = $userType"
                                
                                // REMOVED: Animation delays don't belong in AuthViewModel
                                // These delays were causing 5-second flashing cycles
                                
                                _uiState.value = _uiState.value.copy(
                                    isCheckingSession = false,
                                    isAuthenticated = true,
                                    isLoginSuccessful = true,
                                    userType = userType
                                )
                                // Removed println: "✅ AuthViewModel: Navigation should happen now"
                                
                                // Update FCM token AFTER setting success state
                                try {
                                    fcmTokenManager.updateTokenForUser(user.id)
                                } catch (fcmError: Exception) {
                                    // Removed println: "⚠️ AuthViewModel: FCM token update failed during session restore: ${fcmError.message}"
                                }
                            }
                            .onFailure { error ->
                                // Removed println: "❌ AuthViewModel: Failed to get user type: ${error.message}"
                                
                                // REMOVED: Animation delays
                                
                                // Failed to get user type, show login screen
                                _uiState.value = _uiState.value.copy(
                                    isCheckingSession = false,
                                    isAuthenticated = false,
                                    errorMessage = "Failed to get user type: ${error.message}"
                                )
                            }
                    } else {
                        // Removed println: "❌ AuthViewModel: No valid session found, showing login"
                        
                        // REMOVED: Animation delays
                        
                        // No valid session found
                        _uiState.value = _uiState.value.copy(
                            isCheckingSession = false,
                            isAuthenticated = false
                        )
                    }
                }
                .onFailure { error ->
                    // Removed println: "❌ AuthViewModel: Session restoration failed: ${error.message}"
                    
                    // REMOVED: Animation delays
                    
                    // Check for different types of session errors
                    val errorMessage = error.message ?: ""
                    when {
                        // Valid session but no UserInfo - extract user data and proceed
                        errorMessage.startsWith("VALID_SESSION_NO_USER:") -> {
                            // Removed println: "✅ AuthViewModel: Valid session without UserInfo, extracting data..."
                            val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                            if (parts.size >= 2) {
                                val userId = parts[0]
                                val userEmail = parts[1]
                                // Removed println: "✅ AuthViewModel: Extracted userId: $userId, email: $userEmail"
                                
                                // Determine user type and navigate accordingly
                                authService.checkUserType(userId)
                                    .onSuccess { userType ->
                                        // Removed println: "✅ AuthViewModel: User type determined: $userType"
                                        
                                        _uiState.value = _uiState.value.copy(
                                            isCheckingSession = false,
                                            isAuthenticated = true,
                                            isLoginSuccessful = true,
                                            userType = userType
                                        )
                                        // Removed println: "✅ AuthViewModel: Authentication successful with valid session"
                                        
                                        // Update FCM token AFTER setting success state
                                        try {
                                            fcmTokenManager.updateTokenForUser(userId)
                                        } catch (fcmError: Exception) {
                                            // Removed println: "⚠️ AuthViewModel: FCM token update failed for valid session: ${fcmError.message}"
                                        }
                                    }
                                    .onFailure { typeError ->
                                        // Removed println: "❌ AuthViewModel: Failed to determine user type: ${typeError.message}"
                                        _uiState.value = _uiState.value.copy(
                                            isCheckingSession = false,
                                            isAuthenticated = false,
                                            errorMessage = "Authentication failed. Please try again."
                                        )
                                    }
                            } else {
                                // Removed println: "❌ AuthViewModel: Invalid VALID_SESSION_NO_USER format: $errorMessage"
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
    
    fun setResetToken(token: String) {
        // When a reset token is provided, switch to a password reset confirmation mode
        _uiState.value = _uiState.value.copy(
            mode = AuthMode.RESET_PASSWORD_CONFIRM,
            resetToken = token,
            successMessage = "Please enter your new password below.",
            errorMessage = null,
            password = "",
            confirmPassword = ""
        )
    }
    
    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please fill in all fields")
            return
        }
        
        viewModelScope.launch {
            // CRITICAL: Reset login success state before attempting new login
            // This prevents login loop issues after app cache clear
            println("🔄 AuthViewModel.login: Resetting login state before new attempt")
            _uiState.value = currentState.copy(
                isLoading = true, 
                errorMessage = null, 
                successMessage = null,
                isLoginSuccessful = false, // Reset this flag
                userType = null // Reset user type as well
            )
            
            authService.signIn(currentState.email, currentState.password)
                .onSuccess { user ->
                    authService.checkUserType(user.id)
                        .onSuccess { userType ->
                            println("🎉 AuthViewModel.login: LOGIN SUCCESS - Setting isLoginSuccessful=true, userType=$userType")
                            _uiState.value = currentState.copy(
                                isLoading = false,
                                isLoginSuccessful = true,
                                userType = userType
                            )
                            println("🎉 AuthViewModel.login: LOGIN SUCCESS - State updated, navigation should happen now")
                            
                            // Update FCM token AFTER setting login success (don't block login completion)
                            try {
                                fcmTokenManager.updateTokenForUser(user.id)
                                // Removed println: "✅ AuthViewModel.login: FCM token update initiated"
                            } catch (fcmError: Exception) {
                                // Removed println: "⚠️ AuthViewModel.login: FCM token update failed: ${fcmError.message}"
                                // Don't fail login if FCM update fails
                            }
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
            
            println("🔍 AuthViewModel.register: Registering user with type: ${currentState.selectedUserType}")
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
                    val errorMessage = error.message ?: "Unknown error"

                    // Check if this is actually a successful registration requiring email confirmation
                    if (errorMessage.startsWith("EMAIL_CONFIRMATION_REQUIRED:")) {
                        val confirmationMessage = errorMessage.removePrefix("EMAIL_CONFIRMATION_REQUIRED:")
                        _uiState.value = currentState.copy(
                            isLoading = false,
                            isRegistrationSuccessful = true,
                            successMessage = confirmationMessage,
                            mode = AuthMode.LOGIN // Switch to login mode
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            isLoading = false,
                            errorMessage = errorMessage
                        )
                    }
                }
        }
    }
    
    fun resetPassword() {
        val currentState = _uiState.value
        if (currentState.email.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please enter your email address")
            return
        }
        
        if (!isValidEmail(currentState.email)) {
            _uiState.value = currentState.copy(errorMessage = "Please enter a valid email address")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
            
            authService.resetPassword(currentState.email)
                .onSuccess {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isPasswordResetSent = true,
                        successMessage = "Password reset email sent! Please check your inbox.",
                        mode = AuthMode.LOGIN // Switch back to login mode
                    )
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to send password reset email"
                    )
                }
        }
    }
    
    fun confirmPasswordReset() {
        val currentState = _uiState.value
        if (currentState.password.isBlank() || currentState.confirmPassword.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Please fill in all password fields")
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
        
        if (currentState.resetToken == null) {
            _uiState.value = currentState.copy(errorMessage = "Invalid reset token")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null)
            
            authService.confirmPasswordReset(currentState.resetToken, currentState.password)
                .onSuccess {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        successMessage = "Password updated successfully! You can now sign in with your new password.",
                        mode = AuthMode.LOGIN, // Switch back to login mode
                        resetToken = null,
                        password = "",
                        confirmPassword = ""
                    )
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to update password"
                    )
                }
        }
    }
    
    private fun isValidEmail(email: String): Boolean {
        // Use Android's built-in email validation pattern which properly handles + signs
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }
}