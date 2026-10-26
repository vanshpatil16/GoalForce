package app.drift

import android.app.Application
import app.drift.di.AppContainer
import app.drift.notification.NotificationScheduler
import kotlinx.coroutines.launch

class DriftApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { NotificationScheduler.scheduleNext(this@DriftApp) }
    }
}
