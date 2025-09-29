package com.ausgetrunken.ui.wines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ausgetrunken.R
import com.ausgetrunken.ui.components.FullScreenImageViewer
import coil.request.ImageRequest
import java.io.File

@Composable
fun WinePhotosFullscreenCarousel(
    photos: List<String>,
    modifier: Modifier = Modifier
) {
    var showFullScreenViewer by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }

    if (photos.isEmpty()) {
        // Show placeholder when no photos
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_photos_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { photos.size })

        // Update selected photo index when user swipes
        LaunchedEffect(pagerState.currentPage) {
            selectedPhotoIndex = pagerState.currentPage
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            // Full-screen photo pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(if (photos[page].startsWith("http")) photos[page] else File(photos[page]))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Wine Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            selectedPhotoIndex = page
                            showFullScreenViewer = true
                        },
                    contentScale = ContentScale.Crop
                )
            }

            // Top gradient overlay for navigation bar visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Dot indicators at bottom
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

        // Full-screen image viewer
        if (showFullScreenViewer) {
            FullScreenImageViewer(
                photos = photos,
                initialPhotoIndex = selectedPhotoIndex,
                onDismiss = { showFullScreenViewer = false }
            )
        }
    }
}

@Composable
fun WinePhotosCarousel(
    photos: List<String>,
    modifier: Modifier = Modifier
) {
    var showFullScreenViewer by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }

    if (photos.isEmpty()) {
        // Show placeholder when no photos
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_photos_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Header
                Text(
                    text = stringResource(R.string.wine_photos_count, photos.size),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )

                // Photos carousel
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(photos) { index, photo ->
                        WinePhotoItem(
                            photoUrl = photo,
                            onClick = {
                                selectedPhotoIndex = index
                                showFullScreenViewer = true
                            },
                            modifier = Modifier.size(150.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Full-screen image viewer
        if (showFullScreenViewer) {
            FullScreenImageViewer(
                photos = photos,
                initialPhotoIndex = selectedPhotoIndex,
                onDismiss = { showFullScreenViewer = false }
            )
        }
    }
}

@Composable
private fun WinePhotoItem(
    photoUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(if (photoUrl.startsWith("http")) photoUrl else File(photoUrl))
                .crossfade(true)
                .build(),
            contentDescription = "Wine Photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun WineryPhotosFullscreenCarousel(
    photos: List<String>,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        // Show placeholder when no photos
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_photos_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { photos.size })

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            // Full-screen photo pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(if (photos[page].startsWith("http")) photos[page] else File(photos[page]))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Winery Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Top gradient overlay for navigation bar visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Dot indicators at bottom
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