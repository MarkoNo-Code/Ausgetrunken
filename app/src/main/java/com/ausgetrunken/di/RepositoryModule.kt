package com.ausgetrunken.di

import com.ausgetrunken.auth.SupabaseAuthRepository
import com.ausgetrunken.data.repository.UserRepository
import com.ausgetrunken.data.repository.WineyardRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { SupabaseAuthRepository(get(), get()) }
    single { UserRepository(get(), get()) }
    single { WineyardRepository(get(), get()) }
}