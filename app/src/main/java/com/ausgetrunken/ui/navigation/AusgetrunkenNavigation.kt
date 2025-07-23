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
import com.ausgetrunken.ui.auth.AuthScreen
import com.ausgetrunken.ui.auth.LoginScreen
import com.ausgetrunken.ui.auth.RegisterScreen
import com.ausgetrunken.ui.profile.ProfileScreen
import com.ausgetrunken.ui.splash.SplashScreen
import com.ausgetrunken.ui.wineyard.AddWineyardScreen
import com.ausgetrunken.ui.wineyard.WineyardDetailScreen
import com.ausgetrunken.ui.wines.WineDetailScreen
import com.ausgetrunken.ui.wines.AddWineScreen
import com.ausgetrunken.ui.wines.EditWineScreen
import com.ausgetrunken.ui.customer.CustomerLandingScreen
import com.ausgetrunken.ui.customer.CustomerProfileScreen
import com.ausgetrunken.ui.notifications.NotificationManagementScreen
import kotlinx.coroutines.launch

@Composable
fun AusgetrunkenNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route, // Start with Splash screen for proper loading
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
                onNavigateToLogin = { errorMessage ->
                    println("🔍 Navigation: SplashScreen navigating to login with errorMessage: $errorMessage")
                    
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                    
                    // Pass error message to AuthScreen AFTER navigation
                    errorMessage?.let { 
                        println("🔍 Navigation: Setting flaggedAccountMessage in savedStateHandle AFTER navigation: $it")
                        navController.getBackStackEntry(Screen.Auth.route).savedStateHandle["flaggedAccountMessage"] = it
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
        
        composable(Screen.Login.route) { backStackEntry ->
            val emailFromRegister = backStackEntry.savedStateHandle.get<String>("emailFromRegister")
            // Clear the saved state after reading it
            backStackEntry.savedStateHandle.remove<String>("emailFromRegister")
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
                onNavigateToRegister = { email ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("emailForRegister", email)
                    navController.navigate(Screen.Register.route)
                },
                initialEmail = emailFromRegister
            )
        }
        
        composable(Screen.Register.route) { backStackEntry ->
            val emailFromLogin = navController.previousBackStackEntry?.savedStateHandle?.get<String>("emailForRegister")
            RegisterScreen(
                onNavigateToLogin = { emailFromRegister ->
                    // Pass the email back to login screen
                    emailFromRegister?.let { email ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("emailFromRegister", email)
                    }
                    navController.popBackStack()
                },
                initialEmail = emailFromLogin
            )
        }
        
        composable(Screen.Auth.route) {
            AuthScreen(
                onNavigateToWineyardList = {
                    navController.navigate(Screen.WineyardList.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
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
            CustomerLandingScreen(
                onWineyardClick = { wineyardId ->
                    navController.navigate(Screen.WineyardDetail.createRoute(wineyardId))
                },
                onWineClick = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.CustomerProfile.route)
                }
            )
        }
        
        composable(Screen.CustomerLanding.route) {
            CustomerLandingScreen(
                onWineyardClick = { wineyardId ->
                    navController.navigate(Screen.WineyardDetail.createRoute(wineyardId))
                },
                onWineClick = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.CustomerProfile.route)
                }
            )
        }
        
        composable(Screen.CustomerProfile.route) {
            CustomerProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.WineyardDetail.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            WineyardDetailScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddWine = { wineyardId ->
                    navController.navigate(Screen.AddWine.createRoute(wineyardId))
                },
                onNavigateToEditWine = { wineId ->
                    navController.navigate(Screen.EditWine.createRoute(wineId))
                },
                onNavigateToWineDetail = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                }
            )
        }
        
        composable(Screen.WineDetail.route) { backStackEntry ->
            val wineId = backStackEntry.arguments?.getString("wineId") ?: ""
            WineDetailScreen(
                wineId = wineId,
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateToNotificationManagement = { wineyardId ->
                    navController.navigate(Screen.NotificationManagement.createRoute(wineyardId))
                },
                onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
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
                },
                onNavigateToWineDetail = { wineId ->
                    println("🔥 AddWine navigating to wine detail: $wineId")
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
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
                },
                onNavigateToWineDetail = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                }
            )
        }
        
        composable(Screen.NotificationManagement.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            NotificationManagementScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() }
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