package com.attentionos.attention_os

internal data class UsageTimeRange(val startTimeMs: Long, val endTimeMs: Long) {
    init {
        require(startTimeMs >= 0L) { "startTimeMs must not be negative" }
        require(endTimeMs > startTimeMs) { "endTimeMs must be after startTimeMs" }
    }
}

internal class UsageAccessDeniedException : SecurityException(
    "Usage access has not been granted.",
)

internal data class AppUsageSample(
    val packageName: String,
    val lastTimeUsedMs: Long,
    val totalTimeInForegroundMs: Long,
) {
    fun toChannelMap(): Map<String, Any> = mapOf(
        "packageName" to packageName,
        "lastTimeUsedMs" to lastTimeUsedMs,
        "totalTimeInForegroundMs" to totalTimeInForegroundMs,
    )
}

internal fun requireUsageAccess(isGranted: Boolean) {
    if (!isGranted) {
        throw UsageAccessDeniedException()
    }
}

internal fun mapUsageSamples(
    samples: Iterable<AppUsageSample>,
    ownPackageName: String,
): List<Map<String, Any>> = samples
    .asSequence()
    .filter { it.packageName != ownPackageName }
    .filter { it.totalTimeInForegroundMs > 0L || it.lastTimeUsedMs > 0L }
    .map { it.toChannelMap() }
    .toList()
