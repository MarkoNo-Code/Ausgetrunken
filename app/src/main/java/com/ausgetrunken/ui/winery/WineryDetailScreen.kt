package com.ausgetrunken.ui.winery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ausgetrunken.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import com.ausgetrunken.data.local.entities.WineEntity
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ausgetrunken.ui.components.ImagePickerDialog
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import coil.compose.SubcomposeAsyncImage
import com.ausgetrunken.ui.components.WineryMapComponent
import com.ausgetrunken.ui.components.WineryMapPlaceholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WineryDetailScreen(
    wineryId: String,
    onNavigateBack: () -> Unit,
    onNavigateBackAfterSave: (String) -> Unit = { onNavigateBack() },
    onNavigateToAddWine: (String) -> Unit,
    onNavigateToEditWine: (String) -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    onNavigateToCustomerView: () -> Unit = {},
    onNavigateToLocationPicker: (Double, Double) -> Unit = { _, _ -> },
    onLocationProcessed: () -> Unit = {},
    addedWineId: String? = null,
    editedWineId: String? = null,
    locationResult: Triple<Double, Double, String?>? = null,
    viewModel: WineryDetailViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    var showingSuccess by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Image picker state
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Delete confirmation state
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Create file for camera capture
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = File(context.getExternalFilesDir(null), "Pictures")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }
    
    // Copy image to app internal storage for persistence
    fun copyImageToInternalStorage(sourceUri: Uri): String? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "winery_photo_${timeStamp}.jpg"
            val imagesDir = File(context.filesDir, "winery_images")
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
    
    // Permission checking helper functions
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
    
    fun checkAndLogPermissions() {
        val cameraPermission = getCameraPermission()
        val storagePermission = getStoragePermission()
    }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            
            // Try two approaches: first copy to internal storage, then try using original URI as backup
            val internalPath = copyImageToInternalStorage(selectedUri)
            
            if (internalPath != null) {
                
                // Verify file exists
                val file = File(internalPath)
                
                if (file.exists() && file.length() > 0) {
                    viewModel.addPhoto(internalPath)
                } else {
                    viewModel.addPhoto(selectedUri.toString())
                }
            } else {
                viewModel.addPhoto(selectedUri.toString())
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
                
                // Copy image to internal storage for persistence
                val internalPath = copyImageToInternalStorage(uri)
                if (internalPath != null) {
                    viewModel.addPhoto(internalPath)
                } else {
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
    
    // Handle manual refresh trigger
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refreshData()
        }
    }
    
    // Simplified refresh completion logic that doesn't interfere with PullToRefreshState
    LaunchedEffect(isRefreshing, uiState.isLoading) {
        if (isRefreshing && !uiState.isLoading) {
            // Show success briefly, then keep green during dismissal
            showingSuccess = true
            delay(800) // Success display duration
            showingSuccess = false
            isRefreshing = false
        }
    }
    
    LaunchedEffect(wineryId) {
        viewModel.loadWinery(wineryId)
    }
    
    // Handle location selection result from savedStateHandle (PROPER PATTERN)
    // Wait for winery to be loaded before updating location
    LaunchedEffect(locationResult, uiState.winery) {
        
        if (locationResult != null && uiState.winery != null) {
            val (latitude, longitude, address) = locationResult
            val winery = uiState.winery
            
            // Update location now that winery is loaded
            viewModel.updateWineryLocation(latitude, longitude)
            if (address != null) {
                viewModel.updateWineryAddress(address)
            }
            
            // PROPER CLEANUP: Remove result after successful processing
            onLocationProcessed()
            
        } else if (locationResult != null) {
        }
    }
    
    
    // Refresh wines when returning from add/edit
    LaunchedEffect(addedWineId, editedWineId) {
        val shouldRefresh = addedWineId != null || editedWineId != null
        if (shouldRefresh) {
            // Removed println: "WineryDetailScreen: Refreshing wines after add/edit"
            viewModel.loadWinery(wineryId)
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.navigateBackAfterDelete) {
        if (uiState.navigateBackAfterDelete) {
            onNavigateBack()
        }
    }
    
    LaunchedEffect(uiState.navigateBackAfterSave) {
        if (uiState.navigateBackAfterSave) {
            uiState.winery?.let { winery ->
                onNavigateBackAfterSave(winery.id)
            } ?: onNavigateBack()
        }
    }
    
    // Handle image picker dialog trigger
    LaunchedEffect(uiState.showImagePicker) {
        if (uiState.showImagePicker) {
            checkAndLogPermissions()
            showImagePickerDialog = true
            viewModel.hideImagePicker()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.winery?.name ?: stringResource(R.string.winery_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Subscription button for customers (non-editors)
                    if (!uiState.canEdit && uiState.winery != null) {
                        IconButton(
                            onClick = { viewModel.toggleWinerySubscription() },
                            enabled = !uiState.isSubscriptionLoading,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (uiState.isSubscriptionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    imageVector = if (uiState.isSubscribed) 
                                        Icons.Filled.Notifications 
                                    else 
                                        Icons.Outlined.Notifications,
                                    contentDescription = if (uiState.isSubscribed) 
                                        stringResource(R.string.cd_unsubscribe) 
                                    else 
                                        stringResource(R.string.cd_subscribe),
                                    modifier = Modifier.size(18.dp),
                                    tint = if (uiState.isSubscribed) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Customer view button for winery owners
                    if (uiState.canEdit) {
                        IconButton(
                            onClick = onNavigateToCustomerView
                        ) {
                            Icon(
                                Icons.Default.Visibility, 
                                contentDescription = "View as customer",
                                tint = MaterialTheme.colorScheme.primary
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
                pullToRefreshState = pullToRefreshState,
                isLoading = uiState.isLoading,
                isRefreshing = isRefreshing,
                showingSuccess = showingSuccess,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Hide default indicator pull-to-refresh
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                indicator = { /* Hide default indicator */ }
            ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                uiState.winery?.let { winery ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Unified Photos Section - Always at the top
                        item {
                            UnifiedPhotosSection(
                                photos = uiState.photos,
                                isEditing = uiState.isEditing,
                                canEdit = uiState.canEdit,
                                onAddPhoto = { viewModel.showImagePicker() },
                                onRemovePhoto = viewModel::removePhoto,
                                onToggleEdit = viewModel::toggleEdit,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        
                        // Winery Info (Description, etc.) - Moved to second position
                        item {
                            WineryInfoCard(
                                winery = winery,
                                canEdit = uiState.canEdit,
                                onSaveWinery = { name, description, address -> 
                                    viewModel.updateWineryName(name)
                                    viewModel.updateWineryDescription(description)
                                    viewModel.updateWineryAddress(address)
                                    viewModel.saveWinery()
                                },
                                onLocationClick = { 
                                    onNavigateToLocationPicker(winery.latitude, winery.longitude)
                                },
                                isUpdating = uiState.isUpdating,
                                shouldStartEditing = locationResult != null,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        
                        item {
                            WinesSection(
                                wines = uiState.wines,
                                canEdit = uiState.canEdit,
                                onNavigateToAddWine = { onNavigateToAddWine(wineryId) },
                                onNavigateToEditWine = onNavigateToEditWine,
                                onNavigateToWineDetail = onNavigateToWineDetail,
                                onDeleteWine = viewModel::deleteWine,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        
                        // Delete Winery Button - Only for owners
                        if (uiState.canEdit) {
                            item {
                                DeleteWinerySection(
                                    onDeleteClick = { showDeleteConfirmation = true },
                                    isDeleting = uiState.isDeleting,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        DeleteWineryDialog(
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = { 
                viewModel.deleteWinery()
                showDeleteConfirmation = false
            }
        )
    }
    
    // Image picker dialog
    if (showImagePickerDialog) {
        com.ausgetrunken.ui.components.ImagePickerDialog(
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
            title = "Weingut-Foto hinzufügen",
            subtitle = "Wählen Sie eine Bildquelle für Ihr Weingut-Foto"
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
                    showingSuccess || (isRefreshing && !isLoading) -> Color(0xFF4CAF50) // Green when refresh completes
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
                    showingSuccess || (isRefreshing && !isLoading) -> {
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
                                text = stringResource(R.string.wines_refreshed_success),
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
                                text = stringResource(R.string.refreshing_wines),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    pullProgress > 0f -> {
                        // Show pull progress hint
                        val alpha = (pullProgress * 2f).coerceAtMost(1f)
                        Text(
                            text = if (pullProgress >= 0.8f) stringResource(R.string.release_to_refresh) else stringResource(R.string.pull_to_refresh),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WineryInfoCard(
    winery: com.ausgetrunken.data.local.entities.WineryEntity,
    canEdit: Boolean,
    onSaveWinery: (String, String, String) -> Unit,
    onLocationClick: () -> Unit,
    isUpdating: Boolean,
    shouldStartEditing: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Local editing state - preserve across location updates by using winery ID as key
    var isEditing by remember(winery.id) { mutableStateOf(false) }
    var editName by remember { mutableStateOf(winery.name) }
    var editDescription by remember { mutableStateOf(winery.description) }
    var editAddress by remember { mutableStateOf(winery.address) }
    
    // Reset local state when winery changes (but preserve edit mode during location updates)
    LaunchedEffect(winery.id) {
        editName = winery.name
        editDescription = winery.description
        editAddress = winery.address
        // Only reset edit mode on initial load, not during location updates
        if (!isEditing) {
            isEditing = false
        }
    }
    
    // Update edit fields when winery data changes but preserve edit mode
    LaunchedEffect(winery.name, winery.description, winery.address) {
        if (!isEditing) {
            editName = winery.name
            editDescription = winery.description
            editAddress = winery.address
        } else {
            // In edit mode, only update address if it changed (likely from location selection)
            // but preserve user's manual edits to name and description
            if (editAddress != winery.address && winery.address.isNotBlank()) {
                editAddress = winery.address
            }
        }
    }
    
    // Enter edit mode when location is selected
    LaunchedEffect(shouldStartEditing) {
        if (shouldStartEditing && canEdit) {
            isEditing = true
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with title and edit/save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.winery_details),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (canEdit) {
                    if (isEditing) {
                        Button(
                            onClick = {
                                onSaveWinery(editName, editDescription, editAddress)
                                isEditing = false
                            },
                            enabled = !isUpdating,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = stringResource(R.string.save),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.save),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isEditing && canEdit) {
                // Edit mode
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.winery_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = editAddress,
                    onValueChange = { editAddress = it },
                    label = { Text(stringResource(R.string.address)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show coordinates below address field when available
                if (winery.latitude != 0.0 && winery.longitude != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.coordinates, "${String.format("%.4f", winery.latitude)}, ${String.format("%.4f", winery.longitude)}"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onLocationClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.update_location))
                }
            } else {
                // View mode
                Text(
                    text = winery.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (winery.description.isNotBlank()) {
                    Text(
                        text = winery.description,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.cd_location),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.padding(4.dp))
                    
                    Text(
                        text = winery.address,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.coordinates, "${String.format("%.4f", winery.latitude)}, ${String.format("%.4f", winery.longitude)}"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Map component
                if (winery.latitude != 0.0 && winery.longitude != 0.0) {
                    WineryMapComponent(
                        latitude = winery.latitude,
                        longitude = winery.longitude,
                        address = winery.address,
                        wineryName = winery.name,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    WineryMapPlaceholder(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}


@Composable
private fun WinesSection(
    wines: List<WineEntity>,
    canEdit: Boolean,
    onNavigateToAddWine: () -> Unit,
    onNavigateToEditWine: (String) -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    onDeleteWine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header with wine count and manage button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wines_count, wines.size),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (canEdit) {
                    Button(
                        onClick = onNavigateToAddWine,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_wine),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            text = stringResource(R.string.add_wine),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            if (wines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (canEdit) stringResource(R.string.no_wines_added_yet) else stringResource(R.string.no_wines_available),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    if (canEdit) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tap_add_wine),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Display all wines with full management
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    wines.forEach { wine ->
                        WineManagementItem(
                            wine = wine,
                            canEdit = canEdit,
                            onWineClick = { onNavigateToWineDetail(wine.id) },
                            onEditWine = { onNavigateToEditWine(wine.id) },
                            onDeleteWine = onDeleteWine
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WineManagementItem(
    wine: WineEntity,
    canEdit: Boolean,
    onWineClick: () -> Unit,
    onEditWine: () -> Unit,
    onDeleteWine: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { 
            if (canEdit) {
                onEditWine()
            } else {
                onWineClick()
            }
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = wine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "${wine.wineType} • ${wine.vintage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (wine.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = wine.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "€${wine.price}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (wine.discountedPrice != null && wine.discountedPrice < wine.price) {
                        Text(
                            text = "€${wine.discountedPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stock_count, wine.stockQuantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wine.stockQuantity <= wine.lowStockThreshold) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (wine.stockQuantity <= wine.lowStockThreshold) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    }
                )
                
                if (canEdit) {
                    TextButton(
                        onClick = { onDeleteWine(wine.id) }
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WineryImageCarousel(
    images: List<String>,
    isEditing: Boolean,
    canEdit: Boolean,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { images.size })
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Show placeholder as background first
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = stringResource(R.string.winery_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                // Then overlay the actual image on top with skeleton loading
                val imageUrl = images[page]
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(
                            when {
                                imageUrl.startsWith("/") -> File(imageUrl)
                                imageUrl.startsWith("content://") -> Uri.parse(imageUrl)
                                else -> imageUrl
                            }
                        )
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .listener(
                            onStart = { 
                                if (imageUrl.startsWith("/")) {
                                    val file = File(imageUrl)
                                }
                            },
                            onSuccess = { _, result -> 
                            },
                            onError = { _, error -> 
                                if (imageUrl.startsWith("/")) {
                                    val file = File(imageUrl)
                                }
                            }
                        )
                        .build(),
                    loading = {
                        ShimmerLoadingEffect(
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    },
                    contentDescription = stringResource(R.string.winery_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Remove button when editing
                if (isEditing && canEdit) {
                    IconButton(
                        onClick = { onRemovePhoto(images[page]) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Text(
                            text = "×",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        // Page indicators
        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (index == pagerState.currentPage) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
        
        // Add photo button when can edit and less than 3 photos
        if (canEdit && images.size < 3) {
            FilledIconButton(
                onClick = onAddPhoto,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_add_photo),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun PhotosManagementSection(
    photos: List<String>,
    isEditing: Boolean,
    canEdit: Boolean,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header with photo count and add button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.photos_count_with_limit, photos.size, 3),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (canEdit && photos.size < 3) {
                    Button(
                        onClick = onAddPhoto,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_photo),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            text = stringResource(R.string.add_photo),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            if (photos.isEmpty()) {
                Text(
                    text = if (canEdit) 
                        stringResource(R.string.tap_add_photo_winery) 
                    else 
                        stringResource(R.string.no_photos_yet),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                )
            } else {
                // Show photos in a grid for management
                LazyRow(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(photos) { photo ->
                        PhotoThumbnail(
                            photoUrl = photo,
                            isEditing = isEditing,
                            canEdit = canEdit,
                            onRemove = { onRemovePhoto(photo) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photoUrl: String,
    isEditing: Boolean,
    canEdit: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Show placeholder as background first
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = stringResource(R.string.winery_photo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Then overlay the actual image on top with skeleton loading
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(
                        when {
                            photoUrl.startsWith("/") -> File(photoUrl)
                            photoUrl.startsWith("content://") -> Uri.parse(photoUrl)
                            else -> photoUrl
                        }
                    )
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .listener(
                        onStart = { 
                        },
                        onSuccess = { _, _ -> 
                        },
                        onError = { _, error -> 
                        }
                    )
                    .build(),
                loading = {
                    ShimmerLoadingEffect(
                        modifier = Modifier.fillMaxSize()
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                contentDescription = stringResource(R.string.winery_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (isEditing && canEdit) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            CircleShape
                        )
                ) {
                    Text(
                        text = "×",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


@Composable
private fun ShimmerLoadingEffect(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha.value),
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Photo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun UnifiedPhotosSection(
    photos: List<String>,
    isEditing: Boolean,
    canEdit: Boolean,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onToggleEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header with photo count and action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.photos_count_with_limit, photos.size, 3),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (canEdit) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add photo button (when not at limit and not editing)
                        if (photos.size < 3 && !isEditing) {
                            Button(
                                onClick = onAddPhoto,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.cd_add_photo),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text(
                                    text = stringResource(R.string.add_photo),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        // Edit/Done button (only show if there are photos to edit)
                        if (photos.isNotEmpty()) {
                            IconButton(
                                onClick = onToggleEdit,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditing) "Done editing" else "Edit photos",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            // Photo content area
            if (photos.isEmpty()) {
                // Empty state
                Text(
                    text = if (canEdit) 
                        stringResource(R.string.tap_add_photo_winery) 
                    else 
                        stringResource(R.string.no_photos_yet),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 40.dp)
                )
            } else {
                // Photos carousel for viewing
                val pagerState = rememberPagerState(pageCount = { photos.size })
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        ) {
                            // Placeholder background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = stringResource(R.string.winery_photo),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            // Actual image with skeleton loading
                            val imageUrl = photos[page]
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(
                                        when {
                                            imageUrl.startsWith("/") -> File(imageUrl)
                                            imageUrl.startsWith("content://") -> Uri.parse(imageUrl)
                                            else -> imageUrl
                                        }
                                    )
                                    .crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                loading = {
                                    ShimmerLoadingEffect(
                                        modifier = Modifier.fillMaxSize()
                                    )
                                },
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Photo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                },
                                contentDescription = stringResource(R.string.winery_photo),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Remove button for edit mode
                            if (isEditing && canEdit) {
                                FilledIconButton(
                                    onClick = { onRemovePhoto(imageUrl) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Page indicator if multiple photos
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(photos.size) { index ->
                            val isSelected = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )
                            if (index < photos.size - 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DeleteWinerySection(
    onDeleteClick: () -> Unit,
    isDeleting: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.danger_zone),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.delete_winery_warning),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onDeleteClick,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isDeleting) 
                        stringResource(R.string.deleting) 
                    else 
                        stringResource(R.string.delete_winery),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeleteWineryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_winery_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_winery_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.error,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}