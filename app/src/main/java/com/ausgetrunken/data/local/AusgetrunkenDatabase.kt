package com.ausgetrunken.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.ausgetrunken.data.local.dao.*
import com.ausgetrunken.data.local.entities.*

@Database(
    entities = [
        UserEntity::class,
        WineyardEntity::class,
        WineEntity::class,
        SubscriptionEntity::class,
        WineyardSubscriptionEntity::class,
        NotificationEntity::class,
        NotificationDeliveryEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AusgetrunkenDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun wineyardDao(): WineyardDao
    abstract fun wineDao(): WineDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun wineyardSubscriptionDao(): WineyardSubscriptionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun notificationDeliveryDao(): NotificationDeliveryDao

    companion object {
        const val DATABASE_NAME = "ausgetrunken_database"
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add fullStockQuantity to wines table
                database.execSQL("ALTER TABLE wines ADD COLUMN fullStockQuantity INTEGER NOT NULL DEFAULT 0")
                
                // Add fcmToken to users table
                database.execSQL("ALTER TABLE users ADD COLUMN fcmToken TEXT")
                
                // Update existing wines to set fullStockQuantity = stockQuantity
                database.execSQL("UPDATE wines SET fullStockQuantity = stockQuantity")
            }
        }
    }
}