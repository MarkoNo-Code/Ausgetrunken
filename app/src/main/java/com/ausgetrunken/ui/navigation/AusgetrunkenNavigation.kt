package com.ausgetrunken.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ausgetrunken.ui.auth.AuthScreen
import com.ausgetrunken.ui.profile.OwnerProfileScreen
import com.ausgetrunken.ui.splash.SplashScreen
import com.ausgetrunken.ui.wineyard.AddWineyardScreen
import com.ausgetrunken.ui.wineyard.WineyardDetailScreen
import com.ausgetrunken.ui.wines.WineDetailScreen
import com.ausgetrunken.ui.wines.AddWineScreen
import com.ausgetrunken.ui.wines.EditWineScreen
import com.ausgetrunken.ui.customer.CustomerLandingScreen
import com.ausgetrunken.ui.customer.CustomerProfileScreen
import com.ausgetrunken.ui.customer.CustomerWineyardDetailScreen
import com.ausgetrunken.ui.notifications.NotificationManagementScreen
import com.ausgetrunken.ui.wineyard.WineyardDetailViewModel
import androidx.compose.runtime.remember
import org.koin.androidx.compose.koinViewModel

@Composable
fun AusgetrunkenNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route // Start with Splash screen for proper loading
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        route = Screen.GRAPH,
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
                    navController.navigate(Screen.OwnerProfile.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToWineyardList = {
                    navController.navigate(Screen.CustomerLanding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        // REMOVED: Consolidated LoginScreen and RegisterScreen into AuthScreen
        
        composable(Screen.Auth.route) {
            AuthScreen(
                onNavigateToWineyardList = {
                    navController.navigate(Screen.CustomerLanding.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.OwnerProfile.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.CustomerLanding.route) {
            CustomerLandingScreen(
                onWineyardClick = { wineyardId ->
                    navController.navigate(Screen.CustomerWineyardDetail.createRoute(wineyardId))
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
                onNavigateBackAfterSave = { updatedWineyardId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("updatedWineyardId", updatedWineyardId)
                    navController.popBackStack()
                },
                onNavigateToAddWine = { wineyardId ->
                    navController.navigate(Screen.AddWine.createRoute(wineyardId))
                },
                onNavigateToEditWine = { wineId ->
                    navController.navigate(Screen.EditWine.createRoute(wineId))
                },
                onNavigateToWineDetail = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                },
                onNavigateToCustomerView = {
                    navController.navigate(Screen.CustomerWineyardDetail.createRoute(wineyardId))
                },
                addedWineId = backStackEntry.savedStateHandle.get<String>("addedWineId"),
                editedWineId = backStackEntry.savedStateHandle.get<String>("editedWineId")
            )
        }
        
        composable(Screen.CustomerWineyardDetail.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            CustomerWineyardDetailScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWineDetail = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                },
                onNavigateToMap = { latitude, longitude ->
                    // TODO: Implement map navigation with coordinates
                    navController.navigate(Screen.Map.route)
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
        
        composable(Screen.OwnerProfile.route) { backStackEntry ->
            OwnerProfileScreen(
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
                newWineyardId = backStackEntry.savedStateHandle.get<String>("newWineyardId"),
                updatedWineyardId = backStackEntry.savedStateHandle.get<String>("updatedWineyardId"),
                navController = navController
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
                onNavigateBackWithSuccess = { addedWineId ->
                    println("🔥 AddWine SUCCESS CALLBACK TRIGGERED with wineId: $addedWineId")
                    // Set the added wine ID for highlighting
                    navController.previousBackStackEntry?.savedStateHandle?.set("addedWineId", addedWineId)
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
                onNavigateBackWithSuccess = { editedWineId ->
                    // Set the edited wine ID for highlighting
                    navController.previousBackStackEntry?.savedStateHandle?.set("editedWineId", editedWineId)
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