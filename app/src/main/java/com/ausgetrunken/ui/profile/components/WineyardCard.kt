package com.ausgetrunken.ui.profile.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.ui.theme.WineyardPlaceholderImage
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

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
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp) // Add horizontal margin
            .scale(scale.value)
            .then(
                if (glowAlpha.value > 0f) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFFC0C0C0).copy(alpha = glowAlpha.value), // Silver color
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            ),
        onClick = { onWineyardClick(wineyard.id) },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111111) // Dark gray background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111111)) // Gray background
                .padding(8.dp), // Margin around entire content
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Image with margin
            Box(
                modifier = Modifier
                    .size(80.dp) // Fixed square size for image
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (wineyard.photos.isNotEmpty()) {
                    AsyncImage(
                        model = getOptimizedImageUrl(wineyard.photos.first()),
                        contentDescription = "Wineyard ${wineyard.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            // Optional: Handle loading states if needed
                            when (state) {
                                is AsyncImagePainter.State.Error -> {
                                    // Fall back to placeholder on error
                                }
                                else -> { /* Handle other states if needed */ }
                            }
                        }
                    )
                } else {
                    // Fallback to placeholder when no photos available
                    WineyardPlaceholderImage(
                        modifier = Modifier.fillMaxSize(),
                        aspectRatio = 1f // Square aspect ratio
                    )
                }
            }
            
            // Right side: Text content with proper spacing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp), // Space between image and text
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
}