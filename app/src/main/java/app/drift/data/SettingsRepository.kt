package app.drift.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.drift.domain.StreakState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val usageAccessGranted: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            notificationsEnabled = p[KEY_NOTIFICATIONS] ?: true,
            onboardingComplete = p[KEY_ONBOARDING] ?: false,
            usageAccessGranted = p[KEY_USAGE_ACCESS] ?: false,
        )
    }

    val streak: Flow<StreakState> = context.dataStore.data.map { p ->
        StreakState(
            currentStreak = p[KEY_STREAK] ?: 0,
            bestStreak = p[KEY_BEST_STREAK] ?: 0,
            lastCompletedDay = p[KEY_LAST_DAY],
            freezesAvailable = p[KEY_FREEZES] ?: 0,
            xp = p[KEY_XP] ?: 0,
            wagerActive = p[KEY_WAGER] ?: false,
        )
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }

    suspend fun setOnboardingComplete() =
        context.dataStore.edit { it[KEY_ONBOARDING] = true }

    suspend fun setUsageAccessGranted(granted: Boolean) =
        context.dataStore.edit { it[KEY_USAGE_ACCESS] = granted }

    suspend fun saveStreak(state: StreakState) = context.dataStore.edit {
        it[KEY_STREAK] = state.currentStreak
        it[KEY_BEST_STREAK] = state.bestStreak
        state.lastCompletedDay?.let { d -> it[KEY_LAST_DAY] = d } ?: it.remove(KEY_LAST_DAY)
        it[KEY_FREEZES] = state.freezesAvailable
        it[KEY_XP] = state.xp
        it[KEY_WAGER] = state.wagerActive
    }

    suspend fun setWager(active: Boolean) = context.dataStore.edit { it[KEY_WAGER] = active }

    private companion object {
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding")
        val KEY_USAGE_ACCESS = booleanPreferencesKey("usage_access")
        val KEY_STREAK = intPreferencesKey("streak")
        val KEY_BEST_STREAK = intPreferencesKey("best_streak")
        val KEY_LAST_DAY = stringPreferencesKey("last_day")
        val KEY_FREEZES = intPreferencesKey("freezes")
        val KEY_XP = intPreferencesKey("xp")
        val KEY_WAGER = booleanPreferencesKey("wager")
    }
}
