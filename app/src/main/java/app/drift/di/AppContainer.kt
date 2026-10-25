package app.drift.di

import android.content.Context
import app.drift.data.DriftRepository
import app.drift.data.SettingsRepository
import app.drift.data.db.DriftDatabase
import app.drift.domain.DriftCoordinator
import app.drift.domain.IntentEngine
import app.drift.domain.InterventionBandit
import app.drift.domain.OutcomeEvaluator
import app.drift.domain.StreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Kept deliberately free of a DI framework so the
 * scaffold is small, testable and easy to reason about.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = DriftDatabase.build(context)

    val repository = DriftRepository(database)
    val settings = SettingsRepository(context)

    val intentEngine = IntentEngine()
    val bandit = InterventionBandit()
    val outcomeEvaluator = OutcomeEvaluator()
    val streakManager = StreakManager()

    val coordinator = DriftCoordinator(
        repository = repository,
        settings = settings,
        engine = intentEngine,
        bandit = bandit,
        evaluator = outcomeEvaluator,
        streakManager = streakManager,
        scope = appScope,
    )
}
