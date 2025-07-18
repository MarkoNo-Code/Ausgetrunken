package com.ausgetrunken.ui.splash

import com.ausgetrunken.data.local.entities.UserType

data class SplashUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val userType: UserType? = null,
    val errorMessage: String? = null
)