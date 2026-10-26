package app.drift.domain

import app.drift.domain.model.ForegroundEvent
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Derives the small, easy-to-miss signals that aggregate session stats hide —
 * most importantly *bursts* (e.g. "Instagram opened 4 times in 1 minute") and
 * rapid re-opening of short-form content. Pure and deterministic for tests.
 */
class AttentionAnalytics {

    data class Burst(val label: String, val opens: Int, val windowMs: Long, val at: Long)

    fun opensByApp(events: List<ForegroundEvent>): Map<String, Int> =
        opens(events) { true }

    fun opensTodayByApp(events: List<ForegroundEvent>, now: Long): Map<String, Int> =
        opens(events) { it.at >= startOfDay(now) }

    private fun opens(events: List<ForegroundEvent>, filter: (ForegroundEvent) -> Boolean): Map<String, Int> =
        events.asSequence()
            .filter { it.kind == ForegroundEventKind.APP_OPEN }
            .filter(filter)
            .groupingBy { it.label }
            .eachCount()

    fun totalOpensToday(events: List<ForegroundEvent>, now: Long): Int {
        val dayStart = startOfDay(now)
        return events.count { it.kind == ForegroundEventKind.APP_OPEN && it.at >= dayStart }
    }

    fun reelsBlockedToday(events: List<ForegroundEvent>, now: Long): Int {
        val dayStart = startOfDay(now)
        return events.count { it.kind == ForegroundEventKind.CONTENT_BLOCKED && it.at >= dayStart }
    }

    /**
     * For every app, the max number of APP_OPEN events inside any sliding
     * [windowMs] window. Returns all bursts at or above [minOpens], sorted by
     * opens descending.
     */
    fun bursts(events: List<ForegroundEvent>, windowMs: Long = 60_000L, minOpens: Int = 3): List<Burst> {
        val byLabel = events
            .filter { it.kind == ForegroundEventKind.APP_OPEN }
            .groupBy { it.label }
        val out = mutableListOf<Burst>()
        for ((label, list) in byLabel) {
            val times = list.map { it.at }.sorted()
            var left = 0
            var bestCount = 0
            var bestAt = 0L
            for (right in times.indices) {
                while (times[right] - times[left] > windowMs) left++
                val count = right - left + 1
                if (count > bestCount) {
                    bestCount = count
                    bestAt = times[left]
                }
            }
            if (bestCount >= minOpens) out.add(Burst(label, bestCount, windowMs, bestAt))
        }
        return out.sortedByDescending { it.opens }
    }

    fun topBurst(events: List<ForegroundEvent>, windowMs: Long = 60_000L, minOpens: Int = 3): Burst? =
        bursts(events, windowMs, minOpens).firstOrNull()

    private fun startOfDay(now: Long): Long =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/**
 * Turns raw history into a compact, human-readable report that the assistant
 * injects into its prompt (and can also render directly as an offline fallback).
 */
class AttentionReport(
    private val analytics: AttentionAnalytics = AttentionAnalytics(),
) {
    data class Facts(
        val totalOpensToday: Int,
        val opensTodayByApp: Map<String, Int>,
        val topBurst: AttentionAnalytics.Burst?,
        val reelsBlockedToday: Int,
        val sessionsToday: Int,
        val overrunsToday: Int,
        val reentriesToday: Int,
        val topOpenAppToday: String?,
    )

    fun build(
        events: List<ForegroundEvent>,
        sessions: List<Session>,
        now: Long = System.currentTimeMillis(),
    ): Facts {
        val dayStart = startOfDay(now)
        val opensToday = analytics.opensTodayByApp(events, now)
        val todaySessions = sessions.filter { it.startedAt >= dayStart }
        return Facts(
            totalOpensToday = analytics.totalOpensToday(events, now),
            opensTodayByApp = opensToday,
            topBurst = analytics.topBurst(events),
            reelsBlockedToday = analytics.reelsBlockedToday(events, now),
            sessionsToday = todaySessions.size,
            overrunsToday = todaySessions.count { it.outcome == Outcome.OVERRAN },
            reentriesToday = todaySessions.sumOf { it.reentries },
            topOpenAppToday = opensToday.maxByOrNull { it.value }?.key,
        )
    }

    fun toText(f: Facts): String {
        val lines = mutableListOf<String>()
        lines += "Opens today: ${f.totalOpensToday}."
        val appLine = f.opensTodayByApp.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { "${it.key} (${it.value})" }
        if (appLine.isNotBlank()) lines += "Most-opened apps today: $appLine."
        f.topBurst?.let {
            lines += "Heaviest burst: ${it.label} opened ${it.opens} times within ${it.windowMs / 1000}s around ${fmt(it.at)}."
        }
        if (f.reelsBlockedToday > 0) lines += "Short-form content blocked ${f.reelsBlockedToday} times today."
        lines += "Focus sessions today: ${f.sessionsToday}, overruns: ${f.overrunsToday}, compulsive re-entries: ${f.reentriesToday}."
        return lines.joinToString("\n")
    }

    private fun fmt(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun startOfDay(now: Long): Long =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
