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
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
            null
        }
    }

    fun testTokenInSharedPrefs(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
        val userId = sharedPrefs.getString("current_user_id", null)
        val pendingToken = sharedPrefs.getString("pending_fcm_token", null)
        
        Log.d(TAG, "Current user ID: $userId")
        Log.d(TAG, "Pending token: $pendingToken")
        
        return pendingToken
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