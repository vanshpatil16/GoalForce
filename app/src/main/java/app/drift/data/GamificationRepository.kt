package app.drift.data

import app.drift.data.db.AchievementUnlockEntity
import app.drift.data.db.DailyProgressEntity
import app.drift.data.db.GamificationDao
import app.drift.data.db.RedemptionOutcome
import app.drift.data.db.RewardRedemptionEntity
import app.drift.data.db.UserProgressEntity
import app.drift.data.db.XpTransactionEntity
import app.drift.domain.model.AchievementUnlock
import app.drift.domain.model.DailyProgress
import app.drift.domain.model.RewardRedemption
import app.drift.domain.model.UserProgress
import app.drift.domain.model.XpAwardResult
import app.drift.domain.model.XpCategory
import app.drift.domain.model.XpTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GamificationRepository(private val dao: GamificationDao) {

    val progress: Flow<UserProgress?> = dao.observeProgress().map { it?.toModel() }
    val recentTransactions: Flow<List<XpTransaction>> = dao.observeRecent(50).map { l -> l.map { it.toModel() } }
    val unlocks: Flow<List<AchievementUnlock>> = dao.observeUnlocks().map { l -> l.map { it.toModel() } }
    val redemptions: Flow<List<RewardRedemption>> = dao.observeRedemptions().map { l -> l.map { it.toModel() } }
    val dailyProgress: Flow<List<DailyProgress>> = dao.observeDailyProgress().map { l -> l.map { it.toModel() } }

    suspend fun progressSnapshot(): UserProgress? = dao.progress()?.toModel()

    suspend fun award(tx: XpTransaction): XpAwardResult {
        val id = dao.awardXp(tx.toEntity())
        return if (id == -1L) XpAwardResult.Duplicate else XpAwardResult.Awarded(tx.amount, 0, id)
    }

    suspend fun sumBonusSince(category: XpCategory, since: Long): Int = dao.sumSince(category, since)
    suspend fun sumBonusForSession(category: XpCategory, sessionId: Long): Int = dao.sumForSession(category, sessionId)
    suspend fun lastBonusTimestamp(): Long = dao.lastBonusTimestamp()

    suspend fun unlock(achievementId: String, now: Long): Boolean =
        dao.unlock(AchievementUnlockEntity(achievementId, now)) != -1L

    suspend fun allUnlocks(): List<AchievementUnlock> = dao.unlocks().map { it.toModel() }

    suspend fun redeem(rewardId: String, cost: Int, now: Long): RedemptionOutcome =
        dao.redeem(rewardId, cost, now)

    suspend fun recordDailyProgress(d: DailyProgress) {
        val existing = dao.dailyProgress(d.date)
        val merged = if (existing == null) d else DailyProgress(
            date = d.date,
            qualifyingMinutes = existing.qualifyingMinutes + d.qualifyingMinutes,
            sessionsCompleted = existing.sessionsCompleted + d.sessionsCompleted,
            xpEarned = existing.xpEarned + d.xpEarned,
            tasksCompleted = existing.tasksCompleted + d.tasksCompleted,
            recoveries = existing.recoveries + d.recoveries,
        )
        dao.upsertDailyProgress(merged.toEntity())
    }

    suspend fun allDailyProgress(): List<DailyProgress> = dao.allDailyProgress().map { it.toModel() }

    // ---- mapping ----
    private fun UserProgressEntity.toModel() = UserProgress(lifetimeXp, spendableBalance, spentXp)
    private fun XpTransactionEntity.toModel() =
        XpTransaction(id, idempotencyKey, category, amount, description, timestamp, rulesetVersion, sessionId)
    private fun XpTransaction.toEntity() =
        XpTransactionEntity(id, idempotencyKey, category, amount, description, timestamp, rulesetVersion, sessionId)
    private fun AchievementUnlockEntity.toModel() = AchievementUnlock(achievementId, unlockedAt)
    private fun RewardRedemptionEntity.toModel() = RewardRedemption(id, rewardId, xpCost, redeemedAt)
    private fun DailyProgressEntity.toModel() =
        DailyProgress(date, qualifyingMinutes, sessionsCompleted, xpEarned, tasksCompleted, recoveries)
    private fun DailyProgress.toEntity() =
        DailyProgressEntity(date, qualifyingMinutes, sessionsCompleted, xpEarned, tasksCompleted, recoveries)
}
