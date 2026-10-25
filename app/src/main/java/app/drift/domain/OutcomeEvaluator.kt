package app.drift.domain

import app.drift.domain.model.Intervention
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import app.drift.domain.model.UserIntent

/**
 * Turns raw session history into answers to the question the whole product is
 * about: *"do interventions actually help people meet their own goals?"*
 *
 * The reward for the bandit is defined here: an intervention "worked" when the
 * session it interrupted ended the way the user intended (on-time or purpose
 * achieved) rather than as an overrun or abandon.
 */
class OutcomeEvaluator {

    fun outcomeOf(session: Session, intent: UserIntent): Outcome {
        if (session.state == app.drift.domain.model.SessionState.ABANDONED) return Outcome.ABANDONED
        val elapsed = elapsedMinutes(session)
        return when {
            elapsed <= intent.intendedMinutes -> Outcome.ON_TIME
            else -> Outcome.OVERRAN
        }
    }

    /**
     * Bandit reward: true when the intervention is followed by an on-time (or
     * purpose-achieved) end of the session. Mirrors Duolingo's "completed a
     * lesson within two hours of the notification" reward.
     */
    fun rewardOf(intervention: Intervention, session: Session, intent: UserIntent): Boolean {
        return outcomeOf(session, intent).let { it == Outcome.ON_TIME || it == Outcome.ACHIEVED_PURPOSE }
    }

    /** Aggregate, intent-relative metrics used by the Progress screen. */
    fun metrics(sessions: List<Session>, intents: Map<Long, UserIntent>, interventions: List<Intervention>): Metrics {
        val finished = sessions.filter { it.outcome != null && it.outcome != Outcome.ABANDONED }
        val withIntervention = finished.filter { s -> interventions.any { it.sessionId == s.id } }
        val withoutIntervention = finished.filter { s -> interventions.none { it.sessionId == s.id } }

        fun overrunRate(list: List<Session>): Double =
            if (list.isEmpty()) 0.0
            else list.count { it.outcome == Outcome.OVERRAN }.toDouble() / list.size

        val rewarded = interventions.count { it.reward == true }
        val resolved = interventions.count { it.reward != null }

        return Metrics(
            totalSessions = sessions.size,
            overrunRate = overrunRate(finished),
            overrunRateWithIntervention = overrunRate(withIntervention),
            overrunRateWithoutIntervention = overrunRate(withoutIntervention),
            interventionCount = interventions.size,
            rewardRate = if (resolved == 0) null else rewarded.toDouble() / resolved,
            averageOverrunMinutes = finished.mapNotNull { s ->
                intents[s.intentId]?.let { (elapsedMinutes(s) - it.intendedMinutes).coerceAtLeast(0) }
            }.average().takeIf { it.isFinite() } ?: 0.0,
        )
    }

    private fun elapsedMinutes(session: Session): Int {
        val end = session.endedAt ?: System.currentTimeMillis()
        return ((end - session.startedAt) / 60_000L).toInt()
    }
}

data class Metrics(
    val totalSessions: Int,
    val overrunRate: Double,
    val overrunRateWithIntervention: Double,
    val overrunRateWithoutIntervention: Double,
    val interventionCount: Int,
    val rewardRate: Double?,
    val averageOverrunMinutes: Double,
)
