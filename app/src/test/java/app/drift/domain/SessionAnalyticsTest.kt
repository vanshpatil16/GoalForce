package app.drift.domain

import app.drift.domain.model.ForegroundEvent
import app.drift.domain.model.ForegroundEventKind
import app.drift.domain.model.Outcome
import app.drift.domain.model.Session
import app.drift.domain.model.SessionSummary
import app.drift.domain.model.XpCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalyticsTest {
    private val analytics = SessionAnalytics()
    private val restricted = setOf("com.instagram.android", "com.google.android.youtube")
    private val fullPerms = TrackingPermissions(accessibilityEnabled = true, usageAccess = true)
    private val noPerms = TrackingPermissions(accessibilityEnabled = false, usageAccess = false)

    private fun session(start: Long, end: Long, outcome: Outcome? = Outcome.ON_TIME) =
        Session(intentId = 1, targetPackage = "com.notes", startedAt = start, endedAt = end, outcome = outcome)

    @Test
    fun `complete 15-minute session with no restricted app is distraction free`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val r = analytics.summarize(session(start, end), 15, emptyList(), restricted, fullPerms)
        assertEquals(900, r.actualDurationSeconds)
        assertEquals(0, r.distractionCount)
        assertEquals(0, r.restrictionAttemptCount)
        assertEquals(100, r.focusScore)
        assertEquals("completed", r.completionStatus)
        assertEquals(1.0f, r.trackingCoverage)
    }

    @Test
    fun `restricted app foreground time is estimated from transitions`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val events = listOf(
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram", kind = ForegroundEventKind.APP_OPEN, at = start + 60_000),
            ForegroundEvent(packageName = "com.notes", label = "Notes", kind = ForegroundEventKind.APP_OPEN, at = start + 120_000),
        )
        val r = analytics.summarize(session(start, end), 15, events, restricted, fullPerms)
        assertEquals(60, r.restrictedAppForegroundSeconds)
        assertEquals(1, r.distractionCount)
    }

    @Test
    fun `opening a restricted app at all counts as application-level distraction`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val events = listOf(
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram", kind = ForegroundEventKind.APP_OPEN, at = start + 10_000),
        )
        val r = analytics.summarize(session(start, end), 15, events, restricted, fullPerms)
        assertEquals(1, r.distractionCount)
    }

    @Test
    fun `restriction attempts are counted separately from opens`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val events = listOf(
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram Reels", kind = ForegroundEventKind.CONTENT_BLOCKED, at = start + 30_000),
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram Reels", kind = ForegroundEventKind.CONTENT_BLOCKED, at = start + 31_000),
        )
        val r = analytics.summarize(session(start, end), 15, events, restricted, fullPerms)
        assertEquals(2, r.restrictionAttemptCount)
        assertEquals(2, r.distractionCount)
    }

    @Test
    fun `missing permissions reduce tracking coverage`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val r = analytics.summarize(session(start, end), 15, emptyList(), restricted, noPerms)
        assertEquals(0.0f, r.trackingCoverage)
    }

    @Test
    fun `events after the session end are excluded`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val events = listOf(
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram", kind = ForegroundEventKind.APP_OPEN, at = end + 1_000),
        )
        val r = analytics.summarize(session(start, end), 15, events, restricted, fullPerms)
        assertEquals(0, r.distractionCount)
        assertEquals(0, r.appOpenCount)
    }

    @Test
    fun `recomputing from the same events is deterministic`() {
        val start = 1_000_000_000_000L
        val end = start + 15 * 60_000L
        val events = listOf(
            ForegroundEvent(packageName = "com.instagram.android", label = "Instagram", kind = ForegroundEventKind.APP_OPEN, at = start + 60_000),
            ForegroundEvent(packageName = "com.notes", label = "Notes", kind = ForegroundEventKind.APP_OPEN, at = start + 120_000),
        )
        val s = session(start, end)
        val a = analytics.summarize(s, 15, events, restricted, fullPerms)
        val b = analytics.summarize(s, 15, events, restricted, fullPerms)
        assertEquals(a, b)
    }

    @Test
    fun `abandoned session reports abandoned status`() {
        val start = 1_000_000_000_000L
        val end = start + 5 * 60_000L
        val r = analytics.summarize(session(start, end, Outcome.ABANDONED), 15, emptyList(), restricted, fullPerms)
        assertEquals("abandoned", r.completionStatus)
    }
}

class SessionCoachTest {
    private val coach = SessionCoach()

    private fun summary(id: Long, focus: Int, restricted: Int, distractions: Int, attempts: Int, status: String = "completed") =
        SessionSummary(
            sessionId = id,
            plannedDurationSeconds = 900,
            actualDurationSeconds = 900,
            completionStatus = status,
            restrictedAppForegroundSeconds = restricted,
            distractionCount = distractions,
            restrictionAttemptCount = attempts,
            appOpenCount = 0,
            focusScore = focus,
            trackingCoverage = 1.0f,
            xpEarned = 45,
            createdAt = 0,
        )

    @Test
    fun `context includes recent baseline when history exists`() {
        val ctx = coach.buildContext(summary(3, 90, 0, 0, 0), listOf(summary(1, 80, 10, 1, 0), summary(2, 70, 20, 2, 1)))
        assertEquals(2, ctx.recentSessionCount)
        assertEquals(75, ctx.recentAvgFocusScore) // (80 + 70) / 2
    }

    @Test
    fun `offline coaching is evidence backed and non-empty`() {
        val ctx = coach.buildContext(summary(1, 82, 75, 3, 1), emptyList())
        val c = coach.offlineCoaching(ctx)
        assertTrue(c.summary.isNotBlank())
        assertTrue(c.observations.isNotEmpty())
        assertTrue(c.suggestion.isNotBlank())
        assertTrue(c.encouragement.isNotBlank())
    }

    @Test
    fun `offline coaching suggests a next goal only with baseline`() {
        val noBaseline = coach.offlineCoaching(coach.buildContext(summary(1, 82, 0, 0, 0), emptyList()))
        assertEquals(null, noBaseline.nextGoal)

        val withBaseline = coach.offlineCoaching(coach.buildContext(summary(2, 90, 0, 0, 0), listOf(summary(1, 70, 0, 0, 0))))
        assertTrue(withBaseline.nextGoal != null)
    }

    @Test
    fun `prompt includes only aggregate metrics`() {
        val ctx = coach.buildContext(summary(1, 82, 75, 3, 1), emptyList())
        val p = coach.prompt(ctx, "Alex")
        assertTrue(p.contains("Focus score: 82"))
        assertTrue(p.contains("Distraction count: 3"))
        assertTrue(p.contains("Alex"))
    }
}

class XpRulesNewCategoriesTest {
    @Test
    fun `new categories have configured base values`() {
        val rules = XpRules()
        assertEquals(15, rules.baseFor(XpCategory.DISTRACTION_FREE_BONUS))
        assertEquals(3, rules.baseFor(XpCategory.VERIFIED_DISMISSAL))
    }

    @Test
    fun `configurable values override defaults`() {
        val rules = XpRules(distractionFreeBonus = 20, verifiedDismissal = 5)
        assertEquals(20, rules.baseFor(XpCategory.DISTRACTION_FREE_BONUS))
        assertEquals(5, rules.baseFor(XpCategory.VERIFIED_DISMISSAL))
    }
}
