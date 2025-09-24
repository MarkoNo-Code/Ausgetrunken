package com.ausgetrunken.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.TokenStorage
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.domain.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomerProfileViewModel(
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val tokenStorage: TokenStorage
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CustomerProfileUiState())
    val uiState: StateFlow<CustomerProfileUiState> = _uiState.asStateFlow()
    
    init {
        // Immediately set loading state so screen isn't empty
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                // First try to get user ID from local storage
                val userId = tokenStorage.getUserId()
                println("🔍 CustomerProfileViewModel: Retrieved userId from storage: $userId")

                // If we have user ID, get profile data from database
                if (userId != null) {
                    userRepository.getUserById(userId).collectLatest { userProfile ->
                        if (userProfile != null) {
                            println("✅ CustomerProfileViewModel: Profile loaded from database: ${userProfile.email}")
                            _uiState.value = _uiState.value.copy(
                                userProfile = userProfile,
                                isLoading = false
                            )
                        } else {
                            println("⚠️ CustomerProfileViewModel: No profile found in database for user $userId")
                            // Try to sync from Supabase
                            userRepository.syncUserFromSupabase(userId)
                                .onSuccess { syncedUser ->
                                    println("✅ CustomerProfileViewModel: Profile synced from Supabase: ${syncedUser.email}")
                                    _uiState.value = _uiState.value.copy(
                                        userProfile = syncedUser,
                                        isLoading = false
                                    )
                                }
                                .onFailure { syncError ->
                                    println("❌ CustomerProfileViewModel: Failed to sync profile: ${syncError.message}")
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        errorMessage = "Could not load profile data"
                                    )
                                }
                        }
                    }
                }

                // Try to restore session to get current user info (still useful for some data)
                authService.restoreSession()
                    .onSuccess { user ->
                        println("✅ CustomerProfileViewModel: Session restored successfully")
                        _uiState.value = _uiState.value.copy(user = user)
                    }
                    .onFailure { error ->
                        val errorMessage = error.message ?: ""
                        if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                            println("✅ CustomerProfileViewModel: Valid session without UserInfo, profile data from database is sufficient")
                        } else {
                            println("⚠️ CustomerProfileViewModel: Session restore failed: ${error.message}")
                        }
                    }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoggingOut = true, errorMessage = null)
                
                authService.signOut()
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isLoggingOut = false,
                            logoutSuccess = true
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoggingOut = false,
                            errorMessage = "Failed to logout: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun showDeleteAccountDialog() {
        _uiState.value = _uiState.value.copy(showDeleteAccountDialog = true)
    }
    
    fun hideDeleteAccountDialog() {
        _uiState.value = _uiState.value.copy(showDeleteAccountDialog = false)
    }
    
    fun deleteAccount() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = true,
                    showDeleteAccountDialog = false,
                    errorMessage = null
                )
                
                authService.deleteAccount()
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isDeletingAccount = false,
                            deleteAccountSuccess = true
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isDeletingAccount = false,
                            errorMessage = "Failed to delete account: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeletingAccount = false,
                    errorMessage = "Unexpected error during account deletion: ${e.message}"
                )
            }
        }
    }
}