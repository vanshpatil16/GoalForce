package com.attentionos.attention_os

import android.app.usage.UsageEvents

/** Pure usage-event processing so boundary and ordering rules can be unit tested. */
internal data class UsageEventSample(
    val packageName: String,
    val timestampMs: Long,
    val type: Int,
)

internal data class SessionUsageRecord(
    val packageName: String,
    val firstObservedAtMs: Long,
    val lastObservedAtMs: Long,
    val estimatedForegroundMs: Long,
    val transitionCount: Int,
    val durationIncomplete: Boolean,
) {
    fun toChannelMap(displayName: String?): Map<String, Any> = buildMap {
        put("packageName", packageName)
        if (displayName != null) put("displayName", displayName)
        put("firstObservedAtMs", firstObservedAtMs)
        put("lastObservedAtMs", lastObservedAtMs)
        put("estimatedForegroundMs", estimatedForegroundMs)
        put("transitionCount", transitionCount)
        put("durationIncomplete", durationIncomplete)
        put("dataSource", "Android UsageEvents")
    }
}

internal object UsageEventProcessor {
    private const val MOVE_TO_FOREGROUND = UsageEvents.Event.MOVE_TO_FOREGROUND
    private const val MOVE_TO_BACKGROUND = UsageEvents.Event.MOVE_TO_BACKGROUND
    private const val SCREEN_NON_INTERACTIVE = UsageEvents.Event.SCREEN_NON_INTERACTIVE
    private const val KEYGUARD_SHOWN = UsageEvents.Event.KEYGUARD_SHOWN
    private const val ACTIVITY_RESUMED = UsageEvents.Event.ACTIVITY_RESUMED
    private const val ACTIVITY_PAUSED = UsageEvents.Event.ACTIVITY_PAUSED

    fun process(
        events: Iterable<UsageEventSample>,
        range: UsageTimeRange,
        ownPackageName: String,
    ): List<SessionUsageRecord> {
        val ordered = events.asSequence()
            .filter { it.timestampMs >= range.startTimeMs && it.timestampMs <= range.endTimeMs }
            .distinctBy { Triple(it.packageName, it.timestampMs, it.type) }
            .sortedWith(compareBy<UsageEventSample> { it.timestampMs }.thenBy { it.type })
            .toList()

        data class MutableRecord(
            var first: Long,
            var last: Long,
            var duration: Long = 0,
            var transitions: Int = 0,
            var incomplete: Boolean = false,
        )

        val records = linkedMapOf<String, MutableRecord>()
        var foregroundPackage: String? = null
        var foregroundStartedAt: Long? = null

        fun stopForeground(at: Long, incomplete: Boolean = false) {
            val packageName = foregroundPackage ?: return
            val started = foregroundStartedAt
            val record = records[packageName]
            if (started != null && record != null && at >= started) {
                record.duration += at - started
                record.incomplete = record.incomplete || incomplete
            }
            foregroundPackage = null
            foregroundStartedAt = null
        }

        for (event in ordered) {
            val starts = event.type == MOVE_TO_FOREGROUND || event.type == ACTIVITY_RESUMED
            val ends = event.type == MOVE_TO_BACKGROUND || event.type == ACTIVITY_PAUSED
            if (starts) {
                if (event.packageName.isBlank()) continue
                stopForeground(event.timestampMs)
                if (event.packageName != ownPackageName) {
                    val record = records.getOrPut(event.packageName) {
                        MutableRecord(event.timestampMs, event.timestampMs)
                    }
                    record.first = minOf(record.first, event.timestampMs)
                    record.last = maxOf(record.last, event.timestampMs)
                    record.transitions++
                }
                foregroundPackage = event.packageName
                foregroundStartedAt = event.timestampMs
            } else if (ends) {
                if (event.packageName.isBlank()) continue
                val record = if (event.packageName != ownPackageName) {
                    records.getOrPut(event.packageName) {
                        MutableRecord(event.timestampMs, event.timestampMs)
                    }
                } else null
                if (record != null) {
                    record.last = maxOf(record.last, event.timestampMs)
                    record.transitions++
                    if (foregroundPackage != event.packageName) record.incomplete = true
                }
                if (foregroundPackage == event.packageName) stopForeground(event.timestampMs)
            } else if (event.type == SCREEN_NON_INTERACTIVE || event.type == KEYGUARD_SHOWN) {
                stopForeground(event.timestampMs, incomplete = true)
            }
        }
        stopForeground(range.endTimeMs, incomplete = true)

        return records.map { (packageName, value) ->
            SessionUsageRecord(
                packageName = packageName,
                firstObservedAtMs = value.first,
                lastObservedAtMs = value.last,
                estimatedForegroundMs = value.duration,
                transitionCount = value.transitions,
                durationIncomplete = value.incomplete,
            )
        }.sortedWith(compareByDescending<SessionUsageRecord> { it.estimatedForegroundMs }
            .thenBy { it.firstObservedAtMs })
    }
}
