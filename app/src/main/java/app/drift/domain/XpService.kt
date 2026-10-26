package app.drift.domain

import app.drift.data.GamificationRepository
import app.drift.domain.model.BonusContext
import app.drift.domain.model.BonusResult
import app.drift.domain.model.XpAwardResult
import app.drift.domain.model.XpCategory
import app.drift.domain.model.XpTransaction
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Awards XP through the append-only, idempotent ledger. The base award and the
 * optional variable bonus are separate transactions so caps and cooldowns can be
 * enforced from the persisted ledger.
 */
class XpService(
    private val rules: XpRules,
    private val repo: GamificationRepository,
    private val variableRewards: VariableRewardService,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun award(
        category: XpCategory,
        idempotencyKey: String,
        description: String,
        sessionId: Long? = null,
        amountOverride: Int? = null,
    ): XpAwardResult {
        val amount = amountOverride ?: rules.baseFor(category)
        if (amount <= 0) return XpAwardResult.Duplicate
        val tx = XpTransaction(
            idempotencyKey = idempotencyKey,
            category = category,
            amount = amount,
            description = description,
            timestamp = clock(),
            rulesetVersion = rules.version,
            sessionId = sessionId,
        )
        return repo.award(tx)
    }

    /** Evaluates and (when won) records a bounded variable bonus. Returns the bonus amount, or 0. */
    suspend fun awardVariableBonus(
        idempotencyKey: String,
        context: BonusContext,
        sessionId: Long,
    ): Int {
        val daily = repo.sumBonusSince(XpCategory.VARIABLE_BONUS, startOfTodayMillis())
        val session = repo.sumBonusForSession(XpCategory.VARIABLE_BONUS, sessionId)
        val last = repo.lastBonusTimestamp()
        val result = variableRewards.evaluate(context, daily, session, last)
        if (result !is BonusResult.Awarded) return 0
        val tx = XpTransaction(
            idempotencyKey = idempotencyKey,
            category = XpCategory.VARIABLE_BONUS,
            amount = result.amount,
            description = "Variable bonus",
            timestamp = clock(),
            rulesetVersion = rules.version,
            sessionId = sessionId,
        )
        val award = repo.award(tx)
        return if (award is XpAwardResult.Awarded) award.amount else 0
    }

    private fun startOfTodayMillis(): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
