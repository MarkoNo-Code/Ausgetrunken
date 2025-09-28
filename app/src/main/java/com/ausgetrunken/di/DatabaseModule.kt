package com.ausgetrunken.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.ausgetrunken.data.local.AusgetrunkenDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AusgetrunkenDatabase::class.java,
            AusgetrunkenDatabase.DATABASE_NAME
        )
        .addMigrations(
            AusgetrunkenDatabase.MIGRATION_2_3,
            AusgetrunkenDatabase.MIGRATION_3_4,
            AusgetrunkenDatabase.MIGRATION_4_5,
            AusgetrunkenDatabase.MIGRATION_5_6,
            AusgetrunkenDatabase.MIGRATION_6_7
        )
        // CRITICAL FIX: Use TRUNCATE mode for immediate consistency instead of WAL isolation issues
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        // Enable to reset database if needed for testing
        // .fallbackToDestructiveMigration()
        .build()
    }

    single { get<AusgetrunkenDatabase>().userDao() }
    single { get<AusgetrunkenDatabase>().wineryDao() }
    single { get<AusgetrunkenDatabase>().wineDao() }
    single { get<AusgetrunkenDatabase>().subscriptionDao() }
    single { get<AusgetrunkenDatabase>().winerySubscriptionDao() }
    single { get<AusgetrunkenDatabase>().notificationDao() }
    single { get<AusgetrunkenDatabase>().notificationDeliveryDao() }
    single { get<AusgetrunkenDatabase>().wineryPhotoDao() }
    single { get<AusgetrunkenDatabase>().winePhotoDao() }
}