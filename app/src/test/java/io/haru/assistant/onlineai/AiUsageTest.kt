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
    private fun success() = AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Answer"}]},"groundingMetadata":{"webSearchQueries":["news"],"groundingChunks":[{"web":{"uri":"https://example.com/news","title":"Example News"}}],"searchEntryPoint":{"renderedContent":"<div>Search suggestions</div>"}}}],"usageMetadata":{"totalTokenCount":12}}""")
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
        assertEquals("https://example.com/news", reply.webSources.single().url)
        assertTrue(reply.searchSuggestionsHtml.contains("Search suggestions"))
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
            val level = payload(request).getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel")
            assertEquals(if (calls == 1) "low" else "minimal", level)
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
            assertEquals(256, data.getJSONObject("generationConfig").getInt("maxOutputTokens"))
            assertFalse(data.has("tools"))
            assertEquals("minimal", data.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
            success()
        }
        manager.testFastGemini()
        Unit
    }

    @Test fun agentBudgetFailureDoesNotCreateAnotherTask() = runBlocking {
        var creates = 0
        val manager = manager { request, _, _ ->
            creates++
            assertTrue(request.url.encodedPath.endsWith("/interactions"))
            val body = payload(request)
            assertTrue(body.getBoolean("background"))
            assertFalse(body.has("previous_interaction_id"))
            assertEquals(4096, body.getJSONObject("agent_config").getInt("max_total_tokens"))
            assertEquals(2, body.getJSONArray("tools").length())
            AiHttpResponse(200, """{"id":"v1_test","status":"incomplete","output_text":"Partial"}""")
        }
        try { manager.ask(OnlineProvider.ANTIGRAVITY, "Deep research this topic", "HARU"); fail("Expected budget failure") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("token budget")) }
        assertEquals(1, creates)
    }

    @Test fun rapidSequentialTestTapsAreBlocked() = runBlocking {
        var calls = 0
        val manager = manager { _, _, _ -> calls++; success() }
        manager.testFastGemini()
        try { manager.testFastGemini(); fail("Expected spacing gate") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("3 seconds")) }
        assertEquals(1, calls)
    }

    @Test fun emptyOutputAtTokenLimitIsNotReportedAsQuota() = runBlocking {
        val manager = manager { _, _, _ -> AiHttpResponse(200, """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[]}}]}""") }
        try { manager.testFastGemini(); fail("Expected response budget failure") }
        catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("response budget"))
            assertFalse(e.message!!.contains("quota"))
        }
    }

    @Test fun malformedProviderJsonCannotEchoKeyOrPromptInError() = runBlocking {
        val manager = manager { _, _, _ -> AiHttpResponse(200, "synthetic-test-key private prompt") }
        try { manager.testFastGemini(); fail("Expected invalid JSON error") }
        catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("unreadable JSON"))
            assertFalse(e.message!!.contains("synthetic-test-key"))
            assertFalse(e.message!!.contains("private prompt"))
        }
    }

    @Test fun dailyQuotaUsesPacificResetAndRetryMetadataIsBounded() {
        val now = Instant.parse("2026-10-07T23:00:00Z").toEpochMilli()
        val raw = """{"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        assertEquals(8L * 3_600_000L, AiRequestPolicy.quotaCooldownMs(raw, null, now))
        assertEquals(60_000L, AiRequestPolicy.quotaCooldownMs("bad json", "NaN", now))
        assertEquals(120_000L, AiRequestPolicy.quotaCooldownMs("{}", "120", now))
        assertEquals(86_400_000L, AiRequestPolicy.quotaCooldownMs("{}", "9999999", now))
    }

    @Test fun newsFollowUpProvidesSearchAndDateAfterOldAccessDisclaimer() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            val data = payload(request)
            assertTrue(data.getJSONArray("tools").getJSONObject(0).has("google_search"))
            val instruction = data.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
            assertTrue(instruction.contains("Current device date and time:"))
            assertTrue(instruction.contains("use the search tool now"))
            success()
        }
        manager.ask(OnlineProvider.ANTIGRAVITY, "Tell me more", "HARU", listOf(
            ConversationExchange("Latest Saudi fuel news", "I do not have real-time internet access")))
        assertEquals(1, calls)
    }

    @Test fun ungroundedResponseIsShownWithWarningExcludedFromMemoryAndNotRetried() = runBlocking {
        var calls = 0
        val manager = manager { _, _, _ ->
            calls++
            AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Useful background"}]}}]}""")
        }
        val reply = manager.ask(OnlineProvider.GEMINI, "Saudi news", "HARU")
        assertTrue(reply.text.startsWith(LiveSearchPolicy.UNVERIFIED))
        assertTrue(reply.text.contains("Useful background"))
        assertFalse(reply.memoryText.contains("Useful background"))
        assertFalse(reply.rememberAnswer)
        assertTrue(reply.webSources.isEmpty())
        assertEquals(1, calls)
    }

    @Test fun groqLiveNewsUsesOneGoogleRequestAndSharesGoogleCooldown() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertEquals("generativelanguage.googleapis.com", request.url.host)
            assertTrue(payload(request).has("tools"))
            AiHttpResponse(429, "{}")
        }
        try { manager.ask(OnlineProvider.GROQ, "Latest news", "HARU"); fail("Expected quota") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Gemini/Antigravity")) }
        clock += 4_000L
        try { manager.testFastGemini(); fail("Expected same Google cooldown") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("quota")) }
        assertEquals(1, calls)
    }

    @Test fun ordinaryGroqChatKeepsGroqWithoutSearch() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertEquals("api.groq.com", request.url.host)
            assertFalse(payload(request).has("tools"))
            AiHttpResponse(200, """{"choices":[{"message":{"content":"Hello"}}]}""")
        }
        assertEquals("Hello", manager.ask(OnlineProvider.GROQ, "Hello", "HARU").text)
        assertEquals(1, calls)
    }

    @Test fun groqWithoutGeminiStillAnswersGeneralPartsWithOneGroqRequest() = runBlocking {
        var calls = 0
        val manager = AndroidOnlineAiManager(context, { request, _, _ ->
            calls++
            assertEquals("api.groq.com", request.url.host)
            assertTrue(payload(request).getJSONArray("messages").toString().contains("Live retrieval is unavailable"))
            AiHttpResponse(200, """{"choices":[{"message":{"content":"Background explanation"}}]}""")
        }, { name -> if (name == "groq") "synthetic-test-key" else "" }, { clock })
        val reply = manager.ask(OnlineProvider.GROQ, "Latest news", "HARU")
        assertTrue(reply.text.startsWith(LiveSearchPolicy.KNOWLEDGE_NOTICE))
        assertTrue(reply.text.contains("Background explanation"))
        assertFalse(reply.rememberAnswer)
        assertFalse(reply.usedGeminiSearch)
        assertEquals(1, calls)
    }

    @Test fun knowledgeModePersistsSkipsSearchAndAgentAndKeepsStableMemory() = runBlocking {
        var calls = 0
        val transport: suspend (Request, Int, Int) -> AiHttpResponse = { request, _, _ ->
            calls++
            assertTrue(request.url.encodedPath.endsWith(":generateContent"))
            assertFalse(payload(request).has("tools"))
            AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Useful answer"}]}}]}""")
        }
        manager(transport).saveAnswerMode(AiAnswerMode.KNOWLEDGE)
        val manager = manager(transport)
        assertEquals(AiAnswerMode.KNOWLEDGE, manager.settings().answerMode)
        val current = manager.ask(OnlineProvider.ANTIGRAVITY, "Deep research latest news", "HARU")
        assertTrue(current.text.startsWith(LiveSearchPolicy.KNOWLEDGE_NOTICE))
        assertFalse(current.rememberAnswer)
        clock += 4000
        val stable = manager.ask(OnlineProvider.GEMINI, "Give me a pancake recipe", "HARU")
        assertEquals("Useful answer", stable.text)
        assertTrue(stable.rememberAnswer)
        assertTrue(stable.webSources.isEmpty())
        assertEquals(2, calls)
    }

    @Test fun missingSourcesKeepsFollowUpTopicWithoutSavingUnverifiedAssertions() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertTrue(payload(request).has("tools"))
            if (calls == 1) AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Unverified claim"}]}}]}""")
            else {
                assertFalse(payload(request).toString().contains("Unverified claim"))
                success()
            }
        }
        val first = manager.ask(OnlineProvider.GEMINI, "Latest Saudi news", "HARU")
        clock += 4000
        val next = manager.ask(OnlineProvider.GEMINI, "Tell me more", "HARU",
            listOf(ConversationExchange("Latest Saudi news", first.memoryText)))
        assertTrue(next.rememberAnswer)
        assertEquals("Answer", next.memoryText)
        assertEquals(1, next.webSources.size)
        assertEquals(2, calls)
    }

    @Test fun explicitKnowledgeRequestDisablesToolsAndMarksChangingFacts() = runBlocking {
        val manager = manager { request, _, _ ->
            assertFalse(payload(request).has("tools"))
            AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Background"}]}}]}""")
        }
        val reply = manager.ask(OnlineProvider.ANTIGRAVITY, "Latest news without search", "HARU")
        assertTrue(reply.text.startsWith(LiveSearchPolicy.KNOWLEDGE_NOTICE))
        assertFalse(reply.rememberAnswer)
    }

    @Test fun agentWithoutSourcesKeepsUsefulResponseWithoutPretendingItIsVerified() = runBlocking {
        var calls = 0
        val manager = manager { _, _, _ ->
            calls++
            AiHttpResponse(200, """{"id":"v1_test","status":"completed","output_text":"Useful background"}""")
        }
        val reply = manager.ask(OnlineProvider.ANTIGRAVITY, "Deep research news", "HARU")
        assertTrue(reply.text.startsWith(LiveSearchPolicy.UNVERIFIED))
        assertFalse(reply.rememberAnswer)
        assertEquals(1, calls)
    }

    @Test fun codeReviewUsesOneDirectReasoningRequestWithoutWebOrAgent() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertTrue(request.url.encodedPath.endsWith(":generateContent"))
            val data = payload(request)
            assertFalse(data.has("tools"))
            val config = data.getJSONObject("generationConfig")
            assertEquals(2400, config.getInt("maxOutputTokens"))
            assertEquals("low", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
            success()
        }
        manager.ask(OnlineProvider.ANTIGRAVITY, "Audit this code for bugs: fun add(a: Int, b: Int) = a - b", "HARU")
        assertEquals(1, calls)
    }

    @Test fun groqRoutineAndReasoningBudgetsDifferWithoutHiddenReasoning() = runBlocking {
        val manager = manager { request, _, _ ->
            val data = payload(request)
            assertEquals("hidden", data.getString("reasoning_format"))
            val complex = data.getJSONArray("messages").toString().contains("Solve")
            assertEquals(if (complex) "low" else "none", data.getString("reasoning_effort"))
            assertEquals(if (complex) 2400 else 640, data.getInt("max_completion_tokens"))
            AiHttpResponse(200, """{"choices":[{"message":{"content":"Answer"}}]}""")
        }
        manager.ask(OnlineProvider.GROQ, "Translate hello to French", "HARU")
        clock += 4000
        manager.ask(OnlineProvider.GROQ, "Solve x squared minus 4 equals zero", "HARU")
        Unit
    }

    @Test fun suppliedPageUsesUrlContextOnlyAndVerifiedRetrieval() = runBlocking {
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            val tools = payload(request).getJSONArray("tools")
            assertEquals(1, tools.length())
            assertTrue(tools.getJSONObject(0).has("url_context"))
            AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Summary"}]},"urlContextMetadata":{"urlMetadata":[{"retrievedUrl":"https://example.com/article","urlRetrievalStatus":"URL_RETRIEVAL_STATUS_SUCCESS"}]}}]}""")
        }
        val reply = manager.ask(OnlineProvider.GROQ, "Summarize https://example.com/article", "HARU")
        assertEquals("https://example.com/article", reply.webSources.single().url)
        assertTrue(reply.usedGeminiSearch)
        assertEquals(1, calls)
    }

    @Test fun inaccessiblePageFailsOnceWithoutInventedSourceOrRetry() = runBlocking {
        var calls = 0
        val manager = manager { _, _, _ ->
            calls++
            AiHttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Invented summary https://example.com/article"}]},"urlContextMetadata":{"urlMetadata":[{"retrievedUrl":"https://example.com/article","urlRetrievalStatus":"URL_RETRIEVAL_STATUS_ERROR"}]}}]}""")
        }
        try { manager.ask(OnlineProvider.GEMINI, "Summarize https://example.com/article", "HARU"); fail("Expected retrieval failure") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Paste")) }
        assertEquals(1, calls)
    }

    @Test fun listedEconomySearchModelIsUsedWithoutPaidTierFallback() = runBlocking {
        context.getSharedPreferences("haru_online_ai", Context.MODE_PRIVATE).edit()
            .putString("gemini_catalog", """[{"id":"gemini-3.8-flash","label":"Flash"},{"id":"gemini-3.5-flash-lite","label":"Lite"},{"id":"gemini-2.5-flash-lite","label":"Economy"}]""")
            .putString("gemini_model", "gemini-3.8-flash").commit()
        var calls = 0
        val manager = manager { request, _, _ ->
            calls++
            assertTrue(request.url.encodedPath.contains("gemini-2.5-flash-lite"))
            assertFalse(payload(request).getJSONObject("generationConfig").has("thinkingConfig"))
            AiHttpResponse(503, "{}")
        }
        try { manager.ask(OnlineProvider.GEMINI, "Latest news", "HARU"); fail("Expected provider error") }
        catch (_: IllegalStateException) { }
        assertEquals(1, calls)
    }

}
