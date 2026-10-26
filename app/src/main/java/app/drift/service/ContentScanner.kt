package app.drift.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.drift.domain.content.ContentBlockRule

/**
 * Scans an accessibility window tree for a short-form viewer and decides whether
 * it should be blocked (true) or left alone (false — e.g. a DM-sent video when
 * DM exemption is enabled).
 */
class ContentScanner {

    fun isBlockedContent(
        root: AccessibilityNodeInfo?,
        packageName: String,
        rule: ContentBlockRule,
        allowDmVideos: Boolean,
    ): Boolean {
        if (root == null) return false
        val viewerId = fullViewId(packageName, rule.detectionViewId)
        val viewer = find(root, viewerId) ?: return false
        if (allowDmVideos && rule.dmExemption != null && isDmVideo(root, packageName, rule.dmExemption)) {
            return false
        }
        return true
    }

    /** On-screen bounds of the short-form viewer node, or null if absent/empty. */
    fun viewerBounds(
        root: AccessibilityNodeInfo?,
        packageName: String,
        rule: ContentBlockRule,
    ): Rect? {
        if (root == null) return null
        val viewer = find(root, fullViewId(packageName, rule.detectionViewId)) ?: return null
        val rect = Rect()
        viewer.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return rect
    }

    fun isDmVideo(
        root: AccessibilityNodeInfo,
        packageName: String,
        exemption: app.drift.domain.content.DmExemption,
    ): Boolean {
        val allRequired = exemption.requiredViewIds.all { id -> find(root, fullViewId(packageName, id)) != null }
        val anyForbidden = exemption.forbiddenViewIds.any { id -> find(root, fullViewId(packageName, id)) != null }
        return allRequired && !anyForbidden
    }

    private fun find(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()

    private fun fullViewId(packageName: String, viewId: String) = "$packageName:id/$viewId"
}

