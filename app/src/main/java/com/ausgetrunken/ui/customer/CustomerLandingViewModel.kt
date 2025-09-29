package com.ausgetrunken.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.WineryService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WinerySubscriptionService
import com.ausgetrunken.auth.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class CustomerLandingViewModel(
    private val wineryService: WineryService,
    private val wineService: WineService,
    private val subscriptionService: WinerySubscriptionService,
    private val authRepository: SupabaseAuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CustomerLandingUiState())
    val uiState: StateFlow<CustomerLandingUiState> = _uiState.asStateFlow()
    
    companion object {
        private const val ITEMS_PER_PAGE = 5
    }
    
    init {
        loadWineries()
        loadSubscriptions()
    }
    
    fun switchTab(tab: CustomerTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
        when (tab) {
            CustomerTab.WINERIES -> {
                if (_uiState.value.wineries.isEmpty()) {
                    loadWineries()
                }
            }
            CustomerTab.WINES -> {
                if (_uiState.value.wines.isEmpty()) {
                    loadWines()
                }
            }
        }
    }
    
    fun loadWineries(page: Int = 0) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Check if we need to sync from Supabase first
                if (page == 0) {
                    val localWineries = wineryService.getAllWinerysPaginated(1, 0)
                    if (localWineries.isEmpty()) {
                        // Sync from Supabase if local database is empty
                        wineryService.syncWinerys()
                    }
                }

                val offset = page * ITEMS_PER_PAGE
                val wineries = wineryService.getAllWinerysPaginated(ITEMS_PER_PAGE, offset)
                
                val hasMore = wineries.size == ITEMS_PER_PAGE

                if (page == 0) {
                    // First page - replace all wineries
                    _uiState.value = _uiState.value.copy(
                        wineries = wineries,
                        currentWineryPage = page,
                        hasMoreWineries = hasMore,
                        isLoading = false
                    )
                } else {
                    // Additional pages - append to existing wineries
                    _uiState.value = _uiState.value.copy(
                        wineries = _uiState.value.wineries + wineries,
                        currentWineryPage = page,
                        hasMoreWineries = hasMore,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load wineries: ${e.message}"
                )
            }
        }
    }
    
    fun loadWines(page: Int = 0) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Check if we need to sync from Supabase first
                if (page == 0) {
                    val localWines = wineService.getAllWinesPaginated(1, 0)
                    if (localWines.isEmpty()) {
                        // Sync from Supabase if local database is empty
                        wineService.syncWines()
                    }
                }
                
                val offset = page * ITEMS_PER_PAGE
                val wines = wineService.getAllWinesPaginated(ITEMS_PER_PAGE, offset)
                
                val hasMore = wines.size == ITEMS_PER_PAGE
                
                if (page == 0) {
                    // First page - replace all wines
                    _uiState.value = _uiState.value.copy(
                        wines = wines,
                        currentWinePage = page,
                        hasMoreWines = hasMore,
                        isLoading = false
                    )
                } else {
                    // Additional pages - append to existing wines
                    _uiState.value = _uiState.value.copy(
                        wines = _uiState.value.wines + wines,
                        currentWinePage = page,
                        hasMoreWines = hasMore,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load wines: ${e.message}"
                )
            }
        }
    }
    
    fun loadNextPage() {
        when (_uiState.value.currentTab) {
            CustomerTab.WINERIES -> {
                if (_uiState.value.hasMoreWineries && !_uiState.value.isLoading) {
                    loadWineries(_uiState.value.currentWineryPage + 1)
                }
            }
            CustomerTab.WINES -> {
                if (_uiState.value.hasMoreWines && !_uiState.value.isLoading) {
                    loadWines(_uiState.value.currentWinePage + 1)
                }
            }
        }
    }
    
    fun refreshData() {
        when (_uiState.value.currentTab) {
            CustomerTab.WINERIES -> {
                loadWineries(0)
                loadSubscriptions() // Also refresh subscriptions
            }
            CustomerTab.WINES -> loadWines(0)
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun refreshSubscriptions() {
        loadSubscriptions()
    }
    
    private fun loadSubscriptions() {
        viewModelScope.launch {
            try {
                println("🔄 CustomerLandingViewModel: Loading subscriptions...")
                
                // Check for valid session first
                if (!authRepository.hasValidSession()) {
                    // Removed println: "❌ CustomerLandingViewModel: No valid session for subscription loading"
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    // Removed println: "⚠️ CustomerLandingViewModel: No UserInfo available, attempting session restoration..."
                    authRepository.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                // Removed println: "✅ CustomerLandingViewModel: Session restored successfully"
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    // Removed println: "✅ CustomerLandingViewModel: Extracted userId from session: $userIdFromSession"
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession ?: return@launch.also {
                    // Removed println: "❌ CustomerLandingViewModel: Unable to determine user ID for subscription loading"
                }
                
                println("👤 CustomerLandingViewModel: Loading subscriptions for user: $userId")
                
                // Set loading state
                _uiState.value = _uiState.value.copy(isSubscriptionDataLoading = true)
                
                // CRITICAL FIX: First sync ALL subscription data (active + inactive) from Supabase to local database
                // This ensures local database stays in sync with Supabase
                println("🔄 CustomerLandingViewModel: Syncing all subscription data from Supabase to local database...")
                val syncResult = subscriptionService.syncSubscriptions(userId)
                syncResult.onSuccess { syncedSubscriptions ->
                    // Removed println: "✅ CustomerLandingViewModel: Synced ${syncedSubscriptions.size} total subscriptions from Supabase to local database"
                }.onFailure { syncError ->
                    // Removed println: "⚠️ CustomerLandingViewModel: Full sync failed: ${syncError.message}"
                }
                
                // Now get real-time active subscriptions for UI display
                val supabaseResult = subscriptionService.getUserSubscriptionsFromSupabase(userId)
                supabaseResult.onSuccess { supabaseSubscriptions ->
                    // Removed println: "✅ CustomerLandingViewModel: Loaded ${supabaseSubscriptions.size} active subscriptions from Supabase"
                    val subscribedIds = supabaseSubscriptions.map { it.wineryId }.toSet()
                    _uiState.value = _uiState.value.copy(
                        subscribedWineryIds = subscribedIds,
                        isSubscriptionDataLoading = false
                    )
                }.onFailure { supabaseError ->
                    // Removed println: "⚠️ CustomerLandingViewModel: Supabase active subscription fetch failed, falling back to local: ${supabaseError.message}"
                    
                    // Fallback to local data if Supabase fails
                    val localSubscriptions = subscriptionService.getUserSubscriptions(userId).firstOrNull() ?: emptyList()
                    println("💾 CustomerLandingViewModel: Loaded ${localSubscriptions.size} active subscriptions from local database")
                    val subscribedIds = localSubscriptions.map { it.wineryId }.toSet()
                    _uiState.value = _uiState.value.copy(
                        subscribedWineryIds = subscribedIds,
                        isSubscriptionDataLoading = false
                    )
                }
            } catch (e: Exception) {
                // Removed println: "❌ CustomerLandingViewModel: Error loading subscriptions: ${e.message}"
                e.printStackTrace()
                // Handle error silently for subscriptions - don't crash the app
                _uiState.value = _uiState.value.copy(isSubscriptionDataLoading = false)
            }
        }
    }
    
    fun toggleWinerySubscription(wineryId: String) {
        viewModelScope.launch {
            try {
                println("🔄 CustomerLandingViewModel: toggleWinerySubscription called")
                
                // Check for valid session first
                if (!authRepository.hasValidSession()) {
                    // Removed println: "❌ CustomerLandingViewModel: No valid session for subscription toggle"
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    // Removed println: "⚠️ CustomerLandingViewModel: No UserInfo available, attempting session restoration..."
                    authRepository.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                // Removed println: "✅ CustomerLandingViewModel: Session restored successfully"
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    // Removed println: "✅ CustomerLandingViewModel: Extracted userId from session: $userIdFromSession"
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession ?: return@launch.also {
                    // Removed println: "❌ CustomerLandingViewModel: Unable to determine user ID for subscription toggle"
                }
                println("👤 CustomerLandingViewModel: User ID: $userId")
                
                // Add loading state
                _uiState.value = _uiState.value.copy(
                    subscriptionLoadingIds = _uiState.value.subscriptionLoadingIds + wineryId
                )
                
                // CRITICAL: Check real-time subscription status from database, not UI state
                // UI state might be out of sync with actual database state
                println("🔍 CustomerLandingViewModel: Checking real-time subscription status for user $userId, winery $wineryId")
                val isCurrentlySubscribed = subscriptionService.isSubscribed(userId, wineryId)
                println("🔍 CustomerLandingViewModel: Real-time subscription check result: $isCurrentlySubscribed")
                
                if (isCurrentlySubscribed) {
                    val result = subscriptionService.unsubscribeFromWinery(userId, wineryId)
                    result.onSuccess {
                        // Removed println: "✅ CustomerLandingViewModel: Unsubscribe successful, refreshing subscription list"
                        // Refresh subscriptions from Supabase to ensure consistency
                        loadSubscriptions()
                    }.onFailure { error ->
                        // Removed println: "❌ CustomerLandingViewModel: Unsubscribe failed: ${error.message}"
                        error.printStackTrace()
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to unsubscribe: ${error.message}"
                        )
                    }
                } else {
                    println("🔄 CustomerLandingViewModel: Attempting to subscribe to winery $wineryId")
                    val result = subscriptionService.subscribeToWinery(userId, wineryId)
                    result.onSuccess {
                        // Removed println: "✅ CustomerLandingViewModel: Subscription successful, refreshing subscription list"
                        // Refresh subscriptions from Supabase to ensure consistency
                        loadSubscriptions()
                    }.onFailure { error ->
                        // Removed println: "❌ CustomerLandingViewModel: Subscription failed: ${error.message}"
                        error.printStackTrace()
                        
                        // Provide user-friendly error messages
                        val userFriendlyMessage = when {
                            error.message?.contains("unique constraint", ignoreCase = true) == true ||
                            error.message?.contains("Already subscribed", ignoreCase = true) == true -> {
                                "You are already subscribed to this winery"
                            }
                            error.message?.contains("winery_subscriptions_user_id_winery_id_key", ignoreCase = true) == true -> {
                                "You are already subscribed to this winery"
                            }
                            else -> "Failed to subscribe: ${error.message}"
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            errorMessage = userFriendlyMessage
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update subscription: ${e.message}"
                )
            } finally {
                // Remove loading state
                _uiState.value = _uiState.value.copy(
                    subscriptionLoadingIds = _uiState.value.subscriptionLoadingIds - wineryId
                )
            }
        }
    }
}