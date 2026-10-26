package app.drift.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.drift.R

object DriftNotifications {
    private const val CHANNEL_REMINDER = "drift_reminders"
    private const val CHANNEL_STREAK = "drift_streak"
    private const val CHANNEL_MOTIVATION_NEWS = "drift_motivation_news"

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
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MOTIVATION_NEWS, "Focus motivation", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Brief positive news nudges when short-form content is detected during a focus session"
            }
        )
    }

    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, title: String, text: String, id: Int) {
        if (!canNotify(context)) return
        val clickIntent = Intent(context, NotificationClickReceiver::class.java)
            .putExtra(NotificationClickReceiver.EXTRA_CHANNEL, channel)
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun showReminder(context: Context, title: String, text: String, id: Int) =
        show(context, CHANNEL_REMINDER, title, text, id)

    fun showStreak(context: Context, title: String, text: String, id: Int) =
        show(context, CHANNEL_STREAK, title, text, id)

    fun showMotivationNews(context: Context, title: String, headline: String, summary: String, id: Int): Boolean {
        if (!canNotify(context)) return false
        val clickIntent = Intent(context, NotificationClickReceiver::class.java)
            .putExtra(NotificationClickReceiver.EXTRA_CHANNEL, CHANNEL_MOTIVATION_NEWS)
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val details = buildString {
            append(headline)
            if (summary.isNotBlank()) append("\n\n").append(summary)
            append("\n\nGood News Network")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_MOTIVATION_NEWS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setSubText("Good News Network")
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
        return true
    }
}
