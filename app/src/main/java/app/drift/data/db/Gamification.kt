package app.drift.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import app.drift.domain.model.XpCategory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 1,
    val lifetimeXp: Int = 0,
    val spendableBalance: Int = 0,
    val spentXp: Int = 0,
)

@Entity(tableName = "xp_transactions", indices = [Index(value = ["idempotencyKey"], unique = true)])
data class XpTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,
    val category: XpCategory,
    val amount: Int,
    val description: String,
    val timestamp: Long,
    val rulesetVersion: Int,
    val sessionId: Long?,
)

@Entity(tableName = "achievement_unlocks")
data class AchievementUnlockEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long,
)

@Entity(tableName = "reward_redemptions", indices = [Index(value = ["rewardId"], unique = true)])
data class RewardRedemptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: String,
    val xpCost: Int,
    val redeemedAt: Long,
)

@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val date: String,
    val qualifyingMinutes: Int,
    val sessionsCompleted: Int,
    val xpEarned: Int,
    val tasksCompleted: Int,
    val recoveries: Int,
)

sealed interface RedemptionOutcome {
    data object Success : RedemptionOutcome
    data object InsufficientBalance : RedemptionOutcome
    data object AlreadyRedeemed : RedemptionOutcome
}

@Dao
interface GamificationDao {

    // ---- XP ledger (atomic award: insert + update progress) ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertXp(tx: XpTransactionEntity): Long

    @Query("INSERT OR IGNORE INTO user_progress (id) VALUES (1)")
    suspend fun ensureProgressRow()

    @Query("UPDATE user_progress SET lifetimeXp = lifetimeXp + :amount, spendableBalance = spendableBalance + :amount WHERE id = 1")
    suspend fun addXp(amount: Int)

    @Transaction
    suspend fun awardXp(tx: XpTransactionEntity): Long {
        ensureProgressRow()
        val id = insertXp(tx)
        if (id == -1L) return -1L
        addXp(tx.amount)
        return id
    }

    @Query("SELECT * FROM user_progress WHERE id = 1")
    fun observeProgress(): Flow<UserProgressEntity?>

    @Query("SELECT * FROM user_progress WHERE id = 1")
    suspend fun progress(): UserProgressEntity?

    @Query("SELECT * FROM xp_transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<XpTransactionEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE category = :cat AND timestamp >= :since")
    suspend fun sumSince(cat: XpCategory, since: Long): Int

    @Query("SELECT COALESCE(MAX(timestamp), 0) FROM xp_transactions WHERE category = 'VARIABLE_BONUS'")
    suspend fun lastBonusTimestamp(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE category = :cat AND sessionId = :sessionId")
    suspend fun sumForSession(cat: XpCategory, sessionId: Long): Int

    // ---- Achievements ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(a: AchievementUnlockEntity): Long

    @Query("SELECT * FROM achievement_unlocks")
    fun observeUnlocks(): Flow<List<AchievementUnlockEntity>>

    @Query("SELECT * FROM achievement_unlocks")
    suspend fun unlocks(): List<AchievementUnlockEntity>

    // ---- Redemption ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRedemption(r: RewardRedemptionEntity): Long

    @Query("UPDATE user_progress SET spendableBalance = spendableBalance - :cost, spentXp = spentXp + :cost WHERE id = 1 AND spendableBalance >= :cost")
    suspend fun trySpend(cost: Int): Int

    @Transaction
    suspend fun redeem(rewardId: String, cost: Int, now: Long): RedemptionOutcome {
        ensureProgressRow()
        val inserted = insertRedemption(RewardRedemptionEntity(rewardId = rewardId, xpCost = cost, redeemedAt = now))
        if (inserted == -1L) return RedemptionOutcome.AlreadyRedeemed
        val updated = trySpend(cost)
        return if (updated == 0) RedemptionOutcome.InsufficientBalance else RedemptionOutcome.Success
    }

    @Query("SELECT * FROM reward_redemptions ORDER BY redeemedAt DESC")
    fun observeRedemptions(): Flow<List<RewardRedemptionEntity>>

    // ---- Daily progress / heatmap ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyProgress(d: DailyProgressEntity)

    @Query("SELECT * FROM daily_progress WHERE date = :date")
    suspend fun dailyProgress(date: String): DailyProgressEntity?

    @Query("SELECT * FROM daily_progress ORDER BY date")
    fun observeDailyProgress(): Flow<List<DailyProgressEntity>>

    @Query("SELECT * FROM daily_progress ORDER BY date")
    suspend fun allDailyProgress(): List<DailyProgressEntity>
}
