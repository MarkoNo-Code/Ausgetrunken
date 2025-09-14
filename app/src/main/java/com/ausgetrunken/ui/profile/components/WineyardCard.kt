package com.ausgetrunken.ui.profile.components

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
private fun getOptimizedImageUrl(originalUrl: String, width: Int = 400, height: Int = 120): String {
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
            RoundedCornerShape(12.dp)
        ),
        aspectRatio = 1f
    )
}

@Composable
fun WineyardCard(
    wineyard: WineyardEntity,
    onWineyardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isNewlyAdded: Boolean = false,
    isUpdated: Boolean = false
) {
    val scale = remember { Animatable(if (isNewlyAdded) 0.8f else 1f) }
    val glowAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(isNewlyAdded) {
        if (isNewlyAdded) {
            // Start small, grow to 1.1x, then settle to 1.0x
            scale.animateTo(
                targetValue = 1.1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
    
    LaunchedEffect(isUpdated) {
        if (isUpdated) {
            // Silver glow pulsing animation for 2 seconds
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 2000) {
                glowAlpha.animateTo(
                    targetValue = 0.8f,
                    animationSpec = tween(durationMillis = 600)
                )
                glowAlpha.animateTo(
                    targetValue = 0.3f,
                    animationSpec = tween(durationMillis = 600)
                )
            }
            // Fade out
            glowAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }
    
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .scale(scale.value)
            .then(
                if (glowAlpha.value > 0f) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFFC0C0C0).copy(alpha = glowAlpha.value), // Silver color
                        shape = RoundedCornerShape(0.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = { onWineyardClick(wineyard.id) }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            // Left side: Image (no extra padding to align with screen edge)
            Box(
                modifier = Modifier
                    .size(80.dp) // Fixed square size for image
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (wineyard.photos.isNotEmpty()) {
                    val imageUrl = wineyard.photos.first()
                    Log.d("WineyardCard", "Loading image for ${wineyard.name}: $imageUrl")
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(
                                when {
                                    imageUrl.startsWith("/") -> File(imageUrl)
                                    imageUrl.startsWith("content://") -> Uri.parse(imageUrl)
                                    else -> getOptimizedImageUrl(imageUrl, 160, 160) // 2x size for sharp display
                                }
                            )
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Wineyard ${wineyard.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            // Show pulsating animation while loading
                            Log.d("WineyardCard", "Image loading for ${wineyard.name}")
                            PulsatingPlaceholder(modifier = Modifier.fillMaxSize())
                        },
                        error = {
                            // Show static placeholder on error
                            Log.e("WineyardCard", "Image failed to load for ${wineyard.name}: $imageUrl")
                            WineyardPlaceholderImage(
                                modifier = Modifier.fillMaxSize(),
                                aspectRatio = 1f
                            )
                        },
                        success = { state ->
                            Log.d("WineyardCard", "Image loaded successfully for ${wineyard.name}")
                            Image(
                                painter = state.painter,
                                contentDescription = "Wineyard ${wineyard.name}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    )
                } else {
                    // Fallback to placeholder when no photos available
                    Log.d("WineyardCard", "No photos available for ${wineyard.name}")
                    WineyardPlaceholderImage(
                        modifier = Modifier.fillMaxSize(),
                        aspectRatio = 1f // Square aspect ratio
                    )
                }
            }
            
            // Right side: Text content with proper spacing
            Column(
                modifier = Modifier
                    .weight(1f) // Take remaining space but respect right margin
                    .padding(start = 24.dp), // Reduced space between image and text
                verticalArrangement = Arrangement.Center
            ) {
                // Wineyard name
                Text(
                    text = wineyard.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = 18.sp
                )
                
                // Small gap between name and location
                Spacer(modifier = Modifier.height(4.dp))
                
                // Location/address
                Text(
                    text = wineyard.address,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
        }
    }
}