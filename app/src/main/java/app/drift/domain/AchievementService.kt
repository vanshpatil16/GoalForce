package app.drift.domain

import app.drift.domain.model.Achievement
import app.drift.domain.model.AchievementCategory

/** A point-in-time summary of progress used to evaluate achievement eligibility. */
data class ProgressSnapshot(
    val sessionsCompleted: Int = 0,
    val goalsCompleted: Int = 0,
    val currentStreak: Int = 0,
    val attentionRecoveries: Int = 0,
    val difficultTasksCompleted: Int = 0,
    val personalBestAchieved: Boolean = false,
    val weeklyConsistencyAchieved: Boolean = false,
    val challengesCompleted: Int = 0,
)

/** The initial achievement catalogue. Conditions are evaluated against [ProgressSnapshot]. */
object AchievementCatalog {
    val all: List<Achievement> = listOf(
        Achievement("first_session", "First Focus Session", "Complete your first focus session.", AchievementCategory.FIRST_STEPS, 15),
        Achievement("first_goal", "First Completed Goal", "Achieve your first declared intention.", AchievementCategory.FIRST_STEPS, 20),
        Achievement("streak_3", "Three-Day Streak", "Keep a 3-day streak alive.", AchievementCategory.CONSISTENCY, 30),
        Achievement("streak_7", "Seven-Day Streak", "Keep a 7-day streak alive.", AchievementCategory.CONSISTENCY, 60),
        Achievement("sessions_10", "Ten Focus Sessions", "Complete ten focus sessions.", AchievementCategory.FOCUS, 40),
        Achievement("first_difficult", "First Difficult Task", "Complete a difficult task.", AchievementCategory.FOCUS, 25),
        Achievement("first_recovery", "First Attention Recovery", "Return to your goal after drifting.", AchievementCategory.RECOVERY, 25),
        Achievement("goals_5", "Five Goal Completions", "Complete five intentions.", AchievementCategory.CONSISTENCY, 50),
        Achievement("personal_best", "Personal Best", "Set a new personal best for focus duration.", AchievementCategory.PERSONAL_BEST, 30),
        Achievement("weekly_champion", "Weekly Consistency Champion", "Hit your weekly consistency target.", AchievementCategory.CONSISTENCY, 100),
        Achievement("first_challenge", "First Challenge Completed", "Complete your first challenge.", AchievementCategory.CHALLENGE, 50),
    )
}

class AchievementService {
    fun evaluate(snapshot: ProgressSnapshot): List<Achievement> =
        AchievementCatalog.all.filter { it.id.conditionMet(snapshot) }

    fun byId(id: String): Achievement? = AchievementCatalog.all.firstOrNull { it.id == id }
}

private fun String.conditionMet(s: ProgressSnapshot): Boolean = when (this) {
    "first_session" -> s.sessionsCompleted >= 1
    "first_goal" -> s.goalsCompleted >= 1
    "streak_3" -> s.currentStreak >= 3
    "streak_7" -> s.currentStreak >= 7
    "sessions_10" -> s.sessionsCompleted >= 10
    "first_difficult" -> s.difficultTasksCompleted >= 1
    "first_recovery" -> s.attentionRecoveries >= 1
    "goals_5" -> s.goalsCompleted >= 5
    "personal_best" -> s.personalBestAchieved
    "weekly_champion" -> s.weeklyConsistencyAchieved
    "first_challenge" -> s.challengesCompleted >= 1
    else -> false
}
