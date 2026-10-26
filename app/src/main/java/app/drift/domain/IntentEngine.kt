package app.drift.domain

import app.drift.domain.model.DriftKind
import app.drift.domain.model.IntentKind
import app.drift.domain.model.UsageSnapshot
import app.drift.domain.model.UserIntent

/**
 * Decides whether the user's *actual* usage has drifted from their *stated*
 * intent.
 *
 * The guiding principle (and the thing that makes this different from a naive
 * screen-time limiter): **time alone is never treated as harmful.** A user who
 * said "watch one lecture, 45 minutes" and is 40 minutes into a single video is
 * ON_TRACK, not "addicted". Drift is measured as *divergence from intent*, not
 * absolute duration.
 *
 * This class is pure Kotlin and fully unit-testable.
 */
class IntentEngine {

    fun evaluate(intent: UserIntent, snap: UsageSnapshot, extendedMinutes: Int = 0): DriftKind {
        val effectiveMinutes = intent.intendedMinutes + extendedMinutes

        // 1) Compulsive re-entry: leaving and immediately returning repeatedly is
        //    the strongest behavioural signal of "I'm stuck in a loop", and it is
        //    independent of the intended duration.
        if (snap.reentries >= REENTRY_THRESHOLD &&
            snap.secondsSinceLastExit in 0..REENTRY_WINDOW_MS
        ) {
            return DriftKind.COMPULSIVE_REENTRY
        }

        // 2) Over-run: still going after the time the user gave themselves.
        if (snap.elapsedMinutes > effectiveMinutes) {
            return DriftKind.OVER_RUN
        }

        // 3) Intentional long-form: the user declared a focused, long session and
        //    is actually staying in one app. Do not interrupt.
        if (intent.kind == IntentKind.LONG_FORM && snap.singleAppFocus) {
            return DriftKind.ON_TRACK
        }

        // 4) Everything else is on track for now.
        return DriftKind.ON_TRACK
    }

    /**
     * How strongly we should intervene. Natural stopping points and reflection
     * prompts are soft; over-runs and compulsive loops are firmer.
     */
    fun severity(kind: DriftKind): Int = when (kind) {
        DriftKind.ON_TRACK -> 0
        DriftKind.NATURAL_STOPPING -> 1
        DriftKind.OPENED -> 2
        DriftKind.OVER_RUN -> 2
        DriftKind.COMPULSIVE_REENTRY -> 3
    }

    companion object {
        const val REENTRY_THRESHOLD = 2
        const val REENTRY_WINDOW_MS = 3 * 60_000L
    }
}
