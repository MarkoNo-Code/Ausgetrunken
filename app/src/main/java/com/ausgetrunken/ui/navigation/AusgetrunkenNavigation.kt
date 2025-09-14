package com.ausgetrunken.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ausgetrunken.ui.location.LocationPickerScreen
import com.ausgetrunken.ui.settings.SettingsScreen
import com.ausgetrunken.ui.wineyard.WineyardDetailViewModel
import androidx.compose.runtime.remember
import org.koin.androidx.compose.koinViewModel

@Composable
fun AusgetrunkenNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route, // Start with Splash screen for proper loading
    resetToken: String? = null
) {
    // Navigate directly to AuthScreen when reset token is available
    LaunchedEffect(resetToken) {
        if (resetToken != null) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }
    
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
                },
                resetToken = resetToken
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
                onNavigateToLocationPicker = { currentLat, currentLng ->
                    // Store current coordinates for the location picker
                    backStackEntry.savedStateHandle.set("current_lat", currentLat)
                    backStackEntry.savedStateHandle.set("current_lng", currentLng)
                    navController.navigate(Screen.LocationPicker.createRoute(wineyardId))
                },
                onLocationProcessed = {
                    // PROPER CLEANUP: Remove result after successful processing
                    backStackEntry.savedStateHandle.remove<Triple<Double, Double, String?>>("location_result")
                },
                addedWineId = backStackEntry.savedStateHandle.get<String>("addedWineId"),
                editedWineId = backStackEntry.savedStateHandle.get<String>("editedWineId"),
                locationResult = backStackEntry.savedStateHandle.get<Triple<Double, Double, String?>>("location_result")
            )
        }
        
        composable(Screen.CustomerWineyardDetail.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            CustomerWineyardDetailScreen(
                wineyardId = wineyardId,
                onNavigateBack = { navController.popBackStack() },
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
        
        composable(Screen.OwnerProfile.route) { backStackEntry ->
            OwnerProfileScreen(
                onNavigateToWineyardDetail = { wineyardId ->
                    navController.navigate(Screen.WineyardDetail.createRoute(wineyardId))
                },
                onNavigateToCreateWineyard = {
                    navController.navigate(Screen.AddWineyard.route)
                },
                onNavigateToNotificationManagement = { ownerId ->
                    navController.navigate(Screen.NotificationManagement.createRoute(ownerId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
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
        
        composable(Screen.AddWineyard.route) { backStackEntry ->
            // Get location result from savedStateHandle if available
            val locationResult = backStackEntry.savedStateHandle.get<Triple<Double, Double, String?>>("location_result")

            AddWineyardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBackWithSuccess = { wineyardId ->
                    println("🔥 AusgetrunkenNavigation: SUCCESS CALLBACK TRIGGERED! wineyardId: $wineyardId")
                    navController.previousBackStackEntry?.savedStateHandle?.set("newWineyardId", wineyardId)
                    println("🔥 AusgetrunkenNavigation: About to call popBackStack()")
                    navController.popBackStack()
                    println("🔥 AusgetrunkenNavigation: popBackStack() called")
                },
                onNavigateToLocationPicker = { currentLat, currentLng ->
                    // Store current coordinates for location picker
                    navController.currentBackStackEntry?.savedStateHandle?.set("current_lat", currentLat)
                    navController.currentBackStackEntry?.savedStateHandle?.set("current_lng", currentLng)
                    navController.navigate(Screen.AddWineyardLocationPicker.route)
                },
                locationResult = locationResult
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
            val ownerId = backStackEntry.arguments?.getString("ownerId") ?: ""
            NotificationManagementScreen(
                ownerId = ownerId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.LocationPicker.route) { backStackEntry ->
            val wineyardId = backStackEntry.arguments?.getString("wineyardId") ?: ""
            // Get current coordinates from previous backstack entry if available
            val currentLat = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lat") ?: 0.0
            val currentLng = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lng") ?: 0.0
            android.util.Log.d("Navigation", "🗺️ LocationPicker starting with: wineyardId=$wineyardId, lat=$currentLat, lng=$currentLng")
            
            LocationPickerScreen(
                wineyardId = wineyardId,
                initialLatitude = currentLat,
                initialLongitude = currentLng,
                onLocationSelected = { latitude, longitude, address ->
                    // CORRECT PATTERN: Set result in savedStateHandle and popBackStack()
                    android.util.Log.d("Navigation", "🎯 Setting location result and navigating back: lat=$latitude, lng=$longitude, address=$address")
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("location_result", Triple(latitude, longitude, address))
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AddWineyardLocationPicker.route) {
            // Get current coordinates from previous backstack entry if available
            val currentLat = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lat") ?: 0.0
            val currentLng = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lng") ?: 0.0
            android.util.Log.d("Navigation", "🗺️ AddWineyardLocationPicker starting with: lat=$currentLat, lng=$currentLng")

            LocationPickerScreen(
                wineyardId = "add_wineyard", // Placeholder ID for add wineyard flow
                initialLatitude = currentLat,
                initialLongitude = currentLng,
                onLocationSelected = { latitude, longitude, address ->
                    // CORRECT PATTERN: Set result in savedStateHandle and popBackStack()
                    android.util.Log.d("Navigation", "🎯 Setting AddWineyard location result and navigating back: lat=$latitude, lng=$longitude, address=$address")
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("location_result", Triple(latitude, longitude, address))
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToNotificationManagement = { ownerId ->
                    navController.navigate(Screen.NotificationManagement.createRoute(ownerId))
                },
                onNavigateBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                navController = navController
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