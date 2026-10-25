package app.drift.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.drift.R

object DriftNotifications {
    private const val CHANNEL_REMINDER = "drift_reminders"
    private const val CHANNEL_STREAK = "drift_streak"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle reminders to check in on your intent"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK, "Streak", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Streak and reward nudges"
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, title: String, text: String, id: Int) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    fun showReminder(context: Context, title: String, text: String, id: Int) =
        show(context, CHANNEL_REMINDER, title, text, id)

    fun showStreak(context: Context, title: String, text: String, id: Int) =
        show(context, CHANNEL_STREAK, title, text, id)
}
