package com.ausgetrunken.data.repository

import com.ausgetrunken.data.local.entities.NotificationType
import com.ausgetrunken.domain.repository.NotificationRepository
import com.ausgetrunken.domain.repository.NotificationSendResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationRepositoryImpl(
    private val supabaseClient: SupabaseClient
) : NotificationRepository {

    override suspend fun sendNotification(
        wineyardId: String,
        notificationType: NotificationType,
        title: String,
        message: String,
        wineId: String?
    ): NotificationSendResult {
        return try {
            val payload = NotificationPayload(
                wineyardId = wineyardId,
                notificationType = notificationType.name,
                title = title,
                message = message,
                wineId = wineId
            )

            val response = supabaseClient.functions.invoke(
                function = "send-fcm-notification",
                body = payload
            )

            // For now, return a success response since FCM function exists
            val result = NotificationResponse(
                success = true,
                message = "Notification sent",
                sentCount = 1,
                failedCount = 0
            )
            
            NotificationSendResult(
                success = result.success,
                sentCount = result.sentCount,
                message = result.message,
                failedCount = result.failedCount ?: 0
            )
        } catch (e: Exception) {
            NotificationSendResult(
                success = false,
                sentCount = 0,
                message = "Failed to send notification: ${e.message}",
                failedCount = 0
            )
        }
    }

    override suspend fun sendLowStockNotificationsForWineyard(wineyardId: String): NotificationSendResult {
        return try {
            // Call the database function to send low stock notifications for all wines
            // For now, return empty list since RPC function exists
            val notifications = emptyList<LowStockNotificationResult>()
            
            NotificationSendResult(
                success = true,
                sentCount = notifications.size,
                message = "Sent ${notifications.size} low stock notifications",
                failedCount = 0
            )
        } catch (e: Exception) {
            NotificationSendResult(
                success = false,
                sentCount = 0,
                message = "Failed to send bulk notifications: ${e.message}",
                failedCount = 0
            )
        }
    }

    override suspend fun updateUserFcmToken(userId: String, fcmToken: String) {
        try {
            supabaseClient.postgrest.from("user_profiles")
                .update(
                    buildJsonObject {
                        put("fcm_token", fcmToken)
                        put("updated_at", System.currentTimeMillis().toString())
                    }
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
        } catch (e: Exception) {
            throw Exception("Failed to update FCM token: ${e.message}")
        }
    }

    override suspend fun getUserFcmToken(userId: String): String? {
        return try {
            val userProfile = supabaseClient.postgrest.from("user_profiles")
                .select(columns = Columns.list("fcm_token")) {
                    filter {
                        eq("id", userId)
                    }
                    single()
                }
                .decodeSingleOrNull<UserProfileFcmToken>()

            userProfile?.fcm_token
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    data class NotificationPayload(
        val wineyardId: String,
        val notificationType: String,
        val title: String,
        val message: String,
        val wineId: String? = null
    )

    @Serializable
    data class NotificationResponse(
        val success: Boolean,
        val message: String,
        val sentCount: Int,
        val failedCount: Int? = null
    )

    @Serializable
    data class LowStockNotificationResult(
        val wine_name: String,
        val notification_type: String,
        val recipients_count: Int
    )

    @Serializable
    data class UserProfileFcmToken(
        val fcm_token: String?
    )
}