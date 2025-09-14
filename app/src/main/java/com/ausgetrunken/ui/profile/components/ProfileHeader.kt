package com.ausgetrunken.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import java.io.File
import android.net.Uri
import androidx.compose.foundation.clickable

@Composable
fun ProfileHeader(
    userName: String,
    userEmail: String,
    profilePictureUrl: String?,
    wineyardCount: Int,
    maxWineyards: Int,
    onProfilePictureClick: () -> Unit,
    onNotificationCenterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNameClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Picture with golden border - left aligned (increased by 30%)
        Box(
            modifier = Modifier.size(114.dp),
            contentAlignment = Alignment.Center
        ) {
            // Profile picture background with golden border
            Box(
                modifier = Modifier
                    .size(114.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary, // Burgundy red
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (profilePictureUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(
                                when {
                                    profilePictureUrl.startsWith("/") -> File(profilePictureUrl)
                                    profilePictureUrl.startsWith("content://") -> Uri.parse(profilePictureUrl)
                                    else -> profilePictureUrl
                                }
                            )
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(110.dp),
                        contentScale = ContentScale.Crop,
                        loading = {
                            UserPlaceholderIcon(
                                modifier = Modifier.size(110.dp),
                                size = 110.dp
                            )
                        },
                        error = {
                            UserPlaceholderIcon(
                                modifier = Modifier.size(110.dp),
                                size = 110.dp
                            )
                        }
                    )
                } else {
                    UserPlaceholderIcon(
                        modifier = Modifier.size(110.dp),
                        size = 110.dp
                    )
                }
            }

            // Camera button with burgundy background
            IconButton(
                onClick = onProfilePictureClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary) // Burgundy red
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "Change Profile Picture",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Account info on the right side
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            // User Name (no longer clickable - editing moved to settings)
            Text(
                text = userName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // User Email
            Text(
                text = userEmail,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Notification Center Button (reduced padding)
                OutlinedButton(
                    onClick = onNotificationCenterClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notification Center",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Notifications",
                        fontSize = 11.sp
                    )
                }

                // Settings Button
                OutlinedButton(
                    onClick = onSettingsClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Settings",
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}