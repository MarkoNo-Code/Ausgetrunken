package com.ausgetrunken.ui.auth

import com.ausgetrunken.data.local.entities.UserType

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val userType: UserType? = null
)