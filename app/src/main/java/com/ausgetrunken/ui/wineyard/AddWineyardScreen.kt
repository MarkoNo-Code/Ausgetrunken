package com.ausgetrunken.ui.wineyard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import coil.compose.AsyncImage
import com.ausgetrunken.ui.wineyard.components.WineFormCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWineyardScreen(
    onNavigateBack: () -> Unit,
    onNavigateBackWithSuccess: (String) -> Unit,
    viewModel: AddWineyardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onImageAdded(it.toString()) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            println("🔥 AddWineyardScreen: ERROR DETECTED: $error")
            // TODO: Show snackbar with error
        }
    }

    // Channel-based navigation event handling
    LaunchedEffect(Unit) {
        viewModel.navigationEvent
            .flowWithLifecycle(lifecycle = lifecycle)
            .collect { event ->
                when (event) {
                    is AddWineyardNavigationEvent.NavigateBackWithSuccess -> {
                        println("AddWineyardScreen: Channel navigation event received, wineyardId: ${event.wineyardId}")
                        viewModel.onNavigateBackWithSuccess { wineyardId ->
                            onNavigateBackWithSuccess(wineyardId)
                        }
                        onNavigateBackWithSuccess(event.wineyardId)
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Wineyard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.submitWineyard() },
                        enabled = uiState.canSubmit
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Image Upload Section
                item {
                    WineyardImageSection(
                        images = uiState.selectedImages,
                        onAddImage = { imagePickerLauncher.launch("image/*") },
                        onRemoveImage = viewModel::onImageRemoved
                    )
                }

                // Basic Information Section
                item {
                    WineyardBasicInfoSection(
                        name = uiState.name,
                        description = uiState.description,
                        address = uiState.address,
                        onNameChanged = viewModel::onNameChanged,
                        onDescriptionChanged = viewModel::onDescriptionChanged,
                        onAddressChanged = viewModel::onAddressChanged
                    )
                }

                // Wine List Section
                item {
                    WineListSection(
                        wines = uiState.wines,
                        onAddWine = viewModel::addWine,
                        onRemoveWine = viewModel::removeWine,
                        onWineChanged = viewModel::updateWine
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Loading overlay
            if (uiState.isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Creating wineyard...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WineyardImageSection(
    images: List<String>,
    onAddImage: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Wineyard Photos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Image Button
                item {
                    Card(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        onClick = onAddImage,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Photo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Display Images
                items(images) { imageUri ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Wineyard Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Remove button
                        IconButton(
                            onClick = { onRemoveImage(imageUri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove Photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WineyardBasicInfoSection(
    name: String,
    description: String,
    address: String,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Wineyard Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                label = { Text("Wineyard Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChanged,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            OutlinedTextField(
                value = address,
                onValueChange = onAddressChanged,
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun WineListSection(
    wines: List<WineFormData>,
    onAddWine: () -> Unit,
    onRemoveWine: (String) -> Unit,
    onWineChanged: (String, WineFormData) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedButton(
                    onClick = onAddWine,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Wine",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Wine")
                }
            }

            if (wines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No wines added yet.\nClick 'Add Wine' to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                
                wines.forEachIndexed { index, wine ->
                    WineFormCard(
                        wine = wine,
                        onWineChanged = { updatedWine -> onWineChanged(wine.id, updatedWine) },
                        onRemoveWine = { onRemoveWine(wine.id) }
                    )
                    
                    if (index < wines.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}
