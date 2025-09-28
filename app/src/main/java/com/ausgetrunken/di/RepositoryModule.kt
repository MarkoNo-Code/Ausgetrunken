package com.ausgetrunken.di

import com.ausgetrunken.auth.SimpleAuthManager
import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.TokenStorage
import com.ausgetrunken.data.repository.NotificationRepositoryImpl
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineRepository
import com.ausgetrunken.data.repository.WineryRepository
import com.ausgetrunken.data.repository.WinerySubscriptionRepository
import com.ausgetrunken.domain.repository.NotificationRepository
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WinerySubscriptionService
import com.ausgetrunken.domain.usecase.GetLowStockWinesUseCase
import com.ausgetrunken.domain.usecase.GetLowStockWinesForOwnerUseCase
import com.ausgetrunken.domain.usecase.GetWinerySubscribersUseCase
import com.ausgetrunken.domain.usecase.SendNotificationUseCase
import com.ausgetrunken.domain.util.NetworkConnectivityManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single { TokenStorage(androidContext()) }
    single { NetworkConnectivityManager(androidContext()) }
    single { SupabaseAuthRepository(get(), get(), get(), get(org.koin.core.qualifier.named("serviceRole"))) }
    single { SimpleAuthManager(get(), get()) }
    single { UserRepository(get(), get(), get()) }
    single { WineRepository(get(), get(), get(), get()) }
    single { WineryRepository(get(), get(), get(), get()) }
    single { WinerySubscriptionRepository(get(), get(), get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get(), get()) }
    
    // Use Cases
    factory { GetLowStockWinesUseCase(get<WineService>()) }
    factory { GetLowStockWinesForOwnerUseCase(get<WineService>(), get()) }
    factory { GetWinerySubscribersUseCase(get<WinerySubscriptionService>()) }
    factory { SendNotificationUseCase(get<NotificationService>()) }
    
}