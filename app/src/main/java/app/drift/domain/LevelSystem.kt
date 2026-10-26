package app.drift.domain

import kotlin.math.sqrt

/**
 * A simple, motivating progression system. XP is earned for meeting intentions;
 * the level grows sub-linearly so early progress feels fast and later levels
 * feel earned.
 */
object LevelSystem {

    fun levelFor(xp: Int): Int = (sqrt(xp / 100.0).toInt()) + 1

    fun xpForLevel(level: Int): Int = (level - 1) * (level - 1) * 100

    /** Progress within the current level, in [0, 1]. */
    fun progressInLevel(xp: Int): Float {
        val level = levelFor(xp)
        val base = xpForLevel(level)
        val next = xpForLevel(level + 1)
        return ((xp - base).toFloat() / (next - base)).coerceIn(0f, 1f)
    }

    fun xpToNextLevel(xp: Int): Int = xpForLevel(levelFor(xp) + 1) - xp

    fun title(level: Int): String = when {
        level >= 10 -> "Mindful Master"
        level >= 8 -> "Focused Sage"
        level >= 6 -> "Intentional"
        level >= 4 -> "Finding Focus"
        level >= 2 -> "Warming Up"
        else -> "Starting Out"
    }

    /** A short, warm daily encouragement keyed to the streak. */
    fun encouragement(streakDays: Int): String = when {
        streakDays >= 30 -> "A whole month of intention. You're unstoppable."
        streakDays >= 14 -> "Two weeks strong. This is who you are now."
        streakDays >= 7 -> "A full week. Momentum is on your side."
        streakDays >= 3 -> "Three days in a row — the habit is forming."
        streakDays >= 1 -> "Day one done. Come back tomorrow."
        else -> "Define one small intention today."
    }
}
