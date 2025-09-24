package com.ausgetrunken.ui.profile

import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.remote.model.UserProfile

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val wineyards: List<WineyardEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canAddMoreWineyards: Boolean = true,
    val showProfilePicturePicker: Boolean = false,
    val profilePictureUrl: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    val isLoggingOut: Boolean = false,
    val logoutSuccess: Boolean = false,
    val showDeleteAccountDialog: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteAccountSuccess: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val isUpdatingName: Boolean = false,
    val isUpdatingEmail: Boolean = false,
    val successMessage: String? = null
) {
    val maxWineyards = 5
}