package app.drift.domain.model

/** A notification that was posted, and whether the user clicked it. */
data class NotificationEvent(
    val id: Long = 0,
    val channel: String,
    val postedAt: Long,
    val clickedAt: Long? = null,
)

/** One day's worth of aggregated behaviour. */
data class DailyInsight(
    val date: String,
    val sessions: Int,
    val overruns: Int,
    val interventions: Int,
    val achieved: Int,
)

/** The output of the behavioural-analysis engine. */
data class BehavioralInsights(
    val totalSessions: Int,
    val overrunRate: Double,
    /** Slope of overrun-rate vs day. Negative = improving. */
    val overrunTrend: Double,
    val topOverrunApp: String?,
    val bestHour: Int,
    val notificationsPosted: Int,
    val notificationClickRate: Double,
    val mostEffectiveTemplate: InterventionTemplate?,
    val level: Int,
    val levelTitle: String,
    val streakDays: Int,
)
