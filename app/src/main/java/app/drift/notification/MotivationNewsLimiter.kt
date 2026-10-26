package app.drift.notification

import android.content.Context

/** Persists a small per-session budget so focus nudges cannot become a feed of their own. */
class MotivationNewsLimiter(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "motivation_news_nudges",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun canShow(sessionId: Long, nowMs: Long): Boolean {
        val previousSession = preferences.getLong(KEY_SESSION_ID, NO_SESSION)
        val count = if (previousSession == sessionId) preferences.getInt(KEY_COUNT, 0) else 0
        val lastShownAt = if (previousSession == sessionId) preferences.getLong(KEY_LAST_SHOWN_AT, 0L) else 0L
        return count < MAX_PER_SESSION && (lastShownAt == 0L || nowMs - lastShownAt >= COOLDOWN_MS)
    }

    /** Atomically records a nudge if the cooldown and session budget still allow it. */
    @Synchronized
    fun markShown(sessionId: Long, nowMs: Long): Int? {
        if (!canShow(sessionId, nowMs)) return null
        val previousSession = preferences.getLong(KEY_SESSION_ID, NO_SESSION)
        val count = if (previousSession == sessionId) preferences.getInt(KEY_COUNT, 0) + 1 else 1
        val saved = preferences.edit()
            .putLong(KEY_SESSION_ID, sessionId)
            .putLong(KEY_LAST_SHOWN_AT, nowMs)
            .putInt(KEY_COUNT, count)
            .commit()
        return count.takeIf { saved }
    }

    private companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_LAST_SHOWN_AT = "last_shown_at"
        const val KEY_COUNT = "count"
        const val NO_SESSION = Long.MIN_VALUE
        const val MAX_PER_SESSION = 2
        const val COOLDOWN_MS = 15 * 60 * 1000L
    }
}
