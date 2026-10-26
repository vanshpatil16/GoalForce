package app.drift.domain

import app.drift.domain.model.DriftKind
import app.drift.domain.model.Intervention
import app.drift.domain.model.InterventionTemplate
import app.drift.domain.model.NotificationEvent
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSystemTest {
    @Test
    fun `level grows sub-linearly with xp`() {
        assertEquals(1, LevelSystem.levelFor(0))
        assertEquals(2, LevelSystem.levelFor(100))
        assertTrue(LevelSystem.levelFor(1000) > LevelSystem.levelFor(100))
    }

    @Test
    fun `progress is bounded between 0 and 1`() {
        assertTrue(LevelSystem.progressInLevel(0) in 0f..1f)
        assertTrue(LevelSystem.progressInLevel(150) in 0f..1f)
        assertTrue(LevelSystem.progressInLevel(99999) in 0f..1f)
    }

    @Test
    fun `titles become more motivating with level`() {
        assertTrue(LevelSystem.title(10) != LevelSystem.title(1))
    }
}

class NotificationTimingBanditTest {
    private val bandit = NotificationTimingBandit()

    @Test
    fun `click rate is the proportion clicked`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            NotificationEvent(channel = "reminder", postedAt = now, clickedAt = now),
            NotificationEvent(channel = "reminder", postedAt = now, clickedAt = now),
            NotificationEvent(channel = "reminder", postedAt = now, clickedAt = null),
            NotificationEvent(channel = "reminder", postedAt = now, clickedAt = null),
            NotificationEvent(channel = "reminder", postedAt = now, clickedAt = null),
        )
        assertEquals(0.4, bandit.clickRate(events), 1e-9)
    }

    @Test
    fun `empty history gives zero click rate and default hours`() {
        assertEquals(0.0, bandit.clickRate(emptyList()), 1e-9)
        assertEquals(listOf(9, 18, 20), bandit.bestHours(emptyList(), 3))
    }

    @Test
    fun `selection always returns a valid hour`() {
        val hour = bandit.selectHour(emptyList(), epsilon = 0.0)
        assertTrue(hour in 0..23)
    }
}

class BehavioralAnalyzerTest {
    private val analyzer = BehavioralAnalyzer()

    private fun session(pkg: String, outcome: Outcome, startOffsetMs: Long = 0) = Session(
        intentId = 1,
        targetPackage = pkg,
        startedAt = System.currentTimeMillis() + startOffsetMs,
        outcome = outcome,
    )

    @Test
    fun `analyze identifies the most overrun app`() {
        val sessions = listOf(
            session("com.app.a", Outcome.OVERRAN),
            session("com.app.a", Outcome.OVERRAN),
            session("com.app.b", Outcome.ON_TIME),
        )
        val insights = analyzer.analyze(sessions, emptyList(), emptyList(), xp = 0, streakDays = 0)
        assertEquals("com.app.a", insights.topOverrunApp)
    }

    @Test
    fun `analyze picks the most effective template`() {
        val interventions = listOf(
            Intervention(sessionId = 1, template = InterventionTemplate.BREATH_PAUSE, driftKind = DriftKind.OPENED, at = 0, reward = true),
            Intervention(sessionId = 2, template = InterventionTemplate.BREATH_PAUSE, driftKind = DriftKind.OPENED, at = 0, reward = false),
            Intervention(sessionId = 3, template = InterventionTemplate.REFLECTION, driftKind = DriftKind.OPENED, at = 0, reward = true),
        )
        val insights = analyzer.analyze(emptyList(), interventions, emptyList(), xp = 0, streakDays = 0)
        assertEquals(InterventionTemplate.REFLECTION, insights.mostEffectiveTemplate)
    }

    @Test
    fun `daily insights group sessions by day`() {
        val sessions = listOf(
            session("com.a", Outcome.ON_TIME, 0),
            session("com.a", Outcome.OVERRAN, 0),
            session("com.a", Outcome.ON_TIME, -86_400_000L), // yesterday
        )
        val daily = analyzer.dailyInsights(sessions, emptyList())
        assertEquals(2, daily.size)
        assertEquals(2, daily.first { it.overruns == 1 }.sessions)
    }
}
