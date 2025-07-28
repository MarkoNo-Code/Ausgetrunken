package com.ausgetrunken.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.model.NotificationResult
import com.ausgetrunken.domain.model.SubscriberInfo
import com.ausgetrunken.domain.usecase.GetLowStockWinesUseCase
import com.ausgetrunken.domain.usecase.GetLowStockWinesForOwnerUseCase
import com.ausgetrunken.domain.usecase.GetWineyardSubscribersUseCase
import com.ausgetrunken.domain.usecase.SendNotificationUseCase
import com.ausgetrunken.domain.service.WineyardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationManagementViewModel(
    private val getLowStockWinesUseCase: GetLowStockWinesUseCase,
    private val getLowStockWinesForOwnerUseCase: GetLowStockWinesForOwnerUseCase,
    private val getWineyardSubscribersUseCase: GetWineyardSubscribersUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val wineyardService: WineyardService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationManagementUiState())
    val uiState: StateFlow<NotificationManagementUiState> = _uiState.asStateFlow()

    private var currentOwnerId: String = ""

    fun loadOwnerData(ownerId: String) {
        currentOwnerId = ownerId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                println("🔍 NotificationManagementViewModel: Loading data for owner: $ownerId")
                
                // Load low stock wines from all owner wineyards
                val lowStockWines = getLowStockWinesForOwnerUseCase(ownerId)
                
                // Separate critical stock wines (20% or less)
                val criticalStockWines = lowStockWines.filter { wine ->
                    wine.fullStockQuantity > 0 && 
                    (wine.stockQuantity.toFloat() / wine.fullStockQuantity.toFloat()) <= 0.20f
                }
                
                val regularLowStockWines = lowStockWines.filter { wine ->
                    val percentage = if (wine.fullStockQuantity > 0) {
                        wine.stockQuantity.toFloat() / wine.fullStockQuantity.toFloat()
                    } else 1.0f
                    percentage > 0.20f || wine.fullStockQuantity == 0
                }

                // Get subscriber information for each wineyard
                val wineyardSubscriberInfo = mutableListOf<WineyardSubscriberInfo>()
                
                // Get all unique wineyards from the low stock wines
                val wineyardIds = (regularLowStockWines + criticalStockWines)
                    .map { it.wineyardId }
                    .distinct()
                
                for (wineyardId in wineyardIds) {
                    try {
                        val subscriberInfo = getWineyardSubscribersUseCase(wineyardId)
                        
                        // Get the actual wineyard name
                        val wineyardName = try {
                            val wineyards = wineyardService.getWineyardsByOwnerRemoteFirst(ownerId)
                            wineyards.find { it.id == wineyardId }?.name ?: "Unknown Wineyard"
                        } catch (e: Exception) {
                            println("⚠️ NotificationManagementViewModel: Could not get wineyard name for $wineyardId: ${e.message}")
                            "Wineyard $wineyardId"
                        }
                        
                        wineyardSubscriberInfo.add(
                            WineyardSubscriberInfo(
                                wineyardId = wineyardId,
                                wineyardName = wineyardName,
                                totalSubscribers = subscriberInfo.totalSubscribers,
                                lowStockSubscribers = subscriberInfo.lowStockSubscribers,
                                generalSubscribers = subscriberInfo.generalSubscribers
                            )
                        )
                        
                        println("✅ NotificationManagementViewModel: Wineyard '$wineyardName' has ${subscriberInfo.totalSubscribers} total subscribers")
                    } catch (e: Exception) {
                        println("❌ NotificationManagementViewModel: Error getting subscribers for wineyard $wineyardId: ${e.message}")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lowStockWines = regularLowStockWines,
                    criticalStockWines = criticalStockWines,
                    wineyardSubscriberInfo = wineyardSubscriberInfo
                )
                
                println("✅ NotificationManagementViewModel: Loaded ${regularLowStockWines.size} regular low stock and ${criticalStockWines.size} critical stock wines for owner")

            } catch (e: Exception) {
                println("❌ NotificationManagementViewModel: Error loading owner data: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun sendAllLowStockNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Send notifications for all low stock wines across all owner wineyards
                var totalSent = 0
                val allLowStockWines = _uiState.value.lowStockWines + _uiState.value.criticalStockWines
                
                // Group wines by wineyard and send notifications for each wineyard
                val winesByWineyard = allLowStockWines.groupBy { it.wineyardId }
                
                for ((wineyardId, wines) in winesByWineyard) {
                    for (wine in wines) {
                        val result = sendNotificationUseCase.sendLowStockNotification(
                            wineyardId = wineyardId,
                            wine = wine
                        )
                        totalSent += result.sentCount
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent $totalSent low stock notifications across all wineyards"
                )

                // Reload data to update UI
                if (currentOwnerId.isNotEmpty()) {
                    loadOwnerData(currentOwnerId)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to send notifications: ${e.message}"
                )
            }
        }
    }

    fun sendAllCriticalStockNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                var totalSent = 0
                val criticalWines = _uiState.value.criticalStockWines

                for (wine in criticalWines) {
                    val result = sendNotificationUseCase.sendCriticalStockNotification(
                        wineyardId = wine.wineyardId,
                        wine = wine
                    )
                    totalSent += result.sentCount
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent $totalSent critical stock alerts across all wineyards"
                )

                // Reload data to update UI
                if (currentOwnerId.isNotEmpty()) {
                    loadOwnerData(currentOwnerId)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to send critical alerts: ${e.message}"
                )
            }
        }
    }

    fun sendSpecificWineNotification(wine: WineEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                println("🔍 NotificationViewModel: Sending notification for wine: ${wine.name}")
                println("🔍 NotificationViewModel: Wine ID: ${wine.id}")
                println("🔍 NotificationViewModel: Wine Wineyard ID: ${wine.wineyardId}")
                println("🔍 NotificationViewModel: Current Owner ID: $currentOwnerId")
                
                val result = sendNotificationUseCase.sendLowStockNotification(
                    wineyardId = wine.wineyardId,
                    wine = wine
                )

                println("🔍 NotificationViewModel: Notification result - Success: ${result.success}, Sent: ${result.sentCount}")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent notification for ${wine.name} to ${result.sentCount} subscribers"
                )

            } catch (e: Exception) {
                println("❌ NotificationViewModel: Exception sending notification: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to send notification: ${e.message}"
                )
            }
        }
    }

    fun sendCriticalWineNotification(wine: WineEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val result = sendNotificationUseCase.sendCriticalStockNotification(
                    wineyardId = wine.wineyardId,
                    wine = wine
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent critical alert for ${wine.name} to ${result.sentCount} subscribers"
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to send critical alert: ${e.message}"
                )
            }
        }
    }

    fun sendCustomNotification(title: String, message: String, type: NotificationType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // TODO: Custom notifications for owner-wide view need UI to select specific wineyard
                // For now, disable this functionality as it doesn't work with multiple wineyards
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Custom notifications not available in owner view. Please use individual wine notifications."
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Failed to send custom notification: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class WineyardSubscriberInfo(
    val wineyardId: String,
    val wineyardName: String,
    val totalSubscribers: Int,
    val lowStockSubscribers: Int,
    val generalSubscribers: Int
)

data class NotificationManagementUiState(
    val isLoading: Boolean = false,
    val lowStockWines: List<WineEntity> = emptyList(),
    val criticalStockWines: List<WineEntity> = emptyList(),
    val wineyardSubscriberInfo: List<WineyardSubscriberInfo> = emptyList(),
    val message: String? = null
) {
    // Computed properties for backward compatibility and aggregate stats
    val subscriberCount: Int get() = wineyardSubscriberInfo.sumOf { it.totalSubscribers }
    val lowStockSubscribers: Int get() = wineyardSubscriberInfo.sumOf { it.lowStockSubscribers }
    val generalSubscribers: Int get() = wineyardSubscriberInfo.sumOf { it.generalSubscribers }
}

