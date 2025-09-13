package com.ausgetrunken.di

import com.ausgetrunken.ui.auth.AuthViewModel
import com.ausgetrunken.ui.profile.OwnerProfileViewModel
import com.ausgetrunken.ui.splash.SplashViewModel
import com.ausgetrunken.ui.wineyard.AddWineyardViewModel
import com.ausgetrunken.ui.wineyard.WineyardDetailViewModel
import com.ausgetrunken.ui.wines.WineDetailViewModel
import com.ausgetrunken.ui.wines.AddWineViewModel
import com.ausgetrunken.ui.wines.EditWineViewModel
import com.ausgetrunken.ui.customer.CustomerLandingViewModel
import com.ausgetrunken.ui.customer.CustomerProfileViewModel
import com.ausgetrunken.ui.customer.SubscriptionsViewModel
import com.ausgetrunken.ui.notifications.NotificationManagementViewModel
import com.ausgetrunken.ui.location.LocationPickerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    // Standard ViewModels - recreated on each navigation
    viewModel { AuthViewModel(get(), get()) }
    viewModel { SplashViewModel(get(), get()) }
    viewModel { AddWineyardViewModel(get(), get(), get()) }
    viewModel { AddWineViewModel(get(), get(), get(), get()) }
    viewModel { EditWineViewModel(get(), get(), get(), get()) }
    viewModel { CustomerLandingViewModel(get(), get(), get(), get()) }
    viewModel { CustomerProfileViewModel(get()) }
    viewModel { SubscriptionsViewModel(get(), get(), get()) }
    viewModel { NotificationManagementViewModel(get(), get(), get(), get(), get()) }
    viewModel { WineDetailViewModel(get()) }
    viewModel { LocationPickerViewModel(get()) }
    
    // FIXED: Changed from singleton to regular viewModel to prevent stale logout state
    viewModel { OwnerProfileViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    
    // WineyardDetailViewModel should create new instance per navigation (not singleton)
    // because it needs to load different wineyards based on wineyardId parameter
    viewModel { WineyardDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), androidContext()) }
}