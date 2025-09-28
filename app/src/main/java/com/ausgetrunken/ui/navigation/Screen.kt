package com.ausgetrunken.ui.navigation

sealed class Screen(val route: String) {

    companion object {
        const val GRAPH = "main_graph"
    }

    data object Splash : Screen("splash")
    data object Auth : Screen("auth")
    data object CustomerLanding : Screen("customer_landing")
    data object WineryDetail : Screen("winery_detail/{wineryId}") {
        fun createRoute(wineryId: String) = "winery_detail/$wineryId"
    }
    data object CustomerWineryDetail : Screen("customer_winery_detail/{wineryId}") {
        fun createRoute(wineryId: String) = "customer_winery_detail/$wineryId"
    }
    data object WineDetail : Screen("wine_detail/{wineId}") {
        fun createRoute(wineId: String) = "wine_detail/$wineId"
    }
    data object OwnerProfile : Screen("owner_profile")
    data object CustomerProfile : Screen("customer_profile")
    data object CreateWinery : Screen("create_winery")
    data object AddWinery : Screen("add_winery")
    data object AddWine : Screen("add_wine/{wineryId}") {
        fun createRoute(wineryId: String) = "add_wine/$wineryId"
    }
    data object EditWine : Screen("edit_wine/{wineId}") {
        fun createRoute(wineId: String) = "edit_wine/$wineId"
    }
    data object NotificationManagement : Screen("notification_management/{ownerId}") {
        fun createRoute(ownerId: String) = "notification_management/$ownerId"
    }
    data object LocationPicker : Screen("location_picker/{wineryId}") {
        fun createRoute(wineryId: String) = "location_picker/$wineryId"
    }
    data object AddWineryLocationPicker : Screen("add_winery_location_picker")
    data object Map : Screen("map")
    data object Settings : Screen("settings")
}