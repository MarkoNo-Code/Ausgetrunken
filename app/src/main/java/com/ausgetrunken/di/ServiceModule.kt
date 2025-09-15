package com.ausgetrunken.di

import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.ImageUploadService
import com.ausgetrunken.domain.service.ImageCompressionService
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.ProfilePictureService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import com.ausgetrunken.domain.service.WineyardPhotoService
import com.ausgetrunken.domain.service.DatabaseInspectionService
import com.ausgetrunken.domain.service.SimplePhotoStorage
import com.ausgetrunken.domain.service.PhotoUploadStatusStorage
import com.ausgetrunken.domain.service.BackgroundPhotoUploadService
import com.ausgetrunken.domain.service.NewWineyardPhotoService
import com.ausgetrunken.domain.service.WinePhotoService
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
    factory { ImageUploadService(get(org.koin.core.qualifier.named("serviceRole"))) }
    factory { ImageCompressionService(androidContext()) }
    factory { ProfilePictureService(androidContext(), get(org.koin.core.qualifier.named("serviceRole"))) }
    
    // Legacy photo service (keep for now during transition)
    factory { WineyardPhotoService(get(), get(), get(), androidContext(), get(), get()) }
    factory { DatabaseInspectionService(get(), get()) }
    
    // New simplified photo services
    single { SimplePhotoStorage(androidContext()) }
    single { PhotoUploadStatusStorage(androidContext()) }
    single { BackgroundPhotoUploadService(androidContext(), get(), get()) }
    factory { NewWineyardPhotoService(androidContext(), get(), get(), get()) }
    factory { WinePhotoService(androidContext(), get(), get(), get()) }
    
    // FCM Token Manager  
    single { FCMTokenManager(get(), get<NotificationService>()) }
}