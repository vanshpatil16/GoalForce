package app.drift.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.drift.domain.content.ContentBlockCatalog
import app.drift.domain.StreakState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val gamificationEnabled: Boolean = true,
    val variableRewardsEnabled: Boolean = true,
    val notificationCostEnabled: Boolean = true,
    val socialSharingEnabled: Boolean = false,
    val leaderboardEnabled: Boolean = false,
    val contentBlockingEnabled: Boolean = false,
    val allowDmVideos: Boolean = true,
    val contentBlockMode: String = "COMPLETE_BLOCK",
    val blockedApps: Set<String> = emptySet(),
    val protectedContentApps: Set<String> = ContentBlockCatalog.rules.map { it.appId }.toSet(),
    val screenAnalysisEnabled: Boolean = false,
    val externalAiAnalysisEnabled: Boolean = false,
    val privacyNoticeAccepted: Boolean = false,
    val observationIntervalMinutes: Int = 5,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            notificationsEnabled = p[KEY_NOTIFICATIONS] ?: true,
            onboardingComplete = p[KEY_ONBOARDING] ?: false,
            usageAccessGranted = p[KEY_USAGE_ACCESS] ?: false,
            gamificationEnabled = p[KEY_GAMIFICATION] ?: true,
            variableRewardsEnabled = p[KEY_VARIABLE_REWARDS] ?: true,
            notificationCostEnabled = p[KEY_NOTIFICATION_COST] ?: true,
            socialSharingEnabled = p[KEY_SOCIAL] ?: false,
            leaderboardEnabled = p[KEY_LEADERBOARD] ?: false,
            contentBlockingEnabled = p[KEY_CONTENT_BLOCK] ?: false,
            allowDmVideos = p[KEY_ALLOW_DM] ?: true,
            contentBlockMode = p[KEY_CONTENT_MODE] ?: "COMPLETE_BLOCK",
            blockedApps = p[KEY_BLOCKED_APPS] ?: emptySet(),
            protectedContentApps = p[KEY_PROTECTED_CONTENT_APPS] ?: ContentBlockCatalog.rules.map { it.appId }.toSet(),
            screenAnalysisEnabled = p[KEY_SCREEN_ANALYSIS] ?: false,
            externalAiAnalysisEnabled = p[KEY_EXTERNAL_AI_ANALYSIS] ?: false,
            privacyNoticeAccepted = p[KEY_PRIVACY_NOTICE] ?: false,
            observationIntervalMinutes = p[KEY_OBSERVATION_INTERVAL] ?: 5,
        )
    }

    val streak: Flow<StreakState> = context.dataStore.data.map { p ->
        StreakState(
            currentStreak = p[KEY_STREAK] ?: 0,
            bestStreak = p[KEY_BEST_STREAK] ?: 0,
            lastCompletedDay = p[KEY_LAST_DAY],
            freezesAvailable = p[KEY_FREEZES] ?: 0,
            wagerActive = p[KEY_WAGER] ?: false,
        )
    }

    val profileName: Flow<String> = context.dataStore.data.map { it[KEY_PROFILE_NAME] ?: "You" }

    suspend fun setProfileName(name: String) =
        context.dataStore.edit { it[KEY_PROFILE_NAME] = name }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }

    suspend fun setOnboardingComplete(complete: Boolean = true) =
        context.dataStore.edit { it[KEY_ONBOARDING] = complete }

    suspend fun setUsageAccessGranted(granted: Boolean) =
        context.dataStore.edit { it[KEY_USAGE_ACCESS] = granted }

    suspend fun saveStreak(state: StreakState) = context.dataStore.edit {
        it[KEY_STREAK] = state.currentStreak
        it[KEY_BEST_STREAK] = state.bestStreak
        state.lastCompletedDay?.let { d -> it[KEY_LAST_DAY] = d } ?: it.remove(KEY_LAST_DAY)
        it[KEY_FREEZES] = state.freezesAvailable
        it[KEY_WAGER] = state.wagerActive
    }

    suspend fun setWager(active: Boolean) = context.dataStore.edit { it[KEY_WAGER] = active }

    suspend fun setGamificationEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_GAMIFICATION] = enabled }
    suspend fun setVariableRewardsEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_VARIABLE_REWARDS] = enabled }
    suspend fun setNotificationCostEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_NOTIFICATION_COST] = enabled }
    suspend fun setSocialSharingEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_SOCIAL] = enabled }
    suspend fun setLeaderboardEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_LEADERBOARD] = enabled }
    suspend fun setContentBlockingEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_CONTENT_BLOCK] = enabled }
    suspend fun setAllowDmVideos(enabled: Boolean) = context.dataStore.edit { it[KEY_ALLOW_DM] = enabled }
    suspend fun setContentBlockMode(mode: String) = context.dataStore.edit { it[KEY_CONTENT_MODE] = mode }
    suspend fun setBlockedApps(apps: Set<String>) = context.dataStore.edit { it[KEY_BLOCKED_APPS] = apps }
    suspend fun toggleBlockedApp(packageName: String, blocked: Boolean) = context.dataStore.edit { p ->
        val current = p[KEY_BLOCKED_APPS] ?: emptySet()
        p[KEY_BLOCKED_APPS] = if (blocked) current + packageName else current - packageName
    }
    suspend fun toggleProtectedContentApp(appId: String, protected: Boolean) = context.dataStore.edit { p ->
        val supported = ContentBlockCatalog.rules.map { it.appId }.toSet()
        if (appId !in supported) return@edit
        val current = p[KEY_PROTECTED_CONTENT_APPS] ?: supported
        p[KEY_PROTECTED_CONTENT_APPS] = if (protected) current + appId else current - appId
    }
    suspend fun setScreenAnalysisEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_SCREEN_ANALYSIS] = enabled }
    suspend fun setExternalAiAnalysisEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_EXTERNAL_AI_ANALYSIS] = enabled }
    suspend fun setPrivacyNoticeAccepted(accepted: Boolean) = context.dataStore.edit { it[KEY_PRIVACY_NOTICE] = accepted }
    suspend fun setObservationIntervalMinutes(minutes: Int) = context.dataStore.edit {
        it[KEY_OBSERVATION_INTERVAL] = minutes.coerceIn(1, 60)
    }

    private companion object {
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding")
        val KEY_USAGE_ACCESS = booleanPreferencesKey("usage_access")
        val KEY_STREAK = intPreferencesKey("streak")
        val KEY_BEST_STREAK = intPreferencesKey("best_streak")
        val KEY_LAST_DAY = stringPreferencesKey("last_day")
        val KEY_FREEZES = intPreferencesKey("freezes")
        val KEY_WAGER = booleanPreferencesKey("wager")
        val KEY_PROFILE_NAME = stringPreferencesKey("profile_name")
        val KEY_GAMIFICATION = booleanPreferencesKey("gamification")
        val KEY_VARIABLE_REWARDS = booleanPreferencesKey("variable_rewards")
        val KEY_NOTIFICATION_COST = booleanPreferencesKey("notification_cost")
        val KEY_SOCIAL = booleanPreferencesKey("social")
        val KEY_LEADERBOARD = booleanPreferencesKey("leaderboard")
        val KEY_CONTENT_BLOCK = booleanPreferencesKey("content_block")
        val KEY_ALLOW_DM = booleanPreferencesKey("allow_dm")
        val KEY_CONTENT_MODE = stringPreferencesKey("content_mode")
        val KEY_BLOCKED_APPS = stringSetPreferencesKey("blocked_apps")
        val KEY_PROTECTED_CONTENT_APPS = stringSetPreferencesKey("protected_content_apps")
        val KEY_SCREEN_ANALYSIS = booleanPreferencesKey("screen_analysis_enabled")
        val KEY_EXTERNAL_AI_ANALYSIS = booleanPreferencesKey("external_ai_analysis_enabled")
        val KEY_PRIVACY_NOTICE = booleanPreferencesKey("privacy_notice_accepted")
        val KEY_OBSERVATION_INTERVAL = intPreferencesKey("observation_interval_minutes")
    }
}
