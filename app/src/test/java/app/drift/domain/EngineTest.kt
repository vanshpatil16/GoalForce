package app.drift.domain

import app.drift.domain.model.DriftKind
import app.drift.domain.model.IntentKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import app.drift.domain.model.UsageSnapshot
import app.drift.domain.model.UserIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IntentEngineTest {
    private val engine = IntentEngine()

    private fun intent(minutes: Int, kind: IntentKind = IntentKind.QUICK_CHECK) = UserIntent(
        targetPackage = "com.example.app",
        targetLabel = "App",
        purpose = "purpose",
        intendedMinutes = minutes,
        kind = kind,
        createdAt = 0,
    )

    @Test
    fun `overrun is flagged past intended duration`() {
        val snap = UsageSnapshot("com.example.app", elapsedMinutes = 6, reentries = 0, secondsSinceLastExit = Long.MAX_VALUE, singleAppFocus = true)
        assertEquals(DriftKind.OVER_RUN, engine.evaluate(intent(5), snap))
    }

    @Test
    fun `compulsive reentry is flagged regardless of time`() {
        val snap = UsageSnapshot("com.example.app", elapsedMinutes = 1, reentries = 3, secondsSinceLastExit = 30_000, singleAppFocus = false)
        assertEquals(DriftKind.COMPULSIVE_REENTRY, engine.evaluate(intent(60), snap))
    }

    @Test
    fun `long-form single-app within time is on track`() {
        val snap = UsageSnapshot("com.example.app", elapsedMinutes = 40, reentries = 0, secondsSinceLastExit = Long.MAX_VALUE, singleAppFocus = true)
        assertEquals(DriftKind.ON_TRACK, engine.evaluate(intent(45, IntentKind.LONG_FORM), snap))
    }

    @Test
    fun `time alone is not harmful when within the declared intent`() {
        val snap = UsageSnapshot("com.example.app", elapsedMinutes = 90, reentries = 0, secondsSinceLastExit = Long.MAX_VALUE, singleAppFocus = false)
        assertEquals(DriftKind.ON_TRACK, engine.evaluate(intent(120, IntentKind.ENTERTAINMENT), snap))
    }

    @Test
    fun `extension pushes the overrun boundary`() {
        val snap = UsageSnapshot("com.example.app", elapsedMinutes = 12, reentries = 0, secondsSinceLastExit = Long.MAX_VALUE, singleAppFocus = true)
        assertEquals(DriftKind.OVER_RUN, engine.evaluate(intent(10), snap))
        assertEquals(DriftKind.ON_TRACK, engine.evaluate(intent(10), snap, extendedMinutes = 5))
    }
}

class InterventionBanditTest {
    private val bandit = InterventionBandit()

    @Test
    fun `reward nudge sleeps without a streak`() {
        val eligible = bandit.eligibleTemplates(DriftKind.OVER_RUN, emptyList(), nowMs = 0, hasStreak = false)
        assertFalse(eligible.contains(InterventionTemplate.REWARD_NUDGE))
    }

    @Test
    fun `reward nudge wakes with a streak`() {
        val eligible = bandit.eligibleTemplates(DriftKind.OVER_RUN, emptyList(), nowMs = 0, hasStreak = true)
        assertTrue(eligible.contains(InterventionTemplate.REWARD_NUDGE))
    }

    @Test
    fun `breath pause recovers after cooldown`() {
        val history = listOf(
            Intervention(sessionId = 1, template = InterventionTemplate.BREATH_PAUSE, driftKind = DriftKind.COMPULSIVE_REENTRY, at = 0, reward = null)
        )
        val nowInside = InterventionBandit.BREATH_COOLDOWN_MS - 1
        val nowOutside = InterventionBandit.BREATH_COOLDOWN_MS + 1

        assertFalse(bandit.eligibleTemplates(DriftKind.COMPULSIVE_REENTRY, history, nowInside, true).contains(InterventionTemplate.BREATH_PAUSE))
        assertTrue(bandit.eligibleTemplates(DriftKind.COMPULSIVE_REENTRY, history, nowOutside, true).contains(InterventionTemplate.BREATH_PAUSE))
    }

    @Test
    fun `selection always returns an eligible template`() {
        repeat(20) {
            val t = bandit.select(emptyList(), DriftKind.OVER_RUN, nowMs = 0, hasStreak = false)
            assertTrue(bandit.eligibleTemplates(DriftKind.OVER_RUN, emptyList(), 0, false).contains(t))
        }
    }
}

class OutcomeEvaluatorTest {
    private val evaluator = OutcomeEvaluator()

    @Test
    fun `intervention that ends on time is a reward`() {
        val intent = UserIntent(targetPackage = "p", targetLabel = "l", purpose = "purpose", intendedMinutes = 10, kind = IntentKind.QUICK_CHECK, createdAt = 0)
        val session = Session(1, 1, "p", startedAt = 0, endedAt = 5 * 60_000L, outcome = Outcome.ON_TIME)
        val intervention = Intervention(1, 1, InterventionTemplate.EXIT_PROMPT, DriftKind.OVER_RUN, at = 0, reward = null)
        assertTrue(evaluator.rewardOf(intervention, session, intent))
    }

    @Test
    fun `intervention followed by overrun is not a reward`() {
        val intent = UserIntent(targetPackage = "p", targetLabel = "l", purpose = "purpose", intendedMinutes = 10, kind = IntentKind.QUICK_CHECK, createdAt = 0)
        val session = Session(1, 1, "p", startedAt = 0, endedAt = 30 * 60_000L, outcome = Outcome.OVERRAN)
        val intervention = Intervention(1, 1, InterventionTemplate.EXIT_PROMPT, DriftKind.OVER_RUN, at = 0, reward = null)
        assertFalse(evaluator.rewardOf(intervention, session, intent))
    }
}

class StreakManagerTest {
    private val streak = StreakManager()

    @Test
    fun `streak increments on consecutive successful days`() {
        var s = StreakState()
        s = streak.recordDay(s, LocalDate.of(2026, 1, 1), achieved = true)
        assertEquals(1, s.currentStreak)
        s = streak.recordDay(s, LocalDate.of(2026, 1, 2), achieved = true)
        assertEquals(2, s.currentStreak)
    }

    @Test
    fun `freeze prevents a reset on a missed day`() {
        var s = StreakState(currentStreak = 3, lastCompletedDay = "2026-01-01", freezesAvailable = 1)
        s = streak.recordDay(s, LocalDate.of(2026, 1, 3), achieved = false)
        assertEquals(3, s.currentStreak)
        assertEquals(0, s.freezesAvailable)
    }

    @Test
    fun `wager pays bonus days on a win and resets on a loss`() {
        var s = StreakState(currentStreak = 3, lastCompletedDay = "2026-01-01", wagerActive = true)
        val win = streak.recordDay(s, LocalDate.of(2026, 1, 2), achieved = true)
        assertEquals(6, win.currentStreak) // 3 + 1 + 2 wager bonus

        val loss = streak.recordDay(StreakState(currentStreak = 3, lastCompletedDay = "2026-01-01", wagerActive = true, freezesAvailable = 0), LocalDate.of(2026, 1, 3), achieved = false)
        assertEquals(0, loss.currentStreak)
    }
}
