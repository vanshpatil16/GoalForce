package app.drift.domain

import app.drift.data.ActiveSession
import app.drift.data.AttentionPathStore
import app.drift.data.DriftRepository
import app.drift.data.GamificationRepository
import app.drift.data.SettingsRepository
import app.drift.domain.content.ContentBlockCatalog
import app.drift.domain.model.BonusContext
import app.drift.domain.model.BonusEventType
import app.drift.domain.model.DailyProgress
import app.drift.domain.model.DriftKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.Outcome
import app.drift.domain.model.SessionSummary
import app.drift.domain.model.UsageSnapshot
import app.drift.domain.model.UserIntent
import app.drift.domain.model.XpCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The single entry point for the detection -> decision -> intervention -> reward loop. */
class DriftCoordinator(
    private val repository: DriftRepository,
    private val attentionPathStore: AttentionPathStore,
    private val settings: SettingsRepository,
    private val gamification: GamificationRepository,
    private val engine: IntentEngine,
    private val bandit: InterventionBandit,
    private val evaluator: OutcomeEvaluator,
    private val streakManager: StreakManager,
    private val streakService: StreakService,
    private val xpService: XpService,
    private val achievementService: AchievementService,
    private val trackingPermissions: () -> TrackingPermissions,
    scope: CoroutineScope,
) {
    private val _interventions = MutableStateFlow<List<Intervention>>(emptyList())
    val interventions: StateFlow<List<Intervention>> = _interventions
    private val sessionAnalytics = SessionAnalytics()

    init {
        scope.launch { repository.interventions.collect { _interventions.value = it } }
        scope.launch { repository.restoreActiveSession() }
    }

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

    suspend fun onTargetOpened(): DriftCommand? {
        val active = repository.activeSession() ?: return null
        val kind = DriftKind.OPENED
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

    /** Ends the active session and settles outcomes, XP, streak, daily progress and achievements. */
    suspend fun finishSession(achieved: Boolean) {
        val active = repository.activeSession() ?: return
        val outcome = if (achieved) Outcome.ACHIEVED_PURPOSE else evaluator.outcomeOf(active.session, active.intent)
        val onTime = outcome == Outcome.ON_TIME || outcome == Outcome.ACHIEVED_PURPOSE

        repository.interventionsForSession(active.session.id)
            .forEach { repository.resolveInterventionReward(it.id, onTime) }

        val endedAt = System.currentTimeMillis()
        repository.finishActiveSession(outcome)
        attentionPathStore.recordSessionOutcome(active.session.id, outcome)

        val summary = finalizeSummary(active, outcome, endedAt)

        awardSessionXp(active, onTime, outcome, summary)
        recordDailyProgress(active, onTime, outcome)
        updateStreak(outcome)
        evaluateAchievements(active, outcome)
    }

    /** Derives and persists the session summary from the event timeline. */
    private suspend fun finalizeSummary(
        active: ActiveSession,
        outcome: Outcome,
        endedAt: Long,
    ): SessionAnalytics.Result {
        val session = active.session.copy(endedAt = endedAt)
        val events = repository.foregroundEventsForSession(session.id)
        val restricted = ContentBlockCatalog.rules.flatMap { it.packageIds }.toSet()
        val perms = trackingPermissions()
        val result = sessionAnalytics.summarize(
            session = session,
            plannedMinutes = active.intent.intendedMinutes,
            events = events,
            restrictedPackages = restricted,
            perms = perms,
        )
        repository.upsertSessionSummary(
            SessionSummary(
                sessionId = session.id,
                plannedDurationSeconds = result.plannedDurationSeconds,
                actualDurationSeconds = result.actualDurationSeconds,
                completionStatus = result.completionStatus,
                restrictedAppForegroundSeconds = result.restrictedAppForegroundSeconds,
                distractionCount = result.distractionCount,
                restrictionAttemptCount = result.restrictionAttemptCount,
                appOpenCount = result.appOpenCount,
                focusScore = result.focusScore,
                trackingCoverage = result.trackingCoverage,
                createdAt = endedAt,
            )
        )
        return result
    }

    suspend fun extendSession(extraMinutes: Int) = repository.extendActiveSession(extraMinutes)

    suspend fun startSession(intent: UserIntent) = repository.startSession(intent)

    private suspend fun awardSessionXp(active: ActiveSession, onTime: Boolean, outcome: Outcome, summary: SessionAnalytics.Result) {
        val prefs = settings.settings.first()
        if (!prefs.gamificationEnabled) return
        val sid = active.session.id
        xpService.award(XpCategory.SESSION_COMPLETION, "session:$sid:completion", "Completed a focus session", sessionId = sid)
        if (onTime) {
            xpService.award(XpCategory.TASK_COMPLETION, "session:$sid:task", "Achieved your intention", sessionId = sid)
            if (prefs.variableRewardsEnabled) {
                xpService.awardVariableBonus("session:$sid:bonus", BonusContext(BonusEventType.MILESTONE, System.currentTimeMillis()), sid)
            }
        }
        if (active.session.reentries >= IntentEngine.REENTRY_THRESHOLD) {
            xpService.award(XpCategory.ATTENTION_RECOVERY, "session:$sid:recovery", "Returned to your goal after drifting", sessionId = sid)
        }
        if (summary.distractionCount == 0) {
            xpService.award(XpCategory.DISTRACTION_FREE_BONUS, "session:$sid:distraction_free", "Stayed away from distracting apps", sessionId = sid)
        }
        if (onTime) {
            val today = LocalDate.now()
            xpService.award(XpCategory.DAILY_GOAL, "daily:$today:goal", "Met today's intention")
        }
    }

    private suspend fun recordDailyProgress(active: ActiveSession, onTime: Boolean, outcome: Outcome) {
        val minutes = ((System.currentTimeMillis() - active.session.startedAt) / 60_000L).toInt()
        val qualifying = if (onTime) minutes else 0
        gamification.recordDailyProgress(
            DailyProgress(
                date = LocalDate.now().toString(),
                qualifyingMinutes = qualifying,
                sessionsCompleted = 1,
                xpEarned = 0, // filled from the ledger by consumers if needed
                tasksCompleted = if (onTime) 1 else 0,
                recoveries = if (active.session.reentries >= IntentEngine.REENTRY_THRESHOLD) 1 else 0,
            )
        )
    }

    private suspend fun updateStreak(outcome: Outcome) {
        val achieved = outcome == Outcome.ON_TIME || outcome == Outcome.ACHIEVED_PURPOSE
        val state = streakManager.recordDay(settings.streak.first(), LocalDate.now(), achieved)
        settings.saveStreak(state)
    }

    private suspend fun evaluateAchievements(active: ActiveSession, outcome: Outcome) {
        val sessions = repository.sessions.first()
        val goalsCompleted = sessions.count { it.outcome == Outcome.ON_TIME || it.outcome == Outcome.ACHIEVED_PURPOSE }
        val recoveries = sessions.count { it.reentries >= IntentEngine.REENTRY_THRESHOLD }
        val streak = settings.streak.first()
        val daily = gamification.allDailyProgress().map { LocalDate.parse(it.date) }
        val weeklyConsistency = streakService.compute(daily).qualifyingDaysThisWeek >= 5

        val sessionMinutes = ((System.currentTimeMillis() - active.session.startedAt) / 60_000L).toInt()
        val previousBest = sessions.filter { it.id != active.session.id }
            .mapNotNull { s -> s.endedAt?.let { end -> ((end - s.startedAt) / 60_000L).toInt() } }
            .maxOrNull() ?: 0
        val personalBest = sessionMinutes > previousBest && previousBest > 0

        val snapshot = ProgressSnapshot(
            sessionsCompleted = sessions.size,
            goalsCompleted = goalsCompleted,
            currentStreak = streak.currentStreak,
            attentionRecoveries = recoveries,
            personalBestAchieved = personalBest,
            weeklyConsistencyAchieved = weeklyConsistency,
        )

        val unlockedIds = gamification.allUnlocks().map { it.achievementId }.toSet()
        for (achievement in achievementService.evaluate(snapshot)) {
            if (achievement.id in unlockedIds) continue
            val newly = gamification.unlock(achievement.id, System.currentTimeMillis())
            if (newly) {
                xpService.award(XpCategory.ACHIEVEMENT, "achievement:${achievement.id}", "Unlocked: ${achievement.title}", amountOverride = achievement.xpReward)
            }
        }
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
