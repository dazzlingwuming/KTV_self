package com.ktv.stb.app

import android.app.Application

class KtvApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.start()
    }

    override fun onTerminate() {
        appContainer.stop()
        super.onTerminate()
    }
}
