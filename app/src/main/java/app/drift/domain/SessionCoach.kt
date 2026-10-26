package app.drift.domain

import app.drift.domain.model.SessionSummary

/**
 * Compact, privacy-preserving analytics context handed to the AI assistant.
 * Contains only aggregated session metrics — no raw event contents.
 */
data class CoachingContext(
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    val completionStatus: String,
    val restrictedAppForegroundSeconds: Int,
    val distractionCount: Int,
    val restrictionAttemptCount: Int,
    val focusScore: Int,
    val trackingCoverage: Float,
    val xpEarned: Int,
    val recentSessionCount: Int,
    val recentAvgFocusScore: Int?,
    val recentAvgRestrictedSeconds: Int?,
)

/** Structured coaching output, rendered consistently in the UI. */
data class CoachingResult(
    val summary: String,
    val observations: List<String>,
    val suggestion: String,
    val encouragement: String,
    val nextGoal: String?,
)

/**
 * Builds an evidence-backed coaching context from a session summary (and the
 * user's recent baseline), then either formats a prompt for the LLM or produces
 * a deterministic offline result. The model never computes authoritative XP or
 * metrics — those come from [SessionSummary] and [CoachingContext].
 */
class SessionCoach {

    fun buildContext(summary: SessionSummary, recent: List<SessionSummary>): CoachingContext {
        val recentCompleted = recent.filter { it.sessionId != summary.sessionId }
        val avgFocus = recentCompleted.map { it.focusScore }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgRestricted = recentCompleted.map { it.restrictedAppForegroundSeconds }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        return CoachingContext(
            plannedDurationSeconds = summary.plannedDurationSeconds,
            actualDurationSeconds = summary.actualDurationSeconds,
            completionStatus = summary.completionStatus,
            restrictedAppForegroundSeconds = summary.restrictedAppForegroundSeconds,
            distractionCount = summary.distractionCount,
            restrictionAttemptCount = summary.restrictionAttemptCount,
            focusScore = summary.focusScore,
            trackingCoverage = summary.trackingCoverage,
            xpEarned = summary.xpEarned,
            recentSessionCount = recentCompleted.size,
            recentAvgFocusScore = avgFocus,
            recentAvgRestrictedSeconds = avgRestricted,
        )
    }

    /** Deterministic fallback used when the LLM is unavailable or no key is configured. */
    fun offlineCoaching(context: CoachingContext): CoachingResult {
        val plannedMin = context.plannedDurationSeconds / 60
        val actualMin = context.actualDurationSeconds / 60
        val restrictedSec = context.restrictedAppForegroundSeconds

        val summary = when (context.completionStatus) {
            "completed" -> "You completed your $plannedMin-minute session."
            "overran" -> "You finished your session, running past your planned $plannedMin minutes."
            "abandoned" -> "You ended this session early."
            else -> "Your session is done."
        }

        val observations = mutableListOf<String>()
        if (context.distractionCount == 0) {
            observations += "You didn't open a distracting app the whole session."
        } else {
            observations += "You spent about $restrictedSec seconds in distracting apps across ${context.distractionCount} detours."
        }
        if (context.restrictionAttemptCount > 0) {
            observations += "You hit ${context.restrictionAttemptCount} blocked Reels/Shorts ${if (context.restrictionAttemptCount == 1) "attempt" else "attempts"} and turned back."
        }
        if (context.trackingCoverage < 1f) {
            observations += "Tracking was partial (${(context.trackingCoverage * 100).toInt()}% coverage), so these numbers may undercount."
        }

        val suggestion = if (context.distractionCount == 0) {
            "Nice and clean. Next, try extending your planned time by 5 minutes to build stamina."
        } else {
            "For your next session, mute non-essential notifications or start with a shorter, easier target."
        }

        val encouragement = if (context.focusScore >= 80) {
            "Strong focus — keep this up."
        } else {
            "You kept coming back to your goal, and that's what matters."
        }

        val nextGoal = if (context.recentAvgFocusScore != null && context.recentAvgFocusScore >= 0) {
            if (context.focusScore > context.recentAvgFocusScore) {
                "Beat your recent average focus score of ${context.recentAvgFocusScore}."
            } else {
                "Match or beat your recent average focus score of ${context.recentAvgFocusScore}."
            }
        } else {
            null
        }

        return CoachingResult(
            summary = summary,
            observations = observations,
            suggestion = suggestion,
            encouragement = encouragement,
            nextGoal = nextGoal,
        )
    }

    /** Structured prompt for the LLM; it fills in prose, never the metrics. */
    fun prompt(context: CoachingContext, name: String, memoryText: String = ""): String {
        val who = name.ifBlank { "the user" }
        val memory = memoryText.ifBlank { "No saved preferences yet." }
        val baseline = if (context.recentSessionCount > 0 && context.recentAvgFocusScore != null) {
            "Recent baseline: ${context.recentSessionCount} sessions, average focus score ${context.recentAvgFocusScore}."
        } else {
            "Recent baseline: not enough history yet."
        }
        return """
            You are Drift's focus coach. Use ONLY the metrics below; do not invent numbers.

            Session metrics for $who:
            - Planned duration: ${context.plannedDurationSeconds}s, actual: ${context.actualDurationSeconds}s
            - Completion: ${context.completionStatus}
            - Distracting-app foreground: ${context.restrictedAppForegroundSeconds}s
            - Distraction count: ${context.distractionCount}, restriction attempts: ${context.restrictionAttemptCount}
            - Focus score: ${context.focusScore}/100
            - Tracking coverage: ${context.trackingCoverage}
            - XP earned: ${context.xpEarned}
            $baseline

            Remembered preferences and goals (use only when relevant to suggestions):
            $memory

            Respond in these sections (keep each to 1-3 short sentences):
            1. Summary
            2. Observations (2-3 evidence-backed bullets)
            3. Suggestion (one specific action)
            4. Encouragement (recognize the positive)
            5. Next goal (only if useful)
            Do not claim the user watched a Reel or clicked a notification unless it is directly supported by the metrics above.
        """.trimIndent()
    }
}
