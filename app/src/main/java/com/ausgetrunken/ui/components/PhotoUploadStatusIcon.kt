package com.ausgetrunken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ausgetrunken.domain.model.UploadStatus
import com.ausgetrunken.domain.model.PhotoUploadInfo
import kotlinx.coroutines.delay

/**
 * Shows upload status icon overlay on photos
 */
@Composable
fun PhotoUploadStatusIcon(
    uploadInfo: PhotoUploadInfo,
    modifier: Modifier = Modifier
) {
    when (uploadInfo.status) {
        UploadStatus.PENDING -> {
            PendingIcon(modifier = modifier)
        }
        
        UploadStatus.UPLOADING -> {
            UploadingIcon(
                progress = uploadInfo.uploadProgress,
                modifier = modifier
            )
        }
        
        UploadStatus.COMPLETED -> {
            CompletedIcon(modifier = modifier)
        }
        
        UploadStatus.FAILED -> {
            FailedIcon(
                errorMessage = uploadInfo.errorMessage,
                modifier = modifier
            )
        }
        
        UploadStatus.LOCAL_ONLY -> {
            LocalOnlyIcon(modifier = modifier)
        }
    }
}

@Composable
private fun PendingIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Upload pending",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun UploadingIcon(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        // Animated progress indicator
        var animatedProgress by remember { mutableFloatStateOf(0f) }
        
        LaunchedEffect(progress) {
            val step = (progress - animatedProgress) / 10f
            repeat(10) {
                delay(50)
                animatedProgress += step
            }
        }
        
        CircularProgressIndicator(
            progress = { animatedProgress.coerceIn(0f, 1f) },
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CompletedIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = "Upload completed",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FailedIcon(
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    var showTooltip by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Upload failed: ${errorMessage ?: "Unknown error"}",
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LocalOnlyIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = "Local only",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Upload status badge for use in lists or cards
 */
@Composable
fun PhotoUploadStatusBadge(
    uploadInfo: PhotoUploadInfo,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (uploadInfo.status) {
        UploadStatus.PENDING -> "Queued" to MaterialTheme.colorScheme.outline
        UploadStatus.UPLOADING -> "Uploading" to MaterialTheme.colorScheme.primary
        UploadStatus.COMPLETED -> "Uploaded" to MaterialTheme.colorScheme.primary
        UploadStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        UploadStatus.LOCAL_ONLY -> "Local" to MaterialTheme.colorScheme.outline
    }
    
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}