package app.drift.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Human-readable names for the apps Drift tracks. Resolve labels from Android
 * when a package is not in the small set of common aliases.
 */
object AppLabels {
    @Volatile private var cachedHomePackages: Set<String>? = null

    private val known = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.google.android.apps.youtube.kids" to "YouTube Kids",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.whatsapp" to "WhatsApp",
        "com.facebook.katana" to "Facebook",
        "com.facebook.orca" to "Messenger",
        "com.twitter.android" to "X (Twitter)",
        "com.spotify.music" to "Spotify",
        "com.android.chrome" to "Chrome",
        "com.google.android.gm" to "Gmail",
        "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit",
        "com.netflix.mediaclient" to "Netflix",
        "com.amazon.mShop.android.shopping" to "Amazon",
    )

    fun name(context: Context, packageName: String): String {
        known[packageName]?.let { return it }
        return runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            ).toString().takeIf { it.isNotBlank() } ?: fallbackLabel(packageName)
        }.getOrDefault(fallbackLabel(packageName))
    }

    /** True only for user-facing launchable apps, excluding Android home launchers. */
    fun isUserFacingApp(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName || packageName == "com.android.systemui") return false
        val pm = context.packageManager
        val homePackages = cachedHomePackages ?: synchronized(this) {
            cachedHomePackages ?: runCatching {
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY,
                ).mapNotNull { it.activityInfo?.packageName }.toSet()
            }.getOrDefault(emptySet()).also { cachedHomePackages = it }
        }
        if (packageName in homePackages) return false
        return runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
    }

    private fun fallbackLabel(packageName: String): String =
        packageName.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
}
