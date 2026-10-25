package com.attentionos.attention_os

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_NAME,
        ).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getAndroidVersion" -> result.success(Build.VERSION.SDK_INT)
                    "hasUsageAccess" -> result.success(hasUsageAccess())
                    "openUsageAccessSettings" -> result.success(openUsageAccessSettings())
                    "getRecentAppUsage" -> result.success(getRecentAppUsage(call.arguments))
                    "getSessionAppUsage" -> result.success(getSessionAppUsage(call.arguments))
                    "getTrackableApps" -> result.success(getTrackableApps())
                    else -> result.notImplemented()
                }
            } catch (error: IllegalArgumentException) {
                result.error("INVALID_ARGUMENT", error.message, null)
            } catch (error: UsageAccessDeniedException) {
                result.error("USAGE_ACCESS_DENIED", error.message, null)
            } catch (error: SecurityException) {
                result.error(
                    "USAGE_STATS_UNAVAILABLE",
                    "Android did not allow the usage statistics query.",
                    null,
                )
            } catch (error: IllegalStateException) {
                result.error(
                    "USAGE_STATS_UNAVAILABLE",
                    error.message ?: "Android usage statistics are unavailable.",
                    null,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings(): Boolean {
        return try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun getRecentAppUsage(arguments: Any?): List<Map<String, Any>> {
        val values = arguments as? Map<*, *>
            ?: throw IllegalArgumentException("Expected startTimeMs and endTimeMs.")
        val start = readTimestamp(values["startTimeMs"], "startTimeMs")
        val end = readTimestamp(values["endTimeMs"], "endTimeMs")
        val range = UsageTimeRange(start, end)
        requireUsageAccess(hasUsageAccess())
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: throw IllegalStateException("UsageStatsManager is unavailable.")
        val samples = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            range.startTimeMs,
            range.endTimeMs,
        ).orEmpty()
            .map {
                AppUsageSample(
                    packageName = it.packageName,
                    lastTimeUsedMs = it.lastTimeUsed,
                    totalTimeInForegroundMs = it.totalTimeInForeground,
                )
            }
        return mapUsageSamples(samples, packageName)
    }

    private fun getSessionAppUsage(arguments: Any?): List<Map<String, Any>> {
        val values = arguments as? Map<*, *>
            ?: throw IllegalArgumentException("Expected startTimeMs and endTimeMs.")
        val range = UsageTimeRange(
            readTimestamp(values["startTimeMs"], "startTimeMs"),
            readTimestamp(values["endTimeMs"], "endTimeMs"),
        )
        requireUsageAccess(hasUsageAccess())
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: throw IllegalStateException("UsageStatsManager is unavailable.")
        val usageEvents = manager.queryEvents(range.startTimeMs, range.endTimeMs)
            ?: throw IllegalStateException("Android returned no usage-event stream.")
        val event = UsageEvents.Event()
        val samples = mutableListOf<UsageEventSample>()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            samples += UsageEventSample(
                packageName = event.packageName.orEmpty(),
                timestampMs = event.timeStamp,
                type = event.eventType,
            )
        }
        return UsageEventProcessor.process(samples, range, packageName).map { record ->
            record.toChannelMap(resolveDisplayName(record.packageName))
        }
    }

    private fun resolveDisplayName(targetPackage: String): String? = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getApplicationInfo(targetPackage, 0)
        packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun getTrackableApps(): List<Map<String, String>> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val targetPackage = appInfo.packageName ?: return@mapNotNull null
                if (targetPackage == packageName) return@mapNotNull null
                val label = packageManager.getApplicationLabel(appInfo).toString()
                    .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                targetPackage to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .map { (targetPackage, label) ->
                mapOf("packageName" to targetPackage, "displayName" to label)
            }
    }

    private fun readTimestamp(value: Any?, name: String): Long = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("$name must be an integer.")
    }

    private companion object {
        const val CHANNEL_NAME = "com.attentionos/native"
    }
}
