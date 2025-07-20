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
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Handle notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(it.title, it.body, remoteMessage.data)
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
        val wineyardId = data["wineyardId"]
        val wineId = data["wineId"]
        
        Log.d(TAG, "Notification type: $notificationType, Wineyard: $wineyardId, Wine: $wineId")
        
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            
            // Add data to intent if needed
            data["wineyardId"]?.let { putExtra("wineyardId", it) }
            data["wineId"]?.let { putExtra("wineId", it) }
            data["notificationType"]?.let { putExtra("notificationType", it) }
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title ?: "Ausgetrunken")
            .setContentText(body ?: "You have a new notification")
            .setSmallIcon(R.drawable.ic_wine_glass)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
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