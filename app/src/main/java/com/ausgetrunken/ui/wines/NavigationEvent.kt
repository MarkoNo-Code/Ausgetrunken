package com.ausgetrunken.ui.wines

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
    data class NavigateBackWithWineId(val wineId: String) : NavigationEvent()
    data class NavigateToWineDetail(val wineId: String) : NavigationEvent()
}