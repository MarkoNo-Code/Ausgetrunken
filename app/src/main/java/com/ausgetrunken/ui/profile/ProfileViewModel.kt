package com.ausgetrunken.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineyardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: SupabaseAuthRepository,
    private val wineyardService: WineyardService,
    private val authService: AuthService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val currentUser = authRepository.currentUser
            if (currentUser != null) {
                try {
                    println("🔍 ProfileViewModel: Loading profile for user ID: ${currentUser.id}")
                    println("🔍 ProfileViewModel: User email: ${currentUser.email}")
                    
                    // Load user info
                    val userEmail = currentUser.email ?: ""
                    val userName = currentUser.userMetadata?.get("full_name")?.toString() ?: "Wineyard Owner"
                    val profilePictureUrl = currentUser.userMetadata?.get("avatar_url")?.toString()
                    
                    // First, sync wineyards from Supabase to ensure we have the latest data
                    println("🔄 ProfileViewModel: Syncing wineyards from Supabase...")
                    val syncResult = wineyardService.syncWineyards()
                    if (syncResult.isFailure) {
                        println("⚠️ ProfileViewModel: Sync failed: ${syncResult.exceptionOrNull()?.message}")
                    } else {
                        println("✅ ProfileViewModel: Sync completed successfully")
                    }
                    
                    wineyardService.getWineyardsByOwner(currentUser.id).collect { wineyards ->
                        println("🏭 ProfileViewModel: Found ${wineyards.size} wineyards for owner ${currentUser.id}")
                        wineyards.forEach { wineyard ->
                            println("  - Wineyard: ${wineyard.name} (ID: ${wineyard.id}, Owner: ${wineyard.ownerId})")
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            wineyards = wineyards,
                            canAddMoreWineyards = wineyards.size < 5,
                            userName = userName,
                            userEmail = userEmail,
                            profilePictureUrl = profilePictureUrl,
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    println("❌ ProfileViewModel: Error loading profile: ${e.message}")
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to load profile: ${e.message}"
                    )
                }
            } else {
                println("❌ ProfileViewModel: User not authenticated")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "User not authenticated"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun refreshProfile() {
        loadUserProfile()
    }
    
    fun debugWineyardData() {
        viewModelScope.launch {
            val currentUser = authRepository.currentUser
            if (currentUser != null) {
                println("🐛 DEBUG: Current user ID: ${currentUser.id}")
                println("🐛 DEBUG: Current user email: ${currentUser.email}")
                
                // Force sync wineyards
                println("🐛 DEBUG: Force syncing wineyards...")
                val syncResult = wineyardService.syncWineyards()
                println("🐛 DEBUG: Sync result: ${if (syncResult.isSuccess) "SUCCESS" else "FAILED - ${syncResult.exceptionOrNull()?.message}"}")
                
                // Check what wineyards are in local database
                val allWineyards = wineyardService.getAllWineyards()
                allWineyards.collect { wineyards ->
                    println("🐛 DEBUG: Total wineyards in local DB: ${wineyards.size}")
                    wineyards.forEach { wineyard ->
                        println("🐛 DEBUG:   - ${wineyard.name} (ID: ${wineyard.id}, Owner: ${wineyard.ownerId})")
                        println("🐛 DEBUG:     Owner matches current user: ${wineyard.ownerId == currentUser.id}")
                    }
                    
                    // Check specifically for current user's wineyards
                    val userWineyards = wineyards.filter { it.ownerId == currentUser.id }
                    println("🐛 DEBUG: Wineyards for current user: ${userWineyards.size}")
                    return@collect // Exit after first emission
                }
            }
        }
    }
    
    fun showProfilePicturePicker() {
        _uiState.value = _uiState.value.copy(showProfilePicturePicker = true)
    }
    
    fun hideProfilePicturePicker() {
        _uiState.value = _uiState.value.copy(showProfilePicturePicker = false)
    }
    
    fun updateProfilePicture(imageUrl: String) {
        _uiState.value = _uiState.value.copy(
            profilePictureUrl = imageUrl,
            showProfilePicturePicker = false
        )
        // TODO: Upload to storage and update user metadata
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