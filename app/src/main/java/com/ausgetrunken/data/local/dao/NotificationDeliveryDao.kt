package com.ausgetrunken.data.local.dao

import androidx.room.*
import com.ausgetrunken.data.local.entities.NotificationDeliveryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDeliveryDao {
    
    @Query("SELECT * FROM notification_deliveries WHERE userId = :userId ORDER BY deliveredAt DESC")
    fun getUserNotificationDeliveriesFlow(userId: String): Flow<List<NotificationDeliveryEntity>>
    
    @Query("SELECT * FROM notification_deliveries WHERE userId = :userId ORDER BY deliveredAt DESC")
    suspend fun getUserNotificationDeliveries(userId: String): List<NotificationDeliveryEntity>
    
    @Query("SELECT * FROM notification_deliveries WHERE userId = :userId AND readAt IS NULL ORDER BY deliveredAt DESC")
    fun getUnreadNotificationDeliveriesFlow(userId: String): Flow<List<NotificationDeliveryEntity>>
    
    @Query("SELECT * FROM notification_deliveries WHERE userId = :userId AND readAt IS NULL ORDER BY deliveredAt DESC")
    suspend fun getUnreadNotificationDeliveries(userId: String): List<NotificationDeliveryEntity>
    
    @Query("SELECT COUNT(*) FROM notification_deliveries WHERE userId = :userId AND readAt IS NULL")
    fun getUnreadNotificationCountFlow(userId: String): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM notification_deliveries WHERE userId = :userId AND readAt IS NULL")
    suspend fun getUnreadNotificationCount(userId: String): Int
    
    @Query("SELECT * FROM notification_deliveries WHERE notificationId = :notificationId")
    suspend fun getNotificationDeliveries(notificationId: String): List<NotificationDeliveryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelivery(delivery: NotificationDeliveryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeliveries(deliveries: List<NotificationDeliveryEntity>)
    
    @Update
    suspend fun updateDelivery(delivery: NotificationDeliveryEntity)
    
    @Query("UPDATE notification_deliveries SET readAt = :readAt WHERE id = :deliveryId")
    suspend fun markAsRead(deliveryId: String, readAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE notification_deliveries SET readAt = :readAt WHERE userId = :userId AND readAt IS NULL")
    suspend fun markAllAsRead(userId: String, readAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteDelivery(delivery: NotificationDeliveryEntity)
    
    @Query("DELETE FROM notification_deliveries WHERE notificationId = :notificationId")
    suspend fun deleteNotificationDeliveries(notificationId: String)
}