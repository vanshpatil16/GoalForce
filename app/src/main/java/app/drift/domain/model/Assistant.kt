package app.drift.domain.model

/** A single chat turn (role + content) sent to / received from the LLM. */
data class ChatTurn(val role: String, val content: String)

/** A durable fact the assistant has learned about the user (Hermes-style memory). */
data class MemoryEntry(
    val kind: MemoryKind,
    val content: String,
    val createdAt: Long,
)

enum class MemoryKind { GOAL, PREFERENCE, OPINION, CONTEXT }
