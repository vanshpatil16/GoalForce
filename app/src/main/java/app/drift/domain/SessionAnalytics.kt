package app.drift.domain

import app.drift.domain.model.ForegroundEvent
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import app.drift.domain.model.SessionState

/** Whether real-time (accessibility) and periodic (usage) tracking were available during a session. */
data class TrackingPermissions(
    val accessibilityEnabled: Boolean,
    val usageAccess: Boolean,
)

/**
 * Derives a [SessionAnalytics.Result] from a session and its persisted events.
 * Pure and deterministic — no counters are incremented elsewhere; durations and
 * counts are recomputed from the event timeline so late/duplicate events can be
 * replayed safely.
 *
 * "Observed" vs "estimated": foreground durations are *estimated* by pairing
 * consecutive APP_OPEN events (we do not record an explicit foreground-exit
 * event), while restriction attempts (CONTENT_BLOCKED) are directly observed.
 */
class SessionAnalytics {

    data class Result(
        val plannedDurationSeconds: Int,
        val actualDurationSeconds: Int,
        val completionStatus: String,
        val restrictedAppForegroundSeconds: Int,
        val distractionCount: Int,
        val restrictionAttemptCount: Int,
        val appOpenCount: Int,
        val focusScore: Int,
        val trackingCoverage: Float,
    )

    fun summarize(
        session: Session,
        plannedMinutes: Int,
        events: List<ForegroundEvent>,
        restrictedPackages: Set<String>,
        perms: TrackingPermissions,
    ): Result {
        val startedAt = session.startedAt
        val endedAt = session.endedAt ?: startedAt
        val actualSeconds = ((endedAt - startedAt) / 1000).toInt().coerceAtLeast(0)
        val plannedSeconds = (plannedMinutes * 60).coerceAtLeast(0)

        val opens = events
            .filter { it.kind == ForegroundEventKind.APP_OPEN }
            .filter { it.at >= startedAt && it.at <= endedAt }
            .sortedBy { it.at }

        val blocks = events
            .filter { it.kind == ForegroundEventKind.CONTENT_BLOCKED }
            .filter { it.at >= startedAt && it.at <= endedAt }

        val appOpenCount = opens.size
        val restrictionAttemptCount = blocks.size
        val restrictedOpenCount = opens.count { it.packageName in restrictedPackages }
        val distractionCount = restrictedOpenCount + restrictionAttemptCount

        // Estimate time-in-restricted-app by pairing consecutive opens (interval
        // between an open and the next open is attributed to the earlier app).
        var restrictedSeconds = 0
        val times = opens.map { it.at }
        for (i in times.indices) {
            if (opens[i].packageName !in restrictedPackages) continue
            val from = times[i]
            val to = if (i + 1 < times.size) times[i + 1] else endedAt
            restrictedSeconds += ((to - from) / 1000).toInt().coerceAtLeast(0)
        }

        val focusScore = if (actualSeconds <= 0) {
            100
        } else {
            val ratio = restrictedSeconds.toDouble() / actualSeconds
            (100 - (ratio * 100)).toInt().coerceIn(0, 100)
        }

        val trackingCoverage = when {
            perms.accessibilityEnabled -> 1.0f
            perms.usageAccess -> 0.7f
            else -> 0.0f
        }

        val completionStatus = when {
            session.outcome == Outcome.OVERRAN -> "overran"
            session.outcome == Outcome.ABANDONED || session.state == SessionState.ABANDONED -> "abandoned"
            session.outcome == Outcome.ON_TIME || session.outcome == Outcome.ACHIEVED_PURPOSE -> "completed"
            else -> "unknown"
        }

        return Result(
            plannedDurationSeconds = plannedSeconds,
            actualDurationSeconds = actualSeconds,
            completionStatus = completionStatus,
            restrictedAppForegroundSeconds = restrictedSeconds,
            distractionCount = distractionCount,
            restrictionAttemptCount = restrictionAttemptCount,
            appOpenCount = appOpenCount,
            focusScore = focusScore,
            trackingCoverage = trackingCoverage,
        )
    }
}
