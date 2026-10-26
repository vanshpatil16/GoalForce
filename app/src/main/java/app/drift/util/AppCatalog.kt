package app.drift.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.util.concurrent.TimeUnit

/** An installed, launchable app with its icon, for the "configure apps" list. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/** Discover installed apps and their recent foreground time (usage access). */
object AppCatalog {

    /** All launchable apps, alphabetised by label. */
    fun installedApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { resolve ->
                val pkg = resolve.activityInfo?.packageName ?: return@map null
                InstalledApp(
                    packageName = pkg,
                    label = resolve.loadLabel(pm).toString(),
                    icon = runCatching { resolve.loadIcon(pm) }.getOrNull(),
                )
            }
            .filterNotNull()
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Package → foreground milliseconds over the last 7 days. Empty without usage access. */
    fun screenTimeMillis(context: Context): Map<String, Long> = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(7)
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, since, now)
            .orEmpty()
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }
    }.getOrDefault(emptyMap())

    /** Human-readable duration, e.g. "2h 15m", "45m", "30s". */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            s > 0 -> "${s}s"
            else -> "—"
        }
    }
}
