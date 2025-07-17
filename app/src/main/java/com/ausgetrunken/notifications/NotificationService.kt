package com.ausgetrunken.notifications

interface NotificationService {
    suspend fun sendLowStockNotification(
        userId: String,
        wineName: String,
        stockQuantity: Int
    )
    
    suspend fun sendOwnerStockAlert(
        ownerId: String,
        wineName: String,
        stockQuantity: Int,
        threshold: Int
    )
}

class NotificationServiceImpl : NotificationService {
    override suspend fun sendLowStockNotification(
        userId: String,
        wineName: String,
        stockQuantity: Int
    ) {
        // TODO: Implement notification sending logic
        println("Low stock notification for user $userId: $wineName has $stockQuantity units left")
    }
    
    override suspend fun sendOwnerStockAlert(
        ownerId: String,
        wineName: String,
        stockQuantity: Int,
        threshold: Int
    ) {
        // TODO: Implement notification sending logic
        println("Stock alert for owner $ownerId: $wineName has $stockQuantity units (threshold: $threshold)")
    }
}