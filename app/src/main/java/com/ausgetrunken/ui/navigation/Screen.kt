package com.ausgetrunken.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Auth : Screen("auth")
    object Home : Screen("home")
    object WineyardList : Screen("wineyard_list")
    object WineyardDetail : Screen("wineyard_detail/{wineyardId}") {
        fun createRoute(wineyardId: String) = "wineyard_detail/$wineyardId"
    }
    object WineDetail : Screen("wine_detail/{wineId}") {
        fun createRoute(wineId: String) = "wine_detail/$wineId"
    }
    object Profile : Screen("profile")
    object CreateWineyard : Screen("create_wineyard")
    object ManageWines : Screen("manage_wines")
    object Map : Screen("map")
}