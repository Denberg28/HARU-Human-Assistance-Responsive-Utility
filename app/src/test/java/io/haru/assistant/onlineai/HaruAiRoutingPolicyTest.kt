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
    fun explicitWebResearchEscalatesToAgent() {
        assertEquals(
            HaruAiRoute.ANTIGRAVITY_AGENT,
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
}
