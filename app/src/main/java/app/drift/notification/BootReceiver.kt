package app.drift.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.drift.service.UsagePatternWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DriftNotifications.ensureChannels(context)
            ReminderWorker.schedule(context)
            UsagePatternWorker.schedule(context)
        }
    }
}
