package io.haru.assistant.content

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class AndroidNewsItem(
    val title: String,
    val link: String,
    val source: String,
    val published: String,
)

data class AndroidNewsBundle(
    val local: List<AndroidNewsItem> = emptyList(),
    val international: List<AndroidNewsItem> = emptyList(),
    val error: String = "",
)

class AndroidNewsService {
    suspend fun fetch(region: String = "Philippines"): AndroidNewsBundle =
        withContext(Dispatchers.IO) {
            var error = ""
            val local = runCatching {
                fetchRss(localUrl(region), 8)
            }.getOrElse {
                error = "Local news unavailable."
                emptyList()
            }

            val international = runCatching {
                fetchRss(
                    "https://news.google.com/rss/headlines/section/topic/WORLD?hl=en-PH&gl=PH&ceid=PH:en",
                    8,
                )
            }.getOrElse {
                error = listOf(error, "International news unavailable.")
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                emptyList()
            }

            AndroidNewsBundle(
                local = local,
                international = international,
                error = error,
            )
        }

    private fun localUrl(region: String): String {
        val clean = region.trim().ifBlank { "Philippines" }
        if (clean.equals("Philippines", ignoreCase = true)) {
            return "https://news.google.com/rss?hl=en-PH&gl=PH&ceid=PH:en"
        }
        val query = URLEncoder.encode(
            "\"$clean\" Philippines",
            StandardCharsets.UTF_8.toString(),
        )
        return "https://news.google.com/rss/search?q=$query&hl=en-PH&gl=PH&ceid=PH:en"
    }

    private fun fetchRss(url: String, limit: Int): List<AndroidNewsItem> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "HARU-Android/0.1")

        try {
            require(connection.responseCode in 200..299) {
                "News request failed with HTTP " + connection.responseCode + "."
            }

            val parser = Xml.newPullParser()
            parser.setInput(connection.inputStream, "UTF-8")

            val items = mutableListOf<AndroidNewsItem>()
            var event = parser.eventType
            var inItem = false
            var title = ""
            var link = ""
            var source = ""
            var published = ""

            while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "item" -> {
                                inItem = true
                                title = ""
                                link = ""
                                source = ""
                                published = ""
                            }
                            "title" -> if (inItem) title = parser.nextText().trim()
                            "link" -> if (inItem) link = parser.nextText().trim()
                            "source" -> if (inItem) source = parser.nextText().trim()
                            "pubdate" -> if (inItem) published = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("item", ignoreCase = true) && inItem) {
                            val cleanTitle: String
                            val cleanSource: String
                            if (source.isBlank() && " - " in title) {
                                val split = title.lastIndexOf(" - ")
                                cleanTitle = title.substring(0, split).trim()
                                cleanSource = title.substring(split + 3).trim()
                            } else {
                                cleanTitle = title.trim()
                                cleanSource = source.trim()
                            }

                            if (
                                cleanTitle.isNotBlank() &&
                                link.startsWith("https://")
                            ) {
                                items += AndroidNewsItem(
                                    title = cleanTitle.take(240),
                                    link = link,
                                    source = cleanSource.ifBlank { "News source" }.take(80),
                                    published = published.take(80),
                                )
                            }
                            inItem = false
                        }
                    }
                }
                event = parser.next()
            }

            return items.distinctBy {
                it.title.lowercase().replace(Regex("[^a-z0-9]"), "").take(100)
            }
        } finally {
            connection.disconnect()
        }
    }
}
