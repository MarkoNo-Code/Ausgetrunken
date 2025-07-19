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
    val successMessage: String? = null
)