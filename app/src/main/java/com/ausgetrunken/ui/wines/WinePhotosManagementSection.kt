package com.ausgetrunken.ui.wines

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ausgetrunken.R
import com.ausgetrunken.ui.components.ImagePickerDialog

@Composable
fun WinePhotosManagementSection(
    photos: List<String>,
    canEdit: Boolean,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Dialog state
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Helper functions
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getCameraPermission(): String = Manifest.permission.CAMERA

    fun getStoragePermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "WINE_${timeStamp}_"
        val storageDir: File? = context.getExternalFilesDir(null)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onAddPhoto(it) }
        showImagePickerDialog = false
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { onAddPhoto(it) }
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
                        onClick = { showImagePickerDialog = true },
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
                        "Tap 'Add Photo' to add wine images"
                    else
                        "No photos yet",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                )
            } else {
                // Show photos in a row
                LazyRow(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(photos) { photo ->
                        WinePhotoThumbnail(
                            photoUrl = photo,
                            canEdit = canEdit,
                            onRemove = { onRemovePhoto(photo) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
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
                if (hasPermission(storagePermission)) {
                    galleryLauncher.launch("image/*")
                    showImagePickerDialog = false
                } else {
                    storagePermissionLauncher.launch(storagePermission)
                }
            },
            onDismiss = {
                showImagePickerDialog = false
            },
            title = "Wein-Foto hinzufügen",
            subtitle = "Wählen Sie eine Bildquelle für Ihr Wein-Foto"
        )
    }
}

@Composable
private fun WinePhotoThumbnail(
    photoUrl: String,
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
            // Wine photo image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(if (photoUrl.startsWith("http")) photoUrl else File(photoUrl))
                    .crossfade(true)
                    .build(),
                contentDescription = "Wine Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Remove button (only shown in edit mode)
            if (canEdit) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                ) {
                    Card(
                        modifier = Modifier.size(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Photo",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}