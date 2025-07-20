package com.ausgetrunken.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
                    Log.d(TAG, "Notification permission already granted")
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
                    Log.d(TAG, "Notification permission granted")
                    initializeFCMToken()
                } else {
                    Log.w(TAG, "Notification permission denied")
                }
            }
        }
    }

    fun initializeFCMToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM token retrieved: $token")
                
                // Get current user ID from SharedPreferences
                val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("current_user_id", null)
                
                if (userId != null) {
                    notificationService.updateUserFcmToken(userId, token)
                    Log.d(TAG, "FCM token updated for user: $userId")
                } else {
                    Log.w(TAG, "No user ID found, storing token for later update")
                    // Store token temporarily until user logs in
                    sharedPrefs.edit()
                        .putString("pending_fcm_token", token)
                        .apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retrieve FCM token", e)
            }
        }
    }

    fun updateTokenForUser(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
                
                // Check if there's a pending token to update
                val pendingToken = sharedPrefs.getString("pending_fcm_token", null)
                
                val token = if (pendingToken != null) {
                    // Use pending token and clear it
                    sharedPrefs.edit().remove("pending_fcm_token").apply()
                    pendingToken
                } else {
                    // Get fresh token
                    FirebaseMessaging.getInstance().token.await()
                }
                
                notificationService.updateUserFcmToken(userId, token)
                
                // Store current user ID for future token updates
                sharedPrefs.edit()
                    .putString("current_user_id", userId)
                    .apply()
                
                Log.d(TAG, "FCM token updated for user: $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token for user: $userId", e)
            }
        }
    }

    fun clearUserToken() {
        val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .remove("current_user_id")
            .apply()
        Log.d(TAG, "User FCM token cleared")
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