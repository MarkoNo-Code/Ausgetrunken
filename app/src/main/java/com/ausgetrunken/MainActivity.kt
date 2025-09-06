package com.ausgetrunken

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ausgetrunken.notifications.FCMTokenManager
import com.ausgetrunken.notifications.FCMTestUtils
import com.ausgetrunken.ui.navigation.AusgetrunkenNavigation
import com.ausgetrunken.ui.theme.AusgetrunkenTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val fcmTokenManager: FCMTokenManager by inject()
    private var resetToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle deep link from password reset email
        handleDeepLink(intent)
        
        // Request notification permission and initialize FCM
        fcmTokenManager.requestNotificationPermission(this)
        
        // Debug FCM setup
        FCMTestUtils.logCompleteDebugInfo(this)
        
        setContent {
            AusgetrunkenTheme {
                AusgetrunkenNavigation(
                    resetToken = resetToken
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: Intent) {
        val data: Uri? = intent.data
        
        // Check if this is our password reset deep link
        if (data != null && data.scheme == "ausgetrunken" && data.host == "reset-password") {
            println("🔗 MainActivity: Password reset deep link detected: $data")
            
            // Extract tokens from the URL query parameters
            // Supabase sends URLs like: ausgetrunken://reset-password?access_token=xxx&refresh_token=yyy&type=recovery
            val accessToken = data.getQueryParameter("access_token")
            val refreshToken = data.getQueryParameter("refresh_token")
            val type = data.getQueryParameter("type")
            
            if (accessToken != null && type == "recovery") {
                println("🔗 MainActivity: Valid reset token found: $accessToken")
                resetToken = accessToken
            } else {
                println("⚠️ MainActivity: Invalid or missing reset token in deep link")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        fcmTokenManager.handlePermissionResult(requestCode, grantResults)
    }
}

@Preview(showBackground = true)
@Composable
fun AusgetrunkenPreview() {
    AusgetrunkenTheme {
        AusgetrunkenNavigation(
            resetToken = null
        )
    }
}