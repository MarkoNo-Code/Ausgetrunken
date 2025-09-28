package com.ausgetrunken.ui.winery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.entities.WineryEntity
import com.ausgetrunken.data.repository.WineryRepository
import com.ausgetrunken.domain.service.WineryService
import com.ausgetrunken.domain.service.WineryPhotoService
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AddWineryNavigationEvent {
    data class NavigateBackWithSuccess(val wineryId: String) : AddWineryNavigationEvent()
}

class AddWineryViewModel(
    private val wineryService: WineryService,
    private val wineryPhotoService: WineryPhotoService,
    private val authRepository: SupabaseAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddWineryUiState())
    val uiState: StateFlow<AddWineryUiState> = _uiState.asStateFlow()
    
    private val _navigationEvent = Channel<AddWineryNavigationEvent>(capacity = Channel.CONFLATED)
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

    fun submitWinery() {
        if (!_uiState.value.canSubmit) {
            println("🔥 AddWineryViewModel: Cannot submit - form not valid")
            return
        }

        println("🔥 AddWineryViewModel: Starting winery submission...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)

            try {
                // Check if we have a valid session first
                if (!authRepository.hasValidSession()) {
                    println("🔥 AddWineryViewModel: ERROR - No valid session found")
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
                    println("🔥 AddWineryViewModel: No UserInfo available, attempting session restoration...")
                    authRepository.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                println("🔥 AddWineryViewModel: Session restored successfully")
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    println("🔥 AddWineryViewModel: Extracted userId from session: $userIdFromSession")
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession
                println("🔥 AddWineryViewModel: Current user ID: $userId")
                
                if (userId == null) {
                    println("🔥 AddWineryViewModel: ERROR - Unable to determine user ID")
                    _uiState.value = _uiState.value.copy(
                        error = "User not authenticated",
                        isSubmitting = false
                    )
                    return@launch
                }

                val state = _uiState.value
                val wineryId = UUID.randomUUID().toString()

                // Create winery entity (WITHOUT photos in the main record)
                val wineryEntity = WineryEntity(
                    id = wineryId,
                    ownerId = userId,
                    name = state.name,
                    description = state.description,
                    address = state.address,
                    latitude = state.latitude ?: 0.0,
                    longitude = state.longitude ?: 0.0,
                    photos = emptyList() // Photos will be handled separately via WineryPhotoService
                )

                // Create winery
                println("🔥 AddWineryViewModel: Creating winery entity: $wineryEntity")
                val result = wineryService.createWinery(wineryEntity)
                println("🔥 AddWineryViewModel: Create result - isSuccess: ${result.isSuccess}")

                if (result.isSuccess) {
                    // Upload photos using WineryPhotoService AFTER winery is created
                    state.selectedImages.forEach { imagePath ->
                        try {
                            println("🔥 AddWineryViewModel: Uploading photo: $imagePath")
                            val photoResult = if (imagePath.startsWith("content://")) {
                                wineryPhotoService.addPhoto(wineryId, Uri.parse(imagePath))
                            } else {
                                wineryPhotoService.addPhoto(wineryId, imagePath)
                            }

                            photoResult.onSuccess { localPath ->
                                println("✅ AddWineryViewModel: Photo uploaded successfully: $localPath")
                            }.onFailure { error ->
                                println("❌ AddWineryViewModel: Photo upload failed: $error")
                            }
                        } catch (e: Exception) {
                            println("❌ AddWineryViewModel: Photo upload exception: ${e.message}")
                        }
                    }

                    // Wines can be added later in the winery detail screen

                    println("AddWineryViewModel: Winery created successfully, wineryId: $wineryId")
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = null
                    )
                    
                    // Send navigation event through channel
                    _navigationEvent.trySend(AddWineryNavigationEvent.NavigateBackWithSuccess(wineryId))
                } else {
                    println("🔥 AddWineryViewModel: ERROR - Create failed: ${result.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "Failed to create winery",
                        isSubmitting = false
                    )
                }
            } catch (e: Exception) {
                println("🔥 AddWineryViewModel: EXCEPTION - ${e.message}")
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