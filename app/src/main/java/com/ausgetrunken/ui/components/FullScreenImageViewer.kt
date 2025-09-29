package com.ausgetrunken.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ausgetrunken.R
import java.io.File

/**
 * Full-screen image viewer with zoom and swipe capabilities
 *
 * Features:
 * - Pinch to zoom
 * - Swipe between multiple images
 * - Tap to close
 * - Image counter
 * - Proper full-screen display
 */
@Composable
fun FullScreenImageViewer(
    photos: List<String>,
    initialPhotoIndex: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (photos.isNotEmpty()) {
                val pagerState = rememberPagerState(
                    initialPage = initialPhotoIndex,
                    pageCount = { photos.size }
                )

                // Main image pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Handle tap to close when not zoomed
                            detectTapGestures(
                                onTap = { onDismiss() }
                            )
                        }
                ) { page ->
                    SimpleZoomableImage(
                        photoUrl = photos[page],
                        onTap = { onDismiss() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top bar with counter and close button
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo counter (only show if multiple photos, otherwise spacer)
                    if (photos.size > 1) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${photos.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        // Empty spacer to maintain alignment
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .zIndex(10f)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = Color.White
                        )
                    }
                }

                // Dot indicators at bottom (only show if multiple photos)
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(photos.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (index == pagerState.currentPage) {
                                            Color.White
                                        } else {
                                            Color.White.copy(alpha = 0.5f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleZoomableImage(
    photoUrl: String,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(if (photoUrl.startsWith("http")) photoUrl else File(photoUrl))
            .crossfade(true)
            .build(),
        contentDescription = "Full screen image",
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
            .let { baseModifier ->
                if (scale > 1f) {
                    // When zoomed in, handle both tap and transform gestures
                    baseModifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Reset zoom when tapped while zoomed
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            )
                        }
                        .pointerInput(scale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                } else {
                    // When not zoomed, only handle double tap - NO single tap handling to allow pager swipes
                    baseModifier.pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = 2f
                            }
                            // Deliberately NO onTap to allow HorizontalPager to receive swipe gestures
                        )
                    }
                }
            },
        contentScale = ContentScale.Fit
    )
}