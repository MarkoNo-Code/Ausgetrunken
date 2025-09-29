package com.ausgetrunken.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File
import com.ausgetrunken.R
import com.ausgetrunken.ui.components.WineryMapComponent
import com.ausgetrunken.ui.components.WineryMapPlaceholder
import com.ausgetrunken.ui.winery.WineryDetailViewModel
import org.koin.androidx.compose.koinViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

// UI data classes to avoid passing database entities to composables
data class CustomerWineryUiData(
    val id: String,
    val name: String,
    val description: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class CustomerWineUiData(
    val id: String,
    val name: String,
    val wineType: String,
    val vintage: Int,
    val price: Double,
    val discountedPrice: Double?,
    val stockQuantity: Int,
    val lowStockThreshold: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerWineryDetailScreen(
    wineryId: String,
    onNavigateBack: () -> Unit,
    onNavigateToWineDetail: (String) -> Unit,
    viewModel: WineryDetailViewModel = koinViewModel()
) {
    LaunchedEffect(wineryId) {
        // Performance optimized: Logging disabled for improved app performance
    }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(wineryId) {
        viewModel.loadWinery(wineryId)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.winery == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Winery not found",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                CustomerWineryDetailContent(
                    winery = uiState.winery!!,
                    wines = uiState.wines,
                    photos = uiState.photos,
                    onNavigateToWineDetail = onNavigateToWineDetail,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Floating action buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Subscription button - only for customers (non-editors)
            if (uiState.winery != null && !uiState.canEdit) {
                IconButton(
                    onClick = { viewModel.toggleWinerySubscription() },
                    enabled = !uiState.isSubscriptionLoading,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .size(40.dp)
                ) {
                    if (uiState.isSubscriptionLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
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
                                Color.White
                        )
                    }
                }
            }
        }

        // Floating back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .zIndex(10f)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = Color.White,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .padding(8.dp)
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CustomerWineryDetailContent(
    winery: com.ausgetrunken.data.local.entities.WineryEntity,
    wines: List<com.ausgetrunken.data.local.entities.WineEntity>,
    photos: List<String>,
    onNavigateToWineDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert database entities to UI data classes
    val wineryUiData = CustomerWineryUiData(
        id = winery.id,
        name = winery.name,
        description = winery.description,
        address = winery.address,
        latitude = winery.latitude,
        longitude = winery.longitude
    )

    val winesUiData = wines.map { wine ->
        CustomerWineUiData(
            id = wine.id,
            name = wine.name,
            wineType = wine.wineType.name,
            vintage = wine.vintage,
            price = wine.price,
            discountedPrice = wine.discountedPrice,
            stockQuantity = wine.stockQuantity,
            lowStockThreshold = wine.lowStockThreshold
        )
    }

    Box(modifier = modifier) {
        // Full-screen scrollable content with photos at top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Full-screen winery photos at the top
            CustomerPhotosFullscreenCarousel(
                photos = photos,
                modifier = Modifier.fillMaxWidth()
            )

            // Content below photos with padding
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Beautiful description section
                CustomerDescriptionSection(
                    wineryData = wineryUiData,
                    modifier = Modifier.fillMaxWidth()
                )

                // Wine list section
                CustomerWineListSection(
                    wines = winesUiData,
                    onWineClick = onNavigateToWineDetail
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CustomerPhotosFullscreenCarousel(
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.LocalBar,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.no_photos_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
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
                    contentDescription = stringResource(R.string.winery_photo),
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

@Composable
private fun CustomerDescriptionSection(
    wineryData: CustomerWineryUiData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = wineryData.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (wineryData.description.isNotBlank()) {
            Text(
                text = wineryData.description,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (wineryData.address.isNotBlank()) {
            // Address and map in horizontal layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Address section
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Address",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = wineryData.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Compact map component
                if (wineryData.latitude != 0.0 && wineryData.longitude != 0.0) {
                    CompactWineryMap(
                        latitude = wineryData.latitude,
                        longitude = wineryData.longitude,
                        wineryName = wineryData.name,
                        modifier = Modifier
                            .width(120.dp)
                            .height(80.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomerWineListSection(
    wines: List<CustomerWineUiData>,
    onWineClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocalBar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.wines_count, wines.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (wines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_wines_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                wines.forEach { wine ->
                    CustomerWineCard(
                        wine = wine,
                        onClick = { onWineClick(wine.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerWineCard(
    wine: CustomerWineUiData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = wine.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${wine.wineType} • ${wine.vintage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "€${wine.price}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    wine.discountedPrice?.let { discountedPrice ->
                        Text(
                            text = "€${discountedPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.in_stock, wine.stockQuantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (wine.stockQuantity <= wine.lowStockThreshold) {
                    Text(
                        text = stringResource(R.string.low_stock),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactWineryMap(
    latitude: Double,
    longitude: Double,
    wineryName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val location = LatLng(latitude, longitude)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(location, 14f)
    }

    // Function to open Google Maps
    val openGoogleMaps = {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "geo:$latitude,$longitude?q=$latitude,$longitude($wineryName)".toUri()
        )
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to web version
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                "https://maps.google.com/?q=$latitude,$longitude".toUri()
            )
            context.startActivity(webIntent)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false
                ),
                properties = MapProperties(
                    mapType = MapType.NORMAL
                ),
                onMapClick = { openGoogleMaps() }
            ) {
                Marker(
                    state = MarkerState(position = location),
                    title = wineryName
                )
            }

            // Invisible clickable overlay to ensure clicks are captured
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { openGoogleMaps() }
            )
        }
    }
}