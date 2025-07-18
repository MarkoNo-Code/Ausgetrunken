package com.ausgetrunken.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ausgetrunken.ui.auth.LoginScreen
import com.ausgetrunken.ui.auth.RegisterScreen
import com.ausgetrunken.ui.profile.ProfileScreen
import com.ausgetrunken.ui.splash.SplashScreen
import com.ausgetrunken.ui.wineyard.AddWineyardScreen
import com.ausgetrunken.ui.wineyard.WineyardDetailScreen
import com.ausgetrunken.ui.wines.ManageWinesScreen
import com.ausgetrunken.ui.wines.WineDetailScreen
import com.ausgetrunken.ui.wines.AddWineScreen
import com.ausgetrunken.ui.wines.EditWineScreen
import kotlinx.coroutines.launch

@Composable
fun AusgetrunkenNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToWineyardList = {
                    navController.navigate(Screen.WineyardList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
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
                    navController.navigate(Screen.ManageWines.createRoute(wineyardId))
                }
            )
        }
        
        composable(Screen.WineDetail.route) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId") ?: ""
            WineDetailScreen(
                wineId = wineId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { wineId ->
                    navController.navigate(Screen.EditWine.createRoute(wineId))
                }
            )
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
                    println("🔥 AusgetrunkenNavigation: SUCCESS CALLBACK TRIGGERED! wineyardId: $wineyardId")
                    navController.previousBackStackEntry?.savedStateHandle?.set("newWineyardId", wineyardId)
                    println("🔥 AusgetrunkenNavigation: About to call popBackStack()")
                    navController.popBackStack()
                    println("🔥 AusgetrunkenNavigation: popBackStack() called")
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
        
        composable(Screen.ManageWines.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            println("🔥 Navigation: ManageWines with wineyardId: $wineyardId")
            ManageWinesScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWineDetail = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                },
                onNavigateToAddWine = { wineyardId ->
                    navController.navigate(Screen.AddWine.createRoute(wineyardId))
                }
            )
        }
        
        composable(Screen.AddWine.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            println("🔥 Navigation: AddWine with wineyardId: $wineyardId")
            AddWineScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateBackWithSuccess = {
                    println("🔥 AddWine SUCCESS CALLBACK TRIGGERED!")
                    navController.popBackStack()
                    println("🔥 AddWine popBackStack() called")
                }
            )
        }
        
        composable(Screen.EditWine.route) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId") ?: ""
            EditWineScreen(
                wineId = wineId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateBackWithSuccess = {
                    // Navigate back to the wine detail screen
                    navController.popBackStack()
                }
            )
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