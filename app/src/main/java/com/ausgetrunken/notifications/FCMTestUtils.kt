package com.ausgetrunken.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FCMTestUtils {
    private const val TAG = "FCMTestUtils"

    suspend fun getCurrentToken(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Current FCM token: $token")
            println("🎯 FCM Test Utils: Current Token = $token")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
            println("❌ FCM Test Utils: Failed to get token - ${e.message}")
            null
        }
    }

    fun testTokenInSharedPrefs(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
        val userId = sharedPrefs.getString("current_user_id", null)
        val pendingToken = sharedPrefs.getString("pending_fcm_token", null)
        
        Log.d(TAG, "Current user ID: $userId")
        Log.d(TAG, "Pending token: $pendingToken")
        
        println("🔍 FCM Test Utils: User ID = $userId")
        println("🔍 FCM Test Utils: Pending token = ${pendingToken?.take(20)}...")
        
        return pendingToken
    }
    
    fun logCompleteDebugInfo(context: Context) {
        println("🔧 === FCM Debug Information ===")
        
        // Check SharedPreferences
        val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
        val userId = sharedPrefs.getString("current_user_id", null)
        val pendingToken = sharedPrefs.getString("pending_fcm_token", null)
        
        println("🔍 Current User ID: $userId")
        println("🔍 Pending FCM Token: ${pendingToken?.take(20)}...")
        
        // Check notification permissions
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notificationsEnabled = manager.areNotificationsEnabled()
        println("🔔 Notifications Enabled: $notificationsEnabled")
        
        // Get current token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                println("🎯 Current FCM Token: $token")
                Log.d(TAG, "Complete debug - FCM token: $token")
            } else {
                println("❌ Failed to get current FCM token: ${task.exception?.message}")
                Log.e(TAG, "Failed to get FCM token in debug", task.exception)
            }
        }
        
        println("🔧 === End FCM Debug ===")
    }

    fun subscribeToTestTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("test_notifications")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to test topic"
                if (!task.isSuccessful) {
                    msg = "Failed to subscribe to test topic"
                }
                Log.d(TAG, msg)
            }
    }

    fun unsubscribeFromTestTopic() {
        FirebaseMessaging.getInstance().unsubscribeFromTopic("test_notifications")
            .addOnCompleteListener { task ->
                var msg = "Unsubscribed from test topic"
                if (!task.isSuccessful) {
                    msg = "Failed to unsubscribe from test topic"
                }
                Log.d(TAG, msg)
            }
    }
}