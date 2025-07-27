package com.ausgetrunken.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.ausgetrunken.R
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.compose.koinInject
import com.ausgetrunken.ui.common.DeleteAccountDialog
import com.ausgetrunken.ui.navigation.Screen
import com.ausgetrunken.ui.profile.components.AddWineyardCard
import com.ausgetrunken.ui.profile.components.ProfileHeader
import com.ausgetrunken.ui.profile.components.WineyardCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProfileScreen(
    onNavigateToWineyardDetail: (String) -> Unit,
    onNavigateToCreateWineyard: () -> Unit,
    onNavigateToNotificationManagement: (String) -> Unit,
    onLogoutSuccess: () -> Unit,
    newWineyardId: String? = null,
    updatedWineyardId: String? = null,
    navController: NavController
) {
    // Use shared ViewModel scope to persist data across navigation
    // Since OwnerProfileViewModel is a singleton in Koin, this will return the same instance
    // across all navigation destinations, maintaining state and avoiding reloads
    val viewModel: OwnerProfileViewModel = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    
    // Load data efficiently when screen appears (only if needed)
    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }
    
    // Handle manual refresh trigger
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refreshProfile()
        }
    }
    
    // State to maintain green color during dismissal
    var showingSuccess by remember { mutableStateOf(false) }
    
    // Handle refresh completion - simpler logic that properly dismisses
    LaunchedEffect(isRefreshing, uiState.isLoading) {
        if (isRefreshing && !uiState.isLoading) {
            // Show success briefly then dismiss
            showingSuccess = true
            delay(1000) // Show success for 1 second
            showingSuccess = false // Dismiss
            isRefreshing = false // Reset for next pull
        }
    }
    
    // Reset success state when starting new refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            showingSuccess = false
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    // Handle logout success
    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) {
            onLogoutSuccess()
        }
    }
    
    // Handle delete account success
    LaunchedEffect(uiState.deleteAccountSuccess) {
        if (uiState.deleteAccountSuccess) {
            onLogoutSuccess() // Navigate back to auth screen after successful deletion
        }
    }
    
    // Handle new wineyard creation - add to UI and trigger animation
    LaunchedEffect(newWineyardId) {
        newWineyardId?.let {
            println("ProfileScreen: Received newWineyardId: $it")
            // Add the new wineyard to UI state directly (no refetch needed)
            viewModel.addNewWineyardToUI(it)
            // Wait for animation to complete then clear the saved state
            kotlinx.coroutines.delay(2000) // 2 seconds
        }
    }
    
    // Clear the updatedWineyardId after animation completes
    LaunchedEffect(updatedWineyardId) {
        updatedWineyardId?.let {
            println("ProfileScreen: Received updatedWineyardId: $it")
            // Wait for animation to complete then clear the saved state
            kotlinx.coroutines.delay(2000) // 2 seconds
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Notification Center Button (only show if user has wineyards)
                    if (uiState.wineyards.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                // Find the wineyard with the most subscribers for notifications
                                coroutineScope.launch {
                                    val preferredWineyardId = viewModel.findWineyardWithMostSubscribers()
                                    if (preferredWineyardId != null) {
                                        onNavigateToNotificationManagement(preferredWineyardId)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notification Center",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { viewModel.logout() },
                        enabled = !uiState.isLoggingOut
                    ) {
                        if (uiState.isLoggingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Custom pull-to-refresh accordion that follows finger
            PullToRefreshAccordion(
                pullToRefreshState = pullToRefreshState,
                isLoading = uiState.isLoading,
                isRefreshing = isRefreshing,
                showingSuccess = showingSuccess,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Content with pull-to-refresh (hide default indicator)
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                indicator = { 
                    // Completely suppress the built-in indicator and any default UI
                    Box(modifier = Modifier.size(0.dp))
                }
            ) {
                if (uiState.isLoading && (uiState.userName.isEmpty() || uiState.userName == "Loading...")) {
                    // Show loading state only when initially loading
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Loading your profile...",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Please wait while we fetch your data",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            
                            // Emergency logout button in case loading fails
                            OutlinedButton(
                                onClick = { viewModel.logout() },
                                enabled = !uiState.isLoggingOut,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isLoggingOut) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Signing out...")
                                } else {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                } else if (uiState.errorMessage != null && (uiState.userName.isEmpty() || uiState.userName == "Unknown User" || uiState.userName == "Error")) {
                    // Show error state with logout option
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Failed to load profile",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = uiState.errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.refreshProfile() },
                                    enabled = !uiState.isLoading
                                ) {
                                    Text("Retry")
                                }
                                
                                Button(
                                    onClick = { viewModel.logout() },
                                    enabled = !uiState.isLoggingOut,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    if (uiState.isLoggingOut) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onError
                                        )
                                    } else {
                                        Text("Logout")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show profile content even if there are some errors, as long as we have user info
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    item {
                        ProfileHeader(
                            userName = uiState.userName,
                            userEmail = uiState.userEmail,
                            profilePictureUrl = uiState.profilePictureUrl,
                            wineyardCount = uiState.wineyards.size,
                            maxWineyards = uiState.maxWineyards,
                            onProfilePictureClick = { viewModel.showProfilePicturePicker() }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    
                    // Wineyard Cards
                    items(uiState.wineyards) { wineyard ->
                        WineyardCard(
                            wineyard = wineyard,
                            onWineyardClick = onNavigateToWineyardDetail,
                            isNewlyAdded = newWineyardId == wineyard.id,
                            isUpdated = updatedWineyardId == wineyard.id
                        )
                    }
                    
                    // Add Wineyard Card
                    if (uiState.canAddMoreWineyards) {
                        item {
                            AddWineyardCard(
                                onAddWineyardClick = onNavigateToCreateWineyard
                            )
                        }
                    }
                    
                    // Settings Section
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Settings",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                
                                // Delete Account Button
                                OutlinedButton(
                                    onClick = { viewModel.showDeleteAccountDialog() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    enabled = !uiState.isDeletingAccount
                                ) {
                                    if (uiState.isDeletingAccount) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Flagging Account...")
                                    } else {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Account",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Flag Account for Deletion")
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    
    // Delete Account Dialog
    if (uiState.showDeleteAccountDialog) {
        DeleteAccountDialog(
            onDismiss = { viewModel.hideDeleteAccountDialog() },
            onConfirm = { viewModel.deleteAccount() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshAccordion(
    pullToRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isLoading: Boolean,
    isRefreshing: Boolean,
    showingSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    // Calculate the accordion height based on pull progress and state
    val maxHeight = 60.dp
    val pullProgress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
    
    // Simplified state logic - let PullToRefreshState manage its own lifecycle
    val targetHeight = when {
        showingSuccess || (isRefreshing && !isLoading) -> maxHeight // Show success when refresh completes
        isRefreshing -> maxHeight // Show during refresh
        pullProgress > 0f -> (maxHeight * pullProgress) // Follow finger - works consistently
        else -> 0.dp
    }
    
    // Use immediate animation during pull for perfect finger following
    val accordionHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = if (pullProgress > 0f && !isRefreshing) {
            tween(durationMillis = 0) // Immediate response during pull
        } else {
            tween(durationMillis = 300) // Smooth for state changes
        },
        label = "accordionHeight"
    )
    
    // Only show if there's some height
    if (accordionHeight.value > 0) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(accordionHeight)
                .clipToBounds(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    showingSuccess -> Color(0xFF4CAF50) // Green only when explicitly showing success
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    showingSuccess -> {
                        // Show finished state
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.profile_refreshed),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                    isRefreshing || isLoading -> {
                        // Show loading indicator
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Refreshing wineyards...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    pullProgress > 0f -> {
                        // Show pull progress hint
                        val alpha = (pullProgress * 2f).coerceAtMost(1f)
                        Text(
                            text = if (pullProgress >= 0.8f) "Release to refresh" else "Pull to refresh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                    }
                }
            }
        }
    }
}

