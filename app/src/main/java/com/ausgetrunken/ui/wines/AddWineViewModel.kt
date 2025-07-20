package com.ausgetrunken.ui.wines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.UserType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.data.local.entities.WineType
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AddWineViewModel(
    private val wineService: WineService,
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val wineyardService: WineyardService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddWineUiState())
    val uiState: StateFlow<AddWineUiState> = _uiState.asStateFlow()
    
    private val _navigationEvents = Channel<NavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()
    
    fun setWineyardId(wineyardId: String) {
        _uiState.update { it.copy(wineyardId = wineyardId, isSuccess = false) }
    }
    
    fun resetState() {
        _uiState.update { 
            AddWineUiState()
        }
    }
    
    fun updateName(name: String) {
        _uiState.update { 
            it.copy(
                name = name,
                nameError = if (name.isBlank()) "Name is required" else null
            )
        }
    }
    
    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }
    
    fun updateWineType(wineType: WineType) {
        _uiState.update { 
            it.copy(
                wineType = wineType,
                wineTypeError = null
            )
        }
    }
    
    fun updateVintage(vintage: String) {
        val vintageError = when {
            vintage.isBlank() -> "Vintage is required"
            vintage.toIntOrNull() == null -> "Invalid vintage year"
            vintage.toInt() < 1800 || vintage.toInt() > 2030 -> "Vintage must be between 1800 and 2030"
            else -> null
        }
        
        _uiState.update { 
            it.copy(
                vintage = vintage,
                vintageError = vintageError
            )
        }
    }
    
    fun updatePrice(price: String) {
        val priceError = when {
            price.isBlank() -> "Price is required"
            price.toDoubleOrNull() == null -> "Invalid price"
            price.toDouble() < 0 -> "Price must be positive"
            else -> null
        }
        
        _uiState.update { 
            it.copy(
                price = price,
                priceError = priceError
            )
        }
    }
    
    // Discounted price functionality removed - not in current database schema
    
    fun updateStockQuantity(stockQuantity: String) {
        val stockQuantityError = when {
            stockQuantity.isBlank() -> "Stock quantity is required"
            stockQuantity.toIntOrNull() == null -> "Invalid stock quantity"
            stockQuantity.toInt() < 0 -> "Stock quantity must be non-negative"
            else -> null
        }
        
        _uiState.update { 
            it.copy(
                stockQuantity = stockQuantity,
                stockQuantityError = stockQuantityError
            )
        }
    }
    
    // Low stock threshold functionality removed - not in current database schema
    
    fun createWine() {
        val currentState = _uiState.value
        
        if (!currentState.canSubmit) {
            return
        }
        
        viewModelScope.launch {
            // Check user permissions first
            val currentUser = authService.getCurrentUser().first()
            if (currentUser == null) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "User not authenticated"
                    )
                }
                return@launch
            }
            
            val user = userRepository.getUserById(currentUser.id).first()
            if (user?.userType != UserType.WINEYARD_OWNER) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Only wineyard owners can create wines"
                    )
                }
                return@launch
            }
            
            // Validate wineyard ownership for security
            val isOwner = wineyardService.validateWineyardOwnership(currentUser.id, currentState.wineyardId)
            if (!isOwner) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Access denied: You can only add wines to your own wineyards"
                    )
                }
                return@launch
            }
            
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                
                // Check wine limit
                val existingWines = wineService.getWinesByWineyard(currentState.wineyardId).first()
                if (existingWines.size >= 20) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "Maximum of 20 wines per wineyard allowed"
                        )
                    }
                    return@launch
                }
                
                val wine = WineEntity(
                    id = UUID.randomUUID().toString(),
                    wineyardId = currentState.wineyardId,
                    name = currentState.name,
                    description = currentState.description,
                    wineType = currentState.wineType!!,
                    vintage = currentState.vintage.toInt(),
                    price = currentState.price.toDouble(),
                    stockQuantity = currentState.stockQuantity.toInt(),
                    fullStockQuantity = currentState.stockQuantity.toInt()
                )
                
                wineService.createWine(wine)
                    .onSuccess { createdWine ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isSuccess = true
                            )
                        }
                        // Navigate to the newly created wine's detail page
                        _navigationEvents.trySend(NavigationEvent.NavigateToWineDetail(createdWine.id))
                    }
                    .onFailure { exception ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = exception.message ?: "Failed to create wine"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to create wine"
                    )
                }
            }
        }
    }
}

data class AddWineUiState(
    val wineyardId: String = "",
    val name: String = "",
    val description: String = "",
    val wineType: WineType? = null,
    val vintage: String = "",
    val price: String = "",
    val stockQuantity: String = "",
    val nameError: String? = null,
    val wineTypeError: String? = null,
    val vintageError: String? = null,
    val priceError: String? = null,
    val stockQuantityError: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() &&
                wineType != null &&
                vintage.isNotBlank() &&
                price.isNotBlank() &&
                stockQuantity.isNotBlank() &&
                nameError == null &&
                wineTypeError == null &&
                vintageError == null &&
                priceError == null &&
                stockQuantityError == null
}

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
    data class NavigateToWineDetail(val wineId: String) : NavigationEvent()
}