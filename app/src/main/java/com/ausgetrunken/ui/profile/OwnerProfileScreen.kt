package com.ausgetrunken.ui.profile

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ausgetrunken.R
import com.ausgetrunken.data.local.entities.WineryEntity
import com.ausgetrunken.ui.components.ImagePickerDialog
import com.ausgetrunken.ui.profile.components.AddWineryCard
import com.ausgetrunken.ui.profile.components.ProfileHeader
import com.ausgetrunken.ui.profile.components.WineryCard
import com.ausgetrunken.ui.theme.AusgetrunkenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProfileScreen(
    onNavigateToWineryDetail: (String) -> Unit,
    onNavigateToCreateWinery: () -> Unit,
    onNavigateToNotificationManagement: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogoutSuccess: () -> Unit,
    newWineryId: String? = null,
    updatedWineryId: String? = null
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
    val context = LocalContext.current
    
    // Image picker state
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Helper functions for image picker
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PROFILE_${timeStamp}_"
        val storageDir = File(context.getExternalFilesDir(null), "Pictures")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    fun copyImageToInternalStorage(sourceUri: Uri): String? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "profile_photo_${timeStamp}.jpg"
            val imagesDir = File(context.filesDir, "profile_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            
            val destFile = File(imagesDir, fileName)
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getCameraPermission(): String = android.Manifest.permission.CAMERA
    
    fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val internalPath = copyImageToInternalStorage(selectedUri)
            if (internalPath != null) {
                viewModel.updateProfilePicture(internalPath)
            }
        }
        showImagePickerDialog = false
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            photoUri?.let { uri ->
                val internalPath = copyImageToInternalStorage(uri)
                if (internalPath != null) {
                    viewModel.updateProfilePicture(internalPath)
                }
            }
        }
        showImagePickerDialog = false
    }
    
    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val imageFile = createImageFile()
            photoUri = FileProvider.getUriForFile(
                context,
                "com.ausgetrunken.fileprovider",
                imageFile
            )
            cameraLauncher.launch(photoUri!!)
        }
        showImagePickerDialog = false
    }
    
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        }
        showImagePickerDialog = false
    }
    
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
    
    // Handle new winery creation - add to UI and trigger animation
    LaunchedEffect(newWineryId) {
        newWineryId?.let {
            // Removed println: "ProfileScreen: Received newWineryId: $it"
            // Add the new winery to UI state directly (no refetch needed)
            viewModel.addNewWineryToUI(it)
            // Wait for animation to complete then clear the saved state
            delay(2000) // 2 seconds
        }
    }
    
    // Clear the updatedWineryId after animation completes
    LaunchedEffect(updatedWineryId) {
        updatedWineryId?.let {
            // Removed println: "ProfileScreen: Received updatedWineryId: $it"
            // Wait for animation to complete then clear the saved state
            delay(2000) // 2 seconds
        }
    }
    
    // Handle profile picture picker dialog trigger
    LaunchedEffect(uiState.showProfilePicturePicker) {
        if (uiState.showProfilePicturePicker) {
            showImagePickerDialog = true
            viewModel.hideProfilePicturePicker()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Burgundy gradient from very top of screen (full width)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary, // Same burgundy as add card
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), // Semi-transparent burgundy
                            Color.Transparent // Fade to transparent
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        // Main content without top bar
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background, // Use proper theme background
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
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            item {
                                ProfileHeader(
                                    userName = uiState.userName,
                                    userEmail = uiState.userEmail,
                                    profilePictureUrl = uiState.profilePictureUrl,
                                    onProfilePictureClick = { viewModel.showProfilePicturePicker() },
                                    onNotificationCenterClick = {
                                        // Get current user ID from ViewModel using coroutine scope
                                        coroutineScope.launch {
                                            viewModel.getCurrentOwnerId()?.let { ownerId ->
                                                onNavigateToNotificationManagement(ownerId)
                                            }
                                        }
                                    },
                                    onSettingsClick = onNavigateToSettings
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Winery List Title (only show if there are wineries)
                            if (uiState.wineries.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.your_wineries),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.winery_count_format, uiState.wineries.size, uiState.maxWineries),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            // Winery Cards
                            items(uiState.wineries) { winery ->
                                WineryCard(
                                    winery = winery,
                                    onWineryClick = onNavigateToWineryDetail,
                                    isNewlyAdded = newWineryId == winery.id,
                                    isUpdated = updatedWineryId == winery.id
                                )
                            }
                    
                            // Add Winery Card
                            if (uiState.canAddMoreWineries) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                item {
                                    AddWineryCard(
                                        onAddWineryClick = onNavigateToCreateWinery
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } // End Scaffold
        
        // Image picker dialog
        if (showImagePickerDialog) {
            ImagePickerDialog(
                onCameraClick = {
                    if (hasPermission(getCameraPermission())) {
                        val imageFile = createImageFile()
                        photoUri = FileProvider.getUriForFile(
                            context,
                            "com.ausgetrunken.fileprovider",
                            imageFile
                        )
                        cameraLauncher.launch(photoUri!!)
                        showImagePickerDialog = false
                    } else {
                        cameraPermissionLauncher.launch(getCameraPermission())
                    }
                },
                onGalleryClick = {
                    val storagePermission = getStoragePermission()
                    val hasStoragePermission = hasPermission(storagePermission)

                    if (hasStoragePermission) {
                        galleryLauncher.launch("image/*")
                        showImagePickerDialog = false
                    } else {
                        storagePermissionLauncher.launch(storagePermission)
                    }
                },
                onDismiss = {
                    showImagePickerDialog = false
                },
                title = "Profilbild auswählen",
                subtitle = "Wählen Sie eine Bildquelle für Ihr Profilbild"
            )
        }
        
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
                                text = "Refreshing wineries...",
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

// Hoisted component for easier previewing
@Composable
fun OwnerProfileContent(
    modifier: Modifier = Modifier,
    userName: String,
    userEmail: String,
    profilePictureUrl: String?,
    wineries: List<WineryEntity>,
    maxWineries: Int,
    canAddMoreWineries: Boolean,
    newWineryId: String? = null,
    updatedWineryId: String? = null,
    onProfilePictureClick: () -> Unit = {},
    onNotificationCenterClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onWineryClick: (String) -> Unit = {},
    onAddWineryClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            ProfileHeader(
                userName = userName,
                userEmail = userEmail,
                profilePictureUrl = profilePictureUrl,
                onProfilePictureClick = onProfilePictureClick,
                onNotificationCenterClick = onNotificationCenterClick,
                onSettingsClick = onSettingsClick
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Winery List Title (only show if there are wineries)
        if (wineries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.your_wineries),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.winery_count_format, wineries.size, maxWineries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Winery Cards
        items(wineries) { winery ->
            WineryCard(
                winery = winery,
                onWineryClick = onWineryClick,
                isNewlyAdded = newWineryId == winery.id,
                isUpdated = updatedWineryId == winery.id
            )
        }

        // Add Winery Card
        if (canAddMoreWineries) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                AddWineryCard(
                    onAddWineryClick = onAddWineryClick
                )
            }
        }
    }
}

// Preview parameter provider for different states
class OwnerProfilePreviewParameterProvider : PreviewParameterProvider<OwnerProfilePreviewData> {
    override val values = sequenceOf(
        OwnerProfilePreviewData.EmptyProfile,
        OwnerProfilePreviewData.SingleVineyard,
        OwnerProfilePreviewData.MultipleVineyards,
        OwnerProfilePreviewData.FullProfile
    )
}

data class OwnerProfilePreviewData(
    val userName: String,
    val userEmail: String,
    val profilePictureUrl: String?,
    val wineries: List<WineryEntity>,
    val maxWineries: Int,
    val canAddMoreWineries: Boolean
) {
    companion object {
        val EmptyProfile = OwnerProfilePreviewData(
            userName = "John Winemaker",
            userEmail = "john@winemaker.com",
            profilePictureUrl = null,
            wineries = emptyList(),
            maxWineries = 3,
            canAddMoreWineries = true
        )

        val SingleVineyard = OwnerProfilePreviewData(
            userName = "Maria Gonzalez",
            userEmail = "maria@vineyard.es",
            profilePictureUrl = null,
            wineries = listOf(
                WineryEntity(
                    id = "1",
                    name = "Sunset Valley Vineyard",
                    description = "A beautiful vineyard with stunning sunset views and premium wine production.",
                    address = "1234 Wine Country Rd, Napa Valley, CA",
                    latitude = 38.2975,
                    longitude = -122.4664,
                    ownerId = "owner1",
                    photos = listOf("https://example.com/vineyard1.jpg")
                )
            ),
            maxWineries = 3,
            canAddMoreWineries = true
        )

        val MultipleVineyards = OwnerProfilePreviewData(
            userName = "Francesco Rossi",
            userEmail = "francesco@tuscanwines.it",
            profilePictureUrl = null,
            wineries = listOf(
                WineryEntity(
                    id = "1",
                    name = "Tuscan Hills Winery",
                    description = "Traditional Tuscan winemaking in the heart of Chianti.",
                    address = "Via del Vino 123, Chianti, Tuscany, Italy",
                    latitude = 43.4643,
                    longitude = 11.2958,
                    ownerId = "owner1",
                    photos = listOf("https://example.com/tuscany1.jpg")
                ),
                WineryEntity(
                    id = "2",
                    name = "Mountain Peak Vineyard",
                    description = "High-altitude vineyard producing exceptional cool-climate wines.",
                    address = "Mountain View Rd, Alto Adige, Italy",
                    latitude = 46.4982,
                    longitude = 11.3548,
                    ownerId = "owner1",
                    photos = listOf("https://example.com/mountain1.jpg")
                )
            ),
            maxWineries = 3,
            canAddMoreWineries = true
        )

        val FullProfile = OwnerProfilePreviewData(
            userName = "Robert Mondavi",
            userEmail = "robert@mondavi.com",
            profilePictureUrl = null,
            wineries = listOf(
                WineryEntity(
                    id = "1",
                    name = "Mondavi Estate Winery",
                    description = "World-renowned winery producing exceptional Cabernet Sauvignon and Chardonnay.",
                    address = "7801 St Helena Hwy, Oakville, CA 94562",
                    latitude = 38.4331,
                    longitude = -122.4064,
                    ownerId = "owner1",
                    photos = listOf("https://example.com/mondavi1.jpg")
                ),
                WineryEntity(
                    id = "2",
                    name = "Reserve Vineyard",
                    description = "Premium vineyard dedicated to reserve wine production.",
                    address = "Reserve Rd, Napa Valley, CA",
                    latitude = 38.4431,
                    longitude = -122.4164,
                    ownerId = "owner1",
                    photos = listOf("https://example.com/reserve1.jpg")
                ),
                WineryEntity(
                    id = "3",
                    name = "Heritage Vineyard",
                    description = "Historic vineyard maintaining traditional winemaking methods.",
                    address = "Heritage Lane, Napa Valley, CA",
                    latitude = 38.4531,
                    longitude = -122.4264,
                    ownerId = "owner1",
                    photos = listOf("https://example.com/heritage1.jpg")
                )
            ),
            maxWineries = 3,
            canAddMoreWineries = false
        )
    }
}

// Previews
@Preview(name = "Empty Profile", showBackground = true)
@Composable
fun OwnerProfileContentPreview_Empty() {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            val data = OwnerProfilePreviewData.EmptyProfile
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

@Preview(name = "Single Vineyard", showBackground = true)
@Composable
fun OwnerProfileContentPreview_Single() {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            val data = OwnerProfilePreviewData.SingleVineyard
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

@Preview(name = "Multiple Vineyards", showBackground = true)
@Composable
fun OwnerProfileContentPreview_Multiple() {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            val data = OwnerProfilePreviewData.MultipleVineyards
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

@Preview(name = "Full Profile (Max Vineyards)", showBackground = true)
@Composable
fun OwnerProfileContentPreview_Full() {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            val data = OwnerProfilePreviewData.FullProfile
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

@Preview(name = "Parameterized Preview", showBackground = true)
@Composable
fun OwnerProfileContentPreview_Parameterized(
    @PreviewParameter(OwnerProfilePreviewParameterProvider::class) data: OwnerProfilePreviewData
) {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

// Dark theme previews
@Preview(name = "Empty Profile - Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OwnerProfileContentPreview_Empty_Dark() {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            val data = OwnerProfilePreviewData.EmptyProfile
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

@Preview(name = "Multiple Vineyards - Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OwnerProfileContentPreview_Multiple_Dark() {
    AusgetrunkenTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            val data = OwnerProfilePreviewData.MultipleVineyards
            OwnerProfileContent(
                userName = data.userName,
                userEmail = data.userEmail,
                profilePictureUrl = data.profilePictureUrl,
                wineries = data.wineries,
                maxWineries = data.maxWineries,
                canAddMoreWineries = data.canAddMoreWineries
            )
        }
    }
}

