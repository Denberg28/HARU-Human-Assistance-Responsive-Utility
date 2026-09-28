package io.haru.assistant.onlineai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAgentCatalogTest {
    @Test
    fun keepsOnlyNewestStableFlashAndFlashLite() {
        val models =
            listOf(
                GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
                GeminiModel("gemini-3.6-flash", "Gemini 3.6 Flash"),
                GeminiModel("gemini-3.8-flash", "Gemini 3.8 Flash"),
                GeminiModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
                GeminiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
                GeminiModel("gemini-3-flash-preview", "Gemini 3 Flash Preview"),
                GeminiModel("gemini-3.8-flash-tts", "Gemini 3.8 Flash TTS"),
            )

        assertEquals(
            listOf(
                "gemini-3.8-flash",
                "gemini-3.5-flash-lite",
            ),
            GeminiAgentCatalogPolicy
                .newestSupportedAgents(models)
                .map { it.id },
        )
    }

    @Test
    fun rejectsObsoleteAndSpecializedVariants() {
        assertFalse(
            GeminiAgentCatalogPolicy.isEligibleAgentId(
                "gemini-3-flash-preview"
            )
        )
        assertFalse(
            GeminiAgentCatalogPolicy.isEligibleAgentId(
                "gemini-3.8-flash-tts"
            )
        )
        assertFalse(
            GeminiAgentCatalogPolicy.isEligibleAgentId(
                "gemini-3.8-flash-image"
            )
        )
        assertTrue(
            GeminiAgentCatalogPolicy.isEligibleAgentId(
                "gemini-3.8-flash"
            )
        )
        assertTrue(
            GeminiAgentCatalogPolicy.isEligibleAgentId(
                "gemini-3.5-flash-lite"
            )
        )
    }
}
