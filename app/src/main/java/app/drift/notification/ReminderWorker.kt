package app.drift.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.drift.DriftApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Sends a light-touch reminder when the user has an active streak but has not
 * yet met today's intent. This is the "reward/consistency" layer — the reminder
 * is about keeping their own streak, not about raw usage.
 *
 * (Personalized timing — a bandit over "when the user is most receptive" — is a
 * natural next step and mirrors Duolingo's approach; the intervention-content
 * bandit already in [app.drift.domain.InterventionBandit] is the same pattern.)
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DriftApp).container
        val streak = container.settings.streak.first()
        if (streak.currentStreak == 0) return Result.success()

        val today = LocalDate.now().toString()
        if (streak.lastCompletedDay == today) return Result.success()

        DriftNotifications.ensureChannels(applicationContext)
        DriftNotifications.showReminder(
            applicationContext,
            "${streak.currentStreak}-day streak on the line",
            "Haven't checked in today. What's one small intention you can meet?",
            HASH_REMINDER,
        )
        return Result.success()
    }

    companion object {
        private const val HASH_REMINDER = 1001

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "streak-reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
