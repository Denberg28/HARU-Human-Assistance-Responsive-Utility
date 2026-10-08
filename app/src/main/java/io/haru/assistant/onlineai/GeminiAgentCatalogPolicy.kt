package io.haru.assistant.onlineai

internal object GeminiAgentCatalogPolicy {
    private val safeModelId =
        Regex("^gemini-[A-Za-z0-9._-]{1,80}$")

    fun newestSupportedAgents(
        models: List<GeminiModel>,
    ): List<GeminiModel> {
        val eligible =
            models
                .filter { isEligibleAgentId(it.id) }
                .distinctBy { it.id }

        val newestFlashLite =
            eligible
                .filter { isFlashLite(it.id) }
                .maxByOrNull { versionScore(it.id) }

        val newestFlash =
            eligible
                .filter {
                    isFlash(it.id) &&
                        !isFlashLite(it.id)
                }
                .maxByOrNull { versionScore(it.id) }

        return listOfNotNull(
            newestFlash,
            newestFlashLite,
            eligible.firstOrNull { it.id == "gemini-2.5-flash-lite" },
        ).distinctBy { it.id }
    }

    fun isEligibleAgentId(id: String): Boolean {
        if (!safeModelId.matches(id)) return false

        val value = id.lowercase()
        if (!value.startsWith("gemini-")) return false
        if (
            value.contains("preview") ||
            value.contains("experimental") ||
            value.contains("-exp") ||
            value.contains("deprecated") ||
            value.contains("legacy") ||
            value.contains("embedding") ||
            value.contains("image") ||
            value.contains("veo") ||
            value.contains("tts") ||
            value.contains("audio") ||
            value.contains("robotics") ||
            value.contains("computer-use")
        ) {
            return false
        }

        return isFlash(value) || isFlashLite(value)
    }

    private fun isFlashLite(id: String): Boolean =
        id.lowercase().contains("flash-lite")

    private fun isFlash(id: String): Boolean {
        val value = id.lowercase()
        return value.contains("flash") &&
            !value.contains("flash-thinking") &&
            !value.contains("flash-image")
    }

    private fun versionScore(id: String): Long =
        Regex("\\d+")
            .findAll(id)
            .mapNotNull { it.value.toLongOrNull() }
            .take(3)
            .fold(0L) { acc, value ->
                acc * 1000L + value.coerceAtMost(999L)
            }
}
