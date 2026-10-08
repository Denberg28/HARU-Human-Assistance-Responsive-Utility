package io.haru.assistant.onlineai

enum class AiAnswerMode(val label: String) {
    AUTO("Auto · live lookup"),
    KNOWLEDGE("General knowledge"),
}

enum class HaruAiRoute { FAST_CHAT, ANTIGRAVITY_AGENT }

object HaruAiRoutingPolicy {
    fun routeForAntigravity(prompt: String): HaruAiRoute =
        if (AGENT.containsMatchIn(normalize(prompt))) HaruAiRoute.ANTIGRAVITY_AGENT else HaruAiRoute.FAST_CHAT

    fun needsUrlContext(prompt: String): Boolean =
        URL.containsMatchIn(prompt) && URL_ACTION.containsMatchIn(normalize(prompt))

    fun needsReasoning(prompt: String): Boolean = REASONING.containsMatchIn(normalize(prompt)) || prompt.length > 450

    fun needsWebSearch(prompt: String, previousPrompt: String = ""): Boolean {
        val value = normalize(prompt)
        if (KNOWLEDGE_ONLY.containsMatchIn(value)) return false
        if (AGENT.containsMatchIn(value) || EXPLICIT_SEARCH.containsMatchIn(value)) return true
        if (TEXT_TASK.containsMatchIn(value)) return false
        if (FRESH.containsMatchIn(value) || SENSITIVE.containsMatchIn(value)) return true
        if (needsUrlContext(prompt)) return false // Fetch supplied pages; avoid a separate search.
        if (EDUCATIONAL.containsMatchIn(value)) return false
        return WEB.any { it.containsMatchIn(value) } ||
            (isFollowUp(prompt) && previousPrompt.isNotBlank() && needsWebSearch(previousPrompt))
    }

    fun needsWebSearch(prompt: String, previousPrompts: List<String>): Boolean {
        if (knowledgeOnly(prompt)) return false
        if (needsWebSearch(prompt)) return true
        if (!isFollowUp(prompt)) return false
        val topic = previousPrompts.takeLast(3).asReversed().firstOrNull { !isFollowUp(it) } ?: return false
        return needsWebSearch(topic)
    }

    fun needsLiveVerification(prompt: String, previousPrompts: List<String> = emptyList()): Boolean =
        needsWebSearch(KNOWLEDGE_ONLY.replace(normalize(prompt), ""), previousPrompts.map { KNOWLEDGE_ONLY.replace(normalize(it), "") })

    fun knowledgeOnly(prompt: String): Boolean = KNOWLEDGE_ONLY.containsMatchIn(normalize(prompt))

    fun isFollowUp(prompt: String): Boolean = FOLLOW_UP.containsMatchIn(normalize(prompt))
    private fun normalize(prompt: String) = prompt.lowercase().replace(Regex("""\s+"""), " ").trim()
    private val KNOWLEDGE_ONLY = Regex("""\b(without (?:web )?search|no (?:web )?search|(?:use|from) (?:only )?your (?:own )?knowledge|general knowledge only)\b""")
    private val TEXT_TASK = Regex("""^(?:translate|rewrite|rephrase|proofread|correct (?:the )?grammar|write (?:a |an )?fictional|create (?:a |an )?fictional)\b""")
    private val AGENT = Regex("""\b(deep research|research thoroughly|in-depth research)\b""")
    private val URL = Regex("""https://[^\s<>]+""", RegexOption.IGNORE_CASE)
    private val URL_ACTION = Regex("""\b(read|summarize|summarise|summary|open|visit|inspect|compare|review this (?:article|page|link))\b""")
    private val REASONING = Regex("""\b(solve|derive|calculate|prove|debug|audit|investigate|analy[sz]e|design|code|equation|step.by.step|detailed|thorough|long|essay|complete|full|compare)\b""")
    private val EXPLICIT_SEARCH = Regex("""\b(search (?:the web|online|for)|browse|google|look up|lookup|find online|check online|on the web|internet|compare sources|verify sources|cross.check)\b""")
    private val FRESH = Regex("""\b(latest|breaking|currently|right now|tonight|recent|recently|live|ngayon|pinakabago)\b|\b(current|today)\b.{0,60}\b(news|weather|forecast|price|prices|stock|availability|score|scores|schedule|events|updates|president|ceo|law|regulations)\b|\b(news|weather|forecast|price|prices|stock|availability|score|scores|schedule|events|updates|president|ceo|law|regulations)\b.{0,60}\b(current|today)\b""")
    private val SENSITIVE = Regex("""\b(dosage|contraindications|drug interactions|medical advice|legal advice|tax law|investment advice|caap regulations|pcar)\b""")
    private val EDUCATIONAL = Regex("""^(?:(?:explain|define|describe|teach)(?: me)? (?:the )?(?:basics|concept|principles|weather forecasting|price elasticity|exchange rates?|electric current|news literacy)|what (?:is|are) (?:a |an |the )?(?:news|weather|forecasting|price elasticity|exchange rate|electric current)[?.!]*$|how (?:does|do) (?:weather|forecasting|pricing))\b""")
    private val WEB = listOf(
        Regex("""\bwhat time\b.{0,60}\b(flight|train|bus|event|meeting)\b"""),
        Regex("""\b(news|weather|forecast|price|prices|availability|exchange rate)\b"""),
        Regex("""\b(what(?:'s| is) (?:happening|going on)|what happened|any updates?|situation (?:in|at)|status of|who is (?:the )?(?:president|ceo|prime minister))\b"""),
        Regex("""\b(sino|ano|anong|kumusta|kamusta)\b.{0,60}\b(balita|panahon|presyo|nangyari|nangyayari)\b"""),
    )
    private val FOLLOW_UP = Regex("""^(?:and\b|what about\b|how about\b|tell me more\b|more details\b|why\??$|when\??$|where\??$|continue\??$|check again\b|try again\b|search again\b|update me\b|is (?:that|it)\b|what (?:happened|caused)\b)""")
}
