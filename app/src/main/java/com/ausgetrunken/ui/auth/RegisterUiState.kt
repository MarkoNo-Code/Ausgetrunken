package com.ausgetrunken.ui.auth

import com.ausgetrunken.data.local.entities.UserType

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val selectedUserType: UserType = UserType.CUSTOMER,
    val isLoading: Boolean = false,
    val isRegistrationSuccessful: Boolean = false,
    val errorMessage: String? = null
)