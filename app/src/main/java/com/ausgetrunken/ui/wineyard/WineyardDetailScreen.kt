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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
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
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WineyardDetailScreen(
    wineyardId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddWine: (String) -> Unit,
    onNavigateToEditWine: (String) -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    viewModel: WineyardDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    var showingSuccess by remember { mutableStateOf(false) }
    
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
                    
                    // Edit/Delete buttons for wineyard owners
                    if (uiState.canEdit) {
                        if (uiState.isEditing) {
                            IconButton(
                                onClick = { viewModel.saveWineyard() },
                                enabled = !uiState.isUpdating
                            ) {
                                if (uiState.isUpdating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                                }
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleEdit() }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(
                                onClick = { viewModel.deleteWineyard() },
                                enabled = !uiState.isDeleting
                            ) {
                                if (uiState.isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            WineyardInfoCard(
                                wineyard = wineyard,
                                isEditing = uiState.isEditing,
                                canEdit = uiState.canEdit,
                                onNameChange = viewModel::updateWineyardName,
                                onDescriptionChange = viewModel::updateWineyardDescription,
                                onAddressChange = viewModel::updateWineyardAddress,
                                onLocationClick = { viewModel.showLocationPicker() }
                            )
                        }
                        
                        item {
                            PhotosSection(
                                photos = wineyard.photos,
                                isEditing = uiState.isEditing,
                                canEdit = uiState.canEdit,
                                onAddPhoto = { viewModel.showImagePicker() },
                                onRemovePhoto = viewModel::removePhoto
                            )
                        }
                        
                        item {
                            WinesSection(
                                wines = uiState.wines,
                                canEdit = uiState.canEdit,
                                onNavigateToAddWine = { onNavigateToAddWine(wineyardId) },
                                onNavigateToEditWine = onNavigateToEditWine,
                                onNavigateToWineDetail = onNavigateToWineDetail,
                                onDeleteWine = viewModel::deleteWine
                            )
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
    isEditing: Boolean,
    canEdit: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onLocationClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            if (isEditing && canEdit) {
                OutlinedTextField(
                    value = wineyard.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.wineyard_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = wineyard.description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = wineyard.address,
                    onValueChange = onAddressChange,
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
private fun PhotosSection(
    photos: List<String>,
    isEditing: Boolean,
    canEdit: Boolean,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header with padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.photos_count, photos.size),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (isEditing && canEdit) {
                    IconButton(onClick = onAddPhoto) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_photo),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            if (photos.isEmpty()) {
                Text(
                    text = if (isEditing && canEdit) stringResource(R.string.tap_plus_add_photo) else stringResource(R.string.no_photos_yet),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                )
            } else {
                // Display photos in full-width with no left/right margins
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    photos.forEach { photo ->
                        FullWidthPhotoItem(
                            photoUrl = photo,
                            isEditing = isEditing,
                            canEdit = canEdit,
                            onRemove = { onRemovePhoto(photo) }
                        )
                    }
                    // Bottom spacing
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoItem(
    photoUrl: String,
    isEditing: Boolean,
    canEdit: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.size(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = stringResource(R.string.wineyard_photo),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
            
            if (isEditing && canEdit) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "×",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun FullWidthPhotoItem(
    photoUrl: String,
    isEditing: Boolean,
    canEdit: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // TODO: Load actual image from URL
            // For now, showing placeholder
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = stringResource(R.string.wineyard_photo),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(64.dp)
            )
            
            if (isEditing && canEdit) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "×",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
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
    onDeleteWine: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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