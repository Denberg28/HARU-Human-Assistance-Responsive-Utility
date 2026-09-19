package io.haru.assistant.localai

/**
 * HARU local-model policy.
 *
 * The native inference engine is implemented separately. This file defines
 * the model identities and the default selection policy shared by the app.
 */
object LocalAiModelPolicy {
    const val DEFAULT_MODEL_ID = "google/gemma-4-E4B-it"
    const val LIGHTWEIGHT_MODEL_ID = "google/gemma-4-E2B-it"

    val supportedModels = listOf(
        LocalAiModel(
            id = DEFAULT_MODEL_ID,
            displayName = "Gemma 4 E4B Instruct",
            tier = LocalAiTier.RECOMMENDED,
            multimodal = true,
        ),
        LocalAiModel(
            id = LIGHTWEIGHT_MODEL_ID,
            displayName = "Gemma 4 E2B Instruct",
            tier = LocalAiTier.LIGHTWEIGHT,
            multimodal = true,
        ),
        LocalAiModel(
            id = "Qwen/Qwen3-4B",
            displayName = "Qwen3 4B",
            tier = LocalAiTier.ALTERNATIVE,
            multimodal = false,
        ),
        LocalAiModel(
            id = "microsoft/Phi-4-mini-instruct",
            displayName = "Phi-4 Mini",
            tier = LocalAiTier.ALTERNATIVE,
            multimodal = false,
        ),
    )

    fun recommendedForLowMemoryDevice(): LocalAiModel =
        supportedModels.first { it.id == LIGHTWEIGHT_MODEL_ID }

    fun recommendedDefault(): LocalAiModel =
        supportedModels.first { it.id == DEFAULT_MODEL_ID }
}

enum class LocalAiTier {
    RECOMMENDED,
    LIGHTWEIGHT,
    ALTERNATIVE,
}

data class LocalAiModel(
    val id: String,
    val displayName: String,
    val tier: LocalAiTier,
    val multimodal: Boolean,
)
