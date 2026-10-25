package app.drift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drift.DriftApp
import app.drift.data.ActiveSession
import app.drift.data.AppSettings
import app.drift.di.AppContainer
import app.drift.domain.Metrics
import app.drift.domain.StreakState
import app.drift.domain.model.Intervention
import app.drift.domain.model.Session
import app.drift.domain.model.UserIntent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val metrics: StateFlow<Metrics?> = combine(container.repository.sessions, container.repository.intents, container.coordinator.interventions) { s, i, inv ->
        container.outcomeEvaluator.metrics(s, i.associateBy { it.id }, inv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    fun completeOnboarding() = viewModelScope.launch { container.settings.setOnboardingComplete() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DriftApp
                DriftViewModel(app.container)
            }
        }
    }
}
