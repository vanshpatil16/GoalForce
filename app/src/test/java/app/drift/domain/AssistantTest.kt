package app.drift.domain

import app.drift.domain.model.ForegroundEvent
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.MemoryKind
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionAnalyticsTest {
    private val analytics = AttentionAnalytics()

    private fun open(label: String, at: Long) =
        ForegroundEvent(packageName = "pkg", label = label, kind = ForegroundEventKind.APP_OPEN, at = at)

    @Test
    fun `detects a burst of 4 opens in one minute`() {
        val events = listOf(
            open("Instagram", 0L),
            open("Instagram", 20_000L),
            open("Instagram", 40_000L),
            open("Instagram", 60_000L),
        )
        val burst = analytics.topBurst(events, windowMs = 60_000L, minOpens = 3)
        assertEquals("Instagram", burst?.label)
        assertEquals(4, burst?.opens)
    }

    @Test
    fun `a slow trickle is not flagged as a burst`() {
        val events = listOf(
            open("Instagram", 0L),
            open("Instagram", 10 * 60_000L),
        )
        assertNull(analytics.topBurst(events, windowMs = 60_000L, minOpens = 3))
    }

    @Test
    fun `counts opens and reels blocks today`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            open("Instagram", now - 2_000L),
            open("Instagram", now - 1_000L),
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram Reels", kind = ForegroundEventKind.CONTENT_BLOCKED, at = now - 500L),
            open("Instagram", now - 86_400_000L), // yesterday
        )
        assertEquals(2, analytics.opensTodayByApp(events, now)["Instagram"])
        assertEquals(1, analytics.reelsBlockedToday(events, now))
        assertEquals(2, analytics.totalOpensToday(events, now))
    }
}

class AttentionReportTest {
    @Test
    fun `report text includes the easy-to-miss signals`() {
        val now = System.currentTimeMillis()
        val events = listOf(
            ForegroundEvent(packageName = "ig", label = "Instagram", kind = ForegroundEventKind.APP_OPEN, at = now - 1_000),
            ForegroundEvent(packageName = "ig", label = "Instagram Reels", kind = ForegroundEventKind.CONTENT_BLOCKED, at = now - 500),
        )
        val sessions = listOf(
            Session(intentId = 1, targetPackage = "ig", startedAt = now - 60_000, outcome = Outcome.OVERRAN, reentries = 3),
        )
        val report = AttentionReport()
        val facts = report.build(events, sessions, now)
        assertEquals(1, facts.totalOpensToday)
        assertEquals(1, facts.reelsBlockedToday)
        assertEquals(1, facts.overrunsToday)
        assertEquals(3, facts.reentriesToday)
        val text = report.toText(facts)
        assertTrue(text.contains("Instagram"))
        assertTrue(text.contains("overruns: 1"))
    }
}

class MemoryExtractorTest {
    @Test
    fun `extracts goals preferences and opinions, ignores neutral text`() {
        val goal = MemoryExtractor.extract("I want to stop scrolling Instagram at night.")
        assertEquals(1, goal.size)
        assertEquals(MemoryKind.GOAL, goal[0].kind)

        val pref = MemoryExtractor.extract("I like reading before bed.")
        assertEquals(1, pref.size)
        assertEquals(MemoryKind.PREFERENCE, pref[0].kind)

        val opinion = MemoryExtractor.extract("I think social media is addictive.")
        assertEquals(1, opinion.size)
        assertEquals(MemoryKind.OPINION, opinion[0].kind)

        assertEquals(0, MemoryExtractor.extract("What time is it?").size)
    }

    @Test
    fun `extracts multiple facts from multiple sentences`() {
        val entries = MemoryExtractor.extract("I want to write more. I prefer working in the morning.")
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.kind == MemoryKind.GOAL })
        assertTrue(entries.any { it.kind == MemoryKind.PREFERENCE })
    }
}

class PromptBuilderTest {
    @Test
    fun `system prompt carries report and memory, message roles are ordered`() {
        val builder = PromptBuilder()
        val turns = builder.build(
            name = "Alex",
            reportText = "Opens today: 12.",
            memoryText = "I want to stop scrolling.",
            history = emptyList(),
            question = "How am I doing?",
        )
        assertEquals("system", turns.first().role)
        assertEquals("user", turns.last().role)
        assertEquals("How am I doing?", turns.last().content)
        assertTrue(turns.first().content.contains("Opens today: 12."))
        assertTrue(turns.first().content.contains("I want to stop scrolling."))
        assertTrue(turns.first().content.contains("Alex"))
    }
}

class OfflineAssistantTest {
    @Test
    fun `answers opens, reels and summary queries from facts`() {
        val facts = AttentionReport.Facts(
            totalOpensToday = 4,
            opensTodayByApp = mapOf("Instagram" to 4),
            topBurst = null,
            reelsBlockedToday = 2,
            sessionsToday = 1,
            overrunsToday = 0,
            reentriesToday = 0,
            topOpenAppToday = "Instagram",
        )
        val offline = OfflineAssistant()
        assertTrue(offline.answer("how many times did I open Instagram?", facts, emptyList()).contains("4"))
        assertTrue(offline.answer("did I watch reels?", facts, emptyList()).contains("2"))
        assertTrue(offline.answer("give me a summary", facts, emptyList()).contains("Instagram"))
    }
}
