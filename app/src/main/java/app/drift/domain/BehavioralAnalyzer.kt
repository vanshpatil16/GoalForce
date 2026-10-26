package app.drift.domain

import app.drift.domain.model.BehavioralInsights
import app.drift.domain.model.DailyInsight
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.NotificationEvent
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import java.time.Instant
import java.time.ZoneId

/**
 * Turns raw history into *insights about the person* — how their behaviour
 * changes over time, what derails them, which interventions work, and when
 * they're most receptive. This is the "account-based behavioural analysis"
 * layer (all on-device, privacy-first).
 */
class BehavioralAnalyzer(
    private val timingBandit: NotificationTimingBandit = NotificationTimingBandit(),
) {

    fun dailyInsights(
        sessions: List<Session>,
        interventions: List<Intervention>,
    ): List<DailyInsight> {
        val interventionSessionIds = interventions.map { it.sessionId }.toSet()
        return sessions
            .groupBy { dayOf(it.startedAt) }
            .map { (date, daySessions) ->
                DailyInsight(
                    date = date,
                    sessions = daySessions.size,
                    overruns = daySessions.count { it.outcome == Outcome.OVERRAN },
                    interventions = daySessions.count { it.id in interventionSessionIds },
                    achieved = daySessions.count {
                        it.outcome == Outcome.ON_TIME || it.outcome == Outcome.ACHIEVED_PURPOSE
                    },
                )
            }
            .sortedBy { it.date }
    }

    fun analyze(
        sessions: List<Session>,
        interventions: List<Intervention>,
        notificationEvents: List<NotificationEvent>,
        xp: Int,
        streakDays: Int,
    ): BehavioralInsights {
        val daily = dailyInsights(sessions, interventions)
        val finished = sessions.filter { it.outcome != null }
        val overrunRate =
            if (finished.isEmpty()) 0.0
            else finished.count { it.outcome == Outcome.OVERRAN }.toDouble() / finished.size

        return BehavioralInsights(
            totalSessions = sessions.size,
            overrunRate = overrunRate,
            overrunTrend = overrunTrend(daily),
            topOverrunApp = topOverrunApp(sessions),
            bestHour = timingBandit.bestHours(notificationEvents, 1).firstOrNull() ?: 9,
            notificationsPosted = notificationEvents.size,
            notificationClickRate = timingBandit.clickRate(notificationEvents),
            mostEffectiveTemplate = mostEffectiveTemplate(interventions),
            level = LevelSystem.levelFor(xp),
            levelTitle = LevelSystem.title(LevelSystem.levelFor(xp)),
            streakDays = streakDays,
        )
    }

    /** Linear-regression slope of overrun-rate across days (negative = improving). */
    private fun overrunTrend(daily: List<DailyInsight>): Double {
        if (daily.size < 2) return 0.0
        val xs = daily.indices.map { it.toDouble() }
        val ys = daily.map { it.overruns.toDouble() / it.sessions.coerceAtLeast(1) }
        val meanX = xs.average()
        val meanY = ys.average()
        val numerator = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
        val denominator = xs.indices.sumOf { (xs[it] - meanX) * (xs[it] - meanX) }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    private fun topOverrunApp(sessions: List<Session>): String? =
        sessions
            .filter { it.outcome == Outcome.OVERRAN }
            .groupBy { it.targetPackage }
            .maxByOrNull { it.value.size }
            ?.key

    private fun mostEffectiveTemplate(interventions: List<Intervention>): InterventionTemplate? {
        val resolved = interventions.filter { it.reward != null }
        if (resolved.isEmpty()) return null
        return resolved
            .groupBy { it.template }
            .maxByOrNull { (_, list) -> list.count { it.reward == true }.toDouble() / list.size }
            ?.key
    }

    private fun dayOf(timestampMs: Long): String =
        Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
