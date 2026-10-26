package app.drift.data.news

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class MotivationStory(
    val title: String,
    val summary: String,
    val link: String,
)

/** Reads a small, cached selection of positive headlines from GNN's public RSS feed. */
class MotivationNewsRepository {

    @Volatile private var cachedStories: List<MotivationStory> = emptyList()
    @Volatile private var cachedAtMs: Long = 0L

    suspend fun storyFor(goal: String, savedContext: String): MotivationStory? = withContext(Dispatchers.IO) {
        val stories = loadStories()
        chooseStory(stories, "$goal $savedContext")
    }

    private fun loadStories(): List<MotivationStory> {
        val now = System.currentTimeMillis()
        if (cachedStories.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS) return cachedStories

        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            setRequestProperty("User-Agent", "Drift/1.0 (Android focus assistant)")
        }
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                setInput(connection.inputStream, null)
            }
            val stories = mutableListOf<MotivationStory>()
            var insideItem = false
            var title = ""
            var summary = ""
            var link = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && stories.size < MAX_STORIES) {
                when (event) {
                    XmlPullParser.START_TAG -> when {
                        parser.name.equals("item", ignoreCase = true) -> {
                            insideItem = true
                            title = ""
                            summary = ""
                            link = ""
                        }
                        insideItem && parser.name.equals("title", ignoreCase = true) -> title = parser.nextText()
                        insideItem && parser.name.equals("description", ignoreCase = true) -> summary = parser.nextText()
                        insideItem && parser.name.equals("link", ignoreCase = true) -> link = parser.nextText()
                    }
                    XmlPullParser.END_TAG -> if (insideItem && parser.name.equals("item", ignoreCase = true)) {
                        val cleanTitle = cleanText(title)
                        if (cleanTitle.isNotBlank() && link.startsWith("https://")) {
                            stories += MotivationStory(
                                title = cleanTitle.take(MAX_TITLE_CHARS),
                                summary = cleanText(summary).take(MAX_SUMMARY_CHARS),
                                link = link.trim(),
                            )
                        }
                        insideItem = false
                    }
                }
                event = parser.next()
            }
            if (stories.isNotEmpty()) {
                cachedStories = stories
                cachedAtMs = now
            }
            stories
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun chooseStory(stories: List<MotivationStory>, context: String): MotivationStory? {
        if (stories.isEmpty()) return null
        val keywords = tokens(context)
        if (keywords.isEmpty()) return stories.first()
        return stories.maxByOrNull { story ->
            val storyWords = tokens("${story.title} ${story.summary}")
            keywords.count { it in storyWords }
        }
    }

    private fun cleanText(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(text: String): Set<String> = text.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 && it !in STOP_WORDS }
        .toSet()

    private companion object {
        const val FEED_URL = "https://www.goodnewsnetwork.org/category/news/inspiring/feed/"
        const val CACHE_TTL_MS = 30 * 60 * 1000L
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 4_000
        const val MAX_STORIES = 40
        const val MAX_TITLE_CHARS = 160
        const val MAX_SUMMARY_CHARS = 240
        val STOP_WORDS = setOf(
            "about", "after", "again", "also", "because", "before", "being", "between", "could",
            "every", "from", "have", "into", "just", "more", "most", "over", "some", "such",
            "than", "that", "their", "them", "then", "there", "these", "they", "this", "those",
            "through", "under", "very", "what", "when", "where", "which", "while", "with", "your",
        )
    }
}
