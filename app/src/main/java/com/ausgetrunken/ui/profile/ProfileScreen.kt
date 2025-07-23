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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.min
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ausgetrunken.ui.common.DeleteAccountDialog
import com.ausgetrunken.ui.profile.components.AddWineyardCard
import com.ausgetrunken.ui.profile.components.ProfileHeader
import com.ausgetrunken.ui.profile.components.WineyardCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToWineyardDetail: (String) -> Unit,
    onNavigateToCreateWineyard: () -> Unit,
    onNavigateToNotificationManagement: (String) -> Unit,
    onLogoutSuccess: () -> Unit,
    newWineyardId: String? = null,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Handle manual refresh trigger
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refreshProfile()
        }
    }
    
    // End refresh when loading completes
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && isRefreshing) {
            delay(500) // Small delay to show refresh completed
            isRefreshing = false
        }
    }
    
    // Handle loading completion with finished state and smooth dismissal
    var showFinished by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !showFinished) {
            showFinished = true
            delay(1050) // Show "Finished" for 1.05 seconds
            isDismissing = true // Start smooth dismissal
            delay(400) // Wait for dismissal animation
            showFinished = false
            isDismissing = false
            // Refresh animation handled above
        }
    }
    
    // Reset states when starting new refresh
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            showFinished = false
            isDismissing = false
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
    
    // Clear the newWineyardId after animation completes
    LaunchedEffect(newWineyardId) {
        newWineyardId?.let {
            println("ProfileScreen: Received newWineyardId: $it")
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
            // Custom pull-to-refresh accordion
            PullToRefreshAccordion(
                isLoading = uiState.isLoading,
                showFinished = showFinished,
                isDismissing = isDismissing,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Content with pull-to-refresh (hide default indicator)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                indicator = { /* Hide default indicator */ }
            ) {
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
                            isNewlyAdded = newWineyardId == wineyard.id
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
                                
                                // Debug Button (Development only)
                                OutlinedButton(
                                    onClick = { viewModel.debugWineyardData() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("🐛 Debug Wineyards")
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // FCM Debug Button (Development only)
                                OutlinedButton(
                                    onClick = { viewModel.debugFCMToken() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Text("🔧 Debug FCM Token")
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
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
    isLoading: Boolean,
    showFinished: Boolean,
    isDismissing: Boolean,
    modifier: Modifier = Modifier
) {
    // Calculate the accordion height based on state
    val maxHeight = 60.dp
    
    val targetHeight = when {
        isDismissing -> 0.dp // Smooth dismissal to 0
        isLoading || showFinished -> maxHeight
        else -> 0.dp
    }
    
    // Animate height changes smoothly
    val accordionHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(
            durationMillis = if (isDismissing) 500 else 300,
            delayMillis = 0
        ),
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
                    showFinished -> Color(0xFF4CAF50) // Green when finished
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
                    showFinished -> {
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
                                text = "Profile refreshed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                    isLoading -> {
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
                }
            }
        }
    }
}

