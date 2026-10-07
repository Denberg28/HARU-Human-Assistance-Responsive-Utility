package io.haru.assistant.onlineai

import android.content.Context
import io.haru.assistant.memory.ConversationExchange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AiUsageTest {
    private lateinit var context: Context
    private var clock = 10_000L
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun manager(transport: suspend (Request, Int, Int) -> AiHttpResponse) =
        AndroidOnlineAiManager(context, transport, { "synthetic-test-key" }, { clock })
    private fun success() = AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Answer"}]},"groundingMetadata":{"webSearchQueries":["news"]}}],"usageMetadata":{"totalTokenCount":12}}""")
    private fun payload(request: Request): JSONObject {
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test fun simpleNewsUsesOneGroundedGenerationAndBoundedMemory() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertTrue(request.url.encodedPath.endsWith(":generateContent"))
            val data = payload(request)
            assertTrue(data.getJSONArray("tools").getJSONObject(0).has("google_search"))
            val contents = data.getJSONArray("contents")
            var historyChars = 0
            for (i in 0 until contents.length() - 1) {
                historyChars += contents.getJSONObject(i).getJSONArray("parts").getJSONObject(0).getString("text").length
            }
            assertTrue(historyChars <= 6_000)
            assertTrue(data.getJSONObject("generationConfig").getInt("maxOutputTokens") <= 1_200)
            success()
        }
        val reply = manager.ask(OnlineProvider.ANTIGRAVITY, "Search the web for latest news", "HARU",
            List(10) { ConversationExchange("u".repeat(8_000), "a".repeat(12_000)) }, "s".repeat(3_200))
        assertEquals("Answer", reply.text)
        assertNull(reply.antigravitySession)
        assertEquals(1, calls)
        assertTrue(manager.usageDiagnostics().contains("12 reported tokens"))
    }

    @Test fun quotaStopsBothGooglePathsAndSurvivesManagerRecreation() = runBlocking {
        var calls = 0
        val transport: suspend (Request, Int, Int) -> AiHttpResponse = { _, _, _ ->
            calls++
            AiHttpResponse(429, """{"error":{"message":"synthetic-test-key private prompt","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"90s"}]}}""")
        }
        val first = manager(transport)
        try { first.testFastGemini(); fail("Expected quota") }
        catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("90s"))
            assertFalse(e.message!!.contains("synthetic-test-key"))
            assertFalse(e.message!!.contains("private prompt"))
        }
        clock += 4_000L
        try { manager(transport).testAntigravityAgent(); fail("Expected shared cooldown") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("quota")) }
        assertEquals(1, calls)
    }

    @Test fun concurrentCallsAreRejectedInsteadOfQueued() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val manager = manager { _, _, _ -> calls++; entered.complete(Unit); finish.await(); success() }
        val first = async { manager.testFastGemini() }
        entered.await()
        try { manager.testAntigravityAgent(); fail("Expected single-flight gate") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("already running")) }
        finish.complete(Unit)
        first.await()
        assertEquals(1, calls)
    }

    @Test fun serverFallbackIsOnceAndKeepsGrounding() = runBlocking {
        context.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE).edit()
            .putString("gemini_catalog", """[{"id":"gemini-3.8-flash","label":"Flash"},{"id":"gemini-3.5-flash-lite","label":"Lite"}]""")
            .putString("gemini_model", "gemini-3.8-flash").commit()
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertTrue(payload(request).has("tools"))
            if (calls == 1) AiHttpResponse(503, "{}") else {
                assertTrue(request.url.encodedPath.contains("gemini-3.5-flash-lite"))
                success()
            }
        }
        manager.ask(OnlineProvider.GEMINI, "Latest news", "HARU")
        assertEquals(2, calls)
    }

    @Test fun connectionTestsHaveSmallOutputBudgetAndNoSearch() = runBlocking {
        val manager = manager { request, _, _ ->
            val data = payload(request)
            assertEquals(32, data.getJSONObject("generationConfig").getInt("maxOutputTokens"))
            assertFalse(data.has("tools"))
            success()
        }
        manager.testFastGemini()
    }

    @Test fun dailyQuotaUsesPacificResetAndRetryMetadataIsBounded() {
        val now = Instant.parse("2026-10-07T23:00:00Z").toEpochMilli()
        val raw = """{"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        assertEquals(8L * 3_600_000L, AiRequestPolicy.quotaCooldownMs(raw, null, now))
        assertEquals(60_000L, AiRequestPolicy.quotaCooldownMs("bad json", "NaN", now))
        assertEquals(120_000L, AiRequestPolicy.quotaCooldownMs("{}", "120", now))
        assertEquals(86_400_000L, AiRequestPolicy.quotaCooldownMs("{}", "9999999", now))
    }
}
