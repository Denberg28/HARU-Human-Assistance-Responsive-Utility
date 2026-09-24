package io.haru.assistant.onlineai

enum class HaruAiRoute {
    FAST_CHAT,
    ANTIGRAVITY_AGENT,
}

object HaruAiRoutingPolicy {
    fun routeForAntigravity(prompt: String): HaruAiRoute =
        if (needsAgent(prompt)) {
            HaruAiRoute.ANTIGRAVITY_AGENT
        } else {
            HaruAiRoute.FAST_CHAT
        }

    fun needsWebSearch(prompt: String): Boolean {
        val value = normalize(prompt)

        return WEB_PATTERNS.any { it.containsMatchIn(value) }
    }

    private fun needsAgent(prompt: String): Boolean {
        val value = normalize(prompt)

        if (needsWebSearch(value)) return true

        return AGENT_PATTERNS.any { it.containsMatchIn(value) }
    }

    private fun normalize(prompt: String): String =
        prompt
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val WEB_PATTERNS =
        listOf(
            Regex("""\b(latest|current|currently|today|tonight|breaking|news|recent|recently)\b"""),
            Regex("""\b(search|browse|google|look up|lookup|find online|check online|on the web|internet)\b"""),
            Regex("""\b(price|prices|stock|availability|weather|forecast|score|scores|schedule)\b"""),
        )

    private val AGENT_PATTERNS =
        listOf(
            Regex("""\b(research|deep research|investigate|audit)\b"""),
            Regex("""\b(open|visit|read|inspect)\s+(?:this\s+)?(?:url|website|web page|webpage|link)\b"""),
            Regex("""\b(compare sources|verify sources|cross-check|cross check)\b"""),
        )
}
