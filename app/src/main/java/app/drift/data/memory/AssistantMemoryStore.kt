package app.drift.data.memory

import android.content.Context
import app.drift.domain.model.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A Hermes-style memory file: a single human-readable markdown document in app
 * private storage that accumulates what the assistant has learned about the
 * user — their goals, preferences, opinions and context. It is appended to over
 * time and injected verbatim into every prompt so the assistant grows more
 * personal with use.
 */
class AssistantMemoryStore(context: Context) {

    private val file: File = File(File(context.filesDir, "assistant"), "memory.md")
    private val mutex = Mutex()

    suspend fun readMarkdown(): String = withContext(Dispatchers.IO) { readFile() }

    suspend fun append(entry: MemoryEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = readFile()
            if (existing.contains(entry.content)) return@withLock
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.writeText("# What Drift remembers about you\n\n")
            }
            val line = "- (${entry.kind.name.lowercase()}) ${entry.content}"
            file.appendText(line + "\n")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }

    private fun readFile(): String = if (file.exists()) file.readText() else ""
}
