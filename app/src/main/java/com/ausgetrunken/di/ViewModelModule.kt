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
import com.ausgetrunken.ui.customer.CustomerLandingViewModel
import com.ausgetrunken.ui.customer.CustomerProfileViewModel
import com.ausgetrunken.ui.customer.SubscriptionsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { SplashViewModel(get()) }
    viewModel { AddWineyardViewModel(get(), get(), get()) }
    viewModel { WineyardDetailViewModel(get(), get(), get(), get()) }
    viewModel { ManageWinesViewModel(get(), get(), get()) }
    viewModel { WineDetailViewModel(get()) }
    viewModel { AddWineViewModel(get(), get(), get()) }
    viewModel { EditWineViewModel(get(), get(), get()) }
    viewModel { CustomerLandingViewModel(get(), get(), get(), get()) }
    viewModel { CustomerProfileViewModel(get()) }
    viewModel { SubscriptionsViewModel(get(), get(), get()) }
}