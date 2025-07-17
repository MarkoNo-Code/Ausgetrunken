package com.ausgetrunken.di

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.local.TokenStorage
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineRepository
import com.ausgetrunken.data.repository.WineyardRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single { TokenStorage(androidContext()) }
    single { SupabaseAuthRepository(get(), get(), get()) }
    single { UserRepository(get(), get()) }
    single { WineRepository(get(), get()) }
    single { WineyardRepository(get(), get()) }
}