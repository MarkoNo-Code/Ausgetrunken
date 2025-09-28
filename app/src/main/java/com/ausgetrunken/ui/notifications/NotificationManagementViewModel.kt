package com.ausgetrunken.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.model.NotificationResult
import com.ausgetrunken.domain.model.SubscriberInfo
import com.ausgetrunken.domain.usecase.GetLowStockWinesUseCase
import com.ausgetrunken.domain.usecase.GetLowStockWinesForOwnerUseCase
import com.ausgetrunken.domain.usecase.GetWinerySubscribersUseCase
import com.ausgetrunken.domain.usecase.SendNotificationUseCase
import com.ausgetrunken.domain.service.WineryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationManagementViewModel(
    private val getLowStockWinesUseCase: GetLowStockWinesUseCase,
    private val getLowStockWinesForOwnerUseCase: GetLowStockWinesForOwnerUseCase,
    private val getWinerySubscribersUseCase: GetWinerySubscribersUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val wineryService: WineryService
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
                
                // Load low stock wines from all owner wineries
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

                // Get subscriber information for each winery
                val winerySubscriberInfo = mutableListOf<WinerySubscriberInfo>()
                
                // Get all unique wineries from the low stock wines
                val wineryIds = (regularLowStockWines + criticalStockWines)
                    .map { it.wineryId }
                    .distinct()

                for (wineryId in wineryIds) {
                    try {
                        val subscriberInfo = getWinerySubscribersUseCase(wineryId)
                        
                        // Get the actual winery name
                        val wineryName = try {
                            val wineries = wineryService.getWinerysByOwnerRemoteFirst(ownerId)
                            wineries.find { it.id == wineryId }?.name ?: "Unknown Winery"
                        } catch (e: Exception) {
                            println("⚠️ NotificationManagementViewModel: Could not get winery name for $wineryId: ${e.message}")
                            "Winery $wineryId"
                        }
                        
                        winerySubscriberInfo.add(
                            WinerySubscriberInfo(
                                wineryId = wineryId,
                                wineryName = wineryName,
                                totalSubscribers = subscriberInfo.totalSubscribers,
                                lowStockSubscribers = subscriberInfo.lowStockSubscribers,
                                generalSubscribers = subscriberInfo.generalSubscribers
                            )
                        )
                        
                        println("✅ NotificationManagementViewModel: Winery '$wineryName' has ${subscriberInfo.totalSubscribers} total subscribers")
                    } catch (e: Exception) {
                        println("❌ NotificationManagementViewModel: Error getting subscribers for winery $wineryId: ${e.message}")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lowStockWines = regularLowStockWines,
                    criticalStockWines = criticalStockWines,
                    winerySubscriberInfo = winerySubscriberInfo
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
                // Send notifications for all low stock wines across all owner wineries
                var totalSent = 0
                val allLowStockWines = _uiState.value.lowStockWines + _uiState.value.criticalStockWines
                
                // Group wines by winery and send notifications for each winery
                val winesByWinery = allLowStockWines.groupBy { it.wineryId }
                
                for ((wineryId, wines) in winesByWinery) {
                    for (wine in wines) {
                        val result = sendNotificationUseCase.sendLowStockNotification(
                            wineryId = wineryId,
                            wine = wine
                        )
                        totalSent += result.sentCount
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent $totalSent low stock notifications across all wineries"
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
                        wineryId = wine.wineryId,
                        wine = wine
                    )
                    totalSent += result.sentCount
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent $totalSent critical stock alerts across all wineries"
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
                println("🔍 NotificationViewModel: Wine Winery ID: ${wine.wineryId}")
                println("🔍 NotificationViewModel: Current Owner ID: $currentOwnerId")
                
                val result = sendNotificationUseCase.sendLowStockNotification(
                    wineryId = wine.wineryId,
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
                    wineryId = wine.wineryId,
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
                // TODO: Custom notifications for owner-wide view need UI to select specific winery
                // For now, disable this functionality as it doesn't work with multiple wineries
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

data class WinerySubscriberInfo(
    val wineryId: String,
    val wineryName: String,
    val totalSubscribers: Int,
    val lowStockSubscribers: Int,
    val generalSubscribers: Int
)

data class NotificationManagementUiState(
    val isLoading: Boolean = false,
    val lowStockWines: List<WineEntity> = emptyList(),
    val criticalStockWines: List<WineEntity> = emptyList(),
    val winerySubscriberInfo: List<WinerySubscriberInfo> = emptyList(),
    val message: String? = null
) {
    // Computed properties for backward compatibility and aggregate stats
    val subscriberCount: Int get() = winerySubscriberInfo.sumOf { it.totalSubscribers }
    val lowStockSubscribers: Int get() = winerySubscriberInfo.sumOf { it.lowStockSubscribers }
    val generalSubscribers: Int get() = winerySubscriberInfo.sumOf { it.generalSubscribers }
}

