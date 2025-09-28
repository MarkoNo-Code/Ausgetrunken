package com.ausgetrunken.ui.winery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import coil.compose.AsyncImage
import com.ausgetrunken.R
import com.ausgetrunken.ui.components.ImagePickerDialog
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWineryScreen(
    onNavigateBack: () -> Unit,
    onNavigateBackWithSuccess: (String) -> Unit,
    onNavigateToLocationPicker: (Double, Double) -> Unit = { _, _ -> },
    locationResult: Triple<Double, Double, String?>? = null,
    viewModel: AddWineryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // State for image picker dialog
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }


    // Helper function to create image file
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "WINERY_${timeStamp}_"
        val storageDir = File(context.getExternalFilesDir(null), "Pictures")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onImageAdded(it.toString()) }
        showImagePickerDialog = false
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                viewModel.onImageAdded(uri.toString())
            }
        }
        showImagePickerDialog = false
    }

    // Handle location selection result
    LaunchedEffect(locationResult) {
        locationResult?.let { (latitude, longitude, address) ->
            android.util.Log.d("AddWineryScreen", "🎯 Processing location result: lat=$latitude, lng=$longitude, address=$address")

            // Update the viewModel with the selected location
            address?.let { viewModel.onAddressChanged(it) }
            viewModel.onLocationChanged(latitude, longitude)

            // Clear the result to prevent reprocessing
            // Note: This would be cleared automatically on next navigation in real implementation
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            println("🔥 AddWineryScreen: ERROR DETECTED: $error")
            // TODO: Show snackbar with error
        }
    }

    // Channel-based navigation event handling
    LaunchedEffect(Unit) {
        viewModel.navigationEvent
            .flowWithLifecycle(lifecycle = lifecycle)
            .collect { event ->
                when (event) {
                    is AddWineryNavigationEvent.NavigateBackWithSuccess -> {
                        println("AddWineryScreen: Channel navigation event received, wineryId: ${event.wineryId}")
                        viewModel.onNavigateBackWithSuccess { wineryId ->
                            onNavigateBackWithSuccess(wineryId)
                        }
                        onNavigateBackWithSuccess(event.wineryId)
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_winery)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.submitWinery() },
                        enabled = uiState.canSubmit
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.save))
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
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Image Upload Section
                item {
                    WineryImageSection(
                        images = uiState.selectedImages,
                        onAddImage = { showImagePickerDialog = true },
                        onRemoveImage = viewModel::onImageRemoved
                    )
                }

                // Basic Information Section
                item {
                    WineryBasicInfoSection(
                        name = uiState.name,
                        description = uiState.description,
                        address = uiState.address,
                        latitude = uiState.latitude ?: 0.0,
                        longitude = uiState.longitude ?: 0.0,
                        onNameChanged = viewModel::onNameChanged,
                        onDescriptionChanged = viewModel::onDescriptionChanged,
                        onAddressChanged = viewModel::onAddressChanged,
                        onLocationPickerClick = {
                            onNavigateToLocationPicker(uiState.latitude ?: 0.0, uiState.longitude ?: 0.0)
                        }
                    )
                }

                // Wine Information Section
                item {
                    WineInfoSection()
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
                                text = stringResource(R.string.creating_winery),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // Image picker dialog
    if (showImagePickerDialog) {
        ImagePickerDialog(
            onCameraClick = {
                try {
                    val imageFile = createImageFile()
                    photoUri = FileProvider.getUriForFile(
                        context,
                        "com.ausgetrunken.fileprovider",
                        imageFile
                    )
                    cameraLauncher.launch(photoUri!!)
                } catch (e: Exception) {
                    println("Error creating camera file: ${e.message}")
                    showImagePickerDialog = false
                }
            },
            onGalleryClick = {
                galleryLauncher.launch("image/*")
            },
            onDismiss = {
                showImagePickerDialog = false
            },
            title = "Weingut-Foto hinzufügen",
            subtitle = "Wählen Sie eine Bildquelle für Ihr Weingut-Foto"
        )
    }

}

@Composable
private fun WineryImageSection(
    images: List<String>,
    onAddImage: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.winery_photos),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${images.size}/3",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Image Button (only show if less than 3 images)
                if (images.size < 3) {
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
                                    contentDescription = stringResource(R.string.cd_add_photo),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                            contentDescription = stringResource(R.string.winery_photo),
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
                                contentDescription = stringResource(R.string.cd_remove_photo),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun WineryBasicInfoSection(
    name: String,
    description: String,
    address: String,
    latitude: Double,
    longitude: Double,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onLocationPickerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Winery Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                label = { Text("Winery Name") },
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

            // Coordinates display (show when location is set)
            if (latitude != 0.0 && longitude != 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Koordinaten: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Location picker button
            OutlinedButton(
                onClick = onLocationPickerClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Standort auf Karte auswählen")
            }
    }
}

@Composable
private fun WineInfoSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.wines_can_be_added_later),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.after_winery_created),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

    }
}


