package app.drift.domain

import app.drift.domain.model.XpCategory

/**
 * Configurable XP rules. Keep reward values here, not scattered through widgets.
 * Values are illustrative and adjustable.
 */
data class XpRules(
    val sessionCompletion: Int = 20,
    val taskCompletion: Int = 30,
    val difficultyBonusMin: Int = 10,
    val difficultyBonusMax: Int = 50,
    val attentionRecovery: Int = 10,
    val dailyGoal: Int = 25,
    val weeklyMilestone: Int = 100,
    val personalBest: Int = 20,
    val studyMilestone: Int = 15,
    val weeklyConsistency: Int = 50,
    /** Completed a session without opening a designated distracting app. */
    val distractionFreeBonus: Int = 15,
    /** Verified dismissal of a distracting notification without opening it. */
    val verifiedDismissal: Int = 3,
    /** XP per qualifying 5-minute block of goal-aligned activity. */
    val goalAlignedPer5Min: Int = 1,
    /** Version stamp so historical transactions can be tied to the ruleset. */
    val version: Int = 1,
) {
    fun baseFor(category: XpCategory): Int = when (category) {
        XpCategory.SESSION_COMPLETION -> sessionCompletion
        XpCategory.TASK_COMPLETION -> taskCompletion
        XpCategory.ATTENTION_RECOVERY -> attentionRecovery
        XpCategory.DAILY_GOAL -> dailyGoal
        XpCategory.WEEKLY_MILESTONE -> weeklyMilestone
        XpCategory.PERSONAL_BEST -> personalBest
        XpCategory.STUDY_MILESTONE -> studyMilestone
        XpCategory.CONSISTENCY -> weeklyConsistency
        XpCategory.DISTRACTION_FREE_BONUS -> distractionFreeBonus
        XpCategory.VERIFIED_DISMISSAL -> verifiedDismissal
        // Categories whose amount is computed from context, not a fixed base:
        XpCategory.DIFFICULTY_BONUS,
        XpCategory.ACHIEVEMENT,
        XpCategory.VARIABLE_BONUS,
        XpCategory.GROUP_CHALLENGE,
        XpCategory.GOAL_ALIGNED_ACTIVITY,
        -> 0
    }
}
