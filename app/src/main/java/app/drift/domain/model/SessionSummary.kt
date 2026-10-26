package app.drift.domain.model

/**
 * A per-session analytics summary, derived (not incremented ad-hoc) from the
 * session's persisted events. Observed metrics are kept separate from
 * estimates; [trackingCoverage] quantifies how much of the session was actually
 * observable given the permissions available.
 */
data class SessionSummary(
    val sessionId: Long,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    /** "completed" | "overran" | "abandoned" | "unknown" */
    val completionStatus: String,
    /** Estimated seconds spent in foreground of apps the user designated as distracting. */
    val restrictedAppForegroundSeconds: Int,
    /** Restricted-app opens + restriction attempts. */
    val distractionCount: Int,
    /** Number of times a restriction was enforced (e.g. Reels/Shorts blocked). */
    val restrictionAttemptCount: Int,
    val appOpenCount: Int,
    /** 0..100 — higher is better. Deterministic from distraction time vs session length. */
    val focusScore: Int,
    /** 0.0..1.0 — fraction of the session that could actually be observed. */
    val trackingCoverage: Float,
    /** XP settled for this session (filled from the ledger after awards). */
    val xpEarned: Int = 0,
    val createdAt: Long = 0,
)
