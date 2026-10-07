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

    fun needsWebSearch(prompt: String, previousPrompt: String = ""): Boolean {
        val value = normalize(prompt)
        return WEB_PATTERNS.any { it.containsMatchIn(value) } ||
            AGENT_PATTERNS.any { it.containsMatchIn(value) } ||
            (FOLLOW_UP.containsMatchIn(value) && previousPrompt.isNotBlank() && needsWebSearch(previousPrompt))
    }

    fun needsWebSearch(prompt: String, previousPrompts: List<String>): Boolean {
        if (needsWebSearch(prompt)) return true
        if (!FOLLOW_UP.containsMatchIn(normalize(prompt))) return false
        val topic = previousPrompts.takeLast(3).asReversed()
            .firstOrNull { !FOLLOW_UP.containsMatchIn(normalize(it)) } ?: return false
        return needsWebSearch(topic)
    }

    private fun normalize(prompt: String): String =
        prompt.lowercase().replace(Regex("""\s+"""), " ").trim()

    private val WEB_PATTERNS = listOf(
        Regex("""\b(latest|breaking|news|weather|forecast|price|prices|availability|exchange rate)\b"""),
        Regex("""\b(search (?:the web|online|for)|browse|google|look up|lookup|find online|check online|on the web|internet)\b"""),
        Regex("""\b(current|currently|today|tonight|recent|recently|live)\b.{0,50}\b(price|prices|stock|availability|score|scores|schedule|events|updates)\b"""),
        Regex("""\b(price|prices|stock|availability|score|scores|schedule|events|updates)\b.{0,50}\b(current|currently|today|tonight|recent|recently|live)\b"""),
        Regex("""\b(what(?:'s| is) (?:happening|going on)|what happened|any updates?|situation (?:in|at)|status of)\b"""),
        Regex("""\b(sino|ano|anong|kumusta|kamusta)\b.{0,60}\b(balita|panahon|presyo|nangyari|nangyayari|ngayon|pinakabago)\b"""),
    )

    private val FOLLOW_UP = Regex("""^(?:and\b|what about\b|how about\b|tell me more\b|more details\b|why\??$|when\??$|where\??$|continue\??$|check again\b|try again\b|search again\b|update me\b|is (?:that|it)\b|what (?:happened|caused)\b)""")

    private val AGENT_PATTERNS = listOf(
        Regex("""\b(deep research|research thoroughly|in-depth research|investigate|audit)\b"""),
        Regex("""\b(open|visit|read|inspect)\s+(?:this\s+)?(?:url|website|web page|webpage|link)\b"""),
        Regex("""\b(compare sources|verify sources|cross-check|cross check)\b"""),
    )
}
