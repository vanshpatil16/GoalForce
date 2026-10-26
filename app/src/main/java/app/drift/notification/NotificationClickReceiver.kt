package app.drift.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.drift.DriftApp
import app.drift.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Fired when the user taps a Drift notification. Records the click (so we can
 * measure notification effectiveness and feed the timing bandit), then opens
 * the app.
 */
class NotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channel = intent.getStringExtra(EXTRA_CHANNEL) ?: "reminder"
        val app = context.applicationContext as DriftApp
        app.container.appScope.launch {
            app.container.repository.recordNotificationClick(channel)
        }
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(launch)
    }

    companion object {
        const val EXTRA_CHANNEL = "channel"
    }
}
