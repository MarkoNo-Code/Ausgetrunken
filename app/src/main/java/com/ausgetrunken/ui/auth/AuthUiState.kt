package com.ausgetrunken.ui.auth

import com.ausgetrunken.data.local.entities.UserType

enum class AuthMode {
    LOGIN,
    REGISTER
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val selectedUserType: UserType = UserType.CUSTOMER,
    val isLoading: Boolean = false,
    val isLoginSuccessful: Boolean = false,
    val isRegistrationSuccessful: Boolean = false,
    val userType: UserType? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Session restoration state - CHANGED: Don't check session by default
    // Session checking is done in SplashScreen, AuthScreen just shows login form
    val isCheckingSession: Boolean = false,
    val isAuthenticated: Boolean = false,
    // Flagged account state
    val showFlaggedAccountDialog: Boolean = false,
    val flaggedAccountMessage: String? = null
)