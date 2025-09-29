package com.ausgetrunken.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.ProfilePictureService
import com.ausgetrunken.domain.service.WineryService
import com.ausgetrunken.domain.usecase.GetWinerySubscribersUseCase
import com.ausgetrunken.notifications.FCMTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OwnerProfileViewModel(
    private val authRepository: SupabaseAuthRepository,
    private val userRepository: UserRepository,
    private val wineryService: WineryService,
    private val authService: AuthService,
    private val notificationService: NotificationService,
    private val fcmTokenManager: FCMTokenManager,
    private val getWinerySubscribersUseCase: GetWinerySubscribersUseCase,
    private val profilePictureService: ProfilePictureService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    // Cache management
    private var lastLoadTime = 0L
    private var isInitialLoad = true
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    
    init {
        // Immediately set some basic UI state so the screen isn't empty
        _uiState.value = _uiState.value.copy(
            userName = "Loading...",
            userEmail = "Please wait",
            isLoading = true
        )
        loadUserProfileIfNeeded()
    }
    
    // Public function to load data only when needed (e.g., on first access or manual refresh)
    fun loadIfNeeded() {
        // Only load if we don't have data or it's been a while
        val timeSinceLastLoad = System.currentTimeMillis() - lastLoadTime
        val hasData = _uiState.value.wineries.isNotEmpty()
        
        if (!hasData || timeSinceLastLoad > CACHE_DURATION_MS) {
            println("🔄 ProfileViewModel: loadIfNeeded() triggered - hasData: $hasData, timeSinceLastLoad: ${timeSinceLastLoad}ms")
            loadUserProfile()
        } else {
            // Removed println: "✅ ProfileViewModel: Using cached data - ${_uiState.value.wineries.size} wineries"
        }
    }
    
    private fun loadUserProfileIfNeeded() {
        // Simple approach: always load user info, but be smart about winery syncing
        println("🔄 ProfileViewModel: Loading profile...")
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                println("🔄 ProfileViewModel: Starting profile load...")
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Check if we have a valid session first
                if (!authRepository.hasValidSession()) {
                    // Removed println: "❌ ProfileViewModel: No valid session found"
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
                    // Removed println: "⚠️ ProfileViewModel: No Supabase UserInfo, attempting session restoration..."
                    // Try to restore session to get user info
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                // Removed println: "✅ ProfileViewModel: Session restored successfully"
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                // Removed println: "✅ ProfileViewModel: Valid session without UserInfo, extracting data..."
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    userEmailFromSession = parts[1]
                                    // Removed println: "✅ ProfileViewModel: Extracted userId: $userIdFromSession, email: $userEmailFromSession"
                                }
                            }
                        }
                }
                
                // Determine user ID and email from either currentUser or session data
                val userId = currentUser?.id ?: userIdFromSession
                val userEmail = currentUser?.email ?: userEmailFromSession ?: ""
                
                if (userId.isNullOrEmpty()) {
                    // Removed println: "❌ ProfileViewModel: Unable to determine user ID"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userName = "Unknown User",
                        userEmail = "Error loading user info",
                        wineries = emptyList(),
                        canAddMoreWineries = false,
                        errorMessage = "Unable to identify user. Please sign in again."
                    )
                    return@launch
                }
                
                println("🔍 ProfileViewModel: Loading profile for user ID: $userId")
                println("🔍 ProfileViewModel: User email: $userEmail")
                
                // Load user info from UserRepository (remote-first with email fallback)
                val userFromDb = userRepository.getUserByIdRemoteFirst(userId)
                val userName = userFromDb?.fullName?.takeIf { it.isNotBlank() } ?: userEmail.substringBefore("@").takeIf { it.isNotEmpty() } ?: "User"
                val profilePictureUrl = userFromDb?.profilePictureUrl
                
                println("👤 ProfileViewModel: Resolved user name: '$userName' (from DB: '${userFromDb?.fullName}', email fallback: '${userEmail.substringBefore("@")}')")
                
                // Update UI state immediately with user info, even before wineries load
                _uiState.value = _uiState.value.copy(
                    userName = userName,
                    userEmail = userEmail,
                    profilePictureUrl = profilePictureUrl,
                    isLoading = true // Keep loading true while fetching wineries
                )
                
                // Load wineries using remote-first approach
                try {
                    println("🔄 ProfileViewModel: Loading wineries using remote-first approach for user $userId...")
                    
                    // Use remote-first approach to ensure we get the latest data from Supabase
                    val wineries = wineryService.getWinerysByOwnerRemoteFirst(userId)
                    println("🏭 ProfileViewModel: Found ${wineries.size} wineries for owner $userId")
                    
                    _uiState.value = _uiState.value.copy(
                        wineries = wineries,
                        canAddMoreWineries = wineries.size < 5,
                        isLoading = false
                    )
                    
                    lastLoadTime = System.currentTimeMillis()
                    // Removed println: "✅ ProfileViewModel: Successfully loaded wineries using remote-first approach"
                    
                } catch (e: Exception) {
                    // Removed println: "❌ ProfileViewModel: Error loading wineries: ${e.message}"
                    e.printStackTrace()
                    // Still show user info even if wineries fail to load
                    _uiState.value = _uiState.value.copy(
                        wineries = emptyList(),
                        canAddMoreWineries = true,
                        isLoading = false,
                        errorMessage = "Failed to load wineries: ${e.message}"
                    )
                }
                
            } catch (e: Exception) {
                // Removed println: "❌ ProfileViewModel: Critical error in loadUserProfile: ${e.message}"
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userName = "Error",
                    userEmail = "Failed to load profile",
                    wineries = emptyList(),
                    canAddMoreWineries = false,
                    errorMessage = "Failed to load profile: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun refreshProfile() {
        println("🔄 ProfileViewModel: Manual refresh requested")
        // Force refresh by resetting cache time and ensuring sync happens
        lastLoadTime = 0L
        isInitialLoad = true
        loadUserProfile()
    }
    
    /**
     * Finds the winery with the most active subscribers for notification management.
     * Returns the first winery if none have subscribers or if there's an error.
     */
    suspend fun findWineryWithMostSubscribers(): String? {
        val wineries = _uiState.value.wineries
        if (wineries.isEmpty()) return null
        
        println("🔍 ProfileViewModel: Finding winery with most subscribers from ${wineries.size} wineries")
        
        var bestWinery: String? = null
        var maxSubscribers = 0
        
        for (winery in wineries) {
            try {
                val subscriberInfo = getWinerySubscribersUseCase(winery.id)
                println("📊 ProfileViewModel: Winery ${winery.name} (${winery.id}) has ${subscriberInfo.totalSubscribers} subscribers")
                
                if (subscriberInfo.totalSubscribers > maxSubscribers) {
                    maxSubscribers = subscriberInfo.totalSubscribers
                    bestWinery = winery.id
                }
            } catch (e: Exception) {
                // Removed println: "⚠️ ProfileViewModel: Error checking subscribers for winery ${winery.id}: ${e.message}"
            }
        }
        
        val result = bestWinery ?: wineries.first().id
        // Removed println: "✅ ProfileViewModel: Selected winery for notifications: $result (subscribers: $maxSubscribers")
        return result
    }
    
    
    fun showProfilePicturePicker() {
        _uiState.value = _uiState.value.copy(showProfilePicturePicker = true)
    }
    
    fun hideProfilePicturePicker() {
        _uiState.value = _uiState.value.copy(showProfilePicturePicker = false)
    }
    
    fun updateProfilePicture(localImagePath: String) {
        viewModelScope.launch {
            try {
                println("🖼️ ProfileViewModel: Starting profile picture upload from: $localImagePath")
                
                // Set uploading state
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    showProfilePicturePicker = false,
                    errorMessage = null
                )
                
                // Get current user ID with session restoration logic (same as loadUserProfile)
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    // Removed println: "⚠️ ProfileViewModel: No currentUser for profile picture upload, attempting session restoration..."
                    // Try to restore session to get user info
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                // Removed println: "✅ ProfileViewModel: Session restored successfully for profile picture upload"
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                // Removed println: "✅ ProfileViewModel: Valid session without UserInfo for profile picture upload, extracting data..."
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.isNotEmpty()) {
                                    userIdFromSession = parts[0]
                                    // Removed println: "✅ ProfileViewModel: Extracted userId for profile picture upload: $userIdFromSession"
                                }
                            }
                        }
                }
                
                // Determine user ID from either currentUser or session data
                val currentUserId = currentUser?.id ?: userIdFromSession
                if (currentUserId.isNullOrEmpty()) {
                    throw Exception("User not authenticated - unable to determine user ID")
                }
                
                // Upload to Supabase Storage using ProfilePictureService
                val uploadResult = profilePictureService.uploadProfilePicture(currentUserId, localImagePath)
                
                if (uploadResult.isSuccess) {
                    val supabaseUrl = uploadResult.getOrThrow()
                    // Removed println: "✅ ProfileViewModel: Profile picture uploaded to Supabase: $supabaseUrl"
                    
                    // Update profile picture URL in database
                    val updateResult = userRepository.updateProfilePictureUrl(currentUserId, supabaseUrl)
                    
                    if (updateResult.isSuccess) {
                        // Update UI state with the new Supabase URL
                        _uiState.value = _uiState.value.copy(
                            profilePictureUrl = supabaseUrl,
                            isLoading = false
                        )
                        // Removed println: "✅ ProfileViewModel: Profile picture URL saved to database and UI updated"
                    } else {
                        throw updateResult.exceptionOrNull() ?: Exception("Failed to update profile picture URL in database")
                    }
                } else {
                    throw uploadResult.exceptionOrNull() ?: Exception("Failed to upload profile picture")
                }
                
            } catch (e: Exception) {
                // Removed println: "❌ ProfileViewModel: Error updating profile picture: ${e.message}"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to update profile picture: ${e.message}"
                )
            }
        }
    }
    
    fun showEditNameDialog() {
        _uiState.value = _uiState.value.copy(showEditNameDialog = true)
    }
    
    fun hideEditNameDialog() {
        _uiState.value = _uiState.value.copy(showEditNameDialog = false)
    }
    
    fun updateUserName(newName: String) {
        viewModelScope.launch {
            try {
                println("👤 ProfileViewModel: Updating user name to: $newName")
                
                _uiState.value = _uiState.value.copy(isUpdatingName = true)
                
                // Get current user ID with session restoration logic (same as loadUserProfile)
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    // Removed println: "⚠️ ProfileViewModel: No currentUser for name update, attempting session restoration..."
                    // Try to restore session to get user info
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                // Removed println: "✅ ProfileViewModel: Session restored successfully for name update"
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                // Removed println: "✅ ProfileViewModel: Valid session without UserInfo for name update, extracting data..."
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.isNotEmpty()) {
                                    userIdFromSession = parts[0]
                                    // Removed println: "✅ ProfileViewModel: Extracted userId for name update: $userIdFromSession"
                                }
                            }
                        }
                }
                
                // Determine user ID from either currentUser or session data
                val currentUserId = currentUser?.id ?: userIdFromSession
                if (currentUserId.isNullOrEmpty()) {
                    throw Exception("User not authenticated - unable to determine user ID")
                }
                
                // Update in Supabase via UserRepository
                val result = userRepository.updateUserName(currentUserId, newName)
                
                if (result.isSuccess) {
                    // Update UI state after successful database update
                    _uiState.value = _uiState.value.copy(
                        userName = newName,
                        showEditNameDialog = false,
                        isUpdatingName = false,
                        errorMessage = null
                    )
                    // Removed println: "✅ ProfileViewModel: User name successfully updated in database"
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
                
            } catch (e: Exception) {
                // Removed println: "❌ ProfileViewModel: Error updating user name: ${e.message}"
                _uiState.value = _uiState.value.copy(
                    isUpdatingName = false,
                    showEditNameDialog = false,
                    errorMessage = "Failed to update name: ${e.message}"
                )
            }
        }
    }

    fun updateUserEmail(newEmail: String) {
        viewModelScope.launch {
            try {
                println("📧 ProfileViewModel: Updating user email to: $newEmail")

                _uiState.value = _uiState.value.copy(isUpdatingEmail = true)

                // Call the auth repository to update email
                val result = authRepository.updateUserEmail(newEmail)

                if (result.isSuccess) {
                    val message = result.getOrNull() ?: "Email update initiated"
                    _uiState.value = _uiState.value.copy(
                        isUpdatingEmail = false,
                        successMessage = message
                    )
                    // Removed println: "✅ ProfileViewModel: Email update result: $message"

                    // If email was updated immediately, refresh user profile
                    if (!message.contains("confirmation")) {
                        loadUserProfile()
                    }
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }

            } catch (e: Exception) {
                // Removed println: "❌ ProfileViewModel: Error updating email: ${e.message}"
                _uiState.value = _uiState.value.copy(
                    isUpdatingEmail = false,
                    errorMessage = e.message ?: "Failed to update email"
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
    
    /**
     * Get the current owner's user ID for navigation purposes
     */
    suspend fun getCurrentOwnerId(): String? {
        return try {
            // Try to get current user first
            val currentUser = authRepository.currentUser
            if (currentUser != null) {
                return currentUser.id
            }
            
            // If no current user, try session restoration
            authService.restoreSession()
                .onSuccess { user ->
                    if (user != null) {
                        return user.id
                    }
                }
                .onFailure { error ->
                    val errorMessage = error.message ?: ""
                    if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                        val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                        if (parts.isNotEmpty()) {
                            return parts[0] // userId from session
                        }
                    }
                }
            
            null
        } catch (e: Exception) {
            // Removed println: "❌ ProfileViewModel: Error getting current owner ID: ${e.message}"
            null
        }
    }
    
    /**
     * Add a newly created winery directly to the UI state without refetching all data
     */
    fun addNewWineryToUI(wineryId: String) {
        viewModelScope.launch {
            try {
                println("🏭 ProfileViewModel: Adding new winery $wineryId to UI state...")
                
                // Fetch the specific winery that was just created
                val newWinery = wineryService.getWineryByIdRemoteFirst(wineryId)
                
                if (newWinery != null) {
                    // Removed println: "✅ ProfileViewModel: Found new winery: ${newWinery.name}"
                    
                    // Add to existing list and update UI state
                    val updatedWineries = _uiState.value.wineries.toMutableList().apply {
                        add(newWinery)
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        wineries = updatedWineries,
                        canAddMoreWineries = updatedWineries.size < 5
                    )
                    
                    // Removed println: "✅ ProfileViewModel: Added winery to UI state. Total: ${updatedWineries.size}"
                } else {
                    // Removed println: "❌ ProfileViewModel: Could not find winery $wineryId"
                    // Fall back to full refresh if we can't find the specific winery
                    refreshProfile()
                }
            } catch (e: Exception) {
                // Removed println: "❌ ProfileViewModel: Error adding new winery to UI: ${e.message}"
                // Fall back to full refresh on error
                refreshProfile()
            }
        }
    }
    
}