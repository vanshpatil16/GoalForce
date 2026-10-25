package app.drift.domain

import app.drift.data.ActiveSession
import app.drift.data.DriftRepository
import app.drift.data.SettingsRepository
import app.drift.domain.model.DriftKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.Outcome
import app.drift.domain.model.UsageSnapshot
import app.drift.domain.model.UserIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The single entry point for the detection -> decision -> intervention loop. */
class DriftCoordinator(
    private val repository: DriftRepository,
    private val settings: SettingsRepository,
    private val engine: IntentEngine,
    private val bandit: InterventionBandit,
    private val evaluator: OutcomeEvaluator,
    private val streakManager: StreakManager,
    scope: CoroutineScope,
) {
    private val _interventions = MutableStateFlow<List<Intervention>>(emptyList())
    val interventions: StateFlow<List<Intervention>> = _interventions

    init {
        scope.launch { repository.interventions.collect { _interventions.value = it } }
        scope.launch { repository.restoreActiveSession() }
    }

    /**
     * Called on a usage snapshot (foreground change / periodic tick). Returns a
     * [DriftCommand] describing whether to intervene and how, or null when the
     * user is on track.
     */
    suspend fun evaluate(snapshot: UsageSnapshot): DriftCommand? {
        val active = repository.activeSession() ?: return null
        val kind = engine.evaluate(active.intent, snapshot, active.session.extendedMinutes)
        if (kind == DriftKind.ON_TRACK) return null

        val hasStreak = streakManager.hasActiveStreak(settings.streak.first())
        val template = bandit.select(
            history = _interventions.value,
            driftKind = kind,
            nowMs = System.currentTimeMillis(),
            hasStreak = hasStreak,
        )
        val id = repository.recordIntervention(active.session.id, template, kind)
        return DriftCommand.Show(template, kind, id, active)
    }

    /** Ends the active session and settles outcomes, bandit rewards and the streak. */
    suspend fun finishSession(achieved: Boolean) {
        val active = repository.activeSession() ?: return
        val outcome = if (achieved) {
            Outcome.ACHIEVED_PURPOSE
        } else {
            evaluator.outcomeOf(active.session, active.intent)
        }

        val rewarded = outcome == Outcome.ON_TIME || outcome == Outcome.ACHIEVED_PURPOSE
        repository.interventionsForSession(active.session.id)
            .forEach { repository.resolveInterventionReward(it.id, rewarded) }

        repository.finishActiveSession(outcome)
        updateStreak(outcome)
    }

    suspend fun extendSession(extraMinutes: Int) = repository.extendActiveSession(extraMinutes)

    suspend fun startSession(intent: UserIntent) = repository.startSession(intent)

    private suspend fun updateStreak(outcome: Outcome) {
        val achieved = outcome == Outcome.ON_TIME || outcome == Outcome.ACHIEVED_PURPOSE
        val state = streakManager.recordDay(settings.streak.first(), LocalDate.now(), achieved)
        settings.saveStreak(state)
    }

    suspend fun metrics(): Metrics {
        val sessions = repository.sessions.first()
        val intents = repository.intents.first().associateBy { it.id }
        return evaluator.metrics(sessions, intents, _interventions.value)
    }
}

sealed interface DriftCommand {
    data object None : DriftCommand
    data class Show(
        val template: InterventionTemplate,
        val driftKind: DriftKind,
        val interventionId: Long,
        val active: ActiveSession,
    ) : DriftCommand
}
