package app.drift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drift.DriftApp
import app.drift.data.ActiveSession
import app.drift.data.AppSettings
import app.drift.di.AppContainer
import app.drift.data.db.RedemptionOutcome
import app.drift.domain.AchievementCatalog
import app.drift.domain.AttentionReport
import app.drift.domain.model.AttentionPathGraph
import app.drift.domain.DriftRiskFeatures
import app.drift.domain.Metrics
import app.drift.domain.RewardCatalog
import app.drift.domain.StreakState
import app.drift.domain.model.Achievement
import app.drift.domain.model.AchievementUnlock
import app.drift.domain.model.BehavioralInsights
import app.drift.domain.model.DailyInsight
import app.drift.domain.model.DailyProgress
import app.drift.domain.model.Intervention
import app.drift.domain.model.NotificationEvent
import app.drift.domain.model.RewardItem
import app.drift.domain.model.RewardRedemption
import app.drift.domain.model.Session
import app.drift.domain.model.StreakView
import app.drift.domain.model.UserIntent
import app.drift.domain.model.UserProgress
import app.drift.domain.model.XpTransaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DriftViewModel(private val container: AppContainer) : ViewModel() {

    val activeSession: StateFlow<ActiveSession?> = container.repository.active
    val streak: StateFlow<StreakState> = container.settings.streak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakState())
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val intents: StateFlow<List<UserIntent>> = container.repository.intents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions: StateFlow<List<Session>> = container.repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val interventions: StateFlow<List<Intervention>> = container.coordinator.interventions
    val notificationEvents: StateFlow<List<NotificationEvent>> = container.repository.notificationEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val profileName: StateFlow<String> = container.settings.profileName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "You")
    val attentionPathGraph: StateFlow<AttentionPathGraph> = combine(
        container.attentionPathStore.graph,
        settings,
    ) { graph, currentSettings ->
        graph.copy(protectedAppIds = currentSettings.protectedContentApps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttentionPathGraph())

    // Gamification
    val progress: StateFlow<UserProgress?> = container.gamificationRepository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val recentTransactions: StateFlow<List<XpTransaction>> = container.gamificationRepository.recentTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unlocks: StateFlow<List<AchievementUnlock>> = container.gamificationRepository.unlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val redemptions: StateFlow<List<RewardRedemption>> = container.gamificationRepository.redemptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val dailyProgress: StateFlow<List<DailyProgress>> = container.gamificationRepository.dailyProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSessionXp: StateFlow<Int> = combine(
        container.gamificationRepository.recentTransactions,
        container.repository.active,
    ) { txs, active ->
        val sid = active?.session?.id
        txs.filter { it.sessionId == sid }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val achievements: StateFlow<List<Pair<Achievement, Boolean>>> = container.gamificationRepository.unlocks
        .map { u ->
            val ids = u.map { it.achievementId }.toSet()
            AchievementCatalog.all.map { it to (it.id in ids) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AchievementCatalog.all.map { it to false })

    val streakView: StateFlow<StreakView> = container.gamificationRepository.dailyProgress
        .map { list -> container.streakService.compute(list.map { LocalDate.parse(it.date) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakView())

    private val _redemptionOutcome = kotlinx.coroutines.flow.MutableStateFlow<RedemptionOutcome?>(null)
    val redemptionOutcome: StateFlow<RedemptionOutcome?> = _redemptionOutcome

    val metrics: StateFlow<Metrics?> = combine(container.repository.sessions, container.repository.intents, container.coordinator.interventions) { s, i, inv ->
        container.outcomeEvaluator.metrics(s, i.associateBy { it.id }, inv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val insights: StateFlow<BehavioralInsights?> = combine(
        container.repository.sessions,
        container.repository.interventions,
        container.repository.notificationEvents,
        container.settings.streak,
        container.gamificationRepository.progress,
    ) { s, inv, notif, st, prog ->
        container.behavioralAnalyzer.analyze(s, inv, notif, prog?.lifetimeXp ?: 0, st.currentStreak)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dailyInsights: StateFlow<List<DailyInsight>> = combine(
        container.repository.sessions,
        container.repository.interventions,
    ) { s, inv -> container.behavioralAnalyzer.dailyInsights(s, inv) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val driftRisk: StateFlow<Float> = combine(
        container.repository.sessions,
        container.settings.streak,
    ) { s, st ->
        container.riskPredictor.predict(DriftRiskFeatures.extract(s, st.currentStreak))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

    /** A short, human-readable summary of today's attention patterns, for the assistant greeting. */
    private val attentionReport = AttentionReport()
    val dailyBrief: StateFlow<String> = combine(
        container.repository.sessions,
        container.repository.foregroundEvents,
    ) { sessions, events ->
        val userFacingEvents = container.normalizeForegroundEvents(events)
        if (sessions.isEmpty() && userFacingEvents.isEmpty()) {
            ""
        } else {
            attentionReport.toText(attentionReport.build(userFacingEvents, sessions))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun startIntent(purpose: String, minutes: Int, kind: app.drift.domain.model.IntentKind, targetPackage: String, targetLabel: String) {
        viewModelScope.launch {
            val intent = UserIntent(
                targetPackage = targetPackage,
                targetLabel = targetLabel,
                purpose = purpose,
                intendedMinutes = minutes,
                kind = kind,
                createdAt = System.currentTimeMillis(),
            )
            val id = container.repository.saveIntent(intent)
            container.coordinator.startSession(intent.copy(id = id))
        }
    }

    fun deleteIntent(id: Long) = viewModelScope.launch { container.repository.deleteIntent(id) }
    fun finishActiveSession() = viewModelScope.launch { container.coordinator.finishSession(achieved = false) }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch { container.settings.setNotificationsEnabled(enabled) }
    fun setWager(active: Boolean) = viewModelScope.launch { container.settings.setWager(active) }
    fun setProfileName(name: String) = viewModelScope.launch { container.settings.setProfileName(name) }
    fun completeOnboarding() = viewModelScope.launch { container.settings.setOnboardingComplete(true) }
    fun resetOnboarding() = viewModelScope.launch { container.settings.setOnboardingComplete(false) }

    fun setGamificationEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setGamificationEnabled(enabled) }
    fun setVariableRewardsEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setVariableRewardsEnabled(enabled) }
    fun setNotificationCostEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setNotificationCostEnabled(enabled) }
    fun setSocialSharingEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setSocialSharingEnabled(enabled) }
    fun setLeaderboardEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setLeaderboardEnabled(enabled) }
    fun setContentBlockingEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setContentBlockingEnabled(enabled) }
    fun setAllowDmVideos(enabled: Boolean) = viewModelScope.launch { container.settings.setAllowDmVideos(enabled) }
    fun setContentBlockMode(mode: app.drift.domain.content.ContentBlockMode) =
        viewModelScope.launch { container.settings.setContentBlockMode(mode.name) }
    fun toggleBlockedApp(packageName: String, blocked: Boolean) =
        viewModelScope.launch { container.settings.toggleBlockedApp(packageName, blocked) }
    fun toggleProtectedPath(appId: String, selected: Boolean) =
        viewModelScope.launch { container.settings.toggleProtectedContentApp(appId, selected) }
    fun setScreenAnalysisEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setScreenAnalysisEnabled(enabled) }
    fun setExternalAiAnalysisEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setExternalAiAnalysisEnabled(enabled) }
    fun setPrivacyNoticeAccepted(accepted: Boolean) = viewModelScope.launch { container.settings.setPrivacyNoticeAccepted(accepted) }
    fun setObservationIntervalMinutes(minutes: Int) = viewModelScope.launch { container.settings.setObservationIntervalMinutes(minutes) }

    /** Asks the AI partner a question; returns the reply (offline fallback if unreachable). */
    suspend fun askAssistant(question: String): String = container.assistantService.ask(question)

    /** Evidence-backed coaching for a finished session. */
    suspend fun coachSession(sessionId: Long): String = container.assistantService.coachSession(sessionId)

    /** Evidence-backed coaching for the most recently finished session. */
    suspend fun coachLastSession(): String = container.assistantService.coachLastSession()

    suspend fun assistantMemoryMarkdown(): String = container.assistantService.memoryMarkdown()

    suspend fun createFocusPlan(): String = container.assistantService.createPlan()

    fun redeemReward(item: RewardItem) = viewModelScope.launch {
        _redemptionOutcome.value = container.redemptionService.redeem(item)
    }

    fun clearRedemptionOutcome() {
        _redemptionOutcome.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DriftApp
                DriftViewModel(app.container)
            }
        }
    }
}
