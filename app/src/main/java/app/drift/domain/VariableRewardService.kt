package app.drift.domain

import app.drift.domain.model.BonusContext
import app.drift.domain.model.BonusEventType
import app.drift.domain.model.BonusResult
import kotlin.random.Random

/** Configuration for the bounded variable-reward subsystem. */
data class VariableRewardConfig(
    val enabled: Boolean = true,
    val probabilities: Map<BonusEventType, Double> = mapOf(
        BonusEventType.MILESTONE to 0.10,
        BonusEventType.DIFFICULT_TASK to 0.20,
        BonusEventType.ATTENTION_RECOVERY to 0.25,
        BonusEventType.PERSONAL_BEST to 0.15,
    ),
    val minBonus: Int = 5,
    val maxBonus: Int = 15,
    val dailyCap: Int = 50,
    val sessionCap: Int = 20,
    val cooldownMs: Long = 5 * 60_000L,
)

/**
 * Bounded variable bonuses. The random source is injected so tests are
 * deterministic. Caps, cooldowns and eligibility are all enforced here.
 *
 * `dailyBonusSoFar`, `sessionBonusSoFar` and `lastBonusAt` are supplied by the
 * caller (computed from the persisted ledger) so this class stays pure.
 */
class VariableRewardService(
    private val config: VariableRewardConfig,
    private val roll: () -> Double = { Random.nextDouble() },
    private val rollInt: (Int) -> Int = { bound -> Random.nextInt(bound) },
) {
    fun evaluate(
        context: BonusContext,
        dailyBonusSoFar: Int,
        sessionBonusSoFar: Int,
        lastBonusAt: Long,
    ): BonusResult {
        if (!config.enabled) return BonusResult.Ineligible
        val probability = config.probabilities[context.eventType] ?: return BonusResult.Ineligible
        if (dailyBonusSoFar >= config.dailyCap) return BonusResult.CapReached
        if (sessionBonusSoFar >= config.sessionCap) return BonusResult.CapReached
        if (context.now - lastBonusAt < config.cooldownMs) return BonusResult.CooldownActive

        return if (roll() < probability) {
            BonusResult.Awarded(config.minBonus + rollInt(config.maxBonus - config.minBonus + 1))
        } else {
            BonusResult.NotAwarded
        }
    }
}
