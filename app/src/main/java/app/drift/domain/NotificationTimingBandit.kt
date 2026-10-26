package app.drift.domain

import app.drift.domain.model.NotificationEvent
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * A contextual bandit over *hour-of-day* slots for notification timing — the ML
 * "when to nudge" model inspired by Duolingo's notification work.
 *
 * Arms   = the 24 hour buckets.
 * Reward = 1 if the user clicked the notification, else 0.
 *
 * Selection is epsilon-greedy: mostly exploit the historically best hour, but
 * with probability `epsilon` explore a random hour to keep learning.
 *
 * Pure Kotlin, unit-testable.
 */
class NotificationTimingBandit(private val random: Random = Random.Default) {

    fun selectHour(history: List<NotificationEvent>, epsilon: Double = 0.2): Int =
        if (random.nextDouble() < epsilon) random.nextInt(24)
        else bestHours(history, 1).firstOrNull() ?: 9

    /** The hours with the highest historical click-through, descending. */
    fun bestHours(history: List<NotificationEvent>, n: Int = 3): List<Int> {
        val clicked = history.filter { it.clickedAt != null }
        if (clicked.isEmpty()) return listOf(9, 18, 20)
        val byHour = clicked
            .groupBy { hourOf(it.postedAt) }
            .mapValues { (_, list) -> list.size.toDouble() }
        return byHour.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    fun clickRate(history: List<NotificationEvent>): Double =
        if (history.isEmpty()) 0.0
        else history.count { it.clickedAt != null }.toDouble() / history.size

    private fun hourOf(timestampMs: Long): Int =
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).hour
}
