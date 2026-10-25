package app.drift.domain

import java.time.LocalDate

/**
 * Streak / XP / wager mechanics, inspired by Duolingo's reinforcement design.
 *
 * A "successful day" is one where the user met at least one declared intent
 * (ended a session on-time or achieved its purpose). Streaks reward *consistency
 * of intent*, not raw screen-time reduction — the streak is lost by breaking
 * your own word, not by spending time online.
 *
 * - Freeze: a stored freeze prevents a missed day from resetting the streak.
 * - Wager: the user bets their streak on completing today's intent. Win -> +2
 *   days; lose -> streak resets to zero.
 *
 * Pure and unit-testable.
 */
class StreakManager {

    fun recordDay(state: StreakState, today: LocalDate, achieved: Boolean): StreakState {
        val last = state.lastCompletedDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val newStreak = when {
            // Same day already recorded -> no change.
            last == today -> state.currentStreak

            // Achieved today.
            achieved -> {
                val base = if (last == today.minusDays(1)) state.currentStreak + 1 else 1
                val wagered = if (state.wagerActive) base + 2 else base
                wagered
            }

            // Missed today, but a freeze is available.
            state.freezesAvailable > 0 -> state.currentStreak

            // Missed today, no freeze -> broken streak.
            else -> 0
        }

        val freezes = when {
            last == today -> state.freezesAvailable
            achieved -> state.freezesAvailable
            state.freezesAvailable > 0 -> state.freezesAvailable - 1
            else -> state.freezesAvailable
        }

        val xpGain = if (achieved && last != today) XP_PER_DAY + if (state.wagerActive) XP_WAGER_BONUS else 0 else 0

        return state.copy(
            currentStreak = newStreak,
            bestStreak = maxOf(newStreak, state.bestStreak),
            lastCompletedDay = if (achieved) today.toString() else state.lastCompletedDay,
            freezesAvailable = freezes,
            xp = state.xp + xpGain,
            wagerActive = false, // wager resolves once per day
        )
    }

    fun hasActiveStreak(state: StreakState): Boolean = state.currentStreak >= 1

    companion object {
        const val XP_PER_DAY = 10
        const val XP_WAGER_BONUS = 10
        const val INITIAL_FREEZES = 2
    }
}

data class StreakState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCompletedDay: String? = null,
    val freezesAvailable: Int = StreakManager.INITIAL_FREEZES,
    val xp: Int = 0,
    val wagerActive: Boolean = false,
)
