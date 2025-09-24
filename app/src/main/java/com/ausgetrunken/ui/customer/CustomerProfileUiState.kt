package com.ausgetrunken.ui.customer

import com.ausgetrunken.data.local.entities.UserEntity
import io.github.jan.supabase.gotrue.user.UserInfo

data class CustomerProfileUiState(
    val isLoading: Boolean = false,
    val user: UserInfo? = null,
    val userProfile: UserEntity? = null, // Add profile data from database
    val isLoggingOut: Boolean = false,
    val errorMessage: String? = null,
    val logoutSuccess: Boolean = false,
    val showDeleteAccountDialog: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteAccountSuccess: Boolean = false,
    val isUpdatingName: Boolean = false,
    val isUpdatingEmail: Boolean = false,
    val userName: String = "",
    val userEmail: String = ""
)