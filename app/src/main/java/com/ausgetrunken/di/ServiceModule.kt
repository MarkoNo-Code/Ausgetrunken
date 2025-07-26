package com.ausgetrunken.di

import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.ImageUploadService
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import com.ausgetrunken.domain.service.WineyardPhotoService
import com.ausgetrunken.domain.service.DatabaseInspectionService
import com.ausgetrunken.domain.service.SimplePhotoStorage
import com.ausgetrunken.domain.service.PhotoUploadStatusStorage
import com.ausgetrunken.domain.service.BackgroundPhotoUploadService
import com.ausgetrunken.domain.service.NewWineyardPhotoService
import com.ausgetrunken.notifications.FCMTokenManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val serviceModule = module {
    // Domain Services
    factory { AuthService(get(), get()) }
    factory { WineService(get()) }
    factory { WineyardService(get()) }
    factory { WineyardSubscriptionService(get()) }
    factory { NotificationService(get()) }
    factory { ImageUploadService(get()) }
    
    // Legacy photo service (keep for now during transition)
    factory { WineyardPhotoService(get(), get(), androidContext(), get()) }
    factory { DatabaseInspectionService(get(), get()) }
    
    // New simplified photo services
    single { SimplePhotoStorage(androidContext()) }
    single { PhotoUploadStatusStorage(androidContext()) }
    single { BackgroundPhotoUploadService(androidContext(), get(), get()) }
    factory { NewWineyardPhotoService(androidContext(), get(), get(), get()) }
    
    // FCM Token Manager  
    single { FCMTokenManager(get(), get<NotificationService>()) }
}