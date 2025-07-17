package com.ausgetrunken.di

import com.ausgetrunken.ui.auth.LoginViewModel
import com.ausgetrunken.ui.auth.RegisterViewModel
import com.ausgetrunken.ui.profile.ProfileViewModel
import com.ausgetrunken.ui.wineyard.WineyardDetailViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { WineyardDetailViewModel(get(), get()) }
}