package app.drift.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.drift.domain.content.ContentBlockCatalog
import app.drift.domain.model.AttentionPathNode
import java.util.Locale

/** Maps third-party accessibility state to a fixed, privacy-safe graph node. */
class AttentionStateClassifier(
    private val contentScanner: ContentScanner = ContentScanner(),
) {

    fun classify(
        packageName: String,
        root: AccessibilityNodeInfo?,
        event: AccessibilityEvent,
        activeGoal: String?,
        allowDmVideos: Boolean,
    ): AttentionPathNode? {
        val rule = ContentBlockCatalog.ruleFor(packageName) ?: return null
        if (root == null) return null

        val viewerDetected = contentScanner.isBlockedContent(root, packageName, rule, allowDmVideos)
        val dmContext = rule.dmExemption?.let { contentScanner.isDmVideo(root, packageName, it) } == true
        val eventLabels = if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            event.text.map { it.toString().trim().lowercase(Locale.ROOT) }
                .filter { it in NAVIGATION_LABELS }
        } else {
            emptyList()
        }
        val visibleContent = if (viewerDetected && !dmContext) eventLabels + readVisibleStrings(root) else emptyList()
        val relation = if (visibleContent.isEmpty()) RELATION_UNAVAILABLE else relationToGoal(visibleContent, activeGoal)
        val appName = when (rule.appId) {
            "instagram_reels" -> "Instagram"
            "youtube_shorts" -> "YouTube"
            "tiktok" -> "TikTok"
            else -> return null
        }
        val state = if (viewerDetected) {
            "viewer"
        } else {
            classifySurface(packageName, root, eventLabels)
        }
        val relationSuffix = when (relation) {
            RELATION_GOAL -> "goal_related"
            RELATION_OFF_GOAL -> "no_goal_match"
            else -> "unavailable"
        }
        val label = when {
            viewerDetected -> rule.displayName
            state == "feed" -> "$appName feed"
            state == "inbox" && appName == "Instagram" -> "Instagram inbox"
            state == "search" && appName == "Instagram" -> "Instagram search"
            state == "profile" && appName == "Instagram" -> "Instagram profile"
            else -> "Unknown screen"
        }
        return AttentionPathNode(
            id = "${rule.appId}/$state/$relationSuffix",
            label = label,
            appId = rule.appId,
            relationBucket = relationSuffix,
            supportedForBlocking = viewerDetected,
        )
    }

    private fun classifySurface(packageName: String, root: AccessibilityNodeInfo, visible: List<String>): String {
        if (hasViewId(packageName, root, "reply_bar_edittext") || hasKnownLabel(visible, INBOX_LABELS)) return "inbox"
        if (hasViewId(packageName, root, "action_bar_search_edit_text") || hasViewId(packageName, root, "search_bar") || hasKnownLabel(visible, SEARCH_LABELS)) return "search"
        if (hasViewId(packageName, root, "profile_tab") || hasKnownLabel(visible, PROFILE_LABELS)) return "profile"
        if (hasViewId(packageName, root, "feed_tab") || hasViewId(packageName, root, "feed_view") || hasKnownLabel(visible, HOME_LABELS)) return "feed"
        return "unknown"
    }

    private fun relationToGoal(visible: List<String>, goal: String?): String {
        if (goal.isNullOrBlank()) return RELATION_UNAVAILABLE
        val goalTerms = tokens(goal)
        val contentTerms = tokens(visible.filterNot { it.trim().lowercase(Locale.ROOT) in NAVIGATION_LABELS }.joinToString(" "))
        if (goalTerms.isEmpty() || contentTerms.isEmpty()) return RELATION_UNAVAILABLE
        return if (goalTerms.any { it in contentTerms }) RELATION_GOAL else RELATION_OFF_GOAL
    }

    private fun tokens(value: String): Set<String> = value.lowercase(Locale.ROOT)
        .split(WORD_BREAKS)
        .filter { it.length >= MIN_KEYWORD_LENGTH && it !in STOP_WORDS }
        .toSet()

    private fun hasViewId(packageName: String, root: AccessibilityNodeInfo, id: String): Boolean =
        runCatching { root.findAccessibilityNodeInfosByViewId("$packageName:id/$id").isNotEmpty() }
            .getOrDefault(false)

    private fun hasKnownLabel(visible: List<String>, labels: Set<String>): Boolean =
        visible.any { it.trim().lowercase(Locale.ROOT) in labels }

    private fun readVisibleStrings(root: AccessibilityNodeInfo): List<String> {
        val output = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TREE_DEPTH || output.size >= MAX_VISIBLE_STRINGS) return
            node.text?.toString()?.take(MAX_VISIBLE_TEXT_CHARS)?.let(output::add)
            node.contentDescription?.toString()?.take(MAX_VISIBLE_TEXT_CHARS)?.let(output::add)
            for (index in 0 until node.childCount) {
                if (output.size >= MAX_VISIBLE_STRINGS) break
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
        visit(root, 0)
        return output
    }

    private companion object {
        const val RELATION_GOAL = "goal_related"
        const val RELATION_OFF_GOAL = "no_goal_match"
        const val RELATION_UNAVAILABLE = "unavailable"
        const val MIN_KEYWORD_LENGTH = 4
        const val MAX_VISIBLE_STRINGS = 120
        const val MAX_VISIBLE_TEXT_CHARS = 100
        const val MAX_TREE_DEPTH = 12
        val WORD_BREAKS = Regex("[^\\p{L}\\p{N}]+")
        val HOME_LABELS = setOf("home", "feed")
        val INBOX_LABELS = setOf("messages", "inbox", "chats")
        val SEARCH_LABELS = setOf("search", "explore")
        val PROFILE_LABELS = setOf("profile")
        val NAVIGATION_LABELS = HOME_LABELS + INBOX_LABELS + SEARCH_LABELS + setOf("reels", "shorts", "profile", "following", "for you")
        val STOP_WORDS = setOf("about", "after", "again", "also", "because", "before", "being", "could", "from", "have", "into", "just", "more", "most", "some", "than", "that", "their", "them", "then", "there", "these", "they", "this", "those", "with", "your")
    }
}
