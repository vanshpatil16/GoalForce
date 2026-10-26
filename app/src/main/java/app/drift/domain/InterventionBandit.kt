package app.drift.domain

import app.drift.domain.model.DriftKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import kotlin.math.exp
import kotlin.random.Random

/**
 * A multi-armed bandit over intervention *templates*, inspired by Duolingo's
 * KDD 2020 notification-content bandit (Yancey et al.).
 *
 * Arms   = [InterventionTemplate] values.
 * Reward = 1 if the intervention was followed by the user achieving their
 *          stated outcome within a short window, else 0. Crucially, the reward
 *          is an *intent-relative* outcome, not raw engagement.
 *
 * We address the paper's three practical problems:
 *  1. Novelty effect  — a freshly-seen template gets a temporary bonus so the
 *                       bandit does not over-exploit something merely because
 *                       it is new; the bonus decays with exposures.
 *  2. Recovering arms — an arm's reward "recovers" over time; recently-shown
 *                       arms are temporarily down-weighted (rested-bandit).
 *  3. Sleeping arms   — some templates are ineligible in some contexts (e.g.
 *                       REWARD_NUDGE only when the user has an active streak,
 *                       BREATH_PAUSE not immediately after another breath).
 *
 * This class is pure and unit-testable: it consumes history and returns a
 * choice; persistence happens in the repository layer.
 */
class InterventionBandit(private val random: Random = Random.Default) {

    /** Softmax selection over the eligible templates, using estimated reward. */
    fun select(
        history: List<Intervention>,
        driftKind: DriftKind,
        nowMs: Long,
        hasStreak: Boolean,
    ): InterventionTemplate {
        val eligible = eligibleTemplates(driftKind, history, nowMs, hasStreak)
        if (eligible.isEmpty()) return InterventionTemplate.STOPPING_POINT

        val scores = eligible.map { estimatedReward(it, history, nowMs) }
        return softmax(eligible, scores)
    }

    /** Templates that make sense for a given drift signal. */
    fun eligibleTemplates(
        driftKind: DriftKind,
        history: List<Intervention>,
        nowMs: Long,
        hasStreak: Boolean,
    ): List<InterventionTemplate> = when (driftKind) {
        DriftKind.OPENED -> listOf(
            InterventionTemplate.BREATH_PAUSE,
            InterventionTemplate.REFLECTION,
        )

        DriftKind.OVER_RUN -> listOf(
            InterventionTemplate.STOPPING_POINT,
            InterventionTemplate.EXIT_PROMPT,
            InterventionTemplate.REFLECTION,
            InterventionTemplate.REWARD_NUDGE,
        )

        DriftKind.COMPULSIVE_REENTRY -> listOf(
            InterventionTemplate.BREATH_PAUSE,
            InterventionTemplate.REFLECTION,
            InterventionTemplate.EXIT_PROMPT,
        )

        DriftKind.NATURAL_STOPPING -> listOf(
            InterventionTemplate.STOPPING_POINT,
            InterventionTemplate.EXIT_PROMPT,
        )

        DriftKind.ON_TRACK -> emptyList()
    }.filter { template ->
        when (template) {
            // Sleeping arm: only offer the reward nudge when a streak exists.
            InterventionTemplate.REWARD_NUDGE -> hasStreak
            // Recovering arm: don't repeat a breath pause within a short window.
            InterventionTemplate.BREATH_PAUSE ->
                history.none { it.template == template && nowMs - it.at < BREATH_COOLDOWN_MS }
            else -> true
        }
    }

    /**
     * Estimated reward for an arm, combining:
     *  - its historical success rate (smoothed), and
     *  - a novelty bonus that decays with exposures (anti-novelty-overexploit),
     *  - a recency penalty if it was just shown (recovering bandit).
     */
    private fun estimatedReward(
        template: InterventionTemplate,
        history: List<Intervention>,
        nowMs: Long,
    ): Double {
        val trials = history.filter { it.template == template }
        val successes = trials.count { it.reward == true }
        val n = trials.size

        // Smoothed success rate (Beta(1,1) prior).
        val successRate = (successes + 1.0) / (n + 2.0)

        // Novelty bonus that decays with exposures.
        val novelty = NOVELTY_INITIAL * exp(-n.toDouble() / NOVELTY_DECAY)

        // Recency penalty if shown in the last window (recovering arm).
        val lastShown = trials.maxOfOrNull { it.at } ?: 0L
        val recencyPenalty = if (nowMs - lastShown < RECOVERY_WINDOW_MS) RECOVERY_PENALTY else 0.0

        return (successRate + novelty - recencyPenalty).coerceAtLeast(0.0)
    }

    private fun softmax(
        templates: List<InterventionTemplate>,
        scores: List<Double>,
    ): InterventionTemplate {
        val temperature = 0.5
        val scaled = scores.map { it / temperature }
        val max = scaled.maxOrNull() ?: 0.0
        val exps = scaled.map { exp(it - max) }
        val sum = exps.sum()
        val probs = exps.map { it / sum }

        var r = random.nextDouble()
        for (i in templates.indices) {
            r -= probs[i]
            if (r <= 0.0) return templates[i]
        }
        return templates.last()
    }

    companion object {
        const val NOVELTY_INITIAL = 0.35
        const val NOVELTY_DECAY = 4.0
        const val RECOVERY_WINDOW_MS = 10 * 60_000L
        const val RECOVERY_PENALTY = 0.3
        const val BREATH_COOLDOWN_MS = 5 * 60_000L
    }
}
