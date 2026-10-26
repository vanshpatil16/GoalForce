package app.drift.domain

import app.drift.data.DriftRepository
import app.drift.data.SettingsRepository
import app.drift.data.ai.GroqClient
import app.drift.data.memory.AssistantMemoryStore
import app.drift.domain.model.ChatTurn
import app.drift.domain.model.ForegroundEvent
import kotlinx.coroutines.flow.first

/**
 * Orchestrates a single assistant turn: extract + persist durable memory from
 * the user's message, build a live behavioural report, prompt the LLM, and fall
 * back to a deterministic on-device answer when the network or key is missing.
 */
class AssistantService(
    private val normalizeForegroundEvents: (List<ForegroundEvent>) -> List<ForegroundEvent> = { it },
    private val repository: DriftRepository,
    private val settings: SettingsRepository,
    private val client: GroqClient,
    private val memoryStore: AssistantMemoryStore,
    private val analytics: AttentionAnalytics = AttentionAnalytics(),
    private val report: AttentionReport = AttentionReport(),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val offline: OfflineAssistant = OfflineAssistant(),
    private val sessionCoach: SessionCoach = SessionCoach(),
) {
    private val history = mutableListOf<ChatTurn>()

    suspend fun ask(question: String): String {
        // Learn from what the user just said (goals, preferences, opinions).
        MemoryExtractor.extract(question).forEach { memoryStore.append(it) }

        val name = settings.profileName.first()
        val sessions = repository.sessions.first()
        val events = normalizeForegroundEvents(repository.foregroundEventsSnapshot())

        val facts = report.build(events, sessions)
        val reportText = report.toText(facts)
        val memoryText = memoryStore.readMarkdown()

        val messages = promptBuilder.build(name, reportText, memoryText, history.toList(), question)
        val reply = client.chat(messages).getOrNull()?.trim().takeUnless { it.isNullOrBlank() }
            ?: offline.answer(question, facts, events)

        history += ChatTurn("user", question)
        history += ChatTurn("assistant", reply)
        return reply
    }

    /**
     * Produces evidence-backed coaching for a finished session from its persisted
     * summary (never from invented metrics). Uses the LLM when available, else a
     * deterministic on-device result.
     */
    suspend fun coachSession(sessionId: Long): String {
        val summary = repository.sessionSummary(sessionId)
            ?: return "I couldn't find that session's record."
        val recent = repository.recentSessionSummariesSnapshot(30)
        val context = sessionCoach.buildContext(summary, recent)
        val name = settings.profileName.first()

        val memoryText = memoryStore.readMarkdown()
        val messages = listOf(
            ChatTurn("system", sessionCoach.prompt(context, name, memoryText)),
            ChatTurn("user", "Coach me on this completed focus session."),
        )
        val llm = client.chat(messages).getOrNull()?.trim().takeUnless { it.isNullOrBlank() }
        return llm ?: withSavedContext(formatCoaching(sessionCoach.offlineCoaching(context)), memoryText)
    }

    /** Coaches the most recently finished session, if any. */
    suspend fun coachLastSession(): String {
        val recent = repository.recentSessionSummariesSnapshot(1)
        val last = recent.firstOrNull() ?: return "No finished sessions yet — start a focus session first."
        return coachSession(last.sessionId)
    }

    /** Markdown facts saved from explicit user statements in chat. */
    suspend fun memoryMarkdown(): String = memoryStore.readMarkdown()

    /** Builds a plan from observed behavior and saved user preferences. */
    suspend fun createPlan(): String {
        val name = settings.profileName.first()
        val sessions = repository.sessions.first()
        val events = normalizeForegroundEvents(repository.foregroundEventsSnapshot())
        val facts = report.build(events, sessions)
        val memoryText = memoryStore.readMarkdown()
        val question = "Create a short, practical focus plan for today based on the behavioral report and remembered preferences. " +
            "Use only observed numbers, give 2-3 steps, and do not invent tasks or claim actions the user did not take."
        val messages = promptBuilder.build(name, report.toText(facts), memoryText, emptyList(), question)
        val generated = client.chat(messages).getOrNull()?.trim().takeUnless { it.isNullOrBlank() }
        return generated ?: offlinePlan(facts, memoryText)
    }

    private fun offlinePlan(facts: AttentionReport.Facts, memoryText: String): String = buildString {
        append("A small plan for today\n\n")
        val burst = facts.topBurst
        if (burst != null) {
            append("You reopened ${burst.label} ${burst.opens} times within ${burst.windowMs / 60_000} minute(s). Put that app aside during your next focus block, then check it at a planned stopping point.\n\n")
        } else if (facts.topOpenAppToday != null) {
            append("Your most-opened app today is ${facts.topOpenAppToday}. Choose a specific purpose before opening it during a focus block.\n\n")
        } else {
            append("There isn't enough app-open history yet to identify a distraction pattern. Choose one task, set a realistic timer, and review how it went afterward.\n\n")
        }
        append("You have completed ${facts.sessionsToday} focus session(s) today; use your next session to build on that pattern.")
        savedContext(memoryText)?.let { append("\n\nSaved context to keep in mind: $it") }
    }

    private fun withSavedContext(text: String, memoryText: String): String =
        savedContext(memoryText)?.let { "$text\n\nSaved context to keep in mind: $it" } ?: text

    private fun savedContext(memoryText: String): String? = memoryText.lineSequence()
        .filter { it.startsWith("- ") }
        .map { it.removePrefix("- ") }
        .take(3)
        .toList()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("; ")

    private fun formatCoaching(c: CoachingResult): String = buildString {
        append(c.summary).append("\n\n")
        c.observations.forEach { append("• ").append(it).append('\n') }
        append("\nSuggestion: ").append(c.suggestion).append('\n')
        append(c.encouragement)
        c.nextGoal?.let { append("\n\nNext goal: ").append(it) }
    }
}
