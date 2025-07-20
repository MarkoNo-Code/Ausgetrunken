package com.ausgetrunken.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.data.local.entities.WineEntity
import com.ausgetrunken.domain.model.NotificationResult
import com.ausgetrunken.domain.model.SubscriberInfo
import com.ausgetrunken.domain.usecase.GetLowStockWinesUseCase
import com.ausgetrunken.domain.usecase.GetWineyardSubscribersUseCase
import com.ausgetrunken.domain.usecase.SendNotificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationManagementViewModel(
    private val getLowStockWinesUseCase: GetLowStockWinesUseCase,
    private val getWineyardSubscribersUseCase: GetWineyardSubscribersUseCase,
    private val sendNotificationUseCase: SendNotificationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationManagementUiState())
    val uiState: StateFlow<NotificationManagementUiState> = _uiState.asStateFlow()

    private var currentWineyardId: String = ""

    fun loadWineyardData(wineyardId: String) {
        currentWineyardId = wineyardId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Load low stock wines
                val lowStockWines = getLowStockWinesUseCase(wineyardId)
                
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

                // Load subscriber information
                val subscriberInfo = getWineyardSubscribersUseCase(wineyardId)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lowStockWines = regularLowStockWines,
                    criticalStockWines = criticalStockWines,
                    subscriberCount = subscriberInfo.totalSubscribers,
                    lowStockSubscribers = subscriberInfo.lowStockSubscribers,
                    generalSubscribers = subscriberInfo.generalSubscribers
                )

            } catch (e: Exception) {
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
                val result = sendNotificationUseCase.sendLowStockNotificationsForWineyard(currentWineyardId)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent ${result.sentCount} low stock notifications"
                )

                // Reload data to update UI
                loadWineyardData(currentWineyardId)

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
                        wineyardId = currentWineyardId,
                        wine = wine
                    )
                    totalSent += result.sentCount
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent $totalSent critical stock alerts"
                )

                // Reload data to update UI
                loadWineyardData(currentWineyardId)

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
                val result = sendNotificationUseCase.sendLowStockNotification(
                    wineyardId = currentWineyardId,
                    wine = wine
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent notification for ${wine.name} to ${result.sentCount} subscribers"
                )

            } catch (e: Exception) {
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
                    wineyardId = currentWineyardId,
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
                val result = sendNotificationUseCase.sendCustomNotification(
                    wineyardId = currentWineyardId,
                    title = title,
                    message = message,
                    notificationType = type
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sent custom notification to ${result.sentCount} subscribers"
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

data class NotificationManagementUiState(
    val isLoading: Boolean = false,
    val lowStockWines: List<WineEntity> = emptyList(),
    val criticalStockWines: List<WineEntity> = emptyList(),
    val subscriberCount: Int = 0,
    val lowStockSubscribers: Int = 0,
    val generalSubscribers: Int = 0,
    val message: String? = null
)

