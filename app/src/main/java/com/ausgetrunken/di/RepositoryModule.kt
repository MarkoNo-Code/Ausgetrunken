package com.ausgetrunken.di

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.TokenStorage
import com.ausgetrunken.data.repository.NotificationRepositoryImpl
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineRepository
import com.ausgetrunken.data.repository.WineyardRepository
import com.ausgetrunken.data.repository.WineyardSubscriptionRepository
import com.ausgetrunken.domain.repository.AuthenticatedRepository
import com.ausgetrunken.domain.repository.NotificationRepository
import com.ausgetrunken.domain.service.NotificationService
import com.ausgetrunken.domain.service.WineService
import com.ausgetrunken.domain.service.WineyardSubscriptionService
import com.ausgetrunken.domain.usecase.GetLowStockWinesUseCase
import com.ausgetrunken.domain.usecase.GetWineyardSubscribersUseCase
import com.ausgetrunken.domain.usecase.SendNotificationUseCase
import com.ausgetrunken.domain.util.NetworkConnectivityManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single { TokenStorage(androidContext()) }
    single { NetworkConnectivityManager(androidContext()) }
    single { SupabaseAuthRepository(get(), get(), get()) }
    single { UserRepository(get(), get(), get()) }
    single { WineRepository(get(), get(), get(), get()) }
    single { WineyardRepository(get(), get(), get(), get()) }
    single { WineyardSubscriptionRepository(get(), get(), get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get(), get(), get()) }
    single { AuthenticatedRepository(get()) }
    
    // Use Cases
    factory { GetLowStockWinesUseCase(get<WineService>()) }
    factory { GetWineyardSubscribersUseCase(get<WineyardSubscriptionService>()) }
    factory { SendNotificationUseCase(get<NotificationService>()) }
    
}