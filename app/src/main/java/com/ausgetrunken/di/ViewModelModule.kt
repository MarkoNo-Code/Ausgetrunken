package com.ausgetrunken.di

import com.ausgetrunken.ui.auth.LoginViewModel
import com.ausgetrunken.ui.auth.RegisterViewModel
import com.ausgetrunken.ui.profile.ProfileViewModel
import com.ausgetrunken.ui.splash.SplashViewModel
import com.ausgetrunken.ui.wineyard.AddWineyardViewModel
import com.ausgetrunken.ui.wineyard.WineyardDetailViewModel
import com.ausgetrunken.ui.wines.ManageWinesViewModel
import com.ausgetrunken.ui.wines.WineDetailViewModel
import com.ausgetrunken.ui.wines.AddWineViewModel
import com.ausgetrunken.ui.wines.EditWineViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { SplashViewModel(get(), get()) }
    viewModel { AddWineyardViewModel(get(), get(), get()) }
    viewModel { WineyardDetailViewModel(get(), get(), get()) }
    viewModel { ManageWinesViewModel(get(), get(), get()) }
    viewModel { WineDetailViewModel(get()) }
    viewModel { AddWineViewModel(get(), get()) }
    viewModel { EditWineViewModel(get(), get()) }
}