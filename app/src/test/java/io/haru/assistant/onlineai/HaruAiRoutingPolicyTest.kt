package io.haru.assistant.onlineai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruAiRoutingPolicyTest {
    @Test
    fun normalConversationUsesFastChat() {
        assertEquals(
            HaruAiRoute.FAST_CHAT,
            HaruAiRoutingPolicy.routeForAntigravity(
                "Help me think through my rover steering problem."
            ),
        )
    }

    @Test
    fun currentInformationUsesFastChatWithSearch() {
        assertEquals(
            HaruAiRoute.FAST_CHAT,
            HaruAiRoutingPolicy.routeForAntigravity(
                "What is the latest news about this project?"
            ),
        )
    }

    @Test
    fun sourceComparisonUsesOneDirectRequest() {
        assertEquals(
            HaruAiRoute.FAST_CHAT,
            HaruAiRoutingPolicy.routeForAntigravity(
                "Search the web and compare sources for me."
            ),
        )
    }

    @Test
    fun ordinaryTechnicalQuestionDoesNotEnableSearch() {
        assertFalse(
            HaruAiRoutingPolicy.needsWebSearch(
                "Explain dissymmetry of lift in a helicopter."
            )
        )
    }

    @Test
    fun livePriceQuestionEnablesSearch() {
        assertTrue(
            HaruAiRoutingPolicy.needsWebSearch(
                "Check the current price of this product."
            )
        )
    }
    @Test fun basicWebLookupsDoNotStartAnAgent() {
        assertEquals(HaruAiRoute.FAST_CHAT, HaruAiRoutingPolicy.routeForAntigravity("Search the web for today's news"))
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("Search the web for today's news"))
    }

    @Test fun ordinaryTechnicalAndTaskLanguageDoesNotTriggerSearch() {
        assertFalse(HaruAiRoutingPolicy.needsWebSearch("Explain binary search and electric current"))
        assertFalse(HaruAiRoutingPolicy.needsWebSearch("Help plan my tasks today"))
    }

    @Test fun deepResearchHasWebTools() {
        assertEquals(HaruAiRoute.ANTIGRAVITY_AGENT, HaruAiRoutingPolicy.routeForAntigravity("Deep research this topic"))
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("Deep research this topic"))
    }

    @Test fun naturalNewsQuestionsAndFilipinoRequestsEnableSearch() {
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("What's happening to the Saudi fuel line?"))
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("What happened in Saudi Arabia?"))
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("Ano ang balita sa Saudi ngayon?"))
    }

    @Test fun followUpsKeepLiveSearchButNewTopicsDoNot() {
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("Tell me more", "Latest Saudi fuel news"))
        assertTrue(HaruAiRoutingPolicy.needsWebSearch("And why?", listOf("Latest Saudi fuel news", "Tell me more")))
        assertFalse(HaruAiRoutingPolicy.needsWebSearch("Explain rotor lift", listOf("Latest Saudi news")))
        assertFalse(HaruAiRoutingPolicy.needsWebSearch("Tell me more", listOf("Latest Saudi news", "Explain rotor lift")))
        assertFalse(HaruAiRoutingPolicy.needsWebSearch("Tell me more", "Explain electric current"))
    }
    @Test fun queryCoverageAvoidsToolsForSuppliedReasoningAndConcepts() {
        listOf("Audit this Kotlin code: fun add(a: Int, b: Int) = a+b", "Investigate why my rover oscillates", "Explain weather forecasting", "Define price elasticity", "Translate this paragraph to Filipino", "Solve x + 2 = 5").forEach {
            assertEquals(it, HaruAiRoute.FAST_CHAT, HaruAiRoutingPolicy.routeForAntigravity(it))
            assertFalse(it, HaruAiRoutingPolicy.needsWebSearch(it))
        }
        listOf("What time is flight 5J 325 today?", "What is the weather in Nabua?", "Who is the president of the Philippines?", "Check CAAP regulations and PCAR", "Latest Saudi news", "Explain current weather in Nabua", "Explain news about Saudi fuel").forEach {
            assertTrue(it, HaruAiRoutingPolicy.needsWebSearch(it))
        }
        assertTrue(HaruAiRoutingPolicy.needsUrlContext("Summarize https://example.com/article"))
        assertFalse(HaruAiRoutingPolicy.needsWebSearch("Summarize https://example.com/article"))
    }

    @Test fun budgetsScaleWithQuestionNeedsWithoutChangingTestsOrAgentLimits() {
        assertEquals(256, AiRequestPolicy.outputBudget("Reply with exactly: HARU OK"))
        assertEquals(640, AiRequestPolicy.outputBudget("Translate hello to Filipino"))
        assertEquals(1200, AiRequestPolicy.outputBudget("Latest news"))
        assertEquals(2400, AiRequestPolicy.outputBudget("Derive the equation step by step"))
        assertEquals("low", AiRequestPolicy.thinkingLevel("gemini-3.5-flash-lite", "Solve this equation"))
        assertEquals("minimal", AiRequestPolicy.thinkingLevel("gemini-3.5-flash-lite", "Hello"))
    }

}
