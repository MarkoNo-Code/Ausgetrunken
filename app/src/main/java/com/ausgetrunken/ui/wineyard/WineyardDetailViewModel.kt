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
import com.ausgetrunken.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class WineyardDetailViewModel(
    private val wineyardService: WineyardService,
    private val wineService: WineService,
    private val authenticatedRepository: AuthenticatedRepository,
    private val userRepository: UserRepository,
    private val subscriptionService: WineyardSubscriptionService
) : BaseViewModel() {
    
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
                
                // Update UI state
                _uiState.value = _uiState.value.copy(
                    wineyard = wineyard,
                    canEdit = canEdit
                )
                
                // Load related data
                loadWines(wineyard.id)
                
                // Load subscription status for customers
                if (userEntity?.userType == UserType.CUSTOMER) {
                    loadSubscriptionStatus(user.id, wineyard.id)
                }
                
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
    
    fun addPhoto(photoUrl: String) {
        Log.d("WineyardDetailViewModel", "Adding photo URL: $photoUrl")
        _uiState.value.wineyard?.let { wineyard ->
            val updatedPhotos = wineyard.photos + photoUrl
            Log.d("WineyardDetailViewModel", "Updated photos list: $updatedPhotos")
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(photos = updatedPhotos)
            )
            
            // Auto-save the photo immediately for better UX
            if (_uiState.value.canEdit) {
                Log.d("WineyardDetailViewModel", "Auto-saving photo to database")
                saveWineyardPhotosOnly()
            }
        }
    }
    
    private fun saveWineyardPhotosOnly() {
        val wineyard = _uiState.value.wineyard ?: return
        if (!_uiState.value.canEdit) return
        
        execute("saveWineyardPhotos") {
            authenticatedRepository.executeAuthenticated { user ->
                return@executeAuthenticated wineyardService.updateWineyard(wineyard).fold(
                    onSuccess = {
                        Log.d("WineyardDetailViewModel", "Photos saved successfully")
                        AppResult.success(Unit)
                    },
                    onFailure = { error ->
                        Log.e("WineyardDetailViewModel", "Failed to save photos: $error")
                        AppResult.failure(error.toAppError("photo save"))
                    }
                )
            }
        }
    }
    
    fun removePhoto(photoUrl: String) {
        _uiState.value.wineyard?.let { wineyard ->
            val updatedPhotos = wineyard.photos.filter { it != photoUrl }
            _uiState.value = _uiState.value.copy(
                wineyard = wineyard.copy(photos = updatedPhotos)
            )
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
                
                val isCurrentlySubscribed = _uiState.value.isSubscribed
                
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
                        AppResult.failure(error.toAppError("subscription toggle"))
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