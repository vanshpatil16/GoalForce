package app.drift.data

import app.drift.data.db.DriftDatabase
import app.drift.data.db.ForegroundEventEntity
import app.drift.data.db.InterventionEntity
import app.drift.data.db.NotificationEventEntity
import app.drift.data.db.SessionEntity
import app.drift.data.db.SessionSummaryEntity
import app.drift.data.db.UserIntentEntity
import app.drift.domain.model.DriftKind
import app.drift.domain.model.EventConfidence
import app.drift.domain.model.ForegroundEvent
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.Session
import app.drift.domain.model.SessionState
import app.drift.domain.model.SessionSummary
import app.drift.domain.model.UserIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** A live session joined with the intent that started it. */
data class ActiveSession(val intent: UserIntent, val session: Session)

class DriftRepository(private val db: DriftDatabase) {

    private val intentDao = db.intentDao()
    private val sessionDao = db.sessionDao()
    private val interventionDao = db.interventionDao()
    private val notificationEventDao = db.notificationEventDao()
    private val foregroundEventDao = db.foregroundEventDao()
    private val sessionSummaryDao = db.sessionSummaryDao()

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active

    val intents: Flow<List<UserIntent>> = intentDao.observeAll().map { l -> l.map { it.toModel() } }
    val sessions: Flow<List<Session>> = sessionDao.observeAll().map { l -> l.map { it.toModel() } }
    val interventions: Flow<List<Intervention>> =
        interventionDao.observeAll().map { l -> l.map { it.toModel() } }

    val notificationEvents: Flow<List<app.drift.domain.model.NotificationEvent>> =
        notificationEventDao.observeAll().map { l -> l.map { it.toModel() } }

    val foregroundEvents: Flow<List<ForegroundEvent>> =
        foregroundEventDao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun saveIntent(intent: UserIntent): Long =
        intentDao.insert(
            UserIntentEntity(
                targetPackage = intent.targetPackage,
                targetLabel = intent.targetLabel,
                purpose = intent.purpose,
                intendedMinutes = intent.intendedMinutes,
                kind = intent.kind,
                createdAt = intent.createdAt,
            )
        )

    suspend fun deleteIntent(id: Long) = intentDao.delete(id)

    suspend fun startSession(intent: UserIntent) {
        val session = Session(
            intentId = intent.id,
            targetPackage = intent.targetPackage,
            startedAt = System.currentTimeMillis(),
        )
        val id = sessionDao.insert(session.toEntity())
        _active.value = ActiveSession(intent, session.copy(id = id))
    }

    suspend fun restoreActiveSession() {
        val entity = sessionDao.active() ?: return
        val intent = intentDao.byId(entity.intentId)?.toModel() ?: return
        _active.value = ActiveSession(intent, entity.toModel())
    }

    fun activeSession(): ActiveSession? = _active.value

    suspend fun recordExit() {
        val a = _active.value ?: return
        sessionDao.updateSignals(a.session.id, a.session.reentries, a.session.appSwitches, System.currentTimeMillis())
        _active.value = a.copy(session = a.session.copy(lastExitAt = System.currentTimeMillis()))
    }

    suspend fun recordReentry() {
        val a = _active.value ?: return
        val reentries = a.session.reentries + 1
        sessionDao.updateSignals(a.session.id, reentries, a.session.appSwitches + 1, a.session.lastExitAt)
        _active.value = a.copy(session = a.session.copy(reentries = reentries, appSwitches = a.session.appSwitches + 1))
    }

    suspend fun recordAppSwitch() {
        val a = _active.value ?: return
        sessionDao.updateSignals(a.session.id, a.session.reentries, a.session.appSwitches + 1, a.session.lastExitAt)
        _active.value = a.copy(session = a.session.copy(appSwitches = a.session.appSwitches + 1))
    }

    suspend fun extendActiveSession(extraMinutes: Int) {
        val a = _active.value ?: return
        val newTotal = a.session.extendedMinutes + extraMinutes
        sessionDao.extend(a.session.id, newTotal)
        _active.value = a.copy(session = a.session.copy(extendedMinutes = newTotal))
    }

    suspend fun finishActiveSession(outcome: app.drift.domain.model.Outcome) {
        val a = _active.value ?: return
        sessionDao.finish(a.session.id, System.currentTimeMillis(), SessionState.COMPLETED, outcome)
        _active.value = null
    }

    suspend fun abandonActiveSession() {
        val a = _active.value ?: return
        sessionDao.finish(a.session.id, System.currentTimeMillis(), SessionState.ABANDONED, null)
        _active.value = null
    }

    suspend fun recordIntervention(sessionId: Long, template: InterventionTemplate, driftKind: DriftKind): Long =
        interventionDao.insert(
            InterventionEntity(
                sessionId = sessionId,
                template = template,
                driftKind = driftKind,
                at = System.currentTimeMillis(),
                reward = null,
            )
        )

    suspend fun resolveInterventionReward(id: Long, reward: Boolean) =
        interventionDao.setReward(id, reward)

    suspend fun interventionsForSession(sessionId: Long): List<Intervention> =
        interventionDao.forSession(sessionId).map { it.toModel() }

    suspend fun recordNotificationPosted(channel: String): Long =
        notificationEventDao.insert(
            NotificationEventEntity(channel = channel, postedAt = System.currentTimeMillis(), clickedAt = null)
        )

    suspend fun recordNotificationClick(channel: String) {
        val e = notificationEventDao.latestUnclicked(channel) ?: return
        notificationEventDao.markClicked(e.id, System.currentTimeMillis())
    }

    suspend fun notificationEventsSnapshot(): List<app.drift.domain.model.NotificationEvent> =
        notificationEventDao.all().map { it.toModel() }

    /** Records a fine-grained foreground/block event, tagged with the session it occurred during. */
    suspend fun recordForegroundEvent(
        packageName: String,
        label: String,
        kind: ForegroundEventKind,
        sessionId: Long? = null,
        elapsedRealtimeMs: Long? = null,
        confidence: EventConfidence = EventConfidence.OBSERVED,
    ) {
        foregroundEventDao.insert(
            ForegroundEventEntity(
                sessionId = sessionId,
                packageName = packageName,
                label = label,
                kind = kind,
                at = System.currentTimeMillis(),
                elapsedRealtimeMs = elapsedRealtimeMs,
                confidence = confidence,
            )
        )
    }

    suspend fun foregroundEventsSince(since: Long): List<ForegroundEvent> =
        foregroundEventDao.since(since).map { it.toModel() }

    suspend fun foregroundEventsSnapshot(): List<ForegroundEvent> =
        foregroundEventDao.since(0L).map { it.toModel() }

    suspend fun foregroundEventsForSession(sessionId: Long): List<ForegroundEvent> =
        foregroundEventDao.forSession(sessionId).map { it.toModel() }

    /** Drops event history older than [days] to keep the log bounded. */
    suspend fun pruneForegroundEvents(days: Long) {
        val before = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        foregroundEventDao.prune(before)
    }

    // ---- Session summaries (derived analytics) ----
    val recentSessionSummaries: Flow<List<SessionSummary>> =
        sessionSummaryDao.observeRecent(30).map { l -> l.map { it.toModel() } }

    suspend fun upsertSessionSummary(summary: SessionSummary) = sessionSummaryDao.upsert(summary.toEntity())

    suspend fun sessionSummary(id: Long): SessionSummary? = sessionSummaryDao.byId(id)?.toModel()

    suspend fun recentSessionSummariesSnapshot(limit: Int): List<SessionSummary> =
        sessionSummaryDao.recent(limit).map { it.toModel() }

    private fun Session.toEntity() = SessionEntity(
        id = id,
        intentId = intentId,
        targetPackage = targetPackage,
        startedAt = startedAt,
        endedAt = endedAt,
        lastExitAt = lastExitAt,
        reentries = reentries,
        appSwitches = appSwitches,
        extendedMinutes = extendedMinutes,
        state = state,
        outcome = outcome,
    )

    private fun SessionSummary.toEntity() = SessionSummaryEntity(
        sessionId = sessionId,
        plannedDurationSeconds = plannedDurationSeconds,
        actualDurationSeconds = actualDurationSeconds,
        completionStatus = completionStatus,
        restrictedAppForegroundSeconds = restrictedAppForegroundSeconds,
        distractionCount = distractionCount,
        restrictionAttemptCount = restrictionAttemptCount,
        appOpenCount = appOpenCount,
        focusScore = focusScore,
        trackingCoverage = trackingCoverage,
        xpEarned = xpEarned,
        createdAt = createdAt,
    )
}
