package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.NotificationEntity
import com.ausgetrunken.data.local.entities.NotificationType
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    
    @Query("SELECT * FROM notifications WHERE wineryId = :wineryId ORDER BY createdAt DESC")
    fun getWineryNotificationsFlow(wineryId: String): Flow<List<NotificationEntity>>
    
    @Query("SELECT * FROM notifications WHERE wineryId = :wineryId ORDER BY createdAt DESC")
    suspend fun getWineryNotifications(wineryId: String): List<NotificationEntity>
    
    @Query("SELECT * FROM notifications WHERE id = :notificationId LIMIT 1")
    suspend fun getNotificationById(notificationId: String): NotificationEntity?
    
    @Query("SELECT * FROM notifications WHERE wineryId = :wineryId AND notificationType = :type ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getNotificationsByType(wineryId: String, type: NotificationType, limit: Int = 10): List<NotificationEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)
    
    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)
    
    @Query("DELETE FROM notifications WHERE id = :notificationId")
    suspend fun deleteNotificationById(notificationId: String)
    
    @Query("DELETE FROM notifications WHERE wineryId = :wineryId")
    suspend fun deleteWineryNotifications(wineryId: String)
}