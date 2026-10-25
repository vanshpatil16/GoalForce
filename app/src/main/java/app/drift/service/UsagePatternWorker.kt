package app.drift.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Usage-pattern analysis via [UsageStatsManager]. Complements the accessibility
 * detector with aggregate usage data (time per app, open counts) and is also the
 * "legacy" path for users who decline the accessibility service.
 *
 * Runs as a periodic WorkManager job (background, battery-friendly).
 */
class UsagePatternWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(7)

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, since, now)
        if (stats.isNullOrEmpty()) return Result.retry()

        val byPackage = stats.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }

        // Persist a coarse aggregate for the Progress screen. A full pipeline
        // would store per-package daily buckets; here we keep the last snapshot.
        val top = byPackage.entries.sortedByDescending { it.value }.take(20)
        UsageStatsCache.put(applicationContext, top.associate { it.key to it.value })

        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsagePatternWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "usage-patterns",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/** Minimal in-memory + SharedPreferences cache of the last usage snapshot. */
object UsageStatsCache {
    private const val PREFS = "usage_cache"
    private const val KEY = "top_packages_json"

    fun put(context: Context, map: Map<String, Long>) {
        val json = map.entries.joinToString(",", "{", "}") {
            "\"${it.key.replace("\"", "")}\":${it.value}"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = android.app.AppOpsManager::class.java
        return try {
            val m = appOps.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            val mode = m.invoke(
                context.getSystemService(Context.APP_OPS_SERVICE),
                43, android.os.Process.myUid(), context.packageName,
            ) as Int
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
