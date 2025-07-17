package com.ausgetrunken.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.ausgetrunken.data.local.dao.*
import com.ausgetrunken.data.local.entities.*

@Database(
    entities = [
        UserEntity::class,
        WineyardEntity::class,
        WineEntity::class,
        SubscriptionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AusgetrunkenDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun wineyardDao(): WineyardDao
    abstract fun wineDao(): WineDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val DATABASE_NAME = "ausgetrunken_database"
    }
}