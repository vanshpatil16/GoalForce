package app.drift.domain

import app.drift.domain.model.MemoryEntry
import app.drift.domain.model.MemoryKind

/**
 * Heuristically extracts durable facts (goals, preferences, opinions, context)
 * from a user's chat message so the assistant can build a Hermes-style memory
 * of who the user is — on-device, deterministic, and cheap (no extra LLM call).
 *
 * Deliberately conservative: it only captures explicit, first-person statements
 * so junk doesn't pollute the memory file.
 */
object MemoryExtractor {

    private val markers = listOf(
        Marker(MemoryKind.GOAL, "my goal", "i want to", "i want to stop", "i want to start",
            "i'm trying to", "im trying to", "i aim to", "i need to", "i'd like to", "i would like to",
            "i want to reduce", "i want to quit"),
        Marker(MemoryKind.PREFERENCE, "i like", "i love", "i prefer", "i enjoy", "i hate", "i dislike"),
        Marker(MemoryKind.OPINION, "i think", "i feel", "i believe", "i struggle with", "i find", "i notice"),
        Marker(MemoryKind.CONTEXT, "i am a", "i'm a", "im a", "i work", "i study", "my job"),
    )

    fun extract(text: String): List<MemoryEntry> {
        val sentences = splitSentences(text)
        val out = mutableListOf<MemoryEntry>()
        for (sentence in sentences) {
            val lower = sentence.lowercase().trim()
            if (lower.length < 8) continue
            for (marker in markers) {
                val hit = marker.terms.any { lower.contains(it) }
                if (hit) {
                    out.add(MemoryEntry(marker.kind, sentence.trim(), System.currentTimeMillis()))
                    break
                }
            }
        }
        return out
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?;])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private class Marker(val kind: MemoryKind, vararg terms: String) {
        val terms: List<String> = terms.toList()
    }
}
