package com.ausgetrunken.di

import com.ausgetrunken.domain.usecase.*
import org.koin.dsl.module

val useCaseModule = module {
    // Auth Use Cases
    factory { SignUpUseCase(get(), get()) }
    factory { SignInUseCase(get(), get()) }
    factory { SignOutUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { CheckUserTypeUseCase(get()) }
    factory { RestoreSessionUseCase(get()) }

    // Wineyard Use Cases
    factory { GetAllWineyardsUseCase(get()) }
    factory { GetWineyardsByOwnerUseCase(get()) }
    factory { GetWineyardByIdUseCase(get()) }
    factory { CreateWineyardUseCase(get()) }
    factory { UpdateWineyardUseCase(get()) }
    factory { DeleteWineyardUseCase(get()) }
    factory { GetNearbyWineyardsUseCase(get()) }
    factory { SyncWineyardsUseCase(get()) }

    // Wine Use Cases
    factory { GetAllWinesUseCase(get()) }
    factory { GetWinesByWineyardUseCase(get()) }
    factory { GetWineByIdUseCase(get()) }
    factory { CreateWineUseCase(get()) }
    factory { UpdateWineUseCase(get()) }
    factory { DeleteWineUseCase(get()) }
    factory { SyncWinesUseCase(get()) }
    factory { GetWinesByWineyardFromSupabaseUseCase(get()) }

    // Notification Use Cases
    // factory { CheckLowStockNotificationsUseCase(get(), get(), get()) }
    // factory { SendStockAlertToOwnerUseCase(get()) }
}