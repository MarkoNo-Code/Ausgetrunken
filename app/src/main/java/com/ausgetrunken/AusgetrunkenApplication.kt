package com.ausgetrunken

import android.app.Application
import com.ausgetrunken.di.*
import com.ausgetrunken.domain.service.WineryPhotoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AusgetrunkenApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        
        startKoin {
            androidLogger()
            androidContext(this@AusgetrunkenApplication)
            modules(
                databaseModule,
                networkModule,
                supabaseModule,
                repositoryModule,
                serviceModule,
                viewModelModule
            )
        }
        
        // Initialize photo validation after Koin is set up
        initializePhotoValidation()
    }
    
    private fun initializePhotoValidation() {
        applicationScope.launch {
            try {
                val photoService: WineryPhotoService by inject()
                photoService.validateAndMigratePhotos()
            } catch (e: Exception) {
            }
        }
    }
}