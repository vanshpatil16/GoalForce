package app.drift.domain

import app.drift.data.GamificationRepository
import app.drift.data.db.RedemptionOutcome
import app.drift.domain.model.RewardItem

/**
 * Validates and executes reward redemptions. The actual balance update is
 * atomic in the DAO (guard against negative balance + duplicate redemption).
 */
class RedemptionService(private val repo: GamificationRepository) {

    suspend fun redeem(item: RewardItem, now: Long = System.currentTimeMillis()): RedemptionOutcome =
        repo.redeem(item.id, item.xpCost, now)
}
