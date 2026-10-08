package io.haru.assistant.onlineai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class WebSource(val title: String, val url: String)

internal object LiveSearchPolicy {
    const val NO_SOURCES = "The AI provider responded, but did not return verifiable live sources for this question. HARU could not verify current information. This is a search-result issue; it does not mean your phone is offline. No automatic retry was sent."

    fun instruction(now: ZonedDateTime = ZonedDateTime.now()): String =
        "Current device date and time: ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)} (${now.zone}). " +
            "Google Search is enabled for this request. Search for up-to-date sources before answering. " +
            "Treat previous assistant replies and conversation memory as context, not verified current facts. " +
            "If a previous reply said you lacked internet access, use the search tool now. " +
            "Distinguish publication dates from event dates. Do not invent news or sources. " +
            "If search returns no relevant evidence, say current information could not be verified, without diagnosing the user's internet connection."

    fun safeUrl(value: String): Boolean {
        val url = value.takeIf { it.length <= 4096 }?.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.username.isEmpty() && url.password.isEmpty()
    }

    fun sources(candidate: JSONObject): List<WebSource> {
        val chunks = candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        return buildList {
            for (i in 0 until (chunks?.length() ?: 0)) {
                val web = chunks?.optJSONObject(i)?.optJSONObject("web") ?: continue
                val url = web.optString("uri")
                if (!safeUrl(url)) continue
                val title = web.optString("title").replace(Regex("""[\p{Cntrl}]"""), " ").trim().take(200)
                add(WebSource(title.ifBlank { url.toHttpUrlOrNull()!!.host }, url))
            }
        }.distinctBy { it.url }.take(10)
    }

    fun suggestions(candidate: JSONObject): String = candidate.optJSONObject("groundingMetadata")
        ?.optJSONObject("searchEntryPoint")?.optString("renderedContent").orEmpty().take(65_536)

    fun urlSources(candidate: JSONObject): List<WebSource> {
        val urls = candidate.optJSONObject("urlContextMetadata")?.optJSONArray("urlMetadata")
        return buildList {
            for (i in 0 until (urls?.length() ?: 0)) {
                val item = urls?.optJSONObject(i) ?: continue
                val url = item.optString("retrievedUrl")
                if (item.optString("urlRetrievalStatus") == "URL_RETRIEVAL_STATUS_SUCCESS" && safeUrl(url)) {
                    add(WebSource(url.toHttpUrlOrNull()!!.host, url))
                }
            }
        }.distinctBy { it.url }.take(10)
    }

    fun interactionGrounding(response: JSONObject): JSONObject {
        val chunks = org.json.JSONArray()
        val steps = response.optJSONArray("steps")
        var suggestions = ""
        for (i in 0 until (steps?.length() ?: 0)) {
            val step = steps?.optJSONObject(i) ?: continue
            if (step.optString("type") == "model_output") {
                val content = step.optJSONArray("content")
                for (j in 0 until (content?.length() ?: 0)) {
                    val annotations = content?.optJSONObject(j)?.optJSONArray("annotations")
                    for (k in 0 until (annotations?.length() ?: 0)) {
                        val annotation = annotations?.optJSONObject(k) ?: continue
                        if (annotation.optString("type") == "url_citation") {
                            chunks.put(JSONObject().put("web", JSONObject()
                                .put("uri", annotation.optString("url"))
                                .put("title", annotation.optString("title"))))
                        }
                    }
                }
            }
            if (step.optString("type") == "google_search_result") {
                val result = step.optJSONArray("result")
                for (j in 0 until (result?.length() ?: 0)) {
                    val html = result?.optJSONObject(j)?.optString("search_suggestions").orEmpty()
                    if (html.isNotBlank()) suggestions = html
                }
            }
        }
        return JSONObject().put("groundingMetadata", JSONObject().put("groundingChunks", chunks)
            .put("searchEntryPoint", JSONObject().put("renderedContent", suggestions)))
    }
}
