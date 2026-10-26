package app.drift.domain.model

/** Categories of XP awards — each must have an explicit reason. */
enum class XpCategory {
    SESSION_COMPLETION,
    TASK_COMPLETION,
    DIFFICULTY_BONUS,
    ATTENTION_RECOVERY,
    DAILY_GOAL,
    WEEKLY_MILESTONE,
    PERSONAL_BEST,
    STUDY_MILESTONE,
    CONSISTENCY,
    ACHIEVEMENT,
    VARIABLE_BONUS,
    GROUP_CHALLENGE,
    GOAL_ALIGNED_ACTIVITY,
    /** Completed a session without opening a designated distracting app. */
    DISTRACTION_FREE_BONUS,
    /** Verified dismissal of a distracting notification without opening it. */
    VERIFIED_DISMISSAL,
}

/** An append-only XP ledger entry. */
data class XpTransaction(
    val id: Long = 0,
    val idempotencyKey: String,
    val category: XpCategory,
    val amount: Int,
    val description: String,
    val timestamp: Long,
    val rulesetVersion: Int,
    val sessionId: Long?,
)

/**
 * Lifetime XP (levels) and spendable reward currency are kept separate.
 * `spendableBalance = lifetimeXp - spentXp`; redemption never reduces lifetime XP.
 */
data class UserProgress(
    val lifetimeXp: Int = 0,
    val spendableBalance: Int = 0,
    val spentXp: Int = 0,
)

sealed interface XpAwardResult {
    data class Awarded(val amount: Int, val bonus: Int, val transactionId: Long) : XpAwardResult
    data object Duplicate : XpAwardResult
}

enum class AchievementCategory { FIRST_STEPS, CONSISTENCY, FOCUS, RECOVERY, PERSONAL_BEST, CHALLENGE }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val xpReward: Int,
    val isRepeatable: Boolean = false,
    val isSecret: Boolean = false,
)

data class AchievementUnlock(
    val achievementId: String,
    val unlockedAt: Long,
)

enum class RewardCategory { THEME, AVATAR, FRAME, DASHBOARD, SOUND, REPORT, CHALLENGE_ENTRY, EDUCATIONAL }

data class RewardItem(
    val id: String,
    val title: String,
    val description: String,
    val category: RewardCategory,
    val xpCost: Int,
)

data class RewardRedemption(
    val id: Long = 0,
    val rewardId: String,
    val xpCost: Int,
    val redeemedAt: Long,
)

/** One day's qualifying activity, used for streaks and the contribution heatmap. */
data class DailyProgress(
    val date: String,
    val qualifyingMinutes: Int,
    val sessionsCompleted: Int,
    val xpEarned: Int,
    val tasksCompleted: Int,
    val recoveries: Int,
)

/** Derived streak view (computed from persisted daily progress). */
data class StreakView(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val currentWeekStreak: Int = 0,
    val bestWeekStreak: Int = 0,
    val weeklyTarget: Int = 5,
    val qualifyingDaysThisWeek: Int = 0,
    val lastQualifyingDate: String? = null,
)

enum class BonusEventType { MILESTONE, DIFFICULT_TASK, ATTENTION_RECOVERY, PERSONAL_BEST }

data class BonusContext(val eventType: BonusEventType, val now: Long)

sealed interface BonusResult {
    data class Awarded(val amount: Int) : BonusResult
    data object NotAwarded : BonusResult
    data object CapReached : BonusResult
    data object CooldownActive : BonusResult
    data object Ineligible : BonusResult
}
