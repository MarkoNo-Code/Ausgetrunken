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
        WineryEntity::class,
        WineEntity::class,
        SubscriptionEntity::class,
        WinerySubscriptionEntity::class,
        NotificationEntity::class,
        NotificationDeliveryEntity::class,
        WineryPhotoEntity::class,
        WinePhotoEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AusgetrunkenDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun wineryDao(): WineryDao
    abstract fun wineDao(): WineDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun winerySubscriptionDao(): WinerySubscriptionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun notificationDeliveryDao(): NotificationDeliveryDao
    abstract fun wineryPhotoDao(): WineryPhotoDao
    abstract fun winePhotoDao(): WinePhotoDao

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
                
                // Create winery_photos table
                val createTableSQL = """
                    CREATE TABLE IF NOT EXISTS `winery_photos` (
                        `id` TEXT NOT NULL,
                        `winery_id` TEXT NOT NULL,
                        `local_path` TEXT,
                        `remote_url` TEXT,
                        `display_order` INTEGER NOT NULL DEFAULT 0,
                        `upload_status` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                        `file_size` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`winery_id`) REFERENCES `wineries`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent()
                
                android.util.Log.d("DatabaseMigration", "MIGRATION 3->4: Executing SQL: $createTableSQL")
                database.execSQL(createTableSQL)
                
                // Create index for winery_id for faster queries
                val createIndexSQL = "CREATE INDEX IF NOT EXISTS `index_winery_photos_winery_id` ON `winery_photos` (`winery_id`)"
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
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("DatabaseMigration", "MIGRATION 5->6: Starting migration...")

                // Add profilePictureUrl column to users table
                database.execSQL("ALTER TABLE users ADD COLUMN profilePictureUrl TEXT")

                android.util.Log.d("DatabaseMigration", "MIGRATION 5->6: Migration completed successfully!")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("DatabaseMigration", "MIGRATION 6->7: Starting migration...")

                // Create wine_photos table
                val createTableSQL = """
                    CREATE TABLE IF NOT EXISTS `wine_photos` (
                        `id` TEXT NOT NULL,
                        `wine_id` TEXT NOT NULL,
                        `local_path` TEXT,
                        `remote_url` TEXT,
                        `display_order` INTEGER NOT NULL DEFAULT 0,
                        `upload_status` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                        `file_size` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`wine_id`) REFERENCES `wines`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent()

                android.util.Log.d("DatabaseMigration", "MIGRATION 6->7: Executing SQL: $createTableSQL")
                database.execSQL(createTableSQL)

                // Create index for wine_id for faster queries
                val createIndexSQL = "CREATE INDEX IF NOT EXISTS `index_wine_photos_wine_id` ON `wine_photos` (`wine_id`)"
                android.util.Log.d("DatabaseMigration", "MIGRATION 6->7: Creating index: $createIndexSQL")
                database.execSQL(createIndexSQL)

                android.util.Log.d("DatabaseMigration", "MIGRATION 6->7: Migration completed successfully!")
            }
        }
    }
}