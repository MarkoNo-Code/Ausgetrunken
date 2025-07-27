package com.ausgetrunken

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ausgetrunken.notifications.FCMTokenManager
import com.ausgetrunken.notifications.FCMTestUtils
import com.ausgetrunken.ui.navigation.AusgetrunkenNavigation
import com.ausgetrunken.ui.theme.AusgetrunkenTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val fcmTokenManager: FCMTokenManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request notification permission and initialize FCM
        fcmTokenManager.requestNotificationPermission(this)
        
        // Debug FCM setup
        FCMTestUtils.logCompleteDebugInfo(this)
        
        setContent {
            AusgetrunkenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AusgetrunkenNavigation(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
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
        AusgetrunkenNavigation()
    }
}