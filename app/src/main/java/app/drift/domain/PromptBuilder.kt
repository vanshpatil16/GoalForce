package app.drift.domain

import app.drift.domain.model.ChatTurn

/**
 * Builds the prompt for the assistant: a fixed persona + the live on-device
 * behavioural report + the user's remembered facts (Hermes-style memory).
 * Pure and testable.
 */
class PromptBuilder {

    fun systemPrompt(name: String, reportText: String, memoryText: String): String {
        val who = name.ifBlank { "the user" }
        val memorySection = if (memoryText.isBlank()) {
            "(nothing remembered yet — ask open questions to learn about $who)"
        } else {
            memoryText
        }
        return """
            You are Drift, a warm, non-judgmental attention coach inside a focus app. You help $who understand their phone habits and stay intentional.

            You have a live, on-device behavioural report. Use its numbers concretely and point out the small patterns the user likely missed (bursts of re-opening, rapid app-switching, overruns, blocked short-form content). Be specific, encouraging, and brief.

            Behavioural report:
            $reportText

            What you remember about $who:
            $memorySection

            Rules:
            - Answer in 2-4 short sentences or a few bullets.
            - Cite real numbers; never invent metrics you don't have.
            - If asked something outside your data, say so honestly and ask a helpful follow-up.
            - Frame everything as insight, not blame.
        """.trimIndent()
    }

    fun build(
        name: String,
        reportText: String,
        memoryText: String,
        history: List<ChatTurn>,
        question: String,
    ): List<ChatTurn> =
        listOf(ChatTurn("system", systemPrompt(name, reportText, memoryText))) +
            history.takeLast(12) +
            listOf(ChatTurn("user", question))
}
