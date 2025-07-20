package com.ausgetrunken.di

import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import com.ausgetrunken.notifications.FCMTokenManager
import org.koin.dsl.module

val serviceModule = module {
    // Domain Services
    factory { AuthService(get(), get()) }
    factory { WineService(get()) }
    factory { WineyardService(get()) }
    factory { WineyardSubscriptionService(get()) }
    factory { NotificationService(get()) }
    
    // FCM Token Manager  
    single { FCMTokenManager(get(), get<NotificationService>()) }
}