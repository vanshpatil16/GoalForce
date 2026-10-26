package app.drift.data.ai

import app.drift.domain.model.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Groq chat-completions client using Android's JDK and org.json. */
class GroqClient(
    private val apiKeys: String,
    private val model: String = "openai/gpt-oss-20b",
) {
    private val keyPool: List<String> = apiKeys
        .split(',', ';', '\n', '\r')
        .map { it.trim().trim('"', '\'') }
        .filter { it.isNotBlank() }
        .distinct()

    @Volatile
    private var nextKeyIndex: Int = 0

    suspend fun chat(messages: List<ChatTurn>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val availableKeys = keyPool.ifEmpty { listOf(apiKeys.trim()) }.filter { it.isNotBlank() }
            if (availableKeys.isEmpty()) error("No Groq API key configured")

            val currentKey = availableKeys[nextKeyIndex.mod(availableKeys.size)]
            nextKeyIndex = (nextKeyIndex + 1) % availableKeys.size

            val body = JSONObject()
                .put("model", model)
                .put("messages", JSONArray().apply {
                    messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
                })

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $currentKey")
                connectTimeout = 15_000
                readTimeout = 40_000
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) error("Groq HTTP $code: ${responseText.take(200)}")
            JSONObject(responseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    }
}