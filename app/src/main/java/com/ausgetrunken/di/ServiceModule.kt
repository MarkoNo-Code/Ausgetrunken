package com.ausgetrunken.di

import com.ausgetrunken.domain.service.AuthService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardService
import org.koin.dsl.module

val serviceModule = module {
    // Domain Services
    factory { AuthService(get(), get()) }
    factory { WineService(get()) }
    factory { WineyardService(get()) }

    // Notification Use Cases (keep as individual classes for now)
    // factory { CheckLowStockNotificationsUseCase(get(), get(), get()) }
    // factory { SendStockAlertToOwnerUseCase(get()) }
}