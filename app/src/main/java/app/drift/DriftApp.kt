package app.drift

import android.app.Application
import app.drift.di.AppContainer

class DriftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
