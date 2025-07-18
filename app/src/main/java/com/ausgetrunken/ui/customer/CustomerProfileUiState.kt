package com.ausgetrunken.ui.customer

import io.github.jan.supabase.gotrue.user.UserInfo

data class CustomerProfileUiState(
    val isLoading: Boolean = false,
    val user: UserInfo? = null,
    val isLoggingOut: Boolean = false,
    val errorMessage: String? = null,
    val logoutSuccess: Boolean = false
)