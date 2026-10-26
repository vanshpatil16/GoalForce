package app.drift.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.drift.DriftApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * A one-shot reminder. If the user has an active streak but hasn't met today's
 * intent, it posts a gentle nudge (and records it for click tracking), then
 * re-arms the next reminder at the bandit's best hour.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DriftApp).container
        val streak = container.settings.streak.first()
        val today = LocalDate.now().toString()

        if (streak.currentStreak > 0 && streak.lastCompletedDay != today) {
            DriftNotifications.ensureChannels(applicationContext)
            container.repository.recordNotificationPosted("reminder")
            DriftNotifications.showReminder(
                applicationContext,
                "${streak.currentStreak}-day streak on the line",
                "Haven't checked in today. What's one small intention you can meet?",
                HASH_REMINDER,
            )
        }

        NotificationScheduler.scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        private const val HASH_REMINDER = 1001
    }
}
