package com.ausgetrunken.ui.navigation

sealed class Screen(val route: String) {

    companion object {
        const val GRAPH = "main_graph"
    }

    data object Splash : Screen("splash")
    data object Auth : Screen("auth")
    data object CustomerLanding : Screen("customer_landing")
    data object WineyardDetail : Screen("wineyard_detail/{wineyardId}") {
        fun createRoute(wineyardId: String) = "wineyard_detail/$wineyardId"
    }
    data object CustomerWineyardDetail : Screen("customer_wineyard_detail/{wineyardId}") {
        fun createRoute(wineyardId: String) = "customer_wineyard_detail/$wineyardId"
    }
    data object WineDetail : Screen("wine_detail/{wineId}") {
        fun createRoute(wineId: String) = "wine_detail/$wineId"
    }
    data object OwnerProfile : Screen("owner_profile")
    data object CustomerProfile : Screen("customer_profile")
    data object CreateWineyard : Screen("create_wineyard")
    data object AddWineyard : Screen("add_wineyard")
    data object AddWine : Screen("add_wine/{wineyardId}") {
        fun createRoute(wineyardId: String) = "add_wine/$wineyardId"
    }
    data object EditWine : Screen("edit_wine/{wineId}") {
        fun createRoute(wineId: String) = "edit_wine/$wineId"
    }
    data object NotificationManagement : Screen("notification_management/{ownerId}") {
        fun createRoute(ownerId: String) = "notification_management/$ownerId"
    }
    data object LocationPicker : Screen("location_picker/{wineyardId}") {
        fun createRoute(wineyardId: String) = "location_picker/$wineyardId"
    }
    data object Map : Screen("map")
}