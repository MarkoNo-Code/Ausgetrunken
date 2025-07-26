package com.ausgetrunken.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.usecase.GetWineyardSubscribersUseCase
import com.ausgetrunken.notifications.FCMTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class ProfileViewModel(
    private val authRepository: SupabaseAuthRepository,
    private val wineyardService: WineyardService,
    private val authService: AuthService,
    private val notificationService: NotificationService,
    private val fcmTokenManager: FCMTokenManager,
    private val getWineyardSubscribersUseCase: GetWineyardSubscribersUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    init {
        // Immediately set some basic UI state so the screen isn't empty
        _uiState.value = _uiState.value.copy(
            userName = "Loading...",
            userEmail = "Please wait",
            isLoading = true
        )
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                println("🔄 ProfileViewModel: Starting profile load...")
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Check if we have a valid session first
                if (!authRepository.hasValidSession()) {
                    println("❌ ProfileViewModel: No valid session found")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userName = "Unknown User",
                        userEmail = "Please sign in again",
                        errorMessage = "Session expired. Please sign in again."
                    )
                    return@launch
                }
                
                // Try to get current user, but fallback to session restoration if needed
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                var userEmailFromSession: String? = null
                
                if (currentUser == null) {
                    println("⚠️ ProfileViewModel: No Supabase UserInfo, attempting session restoration...")
                    // Try to restore session to get user info
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                println("✅ ProfileViewModel: Session restored successfully")
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                println("✅ ProfileViewModel: Valid session without UserInfo, extracting data...")
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    userEmailFromSession = parts[1]
                                    println("✅ ProfileViewModel: Extracted userId: $userIdFromSession, email: $userEmailFromSession")
                                }
                            }
                        }
                }
                
                // Determine user ID and email from either currentUser or session data
                val userId = currentUser?.id ?: userIdFromSession
                val userEmail = currentUser?.email ?: userEmailFromSession ?: ""
                
                if (userId.isNullOrEmpty()) {
                    println("❌ ProfileViewModel: Unable to determine user ID")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userName = "Unknown User",
                        userEmail = "Error loading user info",
                        wineyards = emptyList(),
                        canAddMoreWineyards = false,
                        errorMessage = "Unable to identify user. Please sign in again."
                    )
                    return@launch
                }
                
                println("🔍 ProfileViewModel: Loading profile for user ID: $userId")
                println("🔍 ProfileViewModel: User email: $userEmail")
                
                // Load user info - set defaults to ensure UI shows something
                val userName = currentUser?.userMetadata?.get("full_name")?.toString() ?: userEmail.substringBefore("@").takeIf { it.isNotEmpty() } ?: "User"
                val profilePictureUrl = currentUser?.userMetadata?.get("avatar_url")?.toString()
                
                // Update UI state immediately with user info, even before wineyards load
                _uiState.value = _uiState.value.copy(
                    userName = userName,
                    userEmail = userEmail,
                    profilePictureUrl = profilePictureUrl,
                    isLoading = true // Keep loading true while fetching wineyards
                )
                
                // Now try to load wineyards with timeout
                try {
                    println("🔄 ProfileViewModel: Syncing wineyards from Supabase...")
                    val syncResult = wineyardService.syncWineyards()
                    if (syncResult.isFailure) {
                        println("⚠️ ProfileViewModel: Sync failed: ${syncResult.exceptionOrNull()?.message}")
                    } else {
                        println("✅ ProfileViewModel: Sync completed successfully")
                    }
                    
                    // Use withTimeout to prevent hanging
                    kotlinx.coroutines.withTimeout(15000L) { // 15 seconds timeout
                        wineyardService.getWineyardsByOwner(userId).collect { wineyards ->
                            println("🏭 ProfileViewModel: Found ${wineyards.size} wineyards for owner $userId")
                            wineyards.forEach { wineyard ->
                                println("  - Wineyard: ${wineyard.name} (ID: ${wineyard.id}, Owner: ${wineyard.ownerId})")
                            }
                            
                            _uiState.value = _uiState.value.copy(
                                wineyards = wineyards,
                                canAddMoreWineyards = wineyards.size < 5,
                                isLoading = false
                            )
                            return@collect // Exit after first emission
                        }
                    }
                    
                } catch (e: TimeoutCancellationException) {
                    println("⚠️ ProfileViewModel: Wineyard loading timed out")
                    _uiState.value = _uiState.value.copy(
                        wineyards = emptyList(),
                        canAddMoreWineyards = true,
                        isLoading = false,
                        errorMessage = "Loading wineyards took too long - please try refreshing"
                    )
                } catch (e: Exception) {
                    println("❌ ProfileViewModel: Error loading wineyards: ${e.message}")
                    e.printStackTrace()
                    // Still show user info even if wineyards fail to load
                    _uiState.value = _uiState.value.copy(
                        wineyards = emptyList(),
                        canAddMoreWineyards = true,
                        isLoading = false,
                        errorMessage = "Failed to load wineyards: ${e.message}"
                    )
                }
                
            } catch (e: Exception) {
                println("❌ ProfileViewModel: Critical error in loadUserProfile: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userName = "Error",
                    userEmail = "Failed to load profile",
                    wineyards = emptyList(),
                    canAddMoreWineyards = false,
                    errorMessage = "Failed to load profile: ${e.message}"
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
    
    /**
     * Finds the wineyard with the most active subscribers for notification management.
     * Returns the first wineyard if none have subscribers or if there's an error.
     */
    suspend fun findWineyardWithMostSubscribers(): String? {
        val wineyards = _uiState.value.wineyards
        if (wineyards.isEmpty()) return null
        
        println("🔍 ProfileViewModel: Finding wineyard with most subscribers from ${wineyards.size} wineyards")
        
        var bestWineyard: String? = null
        var maxSubscribers = 0
        
        for (wineyard in wineyards) {
            try {
                val subscriberInfo = getWineyardSubscribersUseCase(wineyard.id)
                println("📊 ProfileViewModel: Wineyard ${wineyard.name} (${wineyard.id}) has ${subscriberInfo.totalSubscribers} subscribers")
                
                if (subscriberInfo.totalSubscribers > maxSubscribers) {
                    maxSubscribers = subscriberInfo.totalSubscribers
                    bestWineyard = wineyard.id
                }
            } catch (e: Exception) {
                println("⚠️ ProfileViewModel: Error checking subscribers for wineyard ${wineyard.id}: ${e.message}")
            }
        }
        
        val result = bestWineyard ?: wineyards.first().id
        println("✅ ProfileViewModel: Selected wineyard for notifications: $result (subscribers: $maxSubscribers)")
        return result
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