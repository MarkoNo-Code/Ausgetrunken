package com.ausgetrunken.di

import androidx.room.Room
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
        .fallbackToDestructiveMigration() // Allow destructive migration during development
        .build()
    }

    single { get<AusgetrunkenDatabase>().userDao() }
    single { get<AusgetrunkenDatabase>().wineyardDao() }
    single { get<AusgetrunkenDatabase>().wineDao() }
    single { get<AusgetrunkenDatabase>().subscriptionDao() }
    single { get<AusgetrunkenDatabase>().wineyardSubscriptionDao() }
    single { get<AusgetrunkenDatabase>().notificationDao() }
    single { get<AusgetrunkenDatabase>().notificationDeliveryDao() }
}