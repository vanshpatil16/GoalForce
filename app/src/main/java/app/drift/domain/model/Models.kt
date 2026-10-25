package app.drift.domain.model

/** What the user says they came to do. */
enum class IntentKind { QUICK_CHECK, LONG_FORM, TASK, ENTERTAINMENT }

/** A user-declared purpose + intended duration, anchored to a target app. */
data class UserIntent(
    val id: Long = 0,
    val targetPackage: String,
    val targetLabel: String,
    val purpose: String,
    val intendedMinutes: Int,
    val kind: IntentKind,
    val createdAt: Long,
)

enum class SessionState { ACTIVE, COMPLETED, ABANDONED }

/** Did the session end the way the user intended? */
enum class Outcome { ON_TIME, OVERRAN, ACHIEVED_PURPOSE, ABANDONED }

/**
 * A single "visit" to the target app, tied to a declared intent. Tracks the
 * signals that distinguish intentional use from compulsive browsing.
 */
data class Session(
    val id: Long = 0,
    val intentId: Long,
    val targetPackage: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lastExitAt: Long? = null,
    val reentries: Int = 0,
    val appSwitches: Int = 0,
    val extendedMinutes: Int = 0,
    val state: SessionState = SessionState.ACTIVE,
    val outcome: Outcome? = null,
)

/** The intervention templates (the bandit's "arms"). */
enum class InterventionTemplate {
    STOPPING_POINT,
    EXIT_PROMPT,
    BREATH_PAUSE,
    REFLECTION,
    REWARD_NUDGE,
}

/** How the user's actual usage diverged from their stated intent. */
enum class DriftKind {
    ON_TRACK,
    OVER_RUN,
    COMPULSIVE_REENTRY,
    NATURAL_STOPPING,
}

/** An intervention that was actually shown, with its eventual reward. */
data class Intervention(
    val id: Long = 0,
    val sessionId: Long,
    val template: InterventionTemplate,
    val driftKind: DriftKind,
    val at: Long,
    val reward: Boolean? = null,
)

/** A point-in-time view of the user's usage, used by [app.drift.domain.IntentEngine]. */
data class UsageSnapshot(
    val foregroundPackage: String?,
    val elapsedMinutes: Int,
    val reentries: Int,
    val secondsSinceLastExit: Long,
    val singleAppFocus: Boolean,
    val screenOn: Boolean = true,
)
