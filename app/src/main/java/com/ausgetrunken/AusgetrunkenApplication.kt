package com.ausgetrunken

import android.app.Application
import com.ausgetrunken.di.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AusgetrunkenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@AusgetrunkenApplication)
            modules(
                databaseModule,
                supabaseModule,
                repositoryModule,
                serviceModule,
                viewModelModule
            )
        }
    }
}