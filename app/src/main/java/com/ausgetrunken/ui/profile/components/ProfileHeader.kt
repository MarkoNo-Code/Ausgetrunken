package com.ausgetrunken.ui.profile.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ausgetrunken.ui.theme.UserPlaceholderIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileHeader(
    userName: String,
    userEmail: String,
    profilePictureUrl: String?,
    wineyardCount: Int,
    maxWineyards: Int,
    onProfilePictureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Picture with golden border (like the screenshot)
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Profile picture background with golden border
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary, // Burgundy red
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (profilePictureUrl != null) {
                    // TODO: Load actual image from URL
                    UserPlaceholderIcon(
                        modifier = Modifier.size(114.dp),
                        size = 114.dp
                    )
                } else {
                    UserPlaceholderIcon(
                        modifier = Modifier.size(114.dp),
                        size = 114.dp
                    )
                }
            }
            
            // Camera button with burgundy background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary) // Burgundy red
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "Change Profile Picture",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // User Name (larger and more prominent like the screenshot)
        Text(
            text = userName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // User Email (simpler, no icon like the screenshot)
        Text(
            text = userEmail,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Individual stat cards aligned with wineyard cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Small gap between cards
        ) {
            // Wineyards stat card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp), // Same as wineyard cards
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF111111) // Same dark gray as wineyard cards
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📊", // Chart emoji like in screenshot
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$wineyardCount",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Weingüter",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Max Allowed stat card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp), // Same as wineyard cards
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF111111) // Same dark gray as wineyard cards
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🟢", // Green circle like in screenshot
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$maxWineyards",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Max erlaubt",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Remaining stat card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp), // Same as wineyard cards
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF111111) // Same dark gray as wineyard cards
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🔗", // Link emoji like in screenshot
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${maxWineyards - wineyardCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Verbleibend",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}