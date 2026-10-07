package io.haru.assistant.onlineai

enum class HaruAiRoute {
    FAST_CHAT,
    ANTIGRAVITY_AGENT,
}

object HaruAiRoutingPolicy {
    fun routeForAntigravity(prompt: String): HaruAiRoute =
        if (AGENT_PATTERNS.any { it.containsMatchIn(normalize(prompt)) }) {
            HaruAiRoute.ANTIGRAVITY_AGENT
        } else {
            HaruAiRoute.FAST_CHAT
        }

    fun needsWebSearch(prompt: String): Boolean {
        val value = normalize(prompt)
        return WEB_PATTERNS.any { it.containsMatchIn(value) } ||
            AGENT_PATTERNS.any { it.containsMatchIn(value) }
    }

    private fun normalize(prompt: String): String =
        prompt.lowercase().replace(Regex("""\s+"""), " ").trim()

    private val WEB_PATTERNS = listOf(
        Regex("""\b(latest|breaking|news|weather|forecast)\b"""),
        Regex("""\b(search (?:the web|online|for)|browse|google|look up|lookup|find online|check online|on the web|internet)\b"""),
        Regex("""\b(current|currently|today|tonight|recent|recently|live)\b.{0,50}\b(price|prices|stock|availability|score|scores|schedule|events|updates)\b"""),
        Regex("""\b(price|prices|stock|availability|score|scores|schedule|events|updates)\b.{0,50}\b(current|currently|today|tonight|recent|recently|live)\b"""),
    )

    private val AGENT_PATTERNS = listOf(
        Regex("""\b(deep research|research thoroughly|in-depth research|investigate|audit)\b"""),
        Regex("""\b(open|visit|read|inspect)\s+(?:this\s+)?(?:url|website|web page|webpage|link)\b"""),
        Regex("""\b(compare sources|verify sources|cross-check|cross check)\b"""),
    )
}
