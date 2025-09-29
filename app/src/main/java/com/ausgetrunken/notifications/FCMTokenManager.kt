package com.ausgetrunken.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ausgetrunken.domain.service.NotificationService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FCMTokenManager(
    private val context: Context,
    private val notificationService: NotificationService
) {
    companion object {
        private const val TAG = "FCMTokenManager"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    initializeFCMToken()
                }
                else -> {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        } else {
            // For devices below Android 13, notifications are enabled by default
            initializeFCMToken()
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeFCMToken()
                } else {
                }
            }
        }
    }

    fun initializeFCMToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                
                // Get current user ID from SharedPreferences
                val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("current_user_id", null)
                
                if (userId != null) {
                    notificationService.updateUserFcmToken(userId, token)
                } else {
                    // Store token temporarily until user logs in
                    sharedPrefs.edit()
                        .putString("pending_fcm_token", token)
                        .apply()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun updateTokenForUser(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("🔧 FCMTokenManager: Starting token update for user: $userId")
                val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
                
                // Check if there's a pending token to update
                val pendingToken = sharedPrefs.getString("pending_fcm_token", null)
                println("🔧 FCMTokenManager: Pending token found: ${pendingToken?.take(20)}...")
                
                val token = if (pendingToken != null) {
                    // Use pending token and clear it
                    sharedPrefs.edit().remove("pending_fcm_token").apply()
                    println("🔧 FCMTokenManager: Using pending token")
                    pendingToken
                } else {
                    // Get fresh token
                    println("🔧 FCMTokenManager: Getting fresh FCM token...")
                    val freshToken = FirebaseMessaging.getInstance().token.await()
                    println("🔧 FCMTokenManager: Fresh token retrieved: ${freshToken.take(20)}...")
                    freshToken
                }
                
                println("🔧 FCMTokenManager: Updating token in Supabase for user: $userId")
                notificationService.updateUserFcmToken(userId, token)
                
                // Store current user ID for future token updates
                sharedPrefs.edit()
                    .putString("current_user_id", userId)
                    .apply()
                
                // Removed println: "✅ FCMTokenManager: FCM token updated successfully for user: $userId"
            } catch (e: Exception) {
                // Removed println: "❌ FCMTokenManager: Failed to update FCM token for user: $userId - Error: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    fun clearUserToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("🗑️ FCMTokenManager: Clearing FCM token from database on user logout")
                
                val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
                val currentUserId = sharedPrefs.getString("current_user_id", null)
                
                if (currentUserId != null) {
                    // Clear the FCM token from the user's profile to prevent cross-user notifications
                    notificationService.clearUserFcmToken(currentUserId)
                    // Removed println: "✅ FCMTokenManager: FCM token cleared from database for user: $currentUserId"
                } else {
                    // Removed println: "⚠️ FCMTokenManager: No current user ID found during token cleanup"
                }
                
                sharedPrefs.edit()
                    .remove("current_user_id")
                    .remove("pending_fcm_token")
                    .apply()
                
            } catch (e: Exception) {
                // Removed println: "❌ FCMTokenManager: Error clearing FCM token: ${e.message}"
            }
        }
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Notifications are enabled by default for older versions
        }
    }
}