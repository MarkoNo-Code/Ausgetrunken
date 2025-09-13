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
        NotificationDeliveryEntity::class,
        WineyardPhotoEntity::class
    ],
    version = 5,
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
    abstract fun wineyardPhotoDao(): WineyardPhotoDao

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
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("DatabaseMigration", "MIGRATION 3->4: Starting migration...")
                
                // Create wineyard_photos table
                val createTableSQL = """
                    CREATE TABLE IF NOT EXISTS `wineyard_photos` (
                        `id` TEXT NOT NULL,
                        `wineyard_id` TEXT NOT NULL,
                        `local_path` TEXT,
                        `remote_url` TEXT,
                        `display_order` INTEGER NOT NULL DEFAULT 0,
                        `upload_status` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                        `file_size` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`wineyard_id`) REFERENCES `wineyards`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent()
                
                android.util.Log.d("DatabaseMigration", "MIGRATION 3->4: Executing SQL: $createTableSQL")
                database.execSQL(createTableSQL)
                
                // Create index for wineyard_id for faster queries
                val createIndexSQL = "CREATE INDEX IF NOT EXISTS `index_wineyard_photos_wineyard_id` ON `wineyard_photos` (`wineyard_id`)"
                android.util.Log.d("DatabaseMigration", "MIGRATION 3->4: Creating index: $createIndexSQL")
                database.execSQL(createIndexSQL)
                
                android.util.Log.d("DatabaseMigration", "MIGRATION 3->4: Migration completed successfully!")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("DatabaseMigration", "MIGRATION 4->5: Starting migration...")
                
                // Add fullName column to users table
                database.execSQL("ALTER TABLE users ADD COLUMN fullName TEXT")
                
                android.util.Log.d("DatabaseMigration", "MIGRATION 4->5: Migration completed successfully!")
            }
        }
    }
}