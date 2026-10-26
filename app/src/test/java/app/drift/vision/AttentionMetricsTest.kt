package app.drift.vision

import org.junit.Assert.*
import org.junit.Test

class AttentionMetricsTest {
    @Test fun earUsesTwoVerticalDistancesOverEyeWidth() {
        val points = listOf(
            AttentionMetrics.Point(0f, 0f), AttentionMetrics.Point(1f, -1f),
            AttentionMetrics.Point(2f, -1f), AttentionMetrics.Point(4f, 0f),
            AttentionMetrics.Point(2f, 1f), AttentionMetrics.Point(1f, 1f),
        )
        assertEquals(0.5f, AttentionMetrics.ear(points, intArrayOf(0, 1, 2, 3, 4, 5))!!, 0.001f)
    }

    @Test fun marNormalizesMouthOpeningByWidth() {
        val mar = AttentionMetrics.mar(AttentionMetrics.Point(2f, 4f), AttentionMetrics.Point(2f, 6f), AttentionMetrics.Point(0f, 5f), AttentionMetrics.Point(4f, 5f))
        assertEquals(0.5f, mar!!, 0.001f)
    }

    @Test fun irisOffsetIsRelativeToEyeWidthAndHeight() {
        val p = AttentionMetrics.normalizeIris(AttentionMetrics.Point(6f, 5f), AttentionMetrics.Point(0f, 5f), AttentionMetrics.Point(10f, 5f), AttentionMetrics.Point(5f, 0f), AttentionMetrics.Point(5f, 10f))!!
        assertEquals(0.1f, p.x, 0.001f)
        assertEquals(0f, p.y, 0.001f)
    }

    @Test fun singleBlinkDoesNotBecomeDrowsinessButProlongedClosureDoes() {
        val machine = FatigueStateMachine()
        assertEquals(FatigueStatus.NORMAL, machine.update(0, 0.3f, 0.2f))
        assertEquals(FatigueStatus.NORMAL, machine.update(300, 0.1f, 0.2f))
        assertEquals(FatigueStatus.POSSIBLE_DROWSINESS, machine.update(1_800, 0.1f, 0.2f))
    }

    @Test fun sustainedMouthOpeningAndCombinedSignalsChangeEstimatedState() {
        val machine = FatigueStateMachine()
        machine.update(0, 0.3f, 0.8f)
        assertEquals(FatigueStatus.POSSIBLE_YAWN, machine.update(1_300, 0.3f, 0.8f))
        machine.update(1_400, 0.1f, 0.8f)
        assertEquals(FatigueStatus.ELEVATED_INDICATORS, machine.update(3_000, 0.1f, 0.8f))
    }

    @Test fun missingTrackingReportsUnavailableAfterGracePeriod() {
        val machine = FatigueStateMachine()
        assertEquals(FatigueStatus.MONITORING_UNAVAILABLE, machine.update(0, null, null))
        machine.update(100, 0.3f, 0.2f)
        assertEquals(FatigueStatus.NORMAL, machine.update(500, null, null))
        assertEquals(FatigueStatus.MONITORING_UNAVAILABLE, machine.update(2_001, null, null))
    }
}
