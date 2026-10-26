package app.drift.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.drift.DriftApp
import app.drift.service.UsagePatternWorker
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DriftNotifications.ensureChannels(context)
            UsagePatternWorker.schedule(context)
            val app = context.applicationContext as DriftApp
            app.container.appScope.launch { NotificationScheduler.scheduleNext(context) }
        }
    }
}
