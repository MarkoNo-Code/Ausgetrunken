package com.ausgetrunken.ui.customer.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ausgetrunken.data.local.entities.WineyardEntity
import com.ausgetrunken.ui.theme.WineyardPlaceholderImage

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
            WineyardPlaceholderImage(
                modifier = Modifier.fillMaxSize(),
                aspectRatio = 16f / 9f
            )
            
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
                    FilledIconButton(
                        onClick = { if (!isLoading) onSubscriptionToggle(wineyard.id) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSubscribed) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                Color.White.copy(alpha = 0.2f),
                            contentColor = if (isSubscribed) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                Color.White
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(rotationAngle),
                                strokeWidth = 2.dp,
                                color = if (isSubscribed) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    Color.White
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
                                modifier = Modifier.size(18.dp)
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