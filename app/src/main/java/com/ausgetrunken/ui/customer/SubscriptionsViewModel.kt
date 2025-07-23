package com.ausgetrunken.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.WineyardSubscriptionEntity
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineyardRepository
import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubscriptionsViewModel(
    private val subscriptionService: WineyardSubscriptionService,
    private val authService: AuthService,
    private val wineyardRepository: WineyardRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()
    
    fun loadSubscriptions() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Check if we have a valid session first
                if (!authService.hasValidSession()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "User not authenticated"
                    )
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authService.getCurrentUser().first()
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    println("⚠️ SubscriptionsViewModel: No UserInfo available, attempting session restoration...")
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                println("✅ SubscriptionsViewModel: Session restored successfully")
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    println("✅ SubscriptionsViewModel: Extracted userId from session: $userIdFromSession")
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "User not authenticated"
                    )
                    return@launch
                }
                
                println("🔄 SubscriptionsViewModel: Loading real-time subscriptions for user: $userId")
                
                // Fetch subscriptions directly from Supabase (real-time, no local cache)
                subscriptionService.getUserSubscriptionsFromSupabase(userId)
                    .onSuccess { subscriptions ->
                        println("📊 SubscriptionsViewModel: Found ${subscriptions.size} real-time subscriptions")
                        
                        val subscriptionsWithWineyards = subscriptions.map { subscription ->
                            val wineyard = wineyardRepository.getWineyardById(subscription.wineyardId).first()
                            println("🔗 SubscriptionsViewModel: Processing subscription to wineyard: ${subscription.wineyardId} -> ${wineyard?.name ?: "Unknown"}")
                            SubscriptionWithWineyard(
                                id = subscription.id,
                                wineyardId = subscription.wineyardId,
                                wineyardName = wineyard?.name ?: "Unknown Wineyard",
                                wineyardAddress = wineyard?.address ?: "",
                                lowStockNotifications = subscription.lowStockNotifications,
                                newReleaseNotifications = subscription.newReleaseNotifications,
                                specialOfferNotifications = subscription.specialOfferNotifications,
                                generalNotifications = subscription.generalNotifications,
                                createdAt = subscription.createdAt,
                                formattedDate = formatDate(subscription.createdAt)
                            )
                        }
                        
                        println("✅ SubscriptionsViewModel: Real-time subscriptions loaded successfully")
                        _uiState.value = _uiState.value.copy(
                            subscriptions = subscriptionsWithWineyards,
                            isLoading = false
                        )
                    }
                    .onFailure { error ->
                        println("❌ SubscriptionsViewModel: Failed to load real-time subscriptions: ${error.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to load subscriptions: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                println("❌ SubscriptionsViewModel: Exception during real-time subscription loading: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load subscriptions: ${e.message}"
                )
            }
        }
    }
    
    fun unsubscribe(wineyardId: String) {
        viewModelScope.launch {
            try {
                // Check if we have a valid session first
                if (!authService.hasValidSession()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "User not authenticated"
                    )
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authService.getCurrentUser().first()
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "User not authenticated"
                    )
                    return@launch
                }
                
                println("🔄 SubscriptionsViewModel: Unsubscribing from wineyard: $wineyardId")
                
                subscriptionService.unsubscribeFromWineyard(userId, wineyardId)
                    .onSuccess {
                        println("✅ SubscriptionsViewModel: Successfully unsubscribed from wineyard: $wineyardId")
                        // Reload subscriptions immediately to reflect changes across devices
                        loadSubscriptions()
                    }
                    .onFailure { error ->
                        println("❌ SubscriptionsViewModel: Failed to unsubscribe: ${error.message}")
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to unsubscribe: ${error.message}"
                        )
                    }
            } catch (e: Exception) {
                println("❌ SubscriptionsViewModel: Exception during unsubscribe: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to unsubscribe: ${e.message}"
                )
            }
        }
    }
    
    fun updateNotificationPreferences(
        wineyardId: String,
        lowStock: Boolean,
        newRelease: Boolean,
        specialOffer: Boolean,
        general: Boolean
    ) {
        viewModelScope.launch {
            try {
                // Check if we have a valid session first
                if (!authService.hasValidSession()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "User not authenticated"
                    )
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authService.getCurrentUser().first()
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    authService.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "User not authenticated"
                    )
                    return@launch
                }
                
                println("🔄 SubscriptionsViewModel: Updating notification preferences for wineyard: $wineyardId")
                
                subscriptionService.updateNotificationPreferences(
                    userId,
                    wineyardId,
                    lowStock,
                    newRelease,
                    specialOffer,
                    general
                ).onSuccess {
                    println("✅ SubscriptionsViewModel: Successfully updated notification preferences for wineyard: $wineyardId")
                    // Reload subscriptions to reflect changes across devices
                    loadSubscriptions()
                }.onFailure { error ->
                    println("❌ SubscriptionsViewModel: Failed to update notification preferences: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to update preferences: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                println("❌ SubscriptionsViewModel: Exception during notification preferences update: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update preferences: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}

data class SubscriptionsUiState(
    val subscriptions: List<SubscriptionWithWineyard> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class SubscriptionWithWineyard(
    val id: String,
    val wineyardId: String,
    val wineyardName: String,
    val wineyardAddress: String,
    val lowStockNotifications: Boolean,
    val newReleaseNotifications: Boolean,
    val specialOfferNotifications: Boolean,
    val generalNotifications: Boolean,
    val createdAt: Long,
    val formattedDate: String
)