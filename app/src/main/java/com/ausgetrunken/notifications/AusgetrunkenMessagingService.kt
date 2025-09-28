package com.ausgetrunken.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ausgetrunken.MainActivity
import com.ausgetrunken.R
import com.ausgetrunken.domain.repository.NotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AusgetrunkenMessagingService : FirebaseMessagingService(), KoinComponent {

    private val notificationRepository: NotificationRepository by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "AusgetrunkenFCM"
        private const val CHANNEL_ID = "ausgetrunken_notifications"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        println("🔔 FCM: Message received from: ${remoteMessage.from}")
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        println("🔔 FCM: Full message details:")
        println("   - Message ID: ${remoteMessage.messageId}")
        println("   - Data payload: ${remoteMessage.data}")
        println("   - Notification: ${remoteMessage.notification}")

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            println("🔔 FCM: Processing data payload: ${remoteMessage.data}")
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Handle notification payload
        remoteMessage.notification?.let {
            println("🔔 FCM: Processing notification payload:")
            println("   - Title: ${it.title}")
            println("   - Body: ${it.body}")
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(it.title, it.body, remoteMessage.data)
        }
        
        // If no notification payload, create one from data
        if (remoteMessage.notification == null && remoteMessage.data.isNotEmpty()) {
            println("🔔 FCM: No notification payload, creating from data")
            val title = remoteMessage.data["title"] ?: "Ausgetrunken"
            val body = remoteMessage.data["message"] ?: "You have a new notification"
            showNotification(title, body, remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        
        // Send token to server
        serviceScope.launch {
            try {
                // Get current user ID from SharedPreferences or your auth system
                val sharedPrefs = getSharedPreferences("ausgetrunken_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("current_user_id", null)
                
                if (userId != null) {
                    notificationRepository.updateUserFcmToken(userId, token)
                    Log.d(TAG, "FCM token updated for user: $userId")
                } else {
                    Log.w(TAG, "No user ID found, cannot update FCM token")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
            }
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val notificationType = data["notificationType"]
        val wineryId = data["wineryId"]
        val wineId = data["wineId"]
        
        Log.d(TAG, "Notification type: $notificationType, Winery: $wineryId, Wine: $wineId")
        
        // You can add custom logic here based on notification type
        when (notificationType) {
            "LOW_STOCK" -> {
                // Handle low stock notification
                Log.d(TAG, "Handling low stock notification")
            }
            "CRITICAL_STOCK" -> {
                // Handle critical stock notification
                Log.d(TAG, "Handling critical stock notification")
            }
            "NEW_WINE" -> {
                // Handle new wine notification
                Log.d(TAG, "Handling new wine notification")
            }
            "GENERAL" -> {
                // Handle general notification
                Log.d(TAG, "Handling general notification")
            }
        }
    }

    private fun showNotification(title: String?, body: String?, data: Map<String, String>) {
        println("🔔 FCM: showNotification called")
        println("   - Title: $title")
        println("   - Body: $body")
        println("   - Data: $data")
        
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                
                // Add data to intent if needed
                data["wineryId"]?.let { putExtra("wineryId", it) }
                data["wineId"]?.let { putExtra("wineId", it) }
                data["notificationType"]?.let { putExtra("notificationType", it) }
            }

            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            println("🔔 FCM: Creating notification with channel: $CHANNEL_ID")
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title ?: "Ausgetrunken")
                .setContentText(body ?: "You have a new notification")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Use system icon as fallback
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Higher priority
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound, vibration, lights

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if notifications are enabled
            if (notificationManager.areNotificationsEnabled()) {
                println("🔔 FCM: Notifications are enabled, showing notification")
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                println("✅ FCM: Notification displayed successfully")
            } else {
                println("❌ FCM: Notifications are disabled for this app")
            }
            
        } catch (e: Exception) {
            println("❌ FCM: Error showing notification: ${e.message}")
            Log.e(TAG, "Failed to show notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Ausgetrunken Notifications"
            val descriptionText = "Notifications for wine stock updates and announcements"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}