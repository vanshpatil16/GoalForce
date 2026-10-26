package app.drift.domain

import app.drift.domain.model.ForegroundEvent

/**
 * Deterministic, on-device fallback used when the Groq API is unreachable or
 * no key is configured. Answers the most common questions from the live report
 * so the feature never silently breaks.
 */
class OfflineAssistant(
    private val report: AttentionReport = AttentionReport(),
    private val analytics: AttentionAnalytics = AttentionAnalytics(),
) {
    fun answer(question: String, facts: AttentionReport.Facts, events: List<ForegroundEvent>): String {
        val q = question.lowercase()

        if (q.contains("reels") || q.contains("shorts") || q.contains("tiktok") || q.contains("blocked")) {
            val n = facts.reelsBlockedToday
            return if (n == 0) {
                "I haven't blocked any Reels/Shorts/TikTok today. That's a good sign — you weren't pulled into the scroll."
            } else {
                "Today I blocked short-form content $n time${if (n == 1) "" else "s"}. Each block is a moment you chose your intention over the algorithm."
            }
        }

        // "how many times did I open <app>" / "opens in <app>"
        val app = facts.opensTodayByApp.keys.firstOrNull { label ->
            label.isNotBlank() && q.contains(label.lowercase())
        }
        if (q.contains("open") || q.contains("times") || app != null) {
            val label = app ?: facts.topOpenAppToday
            if (label == null) {
                return "I don't have any app-open data yet — open a few apps today and I'll start spotting patterns."
            }
            val count = facts.opensTodayByApp[label] ?: 0
            return "Today you opened $label $count time${if (count == 1) "" else "s"}."
        }

        if (q.contains("summary") || q.contains("report") || q.contains("analy") || q.contains("insight") || q.contains("how am i")) {
            return "Here's where things stand today:\n\n${report.toText(facts)}"
        }

        val top = facts.topOpenAppToday
        val lead = if (top != null) {
            "Today your most-opened app is $top. "
        } else {
            ""
        }
        return "${lead}I'm running in offline mode right now, so I can only share the basics. " +
            "Ask me for a summary, how many times you opened an app, or about blocked Reels — " +
            "or check your connection and I'll give you deeper insight."
    }
}
