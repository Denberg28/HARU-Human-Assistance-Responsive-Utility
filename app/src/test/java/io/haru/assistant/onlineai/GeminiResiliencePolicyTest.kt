package io.haru.assistant.onlineai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResiliencePolicyTest {
    @Test
    fun retriesOnlyTransientServerErrors() {
        assertTrue(
            GeminiResiliencePolicy.shouldFallback(
                statusCode = 500,
                primaryModelId = "gemini-3.8-flash",
                fallbackModelId = "gemini-3.5-flash-lite",
            )
        )
        assertTrue(
            GeminiResiliencePolicy.shouldFallback(
                statusCode = 503,
                primaryModelId = "gemini-3.8-flash",
                fallbackModelId = "gemini-3.5-flash-lite",
            )
        )
        assertFalse(
            GeminiResiliencePolicy.shouldFallback(
                statusCode = 429,
                primaryModelId = "gemini-3.8-flash",
                fallbackModelId = "gemini-3.5-flash-lite",
            )
        )
        assertFalse(
            GeminiResiliencePolicy.shouldFallback(
                statusCode = 403,
                primaryModelId = "gemini-3.8-flash",
                fallbackModelId = "gemini-3.5-flash-lite",
            )
        )
    }

    @Test
    fun doesNotRetryWhenAlreadyOnFallbackModel() {
        assertFalse(
            GeminiResiliencePolicy.shouldFallback(
                statusCode = 503,
                primaryModelId = "gemini-3.5-flash-lite",
                fallbackModelId = "gemini-3.5-flash-lite",
            )
        )
    }
}
