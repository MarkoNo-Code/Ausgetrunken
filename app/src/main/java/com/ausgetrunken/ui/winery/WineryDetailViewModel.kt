package com.ausgetrunken.ui.winery

import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineryRepository
import com.ausgetrunken.domain.common.AppResult
import com.ausgetrunken.domain.error.AppError
import com.ausgetrunken.domain.error.toAppError
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineryService
import com.ausgetrunken.domain.service.WinerySubscriptionService
import com.ausgetrunken.domain.service.ImageUploadService
import com.ausgetrunken.domain.service.WineryPhotoService
import com.ausgetrunken.domain.service.NewWineryPhotoService
import com.ausgetrunken.domain.service.DatabaseInspectionService
import com.ausgetrunken.data.local.TokenStorage
import android.content.Context
import android.net.Uri
import java.io.File
import com.ausgetrunken.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class WineryDetailViewModel(
    private val wineryService: WineryService,
    private val wineService: WineService,
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val subscriptionService: WinerySubscriptionService,
    private val imageUploadService: ImageUploadService,
    private val wineryPhotoService: WineryPhotoService,
    private val newWineryPhotoService: NewWineryPhotoService,
    private val databaseInspectionService: DatabaseInspectionService,
    private val tokenStorage: TokenStorage,
    private val context: Context
) : BaseViewModel() {
    
    companion object {
        // Feature flag to test new photo service implementation
        private const val USE_NEW_PHOTO_SERVICE = false
    }
    
    private val _uiState = MutableStateFlow(WineryDetailUiState())
    val uiState: StateFlow<WineryDetailUiState> = _uiState.asStateFlow()
    
    fun loadWinery(wineryId: String) {
        execute("loadWinery") {
            AppResult.catchingSuspend {
                // Get winery details directly from service (uses BaseRepository session validation)
                val winery = wineryService.getWineryById(wineryId).first()
                
                if (winery == null) {
                    throw Exception("Winery not found: $wineryId")
                }
                
                // Get current user details for permission checking
                // Use TokenStorage directly to get userId - more reliable than getCurrentUser after session restoration
                val currentUserId = tokenStorage.getUserId()
                val canEdit = if (currentUserId != null) {
                    // Use checkUserType like SplashViewModel does - this works reliably  
                    authService.checkUserType(currentUserId)
                        .onSuccess { userType ->
                            println("🔍 WineryDetailViewModel: Successfully got userType: $userType")
                        }
                        .onFailure { error ->
                            // Removed println: "❌ WineryDetailViewModel: Failed to get userType: ${error.message}"
                        }
                        .getOrNull() == UserType.WINERY_OWNER
                } else {
                    // Removed println: "❌ WineryDetailViewModel: currentUserId is null from TokenStorage, cannot check permissions"
                    false
                }
                
                // Get currentUserEntity for other uses if needed (keeping for compatibility)
                val currentAuthUser = authService.getCurrentUser().first()
                val currentUserEntity = if (currentUserId != null) {
                    userRepository.getUserById(currentUserId).first()
                } else null
                
                // DEBUG: Check canEdit logic
                println("🔍 WineryDetailViewModel DEBUG:")
                println("🔍 currentUserId from TokenStorage: $currentUserId")  
                println("🔍 currentAuthUser from getCurrentUser: ${currentAuthUser?.id}")
                println("🔍 currentUserEntity: ${currentUserEntity?.id}")
                println("🔍 currentUserEntity.userType: ${currentUserEntity?.userType}")
                println("🔍 canEdit calculated: $canEdit")
                println("🔍 Expected for owner: should be TRUE")
                
                // Update UI state (preserve existing photos initially)
                _uiState.value = _uiState.value.copy(
                    winery = winery,
                    canEdit = canEdit
                    // Keep existing photos - they will be updated by loadPhotos()
                )
                
                // DEBUG: Inspect database on winery load
                viewModelScope.launch {
                    databaseInspectionService.inspectDatabase()
                    databaseInspectionService.inspectWineryPhotos(winery.id)
                }
                
                // Load related data
                loadWines(winery.id)
                
                // FIRST: Sync photos from Supabase to local database
                viewModelScope.launch {
                    wineryPhotoService.syncPhotosFromSupabase(winery.id)
                }
                
                loadPhotos(winery.id)
                
                // Load subscription status for customers
                if (currentUserEntity?.userType == UserType.CUSTOMER && currentAuthUser != null) {
                    loadSubscriptionStatus(currentAuthUser.id, winery.id)
                }
                
                winery
            }
        }
    }
    
    fun toggleEdit() {
        if (_uiState.value.canEdit) {
            _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
        }
    }
    
    fun updateWineryName(name: String) {
        _uiState.value.winery?.let { winery ->
            _uiState.value = _uiState.value.copy(
                winery = winery.copy(name = name)
            )
        }
    }
    
    fun updateWineryDescription(description: String) {
        _uiState.value.winery?.let { winery ->
            _uiState.value = _uiState.value.copy(
                winery = winery.copy(description = description)
            )
        }
    }
    
    fun updateWineryAddress(address: String) {
        _uiState.value.winery?.let { winery ->
            _uiState.value = _uiState.value.copy(
                winery = winery.copy(address = address)
            )
        }
    }
    
    fun updateWineryLocation(latitude: Double, longitude: Double) {

        _uiState.value.winery?.let { winery ->
            val updatedWinery = winery.copy(latitude = latitude, longitude = longitude)
            _uiState.value = _uiState.value.copy(winery = updatedWinery)
        }
    }
    
    fun addPhoto(photoPath: String) {
        
        val winery = _uiState.value.winery ?: return
        
        if (!_uiState.value.canEdit) {
            return
        }

        _uiState.value = _uiState.value.copy(isUpdating = true)

        execute("addPhoto") {
            AppResult.catchingSuspend {
                
                val result = when {
                    photoPath.startsWith("content://") -> {
                        wineryPhotoService.addPhoto(winery.id, Uri.parse(photoPath))
                    }
                    photoPath.startsWith("/") -> {
                        wineryPhotoService.addPhoto(winery.id, photoPath)
                    }
                    else -> {
                        // Fallback for existing URLs or other formats
                        Result.failure(Exception("Unsupported photo path format"))
                    }
                }
                
                result.fold(
                    onSuccess = { localPath ->
                        
                        // DEBUG: Inspect database after photo add
                        viewModelScope.launch {
                            databaseInspectionService.inspectDatabase()
                            databaseInspectionService.inspectWineryPhotos(winery.id)
                        }
                        
                        // Immediately add photo to UI state while Flow processes
                        val currentPhotos = _uiState.value.photos.toMutableList()
                        if (!currentPhotos.contains(localPath)) {
                            currentPhotos.add(localPath)
                            _uiState.value = _uiState.value.copy(photos = currentPhotos)
                        }
                        
                        _uiState.value = _uiState.value.copy(isUpdating = false)
                        Unit
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(isUpdating = false)
                        throw error
                    }
                )
            }
        }
    }
    
    
    fun removePhoto(photoUrl: String) {
        if (!_uiState.value.canEdit) {
            return
        }

        _uiState.value = _uiState.value.copy(isUpdating = true)

        execute("removePhoto") {
            AppResult.catchingSuspend {
                
                val result = wineryPhotoService.removePhoto(photoUrl)
                
                result.fold(
                    onSuccess = {
                        
                        // Immediately remove photo from UI state
                        val currentPhotos = _uiState.value.photos.toMutableList()
                        currentPhotos.remove(photoUrl)
                        _uiState.value = _uiState.value.copy(
                            photos = currentPhotos,
                            isUpdating = false
                        )
                        
                        Unit
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(isUpdating = false)
                        throw error
                    }
                )
            }
        }
    }
    
    fun saveWinery() {
        val winery = _uiState.value.winery ?: run {
            return
        }
        if (!_uiState.value.canEdit) {
            return
        }
        
        execute("saveWinery") {
            AppResult.catchingSuspend {
                // Set updating state
                _uiState.value = _uiState.value.copy(isUpdating = true)
                
                // Business rule: Only owner can save
                if (!_uiState.value.canEdit) {
                    throw Exception("Permission denied: Only winery owners can update winerys")
                }
                
                wineryService.updateWinery(winery).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            isEditing = false
                            // Removed: navigateBackAfterSave = true 
                            // Stay on winery detail page after editing
                        )
                        Unit
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(isUpdating = false)
                        throw error
                    }
                )
            }
        }
    }
    
    fun deleteWinery() {
        val winery = _uiState.value.winery ?: return
        if (!_uiState.value.canEdit) return
        
        execute("deleteWinery") {
            AppResult.catchingSuspend {
                // Business rule: Only owner can delete
                if (!_uiState.value.canEdit) {
                    throw Exception("Permission denied: Only winery owners can delete winerys")
                }
                
                wineryService.deleteWinery(winery.id).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            navigateBackAfterDelete = true
                        )
                        Unit
                    },
                    onFailure = { error ->
                        throw error
                    }
                )
            }
        }
    }
    
    fun showImagePicker() {
        _uiState.value = _uiState.value.copy(showImagePicker = true)
    }
    
    fun hideImagePicker() {
        _uiState.value = _uiState.value.copy(showImagePicker = false)
    }
    
    fun showLocationPicker() {
        _uiState.value = _uiState.value.copy(showLocationPicker = true)
    }
    
    fun hideLocationPicker() {
        _uiState.value = _uiState.value.copy(showLocationPicker = false)
    }
    
    private fun loadWines(wineryId: String) {
        execute("loadWines", showLoading = false) {
            AppResult.catchingSuspend {
                // Sync wines and get latest data
                wineService.syncWines()
                val wines = wineService.getWinesByWinery(wineryId).first()
                _uiState.value = _uiState.value.copy(wines = wines)
                wines
            }
        }
    }
    
    private fun loadPhotos(wineryId: String) {
        // Launch a coroutine to continuously collect photo updates
        viewModelScope.launch {
            try {
                if (USE_NEW_PHOTO_SERVICE) {
                    // Start upload service if not already running
                    newWineryPhotoService.startUploadService()
                    
                    newWineryPhotoService.getWineryPhotos(wineryId).collect { photos ->
                        _uiState.value = _uiState.value.copy(photos = photos)
                    }
                } else {
                    wineryPhotoService.getWineryPhotos(wineryId).collect { photos ->
                        _uiState.value = _uiState.value.copy(photos = photos)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
    
    private fun syncPhotosWithSupabase(wineryId: String, remotePhotos: List<String>) {
        execute("syncPhotos", showLoading = false) {
            AppResult.catchingSuspend {
                wineryPhotoService.syncPhotosWithSupabase(wineryId, remotePhotos)
                Unit
            }
        }
    }
    
    private fun loadSubscriptionStatus(userId: String, wineryId: String) {
        execute("loadSubscriptionStatus", showLoading = false) {
            AppResult.catchingSuspend {
                val isSubscribed = subscriptionService.isSubscribed(userId, wineryId)
                _uiState.value = _uiState.value.copy(isSubscribed = isSubscribed)
                isSubscribed
            }
        }
    }
    
    fun deleteWine(wineId: String) {
        if (!_uiState.value.canEdit) return
        
        execute("deleteWine") {
            AppResult.catchingSuspend {
                // Business rule: Only owner can delete wines
                if (!_uiState.value.canEdit) {
                    throw Exception("Permission denied: Only winery owners can delete wines")
                }
                
                wineService.deleteWine(wineId).fold(
                    onSuccess = {
                        // Wines will be automatically updated through the flow
                        Unit
                    },
                    onFailure = { error ->
                        throw error
                    }
                )
            }
        }
    }
    
    fun toggleWinerySubscription() {
        val winery = _uiState.value.winery ?: return

        // Set loading state immediately for visual feedback
        _uiState.value = _uiState.value.copy(isSubscriptionLoading = true)

        execute("toggleSubscription") {
            AppResult.catchingSuspend {
                // Get current user from auth service (not AuthenticatedRepository)
                val currentAuthUser = authService.getCurrentUser().first()
                if (currentAuthUser == null) {
                    _uiState.value = _uiState.value.copy(isSubscriptionLoading = false)
                    throw Exception("User not authenticated")
                }

                // CRITICAL: Check real-time subscription status from database, not UI state
                // UI state might be out of sync with actual database state
                val isCurrentlySubscribed = subscriptionService.isSubscribed(currentAuthUser.id, winery.id)

                val result = if (isCurrentlySubscribed) {
                    subscriptionService.unsubscribeFromWinery(currentAuthUser.id, winery.id)
                } else {
                    subscriptionService.subscribeToWinery(currentAuthUser.id, winery.id)
                }

                result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isSubscribed = !isCurrentlySubscribed,
                            isSubscriptionLoading = false
                        )
                        Unit
                    },
                    onFailure = { error ->
                        // Clear loading state on error
                        _uiState.value = _uiState.value.copy(isSubscriptionLoading = false)

                        // Provide user-friendly error messages for subscription conflicts
                        val userFriendlyMessage = when {
                            error.message?.contains("unique constraint", ignoreCase = true) == true ||
                            error.message?.contains("Already subscribed", ignoreCase = true) == true -> {
                                "You are already subscribed to this winery"
                            }
                            error.message?.contains("winery_subscriptions_user_id_winery_id_key", ignoreCase = true) == true -> {
                                "You are already subscribed to this winery"
                            }
                            else -> error.message ?: "Failed to update subscription"
                        }
                        throw Exception(userFriendlyMessage)
                    }
                )
            }
        }
    }
    
    fun refreshData() {
        val wineryId = _uiState.value.winery?.id ?: return
        execute("refreshData") {
            AppResult.catchingSuspend {
                // Sync wines and get latest data
                wineService.syncWines()
                val wines = wineService.getWinesByWinery(wineryId).first()
                _uiState.value = _uiState.value.copy(wines = wines)

                // Force refresh photos from Supabase (for pull-to-refresh)
                wineryPhotoService.syncPhotosFromSupabase(wineryId)

                wineryPhotoService.refreshPhotosFromSupabase(wineryId)

                wines
            }
        }
    }
}