package com.ausgetrunken.ui.wineyard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.data.repository.WineyardRepository
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineyardPhotoService
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AddWineyardNavigationEvent {
    data class NavigateBackWithSuccess(val wineyardId: String) : AddWineyardNavigationEvent()
}

class AddWineyardViewModel(
    private val wineyardService: WineyardService,
    private val wineyardPhotoService: WineyardPhotoService,
    private val authRepository: SupabaseAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddWineyardUiState())
    val uiState: StateFlow<AddWineyardUiState> = _uiState.asStateFlow()
    
    private val _navigationEvent = Channel<AddWineyardNavigationEvent>(capacity = Channel.CONFLATED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
        validateForm()
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
        validateForm()
    }

    fun onAddressChanged(address: String) {
        _uiState.value = _uiState.value.copy(address = address)
        validateForm()
    }

    fun onLocationChanged(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            latitude = latitude,
            longitude = longitude
        )
    }

    fun onImageAdded(imageUri: String) {
        val currentImages = _uiState.value.selectedImages
        _uiState.value = _uiState.value.copy(
            selectedImages = currentImages + imageUri
        )
    }

    fun onImageRemoved(imageUri: String) {
        val currentImages = _uiState.value.selectedImages
        _uiState.value = _uiState.value.copy(
            selectedImages = currentImages - imageUri
        )
    }


    private fun validateForm() {
        val state = _uiState.value
        val isValid = state.name.isNotBlank() &&
                state.description.isNotBlank() &&
                state.address.isNotBlank()

        _uiState.value = state.copy(isValidForm = isValid)
    }

    fun submitWineyard() {
        if (!_uiState.value.canSubmit) {
            println("🔥 AddWineyardViewModel: Cannot submit - form not valid")
            return
        }

        println("🔥 AddWineyardViewModel: Starting wineyard submission...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)

            try {
                // Check if we have a valid session first
                if (!authRepository.hasValidSession()) {
                    println("🔥 AddWineyardViewModel: ERROR - No valid session found")
                    _uiState.value = _uiState.value.copy(
                        error = "User not authenticated",
                        isSubmitting = false
                    )
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration if needed
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    println("🔥 AddWineyardViewModel: No UserInfo available, attempting session restoration...")
                    authRepository.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                println("🔥 AddWineyardViewModel: Session restored successfully")
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    println("🔥 AddWineyardViewModel: Extracted userId from session: $userIdFromSession")
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession
                println("🔥 AddWineyardViewModel: Current user ID: $userId")
                
                if (userId == null) {
                    println("🔥 AddWineyardViewModel: ERROR - Unable to determine user ID")
                    _uiState.value = _uiState.value.copy(
                        error = "User not authenticated",
                        isSubmitting = false
                    )
                    return@launch
                }

                val state = _uiState.value
                val wineyardId = UUID.randomUUID().toString()

                // Create wineyard entity (WITHOUT photos in the main record)
                val wineyardEntity = WineyardEntity(
                    id = wineyardId,
                    ownerId = userId,
                    name = state.name,
                    description = state.description,
                    address = state.address,
                    latitude = state.latitude ?: 0.0,
                    longitude = state.longitude ?: 0.0,
                    photos = emptyList() // Photos will be handled separately via WineyardPhotoService
                )

                // Create wineyard
                println("🔥 AddWineyardViewModel: Creating wineyard entity: $wineyardEntity")
                val result = wineyardService.createWineyard(wineyardEntity)
                println("🔥 AddWineyardViewModel: Create result - isSuccess: ${result.isSuccess}")

                if (result.isSuccess) {
                    // Upload photos using WineyardPhotoService AFTER wineyard is created
                    state.selectedImages.forEach { imagePath ->
                        try {
                            println("🔥 AddWineyardViewModel: Uploading photo: $imagePath")
                            val photoResult = if (imagePath.startsWith("content://")) {
                                wineyardPhotoService.addPhoto(wineyardId, Uri.parse(imagePath))
                            } else {
                                wineyardPhotoService.addPhoto(wineyardId, imagePath)
                            }

                            photoResult.onSuccess { localPath ->
                                println("✅ AddWineyardViewModel: Photo uploaded successfully: $localPath")
                            }.onFailure { error ->
                                println("❌ AddWineyardViewModel: Photo upload failed: $error")
                            }
                        } catch (e: Exception) {
                            println("❌ AddWineyardViewModel: Photo upload exception: ${e.message}")
                        }
                    }

                    // Wines can be added later in the wineyard detail screen

                    println("AddWineyardViewModel: Wineyard created successfully, wineyardId: $wineyardId")
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = null
                    )
                    
                    // Send navigation event through channel
                    _navigationEvent.trySend(AddWineyardNavigationEvent.NavigateBackWithSuccess(wineyardId))
                } else {
                    println("🔥 AddWineyardViewModel: ERROR - Create failed: ${result.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "Failed to create wineyard",
                        isSubmitting = false
                    )
                }
            } catch (e: Exception) {
                println("🔥 AddWineyardViewModel: EXCEPTION - ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error occurred",
                    isSubmitting = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearNavigationFlag() {
        _uiState.value = _uiState.value.copy(navigateBackWithSuccess = null)
    }
    
    fun onNavigateBackWithSuccess(onNavigateBack: (String) -> Unit) {
        // Clear any form state if needed
        // Then trigger navigation
        // This will be called from the screen when navigation event is received
    }
}