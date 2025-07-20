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
    
    fun debugFCMToken() {
        viewModelScope.launch {
            val currentUser = authRepository.currentUser
            if (currentUser != null) {
                println("🔍 DEBUG FCM: Starting FCM token debug for user: ${currentUser.id}")
                println("🔍 DEBUG FCM: User email: ${currentUser.email}")
                
                try {
                    // 1. Check what token is stored in Supabase
                    val storedToken = notificationService.getUserFcmToken(currentUser.id)
                    println("🔍 DEBUG FCM: Stored token in Supabase: ${storedToken?.take(20) ?: "NULL"}...")
                    
                    if (storedToken != null) {
                        println("🔍 DEBUG FCM: Full token length: ${storedToken.length}")
                        println("🔍 DEBUG FCM: Token format check: ${if (storedToken.contains(":")) "Valid format (contains :)" else "Invalid format (no :)"}")
                        
                        // Check if token starts with valid FCM token pattern
                        val isValidFormat = storedToken.matches(Regex("^[a-zA-Z0-9_-]+:[a-zA-Z0-9_-]+$"))
                        println("🔍 DEBUG FCM: Token validation: ${if (isValidFormat) "VALID" else "INVALID"}")
                        
                        // Additional FCM token checks
                        println("🔍 DEBUG FCM: Token starts with: ${storedToken.take(10)}")
                        println("🔍 DEBUG FCM: Token contains APA91b: ${storedToken.contains("APA91b")}")
                        println("🔍 DEBUG FCM: Full token: $storedToken")
                    }
                    
                    // 2. Force update the FCM token
                    println("🔍 DEBUG FCM: Force updating FCM token...")
                    fcmTokenManager.updateTokenForUser(currentUser.id)
                    
                    // 3. Check again after update
                    kotlinx.coroutines.delay(3000) // Wait for update to complete
                    val updatedToken = notificationService.getUserFcmToken(currentUser.id)
                    println("🔍 DEBUG FCM: Updated token in Supabase: ${updatedToken?.take(20) ?: "NULL"}...")
                    
                    // 4. Compare tokens
                    if (storedToken != updatedToken) {
                        println("✅ DEBUG FCM: Token was updated successfully!")
                    } else {
                        println("⚠️ DEBUG FCM: Token unchanged - might be the same or update failed")
                    }
                    
                    // 5. Log token details for edge function debugging
                    if (updatedToken != null) {
                        println("🔍 DEBUG FCM: For Edge Function debugging:")
                        println("   - User ID: ${currentUser.id}")
                        println("   - Token prefix: ${updatedToken.take(30)}...")
                        println("   - Token suffix: ...${updatedToken.takeLast(10)}")
                    }
                    
                } catch (e: Exception) {
                    println("❌ DEBUG FCM: Error during debug: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println("❌ DEBUG FCM: No user logged in")
            }
        }
    }
}