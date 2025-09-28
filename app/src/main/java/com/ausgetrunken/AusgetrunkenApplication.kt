package com.ausgetrunken

import android.app.Application
import android.util.Log
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
        
        Log.d("AusgetrunkenApp", "Application starting up...")
        
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
                Log.d("AusgetrunkenApp", "Starting photo validation on app startup...")
                val photoService: WineryPhotoService by inject()
                photoService.validateAndMigratePhotos()
                Log.d("AusgetrunkenApp", "Photo validation completed")
            } catch (e: Exception) {
                Log.e("AusgetrunkenApp", "Error during photo validation", e)
            }
        }
    }
}