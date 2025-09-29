package com.ausgetrunken.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ausgetrunken.ui.auth.AuthScreen
import com.ausgetrunken.ui.customer.CustomerLandingScreen
import com.ausgetrunken.ui.customer.CustomerProfileScreen
import com.ausgetrunken.ui.customer.CustomerWineryDetailScreen
import com.ausgetrunken.ui.location.LocationPickerScreen
import com.ausgetrunken.ui.notifications.NotificationManagementScreen
import com.ausgetrunken.ui.profile.OwnerProfileScreen
import com.ausgetrunken.ui.settings.SettingsScreen
import com.ausgetrunken.ui.splash.SplashScreen
import com.ausgetrunken.ui.wines.AddWineScreen
import com.ausgetrunken.ui.wines.EditWineScreen
import com.ausgetrunken.ui.wines.WineDetailScreen
import com.ausgetrunken.ui.winery.AddWineryScreen
import com.ausgetrunken.ui.winery.WineryDetailScreen
import com.ausgetrunken.domain.logging.AusgetrunkenLogger

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
                onNavigateToVineyardList = {
                    navController.navigate(Screen.CustomerLanding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        // REMOVED: Consolidated LoginScreen and RegisterScreen into AuthScreen
        
        composable(Screen.Auth.route) {
            AuthScreen(
                onNavigateToVineyardList = {
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
                onWineryClick = { wineryId ->
                    navController.navigate(Screen.CustomerWineryDetail.createRoute(wineryId))
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
        
        composable(Screen.WineryDetail.route) { backStackEntry ->
            val wineryId = backStackEntry.arguments?.getString("wineryId") ?: ""
            WineryDetailScreen(
                wineryId = wineryId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateBackAfterSave = { updatedWineryId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("updatedWineryId", updatedWineryId)
                    navController.popBackStack()
                },
                onNavigateToAddWine = { wineryId ->
                    navController.navigate(Screen.AddWine.createRoute(wineryId))
                },
                onNavigateToEditWine = { wineId ->
                    navController.navigate(Screen.EditWine.createRoute(wineId))
                },
                onNavigateToWineDetail = { wineId ->
                    navController.navigate(Screen.WineDetail.createRoute(wineId))
                },
                onNavigateToCustomerView = {
                    navController.navigate(Screen.CustomerWineryDetail.createRoute(wineryId))
                },
                onNavigateToLocationPicker = { currentLat, currentLng ->
                    // Store current coordinates for the location picker
                    backStackEntry.savedStateHandle.set("current_lat", currentLat)
                    backStackEntry.savedStateHandle.set("current_lng", currentLng)
                    navController.navigate(Screen.LocationPicker.createRoute(wineryId))
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
        
        composable(Screen.CustomerWineryDetail.route) { backStackEntry ->
            val wineryId = backStackEntry.arguments?.getString("wineryId") ?: ""
            CustomerWineryDetailScreen(
                wineryId = wineryId,
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
                onNavigateToWineryDetail = { wineryId ->
                    navController.navigate(Screen.WineryDetail.createRoute(wineryId))
                },
                onNavigateToCreateWinery = {
                    navController.navigate(Screen.AddWinery.route)
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
                newWineryId = backStackEntry.savedStateHandle.get<String>("newWineryId"),
                updatedWineryId = backStackEntry.savedStateHandle.get<String>("updatedWineryId")
            )
        }
        
        composable(Screen.AddWinery.route) { backStackEntry ->
            // Get location result from savedStateHandle if available
            val locationResult = backStackEntry.savedStateHandle.get<Triple<Double, Double, String?>>("location_result")

            AddWineryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateBackWithSuccess = { wineryId ->
                    println("🔥 AusgetrunkenNavigation: SUCCESS CALLBACK TRIGGERED! wineryId: $wineryId")
                    navController.previousBackStackEntry?.savedStateHandle?.set("newWineryId", wineryId)
                    println("🔥 AusgetrunkenNavigation: About to call popBackStack()")
                    navController.popBackStack()
                    println("🔥 AusgetrunkenNavigation: popBackStack() called")
                },
                onNavigateToLocationPicker = { currentLat, currentLng ->
                    // Store current coordinates for location picker
                    navController.currentBackStackEntry?.savedStateHandle?.set("current_lat", currentLat)
                    navController.currentBackStackEntry?.savedStateHandle?.set("current_lng", currentLng)
                    navController.navigate(Screen.AddWineryLocationPicker.route)
                },
                locationResult = locationResult
            )
        }
        
        composable(Screen.CreateWinery.route) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Create Winery Screen - To be implemented")
            }
        }
        
        
        composable(Screen.AddWine.route) { backStackEntry ->
            val wineryId = backStackEntry.arguments?.getString("wineryId") ?: ""
            println("🔥 Navigation: AddWine with wineryId: $wineryId")
            AddWineScreen(
                wineryId = wineryId,
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
            val wineryId = backStackEntry.arguments?.getString("wineryId") ?: ""
            // Get current coordinates from previous backstack entry if available
            val currentLat = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lat") ?: 0.0
            val currentLng = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lng") ?: 0.0
            AusgetrunkenLogger.d("Navigation", "LocationPicker starting with: wineryId=$wineryId, lat=$currentLat, lng=$currentLng")

            LocationPickerScreen(
                wineryId = wineryId,
                initialLatitude = currentLat,
                initialLongitude = currentLng,
                onLocationSelected = { latitude, longitude, address ->
                    // CORRECT PATTERN: Set result in savedStateHandle and popBackStack()
                    AusgetrunkenLogger.d("Navigation", "Setting location result and navigating back: lat=$latitude, lng=$longitude, address=$address")
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("location_result", Triple(latitude, longitude, address))
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AddWineryLocationPicker.route) {
            // Get current coordinates from previous backstack entry if available
            val currentLat = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lat") ?: 0.0
            val currentLng = navController.previousBackStackEntry?.savedStateHandle?.get<Double>("current_lng") ?: 0.0
            AusgetrunkenLogger.d("Navigation", "AddWineryLocationPicker starting with: lat=$currentLat, lng=$currentLng")

            LocationPickerScreen(
                wineryId = "add_winery", // Placeholder ID for add winery flow
                initialLatitude = currentLat,
                initialLongitude = currentLng,
                onLocationSelected = { latitude, longitude, address ->
                    // CORRECT PATTERN: Set result in savedStateHandle and popBackStack()
                    AusgetrunkenLogger.d("Navigation", "Setting AddWinery location result and navigating back: lat=$latitude, lng=$longitude, address=$address")
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