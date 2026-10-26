package app.drift.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.drift.domain.model.DriftKind
import app.drift.domain.model.EventConfidence
import app.drift.domain.model.ForegroundEvent
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.IntentKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import app.drift.domain.model.SessionState
import app.drift.domain.model.SessionSummary
import app.drift.domain.model.UserIntent
import app.drift.domain.model.XpCategory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_intents")
data class UserIntentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetPackage: String,
    val targetLabel: String,
    val purpose: String,
    val intendedMinutes: Int,
    val kind: IntentKind,
    val createdAt: Long,
) {
    fun toModel() = UserIntent(id, targetPackage, targetLabel, purpose, intendedMinutes, kind, createdAt)
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intentId: Long,
    val targetPackage: String,
    val startedAt: Long,
    val endedAt: Long?,
    val lastExitAt: Long?,
    val reentries: Int,
    val appSwitches: Int,
    val extendedMinutes: Int,
    val state: SessionState,
    val outcome: Outcome?,
) {
    fun toModel() = Session(id, intentId, targetPackage, startedAt, endedAt, lastExitAt, reentries, appSwitches, extendedMinutes, state, outcome)
}

@Entity(tableName = "interventions")
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val template: InterventionTemplate,
    val driftKind: DriftKind,
    val at: Long,
    val reward: Boolean?,
) {
    fun toModel() = Intervention(id, sessionId, template, driftKind, at, reward)
}

@Entity(tableName = "notification_events")
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: String,
    val postedAt: Long,
    val clickedAt: Long?,
) {
    fun toModel() = app.drift.domain.model.NotificationEvent(id, channel, postedAt, clickedAt)
}

@Entity(tableName = "foreground_events")
data class ForegroundEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val packageName: String,
    val label: String,
    val kind: ForegroundEventKind,
    val at: Long,
    val elapsedRealtimeMs: Long?,
    val confidence: EventConfidence,
) {
    fun toModel() = ForegroundEvent(id, sessionId, packageName, label, kind, at, elapsedRealtimeMs, confidence)
}

@Entity(tableName = "session_summaries")
data class SessionSummaryEntity(
    @PrimaryKey val sessionId: Long,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    val completionStatus: String,
    val restrictedAppForegroundSeconds: Int,
    val distractionCount: Int,
    val restrictionAttemptCount: Int,
    val appOpenCount: Int,
    val focusScore: Int,
    val trackingCoverage: Float,
    val xpEarned: Int,
    val createdAt: Long,
) {
    fun toModel() = SessionSummary(
        sessionId, plannedDurationSeconds, actualDurationSeconds, completionStatus,
        restrictedAppForegroundSeconds, distractionCount, restrictionAttemptCount,
        appOpenCount, focusScore, trackingCoverage, xpEarned, createdAt,
    )
}

class Converters {
    @TypeConverter fun intentKindToString(k: IntentKind) = k.name
    @TypeConverter fun stringToIntentKind(s: String) = IntentKind.valueOf(s)
    @TypeConverter fun stateToString(s: SessionState) = s.name
    @TypeConverter fun stringToState(s: String) = SessionState.valueOf(s)
    @TypeConverter fun outcomeToString(o: Outcome?) = o?.name
    @TypeConverter fun stringToOutcome(s: String?) = s?.let { Outcome.valueOf(it) }
    @TypeConverter fun driftKindToString(k: DriftKind) = k.name
    @TypeConverter fun stringToDriftKind(s: String) = DriftKind.valueOf(s)
    @TypeConverter fun templateToString(t: InterventionTemplate) = t.name
    @TypeConverter fun stringToTemplate(s: String) = InterventionTemplate.valueOf(s)
    @TypeConverter fun xpCategoryToString(c: XpCategory) = c.name
    @TypeConverter fun stringToXpCategory(s: String) = XpCategory.valueOf(s)
    @TypeConverter fun foregroundKindToString(k: ForegroundEventKind) = k.name
    @TypeConverter fun stringToForegroundKind(s: String) = ForegroundEventKind.valueOf(s)
    @TypeConverter fun confidenceToString(c: EventConfidence) = c.name
    @TypeConverter fun stringToConfidence(s: String) = EventConfidence.valueOf(s)
}

@Dao
interface IntentDao {
    @Insert suspend fun insert(e: UserIntentEntity): Long
    @Query("SELECT * FROM user_intents ORDER BY createdAt DESC") fun observeAll(): Flow<List<UserIntentEntity>>
    @Query("SELECT * FROM user_intents WHERE id = :id") suspend fun byId(id: Long): UserIntentEntity?
    @Query("DELETE FROM user_intents WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(e: SessionEntity): Long
    @Query("SELECT * FROM sessions WHERE state = 'ACTIVE' LIMIT 1") suspend fun active(): SessionEntity?
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun observeAll(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun byId(id: Long): SessionEntity?
    @Query("UPDATE sessions SET reentries = :reentries, appSwitches = :appSwitches, lastExitAt = :lastExitAt WHERE id = :id")
    suspend fun updateSignals(id: Long, reentries: Int, appSwitches: Int, lastExitAt: Long?)
    @Query("UPDATE sessions SET extendedMinutes = :minutes WHERE id = :id")
    suspend fun extend(id: Long, minutes: Int)
    @Query("UPDATE sessions SET endedAt = :endedAt, state = :state, outcome = :outcome WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, state: SessionState, outcome: Outcome?)
    @Query("UPDATE sessions SET state = 'ABANDONED', endedAt = :endedAt WHERE state = 'ACTIVE'")
    suspend fun abandonActive(endedAt: Long)
}

@Dao
interface InterventionDao {
    @Insert suspend fun insert(e: InterventionEntity): Long
    @Query("SELECT * FROM interventions ORDER BY at DESC") fun observeAll(): Flow<List<InterventionEntity>>
    @Query("UPDATE interventions SET reward = :reward WHERE id = :id") suspend fun setReward(id: Long, reward: Boolean)
    @Query("SELECT * FROM interventions WHERE sessionId = :sessionId") suspend fun forSession(sessionId: Long): List<InterventionEntity>
}

@Dao
interface NotificationEventDao {
    @Insert suspend fun insert(e: NotificationEventEntity): Long
    @Query("SELECT * FROM notification_events ORDER BY postedAt DESC") fun observeAll(): Flow<List<NotificationEventEntity>>
    @Query("SELECT * FROM notification_events ORDER BY postedAt DESC") suspend fun all(): List<NotificationEventEntity>
    @Query("SELECT * FROM notification_events WHERE channel = :channel AND clickedAt IS NULL ORDER BY postedAt DESC LIMIT 1")
    suspend fun latestUnclicked(channel: String): NotificationEventEntity?
    @Query("UPDATE notification_events SET clickedAt = :clickedAt WHERE id = :id") suspend fun markClicked(id: Long, clickedAt: Long)
}

@Dao
interface ForegroundEventDao {
    @Insert suspend fun insert(e: ForegroundEventEntity): Long
    @Query("SELECT * FROM foreground_events ORDER BY at DESC") fun observeAll(): Flow<List<ForegroundEventEntity>>
    @Query("SELECT * FROM foreground_events WHERE at >= :since ORDER BY at ASC") suspend fun since(since: Long): List<ForegroundEventEntity>
    @Query("SELECT * FROM foreground_events WHERE sessionId = :sessionId ORDER BY at ASC, id ASC") suspend fun forSession(sessionId: Long): List<ForegroundEventEntity>
    @Query("DELETE FROM foreground_events WHERE at < :before") suspend fun prune(before: Long)
}

@Dao
interface SessionSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SessionSummaryEntity)

    @Query("SELECT * FROM session_summaries WHERE sessionId = :sessionId")
    suspend fun byId(sessionId: Long): SessionSummaryEntity?

    @Query("SELECT * FROM session_summaries ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionSummaryEntity>>

    @Query("SELECT * FROM session_summaries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SessionSummaryEntity>
}

@Database(
    entities = [
        UserIntentEntity::class, SessionEntity::class, InterventionEntity::class, NotificationEventEntity::class,
        UserProgressEntity::class, XpTransactionEntity::class, AchievementUnlockEntity::class,
        RewardRedemptionEntity::class, DailyProgressEntity::class, ForegroundEventEntity::class,
        SessionSummaryEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DriftDatabase : RoomDatabase() {
    abstract fun intentDao(): IntentDao
    abstract fun sessionDao(): SessionDao
    abstract fun interventionDao(): InterventionDao
    abstract fun notificationEventDao(): NotificationEventDao
    abstract fun foregroundEventDao(): ForegroundEventDao
    abstract fun sessionSummaryDao(): SessionSummaryDao
    abstract fun gamificationDao(): GamificationDao

    companion object {
        fun build(context: Context): DriftDatabase =
            Room.databaseBuilder(context, DriftDatabase::class.java, "drift.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
