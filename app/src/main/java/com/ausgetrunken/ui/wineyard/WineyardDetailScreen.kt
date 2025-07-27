package com.ausgetrunken.ui.wineyard

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
import android.util.Log
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WineyardDetailScreen(
    wineyardId: String,
    onNavigateBack: () -> Unit,
    onNavigateBackAfterSave: (String) -> Unit = { onNavigateBack() },
    onNavigateToAddWine: (String) -> Unit,
    onNavigateToEditWine: (String) -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    onNavigateToCustomerView: () -> Unit = {},
    viewModel: WineyardDetailViewModel = koinInject()
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
            val fileName = "wineyard_photo_${timeStamp}.jpg"
            val imagesDir = File(context.filesDir, "wineyard_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            
            val destFile = File(imagesDir, fileName)
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d("WineyardDetail", "Image copied to internal storage: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("WineyardDetail", "Failed to copy image to internal storage", e)
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
        Log.d("WineyardDetail", "Camera permission ($cameraPermission): ${hasPermission(cameraPermission)}")
        Log.d("WineyardDetail", "Storage permission ($storagePermission): ${hasPermission(storagePermission)}")
    }
    
    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            Log.d("WineyardDetail", "Gallery URI selected: $selectedUri")
            
            // Try two approaches: first copy to internal storage, then try using original URI as backup
            val internalPath = copyImageToInternalStorage(selectedUri)
            Log.d("WineyardDetail", "Internal path result: $internalPath")
            
            if (internalPath != null) {
                Log.d("WineyardDetail", "Adding internal path to viewModel: $internalPath")
                
                // Verify file exists
                val file = File(internalPath)
                Log.d("WineyardDetail", "File exists: ${file.exists()}, Size: ${file.length()} bytes")
                
                if (file.exists() && file.length() > 0) {
                    viewModel.addPhoto(internalPath)
                } else {
                    Log.w("WineyardDetail", "Internal file invalid, trying original URI: $selectedUri")
                    viewModel.addPhoto(selectedUri.toString())
                }
            } else {
                Log.e("WineyardDetail", "Failed to save to internal storage, using original URI: $selectedUri")
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
                Log.d("WineyardDetail", "Camera URI captured: $uri")
                
                // Copy image to internal storage for persistence
                val internalPath = copyImageToInternalStorage(uri)
                if (internalPath != null) {
                    viewModel.addPhoto(internalPath)
                } else {
                    Log.e("WineyardDetail", "Failed to save camera image")
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
    
    LaunchedEffect(wineyardId) {
        viewModel.loadWineyard(wineyardId)
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
            uiState.wineyard?.let { wineyard ->
                onNavigateBackAfterSave(wineyard.id)
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
                title = { Text(uiState.wineyard?.name ?: stringResource(R.string.wineyard_details)) },
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
                    if (!uiState.canEdit && uiState.wineyard != null) {
                        IconButton(
                            onClick = { viewModel.toggleWineyardSubscription() },
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
                    
                    // Customer view button for wineyard owners
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
                uiState.wineyard?.let { wineyard ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        
                        // Wineyard Info (Description, etc.) - Moved to second position
                        item {
                            WineyardInfoCard(
                                wineyard = wineyard,
                                canEdit = uiState.canEdit,
                                onSaveWineyard = { name, description, address -> 
                                    viewModel.updateWineyardName(name)
                                    viewModel.updateWineyardDescription(description)
                                    viewModel.updateWineyardAddress(address)
                                    viewModel.saveWineyard()
                                },
                                onLocationClick = { viewModel.showLocationPicker() },
                                isUpdating = uiState.isUpdating,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        
                        item {
                            WinesSection(
                                wines = uiState.wines,
                                canEdit = uiState.canEdit,
                                onNavigateToAddWine = { onNavigateToAddWine(wineyardId) },
                                onNavigateToEditWine = onNavigateToEditWine,
                                onNavigateToWineDetail = onNavigateToWineDetail,
                                onDeleteWine = viewModel::deleteWine,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        
                        // Delete Wineyard Button - Only for owners
                        if (uiState.canEdit) {
                            item {
                                DeleteWineyardSection(
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
        DeleteWineyardDialog(
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = { 
                viewModel.deleteWineyard()
                showDeleteConfirmation = false
            }
        )
    }
    
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
                Log.d("WineyardDetail", "Storage permission ($storagePermission): $hasStoragePermission")
                
                if (hasStoragePermission) {
                    galleryLauncher.launch("image/*")
                    showImagePickerDialog = false
                } else {
                    Log.d("WineyardDetail", "Requesting storage permission: $storagePermission")
                    storagePermissionLauncher.launch(storagePermission)
                }
            },
            onDismiss = {
                showImagePickerDialog = false
            }
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
private fun WineyardInfoCard(
    wineyard: com.ausgetrunken.data.local.entities.WineyardEntity,
    canEdit: Boolean,
    onSaveWineyard: (String, String, String) -> Unit,
    onLocationClick: () -> Unit,
    isUpdating: Boolean,
    modifier: Modifier = Modifier
) {
    // Local editing state
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(wineyard.name) }
    var editDescription by remember { mutableStateOf(wineyard.description) }
    var editAddress by remember { mutableStateOf(wineyard.address) }
    
    // Reset local state when wineyard changes
    LaunchedEffect(wineyard) {
        editName = wineyard.name
        editDescription = wineyard.description
        editAddress = wineyard.address
        isEditing = false
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
                    text = stringResource(R.string.wineyard_details),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (canEdit) {
                    if (isEditing) {
                        Button(
                            onClick = {
                                onSaveWineyard(editName, editDescription, editAddress)
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
                    label = { Text(stringResource(R.string.wineyard_name)) },
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
                    text = wineyard.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (wineyard.description.isNotBlank()) {
                    Text(
                        text = wineyard.description,
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
                        text = wineyard.address,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.coordinates, "${String.format("%.4f", wineyard.latitude)}, ${String.format("%.4f", wineyard.longitude)}"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun WineyardImageCarousel(
    images: List<String>,
    isEditing: Boolean,
    canEdit: Boolean,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d("WineyardImageCarousel", "Rendering carousel with ${images.size} images: $images")
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
                        contentDescription = stringResource(R.string.wineyard_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                // Then overlay the actual image on top
                val imageUrl = images[page]
                Log.d("WineyardImageCarousel", "Loading image at page $page: $imageUrl")
                AsyncImage(
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
                                Log.d("WineyardImageCarousel", "Started loading: $imageUrl")
                                if (imageUrl.startsWith("/")) {
                                    val file = File(imageUrl)
                                    Log.d("WineyardImageCarousel", "File check - exists: ${file.exists()}, size: ${file.length()}")
                                }
                            },
                            onSuccess = { _, result -> 
                                Log.d("WineyardImageCarousel", "Successfully loaded: $imageUrl")
                                Log.d("WineyardImageCarousel", "Result drawable: ${result.drawable}")
                            },
                            onError = { _, error -> 
                                Log.e("WineyardImageCarousel", "Failed to load $imageUrl: ${error.throwable}")
                                Log.e("WineyardImageCarousel", "Error message: ${error.throwable.message}")
                                if (imageUrl.startsWith("/")) {
                                    val file = File(imageUrl)
                                    Log.e("WineyardImageCarousel", "File debug - path: ${file.absolutePath}, exists: ${file.exists()}, readable: ${file.canRead()}")
                                }
                            }
                        )
                        .build(),
                    contentDescription = stringResource(R.string.wineyard_photo),
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
                        stringResource(R.string.tap_add_photo_wineyard) 
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
                    contentDescription = stringResource(R.string.wineyard_photo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Then overlay the actual image on top
            AsyncImage(
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
                            Log.d("PhotoThumbnail", "Started loading thumbnail: $photoUrl")
                        },
                        onSuccess = { _, _ -> 
                            Log.d("PhotoThumbnail", "Successfully loaded thumbnail: $photoUrl")
                        },
                        onError = { _, error -> 
                            Log.e("PhotoThumbnail", "Failed to load thumbnail $photoUrl: ${error.throwable}")
                        }
                    )
                    .build(),
                contentDescription = stringResource(R.string.wineyard_photo),
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

// Image picker dialog
@Composable
private fun ImagePickerDialog(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_image_source),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(R.string.choose_image_source_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onCameraClick) {
                Text(stringResource(R.string.camera))
            }
        },
        dismissButton = {
            TextButton(onClick = onGalleryClick) {
                Text(stringResource(R.string.gallery))
            }
        }
    )
}

@Composable
private fun UnifiedPhotosSection(
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
            
            // Photo content area
            if (photos.isEmpty()) {
                // Empty state
                Text(
                    text = if (canEdit) 
                        stringResource(R.string.tap_add_photo_wineyard) 
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
                Log.d("UnifiedPhotosSection", "Rendering carousel with ${photos.size} photos: $photos")
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
                                    contentDescription = stringResource(R.string.wineyard_photo),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            // Actual image
                            val imageUrl = photos[page]
                            AsyncImage(
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
                                contentDescription = stringResource(R.string.wineyard_photo),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
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
private fun DeleteWineyardSection(
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
                text = stringResource(R.string.delete_wineyard_warning),
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
                        stringResource(R.string.delete_wineyard),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeleteWineyardDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_wineyard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_wineyard_message),
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