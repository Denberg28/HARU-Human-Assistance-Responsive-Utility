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
    fun currentInformationEscalatesToAgent() {
        assertEquals(
            HaruAiRoute.ANTIGRAVITY_AGENT,
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
}
