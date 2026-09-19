package io.haru.assistant.localai

/**
 * HARU local-model policy.
 *
 * HARU Android uses GGUF + llama.cpp so the phone model can be downloaded,
 * resumed, integrity checked, and loaded fully on-device.
 */
object LocalAiModelPolicy {
    const val DEFAULT_MODEL_ID = "unsloth/Qwen3.5-2B-GGUF"
    const val LIGHTWEIGHT_MODEL_ID = DEFAULT_MODEL_ID

    val supportedModels = listOf(
        LocalAiModel(
            id = DEFAULT_MODEL_ID,
            displayName = "Qwen3.5 2B Q4_K_M",
            tier = LocalAiTier.RECOMMENDED,
            multimodal = false,
        ),
    )

    fun recommendedForLowMemoryDevice(): LocalAiModel = supportedModels.first()

    fun recommendedDefault(): LocalAiModel = supportedModels.first()
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
