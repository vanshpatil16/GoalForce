package app.drift.domain

import app.drift.domain.model.BonusContext
import app.drift.domain.model.BonusEventType
import app.drift.domain.model.BonusResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VariableRewardServiceTest {
    private val config = VariableRewardConfig()

    private fun svc(roll: () -> Double, rollInt: (Int) -> Int = { 0 }) =
        VariableRewardService(config, roll = roll, rollInt = rollInt)

    @Test
    fun `bonus awarded when roll below probability`() {
        val r = svc(roll = { 0.0 }).evaluate(BonusContext(BonusEventType.MILESTONE, 1_000_000_000), 0, 0, 0)
        assertTrue(r is BonusResult.Awarded)
        assertEquals(config.minBonus, (r as BonusResult.Awarded).amount)
    }

    @Test
    fun `bonus not awarded when roll above probability`() {
        val r = svc(roll = { 0.99 }).evaluate(BonusContext(BonusEventType.MILESTONE, 1_000_000_000), 0, 0, 0)
        assertTrue(r is BonusResult.NotAwarded)
    }

    @Test
    fun `daily cap enforced`() {
        val r = svc(roll = { 0.0 }).evaluate(BonusContext(BonusEventType.MILESTONE, 1000), config.dailyCap, 0, 0)
        assertEquals(BonusResult.CapReached, r)
    }

    @Test
    fun `session cap enforced`() {
        val r = svc(roll = { 0.0 }).evaluate(BonusContext(BonusEventType.MILESTONE, 1000), 0, config.sessionCap, 0)
        assertEquals(BonusResult.CapReached, r)
    }

    @Test
    fun `cooldown enforced`() {
        val last = 1000 - (config.cooldownMs - 1)
        val r = svc(roll = { 0.0 }).evaluate(BonusContext(BonusEventType.MILESTONE, 1000), 0, 0, last)
        assertEquals(BonusResult.CooldownActive, r)
    }

    @Test
    fun `disabled rewards are ineligible`() {
        val disabled = VariableRewardService(config.copy(enabled = false), roll = { 0.0 }, rollInt = { 0 })
        assertEquals(BonusResult.Ineligible, disabled.evaluate(BonusContext(BonusEventType.MILESTONE, 1000), 0, 0, 0))
    }
}

class NotificationCostServiceTest {
    @Test
    fun `no history gives insufficient data`() {
        assertEquals(CostEstimate.InsufficientData, NotificationCostService().estimate(emptyList()))
    }

    @Test
    fun `estimate uses only valid observations`() {
        val obs = listOf(
            InterruptionObservation(true, 10.0),
            InterruptionObservation(true, 20.0),
            InterruptionObservation(false, 0.0),
            InterruptionObservation(false, 0.0),
        )
        val est = NotificationCostService().estimate(obs)
        assertTrue(est is CostEstimate.Available)
        val a = est as CostEstimate.Available
        assertEquals(0.5, a.driftProbability, 1e-9)
        assertEquals(7.5, a.expectedCostMinutes, 1e-9) // 15 avg * 0.5
    }
}

class AchievementServiceTest {
    private val svc = AchievementService()

    @Test
    fun `first session unlocks first session achievement`() {
        assertTrue(svc.evaluate(ProgressSnapshot(sessionsCompleted = 1)).any { it.id == "first_session" })
    }

    @Test
    fun `empty progress unlocks nothing`() {
        assertFalse(svc.evaluate(ProgressSnapshot()).any { it.id == "first_session" })
    }

    @Test
    fun `streak achievements unlock at thresholds`() {
        val ids = svc.evaluate(ProgressSnapshot(currentStreak = 7)).map { it.id }
        assertTrue("streak_3" in ids)
        assertTrue("streak_7" in ids)
    }
}

class StreakServiceTest {
    private val svc = StreakService(weeklyTarget = 5)

    @Test
    fun `daily streak counts consecutive qualifying days`() {
        val today = LocalDate.of(2026, 6, 10)
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, svc.currentDailyStreak(dates, today))
    }

    @Test
    fun `daily streak resets after a missed day`() {
        val today = LocalDate.of(2026, 6, 10)
        val dates = listOf(today, today.minusDays(2))
        assertEquals(1, svc.currentDailyStreak(dates, today))
    }

    @Test
    fun `best daily streak spans the whole history`() {
        val dates = listOf(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
        )
        assertEquals(3, svc.bestDailyStreak(dates))
    }

    @Test
    fun `qualifying days this week and weekly streak`() {
        val today = LocalDate.of(2026, 6, 10) // Wednesday; week = Mon Jun 8 .. Sun Jun 14
        val week = listOf(
            LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12),
        )
        val view = svc.compute(week, today)
        assertEquals(5, view.qualifyingDaysThisWeek)
        assertEquals(1, view.currentWeekStreak)
    }
}

class XpRulesTest {
    @Test
    fun `base values are configurable and category-specific`() {
        val rules = XpRules()
        assertEquals(20, rules.baseFor(app.drift.domain.model.XpCategory.SESSION_COMPLETION))
        assertEquals(30, rules.baseFor(app.drift.domain.model.XpCategory.TASK_COMPLETION))
        assertEquals(100, rules.baseFor(app.drift.domain.model.XpCategory.WEEKLY_MILESTONE))
    }
}
