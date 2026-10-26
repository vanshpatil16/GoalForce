package app.drift.di

import android.content.Context
import app.drift.BuildConfig
import app.drift.data.DriftRepository
import app.drift.data.AttentionPathStore
import app.drift.data.GamificationRepository
import app.drift.data.SettingsRepository
import app.drift.data.ai.GroqClient
import app.drift.data.db.DriftDatabase
import app.drift.data.memory.AssistantMemoryStore
import app.drift.data.news.MotivationNewsRepository
import app.drift.domain.AchievementService
import app.drift.domain.AttentionPathAnalyzer
import app.drift.domain.AssistantService
import app.drift.domain.BehavioralAnalyzer
import app.drift.domain.DriftCoordinator
import app.drift.domain.DriftRiskPredictor
import app.drift.domain.IntentEngine
import app.drift.domain.InterventionBandit
import app.drift.domain.NotificationCostService
import app.drift.domain.NotificationTimingBandit
import app.drift.domain.OutcomeEvaluator
import app.drift.domain.RedemptionService
import app.drift.domain.StreakManager
import app.drift.domain.StreakService
import app.drift.domain.TfliteDriftRiskPredictor
import app.drift.domain.TrackingPermissions
import app.drift.domain.VariableRewardConfig
import app.drift.domain.VariableRewardService
import app.drift.domain.XpRules
import app.drift.domain.XpService
import app.drift.notification.MotivationNewsLimiter
import app.drift.domain.model.ForegroundEvent
import app.drift.service.UsageStatsCache
import app.drift.util.AppLabels
import app.drift.util.Permissions
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
    val attentionPathStore = AttentionPathStore(context, AttentionPathAnalyzer())
    val settings = SettingsRepository(context)
    val motivationNewsRepository = MotivationNewsRepository()
    val motivationNewsLimiter = MotivationNewsLimiter(context)
    val normalizeForegroundEvents: (List<ForegroundEvent>) -> List<ForegroundEvent> = { events ->
        events.asSequence()
            .filter { AppLabels.isUserFacingApp(context, it.packageName) }
            .map { it.copy(label = AppLabels.name(context, it.packageName)) }
            .toList()
    }

    val intentEngine = IntentEngine()
    val bandit = InterventionBandit()
    val outcomeEvaluator = OutcomeEvaluator()
    val streakManager = StreakManager()
    val timingBandit = NotificationTimingBandit()
    val behavioralAnalyzer = BehavioralAnalyzer(timingBandit)
    val riskPredictor: DriftRiskPredictor = TfliteDriftRiskPredictor(context)

    // Gamification
    val gamificationRepository = GamificationRepository(database.gamificationDao())
    val variableRewards = VariableRewardService(VariableRewardConfig())
    val xpService = XpService(XpRules(), gamificationRepository, variableRewards)
    val achievementService = AchievementService()
    val streakService = StreakService()
    val notificationCostService = NotificationCostService()
    val redemptionService = RedemptionService(gamificationRepository)

    // Assistant (AI partner)
    val assistantService = AssistantService(
        normalizeForegroundEvents = normalizeForegroundEvents,
        repository = repository,
        settings = settings,
        client = GroqClient(BuildConfig.GROQ_API_KEY),
        memoryStore = AssistantMemoryStore(context),
    )

    val coordinator = DriftCoordinator(
        repository = repository,
        attentionPathStore = attentionPathStore,
        settings = settings,
        gamification = gamificationRepository,
        engine = intentEngine,
        bandit = bandit,
        evaluator = outcomeEvaluator,
        streakManager = streakManager,
        streakService = streakService,
        xpService = xpService,
        achievementService = achievementService,
        trackingPermissions = {
            TrackingPermissions(
                accessibilityEnabled = Permissions.isAccessibilityEnabled(context),
                usageAccess = UsageStatsCache.hasUsageAccess(context),
            )
        },
        scope = appScope,
    )
}
