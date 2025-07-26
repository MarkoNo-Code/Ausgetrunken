package com.ausgetrunken.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import com.ausgetrunken.domain.util.RemoteFirstTestUtils
import com.ausgetrunken.auth.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class CustomerLandingViewModel(
    private val wineyardService: WineyardService,
    private val wineService: WineService,
    private val subscriptionService: WineyardSubscriptionService,
    private val authRepository: SupabaseAuthRepository,
    private val remoteFirstTestUtils: RemoteFirstTestUtils
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CustomerLandingUiState())
    val uiState: StateFlow<CustomerLandingUiState> = _uiState.asStateFlow()
    
    companion object {
        private const val ITEMS_PER_PAGE = 5
    }
    
    init {
        loadWineyards()
        loadSubscriptions()
    }
    
    fun switchTab(tab: CustomerTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
        when (tab) {
            CustomerTab.WINEYARDS -> {
                if (_uiState.value.wineyards.isEmpty()) {
                    loadWineyards()
                }
            }
            CustomerTab.WINES -> {
                if (_uiState.value.wines.isEmpty()) {
                    loadWines()
                }
            }
        }
    }
    
    fun loadWineyards(page: Int = 0) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                // Check if we need to sync from Supabase first
                if (page == 0) {
                    val localWineyards = wineyardService.getAllWineyardsPaginated(1, 0)
                    if (localWineyards.isEmpty()) {
                        // Sync from Supabase if local database is empty
                        wineyardService.syncWineyards()
                    }
                }
                
                val offset = page * ITEMS_PER_PAGE
                val wineyards = wineyardService.getAllWineyardsPaginated(ITEMS_PER_PAGE, offset)
                
                val hasMore = wineyards.size == ITEMS_PER_PAGE
                
                if (page == 0) {
                    // First page - replace all wineyards
                    _uiState.value = _uiState.value.copy(
                        wineyards = wineyards,
                        currentWineyardPage = page,
                        hasMoreWineyards = hasMore,
                        isLoading = false
                    )
                } else {
                    // Additional pages - append to existing wineyards
                    _uiState.value = _uiState.value.copy(
                        wineyards = _uiState.value.wineyards + wineyards,
                        currentWineyardPage = page,
                        hasMoreWineyards = hasMore,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load wineyards: ${e.message}"
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
            CustomerTab.WINEYARDS -> {
                if (_uiState.value.hasMoreWineyards && !_uiState.value.isLoading) {
                    loadWineyards(_uiState.value.currentWineyardPage + 1)
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
            CustomerTab.WINEYARDS -> {
                loadWineyards(0)
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
                    println("❌ CustomerLandingViewModel: No valid session for subscription loading")
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    println("⚠️ CustomerLandingViewModel: No UserInfo available, attempting session restoration...")
                    authRepository.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                println("✅ CustomerLandingViewModel: Session restored successfully")
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    println("✅ CustomerLandingViewModel: Extracted userId from session: $userIdFromSession")
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession ?: return@launch.also {
                    println("❌ CustomerLandingViewModel: Unable to determine user ID for subscription loading")
                }
                
                println("👤 CustomerLandingViewModel: Loading subscriptions for user: $userId")
                
                // Set loading state
                _uiState.value = _uiState.value.copy(isSubscriptionDataLoading = true)
                
                // CRITICAL FIX: First sync ALL subscription data (active + inactive) from Supabase to local database
                // This ensures local database stays in sync with Supabase
                println("🔄 CustomerLandingViewModel: Syncing all subscription data from Supabase to local database...")
                val syncResult = subscriptionService.syncSubscriptions(userId)
                syncResult.onSuccess { syncedSubscriptions ->
                    println("✅ CustomerLandingViewModel: Synced ${syncedSubscriptions.size} total subscriptions from Supabase to local database")
                }.onFailure { syncError ->
                    println("⚠️ CustomerLandingViewModel: Full sync failed: ${syncError.message}")
                }
                
                // Now get real-time active subscriptions for UI display
                val supabaseResult = subscriptionService.getUserSubscriptionsFromSupabase(userId)
                supabaseResult.onSuccess { supabaseSubscriptions ->
                    println("✅ CustomerLandingViewModel: Loaded ${supabaseSubscriptions.size} active subscriptions from Supabase")
                    val subscribedIds = supabaseSubscriptions.map { it.wineyardId }.toSet()
                    _uiState.value = _uiState.value.copy(
                        subscribedWineyardIds = subscribedIds,
                        isSubscriptionDataLoading = false
                    )
                }.onFailure { supabaseError ->
                    println("⚠️ CustomerLandingViewModel: Supabase active subscription fetch failed, falling back to local: ${supabaseError.message}")
                    
                    // Fallback to local data if Supabase fails
                    val localSubscriptions = subscriptionService.getUserSubscriptions(userId).firstOrNull() ?: emptyList()
                    println("💾 CustomerLandingViewModel: Loaded ${localSubscriptions.size} active subscriptions from local database")
                    val subscribedIds = localSubscriptions.map { it.wineyardId }.toSet()
                    _uiState.value = _uiState.value.copy(
                        subscribedWineyardIds = subscribedIds,
                        isSubscriptionDataLoading = false
                    )
                }
            } catch (e: Exception) {
                println("❌ CustomerLandingViewModel: Error loading subscriptions: ${e.message}")
                e.printStackTrace()
                // Handle error silently for subscriptions - don't crash the app
                _uiState.value = _uiState.value.copy(isSubscriptionDataLoading = false)
            }
        }
    }
    
    fun toggleWineyardSubscription(wineyardId: String) {
        viewModelScope.launch {
            try {
                println("🔄 CustomerLandingViewModel: toggleWineyardSubscription called")
                
                // Check for valid session first
                if (!authRepository.hasValidSession()) {
                    println("❌ CustomerLandingViewModel: No valid session for subscription toggle")
                    return@launch
                }
                
                // Try to get current user, fallback to session restoration
                var currentUser = authRepository.currentUser
                var userIdFromSession: String? = null
                
                if (currentUser == null) {
                    println("⚠️ CustomerLandingViewModel: No UserInfo available, attempting session restoration...")
                    authRepository.restoreSession()
                        .onSuccess { user ->
                            if (user != null) {
                                currentUser = user
                                println("✅ CustomerLandingViewModel: Session restored successfully")
                            }
                        }
                        .onFailure { error ->
                            val errorMessage = error.message ?: ""
                            if (errorMessage.startsWith("VALID_SESSION_NO_USER:")) {
                                val parts = errorMessage.removePrefix("VALID_SESSION_NO_USER:").split(":")
                                if (parts.size >= 2) {
                                    userIdFromSession = parts[0]
                                    println("✅ CustomerLandingViewModel: Extracted userId from session: $userIdFromSession")
                                }
                            }
                        }
                }
                
                val userId = currentUser?.id ?: userIdFromSession ?: return@launch.also {
                    println("❌ CustomerLandingViewModel: Unable to determine user ID for subscription toggle")
                }
                println("👤 CustomerLandingViewModel: User ID: $userId")
                
                // Add loading state
                _uiState.value = _uiState.value.copy(
                    subscriptionLoadingIds = _uiState.value.subscriptionLoadingIds + wineyardId
                )
                
                // CRITICAL: Check real-time subscription status from database, not UI state
                // UI state might be out of sync with actual database state
                println("🔍 CustomerLandingViewModel: Checking real-time subscription status for user $userId, wineyard $wineyardId")
                val isCurrentlySubscribed = subscriptionService.isSubscribed(userId, wineyardId)
                println("🔍 CustomerLandingViewModel: Real-time subscription check result: $isCurrentlySubscribed")
                
                if (isCurrentlySubscribed) {
                    val result = subscriptionService.unsubscribeFromWineyard(userId, wineyardId)
                    result.onSuccess {
                        println("✅ CustomerLandingViewModel: Unsubscribe successful, refreshing subscription list")
                        // Refresh subscriptions from Supabase to ensure consistency
                        loadSubscriptions()
                    }.onFailure { error ->
                        println("❌ CustomerLandingViewModel: Unsubscribe failed: ${error.message}")
                        error.printStackTrace()
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Failed to unsubscribe: ${error.message}"
                        )
                    }
                } else {
                    println("🔄 CustomerLandingViewModel: Attempting to subscribe to wineyard $wineyardId")
                    val result = subscriptionService.subscribeToWineyard(userId, wineyardId)
                    result.onSuccess {
                        println("✅ CustomerLandingViewModel: Subscription successful, refreshing subscription list")
                        // Refresh subscriptions from Supabase to ensure consistency
                        loadSubscriptions()
                    }.onFailure { error ->
                        println("❌ CustomerLandingViewModel: Subscription failed: ${error.message}")
                        error.printStackTrace()
                        
                        // Provide user-friendly error messages
                        val userFriendlyMessage = when {
                            error.message?.contains("unique constraint", ignoreCase = true) == true ||
                            error.message?.contains("Already subscribed", ignoreCase = true) == true -> {
                                "You are already subscribed to this wineyard"
                            }
                            error.message?.contains("wineyard_subscriptions_user_id_wineyard_id_key", ignoreCase = true) == true -> {
                                "You are already subscribed to this wineyard"
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
                    subscriptionLoadingIds = _uiState.value.subscriptionLoadingIds - wineyardId
                )
            }
        }
    }
    
    // ================================================================================================
    // REMOTE-FIRST DATA STRATEGY TESTING
    // ================================================================================================
    
    /**
     * Test the new remote-first data strategy for wineyards
     * This will show detailed logs of the process
     */
    fun testRemoteFirstDataStrategy() {
        viewModelScope.launch {
            try {
                println("🚀 CustomerLandingViewModel: Starting remote-first test...")
                remoteFirstTestUtils.runAllTests()
                println("✅ CustomerLandingViewModel: Remote-first test completed successfully!")
                
                // Optionally refresh the UI with the new data
                refreshData()
                
            } catch (e: Exception) {
                println("❌ CustomerLandingViewModel: Remote-first test failed: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Remote-first test failed: ${e.message}"
                )
            }
        }
    }
}