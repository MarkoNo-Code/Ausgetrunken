package com.ausgetrunken.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ausgetrunken.ui.auth.LoginScreen
import com.ausgetrunken.ui.auth.RegisterScreen
import com.ausgetrunken.ui.profile.ProfileScreen
import com.ausgetrunken.ui.wineyard.AddWineyardScreen
import com.ausgetrunken.ui.wineyard.WineyardDetailScreen

@Composable
fun AusgetrunkenNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToWineyardList = {
                    navController.navigate(Screen.WineyardList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Auth.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Auth Screen - To be implemented")
            }
        }
        
        composable(Screen.Home.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Home Screen - To be implemented")
            }
        }
        
        composable(Screen.WineyardList.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Wineyard List Screen - To be implemented")
            }
        }
        
        composable(Screen.WineyardDetail.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            WineyardDetailScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWineManagement = { wineyardId ->
                    navController.navigate(Screen.ManageWines.route + "/$wineyardId")
                }
            )
        }
        
        composable(Screen.WineDetail.route) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId") ?: ""
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Wine Detail Screen - ID: $wineId")
            }
        }
        
        composable(Screen.Profile.route) { backStackEntry ->
            ProfileScreen(
                onNavigateToWineyardDetail = { wineyardId ->
                    navController.navigate(Screen.WineyardDetail.createRoute(wineyardId))
                },
                onNavigateToCreateWineyard = {
                    navController.navigate(Screen.AddWineyard.route)
                },
                newWineyardId = backStackEntry.savedStateHandle.get<String>("newWineyardId")
            )
        }
        
        composable(Screen.AddWineyard.route) {
            AddWineyardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBackWithSuccess = { wineyardId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("newWineyardId", wineyardId)
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.CreateWineyard.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Create Wineyard Screen - To be implemented")
            }
        }
        
        composable(Screen.ManageWines.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Manage Wines Screen - To be implemented")
            }
        }
        
        composable(Screen.Map.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Map Screen - To be implemented")
            }
        }
    }
}