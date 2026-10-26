package app.drift.domain.content

/** How to block detected short-form content. */
enum class ContentBlockMode {
    /** Press Back to leave the viewer entirely (default). */
    COMPLETE_BLOCK,
    /** Cover the video with an overlay while keeping the rest of the app usable. */
    COVER_OVERLAY,
}

/**
 * Rules for detecting short-form "infinite scroll" viewers (Reels/Shorts) by their
 * accessibility view IDs, and for exempting videos a friend sent you in a DM.
 *
 * The view IDs are how the apps expose their screens to accessibility services;
 * they are used here purely for interoperability (detecting a screen), not to
 * read message content.
 */
data class ContentBlockRule(
    val appId: String,
    val displayName: String,
    val packageIds: Set<String>,
    /** View id that identifies the full-screen Reels/Shorts viewer. */
    val detectionViewId: String,
    val dmExemption: DmExemption? = null,
)

data class DmExemption(
    /** All these view ids must be present for a video to count as DM-sent. */
    val requiredViewIds: Set<String>,
    /** Any of these view ids present marks it as algorithmic (so it is NOT exempt). */
    val forbiddenViewIds: Set<String> = emptySet(),
)

object ContentBlockCatalog {
    val rules: List<ContentBlockRule> = listOf(
        ContentBlockRule(
            appId = "instagram_reels",
            displayName = "Instagram Reels",
            packageIds = setOf("com.instagram.android"),
            detectionViewId = "clips_viewer_view_pager",
            dmExemption = DmExemption(
                requiredViewIds = setOf("sender_username_or_fullname", "sender_timestamp", "reply_bar_edittext"),
                forbiddenViewIds = setOf("suggested_title"),
            ),
        ),
        ContentBlockRule(
            appId = "youtube_shorts",
            displayName = "YouTube Shorts",
            packageIds = setOf("com.google.android.youtube", "com.google.android.apps.youtube.kids"),
            detectionViewId = "reel_player_page_container",
        ),
        ContentBlockRule(
            appId = "tiktok",
            displayName = "TikTok",
            packageIds = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            detectionViewId = "player_view",
        ),
    )

    fun ruleFor(packageName: String): ContentBlockRule? =
        rules.firstOrNull { packageName in it.packageIds }
}
