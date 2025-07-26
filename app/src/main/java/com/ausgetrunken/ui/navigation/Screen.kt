package com.ausgetrunken.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Auth : Screen("auth")
    object Home : Screen("home")
    object WineyardList : Screen("wineyard_list")
    object CustomerLanding : Screen("customer_landing")
    object WineyardDetail : Screen("wineyard_detail/{wineyardId}") {
        fun createRoute(wineyardId: String) = "wineyard_detail/$wineyardId"
    }
    object CustomerWineyardDetail : Screen("customer_wineyard_detail/{wineyardId}") {
        fun createRoute(wineyardId: String) = "customer_wineyard_detail/$wineyardId"
    }
    object WineDetail : Screen("wine_detail/{wineId}") {
        fun createRoute(wineId: String) = "wine_detail/$wineId"
    }
    object Profile : Screen("profile")
    object CustomerProfile : Screen("customer_profile")
    object CreateWineyard : Screen("create_wineyard")
    object AddWineyard : Screen("add_wineyard")
    object AddWine : Screen("add_wine/{wineyardId}") {
        fun createRoute(wineyardId: String) = "add_wine/$wineyardId"
    }
    object EditWine : Screen("edit_wine/{wineId}") {
        fun createRoute(wineId: String) = "edit_wine/$wineId"
    }
    object NotificationManagement : Screen("notification_management/{wineyardId}") {
        fun createRoute(wineyardId: String) = "notification_management/$wineyardId"
    }
    object Map : Screen("map")
}