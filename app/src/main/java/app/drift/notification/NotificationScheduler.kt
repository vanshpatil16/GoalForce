package app.drift.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.drift.DriftApp
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the next reminder at the user's *learned best hour* (from the
 * notification-timing bandit), rather than on a fixed interval. Each reminder
 * run re-arms the next one, so timing keeps adapting as clicks accumulate.
 */
object NotificationScheduler {
    const val WORK_NAME = "streak-reminder"

    suspend fun scheduleNext(context: Context) {
        val container = (context.applicationContext as DriftApp).container
        val history = container.repository.notificationEventsSnapshot()
        val bestHour = container.timingBandit.bestHours(history, 1).firstOrNull() ?: 9
        val delayMs = delayToNextHour(bestHour)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Milliseconds until the next occurrence of [hour] (0-23) in local time. */
    fun delayToNextHour(hour: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var target = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}
