package io.haru.assistant.onlineai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class LiveSearchPolicyTest {
    @Test fun sourceLinksRejectNonHttpsCredentialsAndKeepUniqueProviderSources() {
        val candidate = JSONObject("""{"groundingMetadata":{"groundingChunks":[
            {"web":{"uri":"javascript:alert(1)","title":"Bad"}},
            {"web":{"uri":"http://example.com","title":"Insecure"}},
            {"web":{"uri":"https://user:pass@example.com","title":"Credentials"}},
            {"web":{"uri":"https://example.com/news","title":"Publisher"}},
            {"web":{"uri":"https://example.com/news","title":"Duplicate"}}
        ]}}""")
        assertEquals(listOf(WebSource("Publisher", "https://example.com/news")), LiveSearchPolicy.sources(candidate))
        assertTrue(LiveSearchPolicy.sources(JSONObject()).isEmpty())
    }

    @Test fun deviceDateIncludesLocalOffsetAndZone() {
        val instruction = LiveSearchPolicy.instruction(ZonedDateTime.parse("2026-10-08T04:58:00+08:00[Asia/Manila]"))
        assertTrue(instruction.contains("2026-10-08T04:58:00+08:00"))
        assertTrue(instruction.contains("Asia/Manila"))
    }

    @Test fun agentCitationsAndSuggestionsAreExtractedWithoutTrustingModelTextUrls() {
        val response = JSONObject("""{"output_text":"https://invented.example","steps":[
          {"type":"google_search_result","result":[{"search_suggestions":"<div>Search</div>"}]},
          {"type":"model_output","content":[{"type":"text","text":"Summary","annotations":[
            {"type":"url_citation","url":"https://example.com/news","title":"Publisher"}]}]}
        ]}""")
        val grounding = LiveSearchPolicy.interactionGrounding(response)
        assertEquals("https://example.com/news", LiveSearchPolicy.sources(grounding).single().url)
        assertEquals("<div>Search</div>", LiveSearchPolicy.suggestions(grounding))
    }
}
