package app.drift.domain

import app.drift.domain.model.StreakView
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Computes daily and weekly streaks from the set of qualifying dates (persisted
 * as daily progress). Deriving from dates — rather than storing mutable streak
 * state — keeps the value correct across restarts and timezone changes.
 */
class StreakService(private val weeklyTarget: Int = 5) {

    fun compute(dates: List<LocalDate>, today: LocalDate = LocalDate.now()): StreakView {
        val distinct = dates.distinct().sorted()
        val currentWeekDays = distinct.count { weekKey(it) == weekKey(today) }
        return StreakView(
            currentStreak = currentDailyStreak(distinct, today),
            bestStreak = bestDailyStreak(distinct),
            currentWeekStreak = currentWeeklyStreak(distinct, today),
            bestWeekStreak = bestWeeklyStreak(distinct),
            weeklyTarget = weeklyTarget,
            qualifyingDaysThisWeek = currentWeekDays,
            lastQualifyingDate = distinct.lastOrNull()?.toString(),
        )
    }

    fun currentDailyStreak(dates: List<LocalDate>, today: LocalDate): Int {
        var streak = 0
        var cursor = if (dates.contains(today)) today else today.minusDays(1)
        while (dates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun bestDailyStreak(dates: List<LocalDate>): Int {
        val set = dates.distinct().toSet()
        var best = 0
        var run = 0
        var cursor: LocalDate? = null
        for (d in set.sorted()) {
            run = if (cursor != null && d == cursor.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            cursor = d
        }
        return best
    }

    fun currentWeeklyStreak(dates: List<LocalDate>, today: LocalDate): Int {
        val weeks = qualifyingWeeks(dates)
        var streak = 0
        var cursor = today
        // Count back from the current week while the (already-completed) week qualifies.
        while (weeks[weekKey(cursor)] == true) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    fun bestWeeklyStreak(dates: List<LocalDate>): Int {
        val weeks = qualifyingWeeks(dates)
        val ordered = weeks.keys.sorted()
        var best = 0
        var run = 0
        var prev: String? = null
        for (wk in ordered) {
            run = if (prev != null && isConsecutiveWeek(prev, wk)) run + 1 else 1
            best = maxOf(best, run)
            prev = wk
        }
        return best
    }

    /** Weeks (keyed "year-W##") that met the weekly target. */
    private fun qualifyingWeeks(dates: List<LocalDate>): Map<String, Boolean> =
        dates.distinct()
            .groupBy { weekKey(it) }
            .mapValues { (_, days) -> days.size >= weeklyTarget }

    private fun weekKey(d: LocalDate): String {
        val week = d.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = d.get(WeekFields.ISO.weekBasedYear())
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    private fun isConsecutiveWeek(a: String, b: String): Boolean {
        val (ay, aw) = parse(a); val (by, bw) = parse(b)
        val aStart = LocalDate.of(ay, 1, 4)
            .minusDays(((LocalDate.of(ay, 1, 4).dayOfWeek.value + 6) % 7).toLong())
            .plusWeeks((aw - 1).toLong())
        val bStart = LocalDate.of(by, 1, 4)
            .minusDays(((LocalDate.of(by, 1, 4).dayOfWeek.value + 6) % 7).toLong())
            .plusWeeks((bw - 1).toLong())
        return bStart == aStart.plusWeeks(1)
    }

    private fun parse(key: String): Pair<Int, Int> {
        val parts = key.removePrefix("").split("-W")
        return parts[0].toInt() to parts[1].toInt()
    }
}
