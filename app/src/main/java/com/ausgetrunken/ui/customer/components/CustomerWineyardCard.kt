package com.ausgetrunken.ui.customer.components

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.ui.theme.WineyardPlaceholderImage
import java.io.File

/**
 * Creates an optimized Supabase image URL with transformations for better performance
 */
private fun getOptimizedImageUrl(originalUrl: String, width: Int = 800, height: Int = 450): String {
    return if (originalUrl.contains("supabase")) {
        "$originalUrl?resize=cover&width=$width&height=$height&quality=75&format=webp"
    } else {
        originalUrl
    }
}

/**
 * Pulsating loading animation for image placeholders
 */
@Composable
private fun PulsatingPlaceholder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulsatingPlaceholder")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    WineyardPlaceholderImage(
        modifier = modifier.background(
            Color.White.copy(alpha = alpha),
            RoundedCornerShape(16.dp)
        ),
        aspectRatio = 16f / 9f
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerWineyardCard(
    wineyard: WineyardEntity,
    onWineyardClick: (String) -> Unit,
    isSubscribed: Boolean = false,
    isLoading: Boolean = false,
    onSubscriptionToggle: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animation for loading state
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        onClick = { onWineyardClick(wineyard.id) },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            // Background wineyard image
            if (wineyard.photos.isNotEmpty()) {
                val imageUrl = wineyard.photos.first()
                Log.d("CustomerWineyardCard", "Loading backdrop image for ${wineyard.name}: $imageUrl")
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(
                            when {
                                imageUrl.startsWith("/") -> File(imageUrl)
                                imageUrl.startsWith("content://") -> Uri.parse(imageUrl)
                                else -> getOptimizedImageUrl(imageUrl, 800, 450) // Larger size for backdrop
                            }
                        )
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "Wineyard ${wineyard.name} backdrop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        // Show pulsating animation while loading
                        Log.d("CustomerWineyardCard", "Backdrop image loading for ${wineyard.name}")
                        PulsatingPlaceholder(modifier = Modifier.fillMaxSize())
                    },
                    error = {
                        // Show static placeholder on error
                        Log.e("CustomerWineyardCard", "Backdrop image failed to load for ${wineyard.name}: $imageUrl")
                        WineyardPlaceholderImage(
                            modifier = Modifier.fillMaxSize(),
                            aspectRatio = 16f / 9f
                        )
                    },
                    success = { state ->
                        Log.d("CustomerWineyardCard", "Backdrop image loaded successfully for ${wineyard.name}")
                        Image(
                            painter = state.painter,
                            contentDescription = "Wineyard ${wineyard.name} backdrop",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                )
            } else {
                // Fallback to placeholder when no photos available
                Log.d("CustomerWineyardCard", "No photos available for ${wineyard.name}")
                WineyardPlaceholderImage(
                    modifier = Modifier.fillMaxSize(),
                    aspectRatio = 16f / 9f
                )
            }
            
            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row - subscription button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { if (!isLoading) onSubscriptionToggle(wineyard.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isLoading) {
                            // Show spinning bell icon while loading subscription state
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Loading subscription state...",
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(rotationAngle),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        } else {
                            Icon(
                                imageVector = if (isSubscribed) 
                                    Icons.Filled.Notifications 
                                else 
                                    Icons.Outlined.Notifications,
                                contentDescription = if (isSubscribed) 
                                    "Unsubscribe from notifications" 
                                else 
                                    "Subscribe to notifications",
                                modifier = Modifier.size(18.dp),
                                tint = if (isSubscribed) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    Color.White
                            )
                        }
                    }
                }
                
                // Bottom content
                Column {
                    // Wineyard name
                    Text(
                        text = wineyard.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Description
                    if (wineyard.description.isNotBlank()) {
                        Text(
                            text = wineyard.description,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Location
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = wineyard.address,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (isSubscribed) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✓ Subscribed",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(
                                        Color.White.copy(alpha = 0.9f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}