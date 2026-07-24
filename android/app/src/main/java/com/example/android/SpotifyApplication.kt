package com.example.android

import android.app.Application
import com.example.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SpotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SpotifyApplication)
            modules(appModule)
        }
    }
}
