package com.ladb.reborn

import android.app.Application
import android.content.Context
import com.ladb.reborn.core.Adb
import com.ladb.reborn.core.AdbManager
import com.ladb.reborn.core.AppsRepository
import com.ladb.reborn.core.HistoryRepository
import com.ladb.reborn.core.SettingsRepository

class LadbApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Adb.init(this)
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val adbManager = AdbManager(app, settings)
    val history = HistoryRepository(app)
    val apps = AppsRepository(app, adbManager)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LadbApp).container
