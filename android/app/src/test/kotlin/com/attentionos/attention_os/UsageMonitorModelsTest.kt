package com.attentionos.attention_os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import android.app.usage.UsageEvents

class UsageMonitorModelsTest {
    @Test
    fun acceptsValidUsageRange() {
        assertEquals(UsageTimeRange(10L, 20L), UsageTimeRange(10L, 20L))
    }

    @Test
    fun rejectsNegativeStartTime() {
        assertThrows(IllegalArgumentException::class.java) {
            UsageTimeRange(-1L, 20L)
        }
    }

    @Test
    fun rejectsEndTimeBeforeOrEqualToStartTime() {
        assertThrows(IllegalArgumentException::class.java) {
            UsageTimeRange(20L, 20L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UsageTimeRange(21L, 20L)
        }
    }

    @Test
    fun mapsUsageRecordsAndExcludesThisApplication() {
        val result = mapUsageSamples(
            listOf(
                AppUsageSample("com.example.reader", 123L, 456L),
                AppUsageSample("com.attentionos.attention_os", 789L, 100L),
                AppUsageSample("com.example.empty", 0L, 0L),
            ),
            "com.attentionos.attention_os",
        )

        assertEquals(1, result.size)
        assertEquals(
            mapOf(
                "packageName" to "com.example.reader",
                "lastTimeUsedMs" to 123L,
                "totalTimeInForegroundMs" to 456L,
            ),
            result.single(),
        )
    }

    @Test
    fun mapsEmptyUsageResultsToAnEmptyList() {
        assertEquals(emptyList<Map<String, Any>>(), mapUsageSamples(emptyList(), "own"))
    }

    @Test
    fun rejectsMissingUsageAccess() {
        assertThrows(SecurityException::class.java) {
            requireUsageAccess(false)
        }
    }

    @Test
    fun processesOrderedDeduplicatedSessionTransitionsAndEstimatesDuration() {
        val range = UsageTimeRange(100L, 500L)
        val result = UsageEventProcessor.process(
            listOf(
                UsageEventSample("com.example.app", 300L, UsageEvents.Event.ACTIVITY_PAUSED),
                UsageEventSample("com.example.app", 200L, UsageEvents.Event.ACTIVITY_RESUMED),
                UsageEventSample("com.example.app", 200L, UsageEvents.Event.ACTIVITY_RESUMED),
                UsageEventSample("com.outside.app", 90L, UsageEvents.Event.ACTIVITY_RESUMED),
                UsageEventSample("com.example.app", 600L, UsageEvents.Event.ACTIVITY_RESUMED),
            ),
            range,
            "com.attentionos.attention_os",
        ).single()

        assertEquals("com.example.app", result.packageName)
        assertEquals(200L, result.firstObservedAtMs)
        assertEquals(300L, result.lastObservedAtMs)
        assertEquals(100L, result.estimatedForegroundMs)
        assertEquals(2, result.transitionCount)
        assertEquals(false, result.durationIncomplete)
    }

    @Test
    fun screenOffClosesForegroundAndMarksDurationIncomplete() {
        val result = UsageEventProcessor.process(
            listOf(
                UsageEventSample("com.example.app", 120L, UsageEvents.Event.ACTIVITY_RESUMED),
                UsageEventSample("android", 180L, UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            ),
            UsageTimeRange(100L, 500L),
            "com.attentionos.attention_os",
        ).single()

        assertEquals(60L, result.estimatedForegroundMs)
        assertEquals(true, result.durationIncomplete)
    }

    @Test
    fun incompleteForegroundClosesAtSessionEndAndPreservesPackage() {
        val result = UsageEventProcessor.process(
            listOf(UsageEventSample("org.example", 250L, UsageEvents.Event.MOVE_TO_FOREGROUND)),
            UsageTimeRange(100L, 400L),
            "own",
        ).single()

        assertEquals("org.example", result.packageName)
        assertEquals(150L, result.estimatedForegroundMs)
        assertEquals(true, result.durationIncomplete)
    }
}
