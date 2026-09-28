package io.haru.assistant.onlineai

internal object GeminiResiliencePolicy {
    fun shouldFallback(
        statusCode: Int,
        primaryModelId: String,
        fallbackModelId: String,
    ): Boolean =
        statusCode in 500..599 &&
            primaryModelId != fallbackModelId
}
