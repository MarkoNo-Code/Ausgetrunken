package com.ausgetrunken.ui.wineyard

import android.util.Log
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.domain.common.AppResult
import com.ausgetrunken.domain.error.AppError
import com.ausgetrunken.domain.error.toAppError
import com.ausgetrunken.domain.repository.AuthenticatedRepository
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import com.ausgetrunken.domain.service.ImageUploadService
import com.ausgetrunken.domain.service.WineyardPhotoService
import com.ausgetrunken.domain.service.NewWineyardPhotoService
import com.ausgetrunken.domain.service.DatabaseInspectionService
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

class WineyardDetailViewModel(
    private val wineyardService: WineyardService,
    private val wineService: WineService,
    private val authenticatedRepository: AuthenticatedRepository,
    private val userRepository: UserRepository,
    private val subscriptionService: WineyardSubscriptionService,
    private val imageUploadService: ImageUploadService,
    private val wineyardPhotoService: WineyardPhotoService,
    private val newWineyardPhotoService: NewWineyardPhotoService,
    private val databaseInspectionService: DatabaseInspectionService,
    private val context: Context
) : BaseViewModel() {
    
    companion object {
        // Feature flag to test new photo service implementation
        private const val USE_NEW_PHOTO_SERVICE = false
    }
    
    private val _uiState = MutableStateFlow(WineyardDetailUiState())
    val uiState: StateFlow<WineyardDetailUiState> = _uiState.asStateFlow()
    
    fun loadWineyard(wineyardId: String) {
        execute("loadWineyard") {
            authenticatedRepository.executeAuthenticated { user ->
                
                // Get user details for permission checking
                val userEntity = userRepository.getUserById(user.id).first()
                val wineyard = wineyardService.getWineyardById(wineyardId).first()
                
                if (wineyard == null) {
                    return@executeAuthenticated AppResult.failure(
                        AppError.DataError.NotFound("Wineyard", wineyardId)
                    )
                }
                
                // Determine permissions
                val canEdit = userEntity?.userType == UserType.WINEYARD_OWNER
                
                // Update UI state (preserve existing photos)
                _uiState.value = _uiState.value.copy(
                    wineyard = wineyard,
                    canEdit = canEdit
                    // Keep existing photos - they will be updated by loadPhotos()
                )
                
                // DEBUG: Inspect database on wineyard load
                viewModelScope.launch {
                    databaseInspectionService.inspectDatabase()
                    databaseInspectionService.inspectWineyardPhotos(wineyard.id)
                }
                
                // Load related data
                loadWines(wineyard.id)
                loadPhotos(wineyard.id)
                
                // Load subscription status for customers
                if (userEntity?.userType == UserType.CUSTOMER) {
                    loadSubscriptionStatus(user.id, wineyard.id)
                }
                
                // CRITICAL DEBUG: Temporarily disable Supabase sync to test if it's clearing database
                // Sync photos with Supabase in background
                // if (wineyard.photos.isNotEmpty()) {
                //     syncPhotosWithSupabase(wineyard.id, wineyard.photos)
                // }
                Log.d("WineyardDetailViewModel", "DISABLED Supabase sync to test database persistence")
                
                AppResult.success(wineyard)
            }
        }
    }
    
    fun toggleEdit() {
        if (_uiState.value.canEdit) {
            _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
        }
    }
    
    fun updateWineyardName(name: String) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(name = name)
            )
        }
    }
    
    fun updateWineyardDescription(description: String) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(description = description)
            )
        }
    }
    
    fun updateWineyardAddress(address: String) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(address = address)
            )
        }
    }
    
    fun updateWineyardLocation(latitude: Double, longitude: Double) {
        _uiState.value.wineyard?.let { wineyard ->
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(latitude = latitude, longitude = longitude)
            )
        }
    }
    
    fun addPhoto(photoPath: String) {
        Log.d("WineyardDetailViewModel", "🚀 ADD_PHOTO CALLED: $photoPath")
        Log.d("WineyardDetailViewModel", "🚀 Current wineyard: ${_uiState.value.wineyard?.id}")
        Log.d("WineyardDetailViewModel", "🚀 Can edit: ${_uiState.value.canEdit}")
        
        val wineyard = _uiState.value.wineyard ?: return
        
        if (!_uiState.value.canEdit) {
            Log.w("WineyardDetailViewModel", "Cannot add photo: no edit permission")
            return
        }

        _uiState.value = _uiState.value.copy(isUpdating = true)

        execute("addPhoto") {
            authenticatedRepository.executeAuthenticated { user ->
                try {
                    Log.d("WineyardDetailViewModel", "Starting photo add process for: $photoPath")
                    
                    val result = when {
                        photoPath.startsWith("content://") -> {
                            wineyardPhotoService.addPhoto(wineyard.id, Uri.parse(photoPath))
                        }
                        photoPath.startsWith("/") -> {
                            wineyardPhotoService.addPhoto(wineyard.id, photoPath)
                        }
                        else -> {
                            // Fallback for existing URLs or other formats
                            Log.w("WineyardDetailViewModel", "Unsupported photo path format: $photoPath")
                            Result.failure(Exception("Unsupported photo path format"))
                        }
                    }
                    
                    result.fold(
                        onSuccess = { localPath ->
                            Log.d("WineyardDetailViewModel", "Photo added successfully: $localPath")
                            
                            // DEBUG: Inspect database after photo add
                            viewModelScope.launch {
                                databaseInspectionService.inspectDatabase()
                                databaseInspectionService.inspectWineyardPhotos(wineyard.id)
                            }
                            
                            // Immediately add photo to UI state while Flow processes
                            val currentPhotos = _uiState.value.photos.toMutableList()
                            if (!currentPhotos.contains(localPath)) {
                                currentPhotos.add(localPath)
                                _uiState.value = _uiState.value.copy(photos = currentPhotos)
                                Log.d("WineyardDetailViewModel", "Immediately updated UI state with new photo: $localPath")
                            }
                            
                            _uiState.value = _uiState.value.copy(isUpdating = false)
                            AppResult.success(Unit)
                        },
                        onFailure = { error ->
                            Log.e("WineyardDetailViewModel", "Failed to add photo: $error")
                            _uiState.value = _uiState.value.copy(isUpdating = false)
                            AppResult.failure(error.toAppError("photo add"))
                        }
                    )
                } catch (e: Exception) {
                    Log.e("WineyardDetailViewModel", "Exception in photo add process", e)
                    _uiState.value = _uiState.value.copy(isUpdating = false)
                    AppResult.failure(e.toAppError("photo add"))
                }
            }
        }
    }
    
    
    fun removePhoto(photoUrl: String) {
        if (!_uiState.value.canEdit) {
            Log.w("WineyardDetailViewModel", "Cannot remove photo: no edit permission")
            return
        }

        _uiState.value = _uiState.value.copy(isUpdating = true)

        execute("removePhoto") {
            authenticatedRepository.executeAuthenticated { user ->
                try {
                    Log.d("WineyardDetailViewModel", "Starting photo removal process for: $photoUrl")
                    
                    val result = wineyardPhotoService.removePhoto(photoUrl)
                    
                    result.fold(
                        onSuccess = {
                            Log.d("WineyardDetailViewModel", "Photo removed successfully: $photoUrl")
                            _uiState.value = _uiState.value.copy(isUpdating = false)
                            AppResult.success(Unit)
                        },
                        onFailure = { error ->
                            Log.e("WineyardDetailViewModel", "Failed to remove photo: $error")
                            _uiState.value = _uiState.value.copy(isUpdating = false)
                            AppResult.failure(error.toAppError("photo removal"))
                        }
                    )
                } catch (e: Exception) {
                    Log.e("WineyardDetailViewModel", "Exception in photo removal process", e)
                    _uiState.value = _uiState.value.copy(isUpdating = false)
                    AppResult.failure(e.toAppError("photo removal"))
                }
            }
        }
    }
    
    fun saveWineyard() {
        val wineyard = _uiState.value.wineyard ?: return
        if (!_uiState.value.canEdit) return
        
        execute("saveWineyard") {
            authenticatedRepository.executeAuthenticated { user ->
                
                // Set updating state
                _uiState.value = _uiState.value.copy(isUpdating = true)
                
                // Business rule: Only owner can save
                if (!_uiState.value.canEdit) {
                    return@executeAuthenticated AppResult.failure(
                        AppError.AuthError.PermissionDenied("update wineyard", "WINEYARD_OWNER")
                    )
                }
                
                return@executeAuthenticated wineyardService.updateWineyard(wineyard).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isUpdating = false,
                            isEditing = false,
                            navigateBackAfterSave = true
                        )
                        AppResult.success(Unit)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(isUpdating = false)
                        AppResult.failure(error.toAppError("wineyard update"))
                    }
                )
            }
        }
    }
    
    fun deleteWineyard() {
        val wineyard = _uiState.value.wineyard ?: return
        if (!_uiState.value.canEdit) return
        
        execute("deleteWineyard") {
            authenticatedRepository.executeAuthenticated { user ->
                
                // Business rule: Only owner can delete
                if (!_uiState.value.canEdit) {
                    return@executeAuthenticated AppResult.failure(
                        AppError.AuthError.PermissionDenied("delete wineyard", "WINEYARD_OWNER")
                    )
                }
                
                return@executeAuthenticated wineyardService.deleteWineyard(wineyard.id).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            navigateBackAfterDelete = true
                        )
                        AppResult.success(Unit)
                    },
                    onFailure = { error ->
                        AppResult.failure(error.toAppError("wineyard deletion"))
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
    
    private fun loadWines(wineyardId: String) {
        execute("loadWines", showLoading = false) {
            AppResult.catchingSuspend {
                // Sync wines and get latest data
                wineService.syncWines()
                val wines = wineService.getWinesByWineyard(wineyardId).first()
                _uiState.value = _uiState.value.copy(wines = wines)
                wines
            }
        }
    }
    
    private fun loadPhotos(wineyardId: String) {
        // Launch a coroutine to continuously collect photo updates
        viewModelScope.launch {
            try {
                if (USE_NEW_PHOTO_SERVICE) {
                    Log.d("WineyardDetailViewModel", "Using NEW photo service for wineyard: $wineyardId")
                    // Start upload service if not already running
                    newWineyardPhotoService.startUploadService()
                    
                    newWineyardPhotoService.getWineyardPhotos(wineyardId).collect { photos ->
                        Log.d("WineyardDetailViewModel", "NEW SERVICE: Photo list updated: ${photos.size} photos - $photos")
                        _uiState.value = _uiState.value.copy(photos = photos)
                        Log.d("WineyardDetailViewModel", "NEW SERVICE: Updated UI state with photos: $photos")
                    }
                } else {
                    Log.d("WineyardDetailViewModel", "Using LEGACY photo service for wineyard: $wineyardId")
                    wineyardPhotoService.getWineyardPhotos(wineyardId).collect { photos ->
                        Log.d("WineyardDetailViewModel", "LEGACY SERVICE: Photo list updated: ${photos.size} photos - $photos")
                        _uiState.value = _uiState.value.copy(photos = photos)
                        Log.d("WineyardDetailViewModel", "LEGACY SERVICE: Updated UI state with photos: $photos")
                    }
                }
            } catch (e: Exception) {
                Log.e("WineyardDetailViewModel", "Error loading photos", e)
            }
        }
    }
    
    private fun syncPhotosWithSupabase(wineyardId: String, remotePhotos: List<String>) {
        execute("syncPhotos", showLoading = false) {
            AppResult.catchingSuspend {
                wineyardPhotoService.syncPhotosWithSupabase(wineyardId, remotePhotos)
                Log.d("WineyardDetailViewModel", "Photo sync completed for wineyard: $wineyardId")
                Unit
            }
        }
    }
    
    private fun loadSubscriptionStatus(userId: String, wineyardId: String) {
        execute("loadSubscriptionStatus", showLoading = false) {
            AppResult.catchingSuspend {
                val isSubscribed = subscriptionService.isSubscribed(userId, wineyardId)
                _uiState.value = _uiState.value.copy(isSubscribed = isSubscribed)
                isSubscribed
            }
        }
    }
    
    fun deleteWine(wineId: String) {
        if (!_uiState.value.canEdit) return
        
        execute("deleteWine") {
            authenticatedRepository.executeAuthenticated { user ->
                
                // Business rule: Only owner can delete wines
                if (!_uiState.value.canEdit) {
                    return@executeAuthenticated AppResult.failure(
                        AppError.AuthError.PermissionDenied("delete wine", "WINEYARD_OWNER")
                    )
                }
                
                return@executeAuthenticated wineService.deleteWine(wineId).fold(
                    onSuccess = {
                        // Wines will be automatically updated through the flow
                        AppResult.success(Unit)
                    },
                    onFailure = { error ->
                        AppResult.failure(error.toAppError("wine deletion"))
                    }
                )
            }
        }
    }
    
    fun toggleWineyardSubscription() {
        val wineyard = _uiState.value.wineyard ?: return
        
        execute("toggleSubscription") {
            authenticatedRepository.executeAuthenticated { user ->
                
                // CRITICAL: Check real-time subscription status from database, not UI state
                // UI state might be out of sync with actual database state
                val isCurrentlySubscribed = subscriptionService.isSubscribed(user.id, wineyard.id)
                
                val result = if (isCurrentlySubscribed) {
                    subscriptionService.unsubscribeFromWineyard(user.id, wineyard.id)
                } else {
                    subscriptionService.subscribeToWineyard(user.id, wineyard.id)
                }
                
                return@executeAuthenticated result.fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isSubscribed = !isCurrentlySubscribed
                        )
                        AppResult.success(Unit)
                    },
                    onFailure = { error ->
                        // Provide user-friendly error messages for subscription conflicts
                        val userFriendlyMessage = when {
                            error.message?.contains("unique constraint", ignoreCase = true) == true ||
                            error.message?.contains("Already subscribed", ignoreCase = true) == true -> {
                                "You are already subscribed to this wineyard"
                            }
                            error.message?.contains("wineyard_subscriptions_user_id_wineyard_id_key", ignoreCase = true) == true -> {
                                "You are already subscribed to this wineyard"
                            }
                            else -> error.message ?: "Failed to update subscription"
                        }
                        AppResult.failure(Exception(userFriendlyMessage).toAppError("subscription toggle"))
                    }
                )
            }
        }
    }
    
    fun refreshData() {
        val wineyardId = _uiState.value.wineyard?.id ?: return
        execute("refreshData") {
            AppResult.catchingSuspend {
                // Sync wines and get latest data
                wineService.syncWines()
                val wines = wineService.getWinesByWineyard(wineyardId).first()
                _uiState.value = _uiState.value.copy(wines = wines)
                wines
            }
        }
    }
}