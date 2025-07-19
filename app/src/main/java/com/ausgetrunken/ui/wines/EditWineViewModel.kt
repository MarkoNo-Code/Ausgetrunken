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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditWineViewModel(
    private val wineService: WineService,
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val wineyardService: WineyardService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EditWineUiState())
    val uiState: StateFlow<EditWineUiState> = _uiState.asStateFlow()
    
    private var originalWine: WineEntity? = null
    
    fun loadWine(wineId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                val wine = wineService.getWineById(wineId).first()
                if (wine != null) {
                    originalWine = wine
                    _uiState.update {
                        it.copy(
                            wineId = wine.id,
                            name = wine.name,
                            description = wine.description,
                            wineType = wine.wineType,
                            vintage = wine.vintage.toString(),
                            price = wine.price.toString(),
                            discountedPrice = wine.discountedPrice?.toString() ?: "",
                            stockQuantity = wine.stockQuantity.toString(),
                            lowStockThreshold = wine.lowStockThreshold.toString(),
                            isLoading = false,
                            isDataLoaded = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Wine not found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load wine"
                    )
                }
            }
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
    
    fun updateDiscountedPrice(discountedPrice: String) {
        val discountedPriceError = when {
            discountedPrice.isNotBlank() && discountedPrice.toDoubleOrNull() == null -> "Invalid discounted price"
            discountedPrice.isNotBlank() && discountedPrice.toDouble() < 0 -> "Discounted price must be positive"
            discountedPrice.isNotBlank() && _uiState.value.price.toDoubleOrNull() != null &&
                    discountedPrice.toDouble() >= _uiState.value.price.toDouble() -> "Discounted price must be less than regular price"
            else -> null
        }
        
        _uiState.update {
            it.copy(
                discountedPrice = discountedPrice,
                discountedPriceError = discountedPriceError
            )
        }
    }
    
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
    
    fun updateLowStockThreshold(lowStockThreshold: String) {
        val lowStockThresholdError = when {
            lowStockThreshold.isBlank() -> "Low stock threshold is required"
            lowStockThreshold.toIntOrNull() == null -> "Invalid low stock threshold"
            lowStockThreshold.toInt() < 0 -> "Low stock threshold must be non-negative"
            else -> null
        }
        
        _uiState.update {
            it.copy(
                lowStockThreshold = lowStockThreshold,
                lowStockThresholdError = lowStockThresholdError
            )
        }
    }
    
    fun updateWine() {
        val currentState = _uiState.value
        val originalWine = this.originalWine
        
        if (!currentState.canSubmit || originalWine == null) return
        
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
                        errorMessage = "Only wineyard owners can edit wines"
                    )
                }
                return@launch
            }
            
            // Validate wineyard ownership for security
            val isOwner = wineyardService.validateWineyardOwnership(currentUser.id, originalWine.wineyardId)
            if (!isOwner) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Access denied: You can only edit wines from your own wineyards"
                    )
                }
                return@launch
            }
            
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                
                val updatedWine = originalWine.copy(
                    name = currentState.name,
                    description = currentState.description,
                    wineType = currentState.wineType!!,
                    vintage = currentState.vintage.toInt(),
                    price = currentState.price.toDouble(),
                    discountedPrice = currentState.discountedPrice.toDoubleOrNull(),
                    stockQuantity = currentState.stockQuantity.toInt(),
                    lowStockThreshold = currentState.lowStockThreshold.toInt(),
                    updatedAt = System.currentTimeMillis()
                )
                
                wineService.updateWine(updatedWine)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSuccess = true
                            )
                        }
                    }
                    .onFailure { exception ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = exception.message ?: "Failed to update wine"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to update wine"
                    )
                }
            }
        }
    }
}

data class EditWineUiState(
    val wineId: String = "",
    val name: String = "",
    val description: String = "",
    val wineType: WineType? = null,
    val vintage: String = "",
    val price: String = "",
    val discountedPrice: String = "",
    val stockQuantity: String = "",
    val lowStockThreshold: String = "",
    val nameError: String? = null,
    val wineTypeError: String? = null,
    val vintageError: String? = null,
    val priceError: String? = null,
    val discountedPriceError: String? = null,
    val stockQuantityError: String? = null,
    val lowStockThresholdError: String? = null,
    val isLoading: Boolean = false,
    val isDataLoaded: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() &&
                wineType != null &&
                vintage.isNotBlank() &&
                price.isNotBlank() &&
                stockQuantity.isNotBlank() &&
                lowStockThreshold.isNotBlank() &&
                nameError == null &&
                wineTypeError == null &&
                vintageError == null &&
                priceError == null &&
                discountedPriceError == null &&
                stockQuantityError == null &&
                lowStockThresholdError == null
}