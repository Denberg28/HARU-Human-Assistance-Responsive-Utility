package io.haru.assistant.memory

data class ConversationExchange(
    val user: String,
    val assistant: String,
)

data class AntigravitySession(
    val interactionId: String,
    val environmentId: String,
    val updatedAtMs: Long,
) {
    fun isFresh(
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean =
        interactionId.isNotBlank() &&
            environmentId.isNotBlank() &&
            updatedAtMs > 0L &&
            nowMs >= updatedAtMs &&
            nowMs - updatedAtMs <= ConversationMemoryPolicy.ANTIGRAVITY_SESSION_MAX_AGE_MS
}

data class ConversationMemoryState(
    val exchanges: List<ConversationExchange> = emptyList(),
    val summary: String = "",
    val antigravitySession: AntigravitySession? = null,
)

object ConversationMemoryPolicy {
    const val MAX_EXCHANGES = 10
    const val PROVIDER_RECENT_EXCHANGES = 5
    const val MAX_USER_CHARS = 8_000
    const val MAX_ASSISTANT_CHARS = 12_000
    const val MAX_TOTAL_CHARS = 48_000
    const val MAX_SUMMARY_CHARS = 3_200
    const val MAX_SUMMARY_USER_CHARS = 220
    const val MAX_SUMMARY_ASSISTANT_CHARS = 320
    const val ANTIGRAVITY_SESSION_MAX_AGE_MS = 20L * 60L * 60L * 1000L

    fun sanitizeUser(value: String): String =
        sanitize(value, MAX_USER_CHARS)

    fun sanitizeAssistant(value: String): String =
        sanitize(value, MAX_ASSISTANT_CHARS)

    fun sanitizeSummary(value: String): String =
        value
            .replace("\u0000", "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .takeLast(MAX_SUMMARY_CHARS)

    private fun sanitize(
        value: String,
        maxChars: Int,
    ): String =
        value
            .replace("\u0000", "")
            .trim()
            .take(maxChars)
}

class ConversationMemory(
    initial: List<ConversationExchange> = emptyList(),
    initialSummary: String = "",
    private val maxExchanges: Int = ConversationMemoryPolicy.MAX_EXCHANGES,
    private val maxTotalChars: Int = ConversationMemoryPolicy.MAX_TOTAL_CHARS,
    private val maxSummaryChars: Int = ConversationMemoryPolicy.MAX_SUMMARY_CHARS,
) {
    private val exchanges = mutableListOf<ConversationExchange>()
    private var rollingSummary =
        ConversationMemoryPolicy.sanitizeSummary(initialSummary)
            .takeLast(maxSummaryChars)

    init {
        initial.forEach { exchange ->
            append(exchange.user, exchange.assistant)
        }
    }

    fun snapshot(): List<ConversationExchange> =
        exchanges.toList()

    fun summary(): String =
        rollingSummary

    fun state(
        antigravitySession: AntigravitySession? = null,
    ): ConversationMemoryState =
        ConversationMemoryState(
            exchanges = snapshot(),
            summary = summary(),
            antigravitySession = antigravitySession,
        )

    fun append(
        user: String,
        assistant: String,
    ): List<ConversationExchange> {
        val cleanUser =
            ConversationMemoryPolicy.sanitizeUser(user)
        val cleanAssistant =
            ConversationMemoryPolicy.sanitizeAssistant(assistant)

        if (cleanUser.isBlank() || cleanAssistant.isBlank()) {
            return snapshot()
        }

        exchanges +=
            ConversationExchange(
                user = cleanUser,
                assistant = cleanAssistant,
            )

        while (exchanges.size > maxExchanges) {
            absorbOldestExchange()
        }

        while (
            exchanges.size > 1 &&
            totalChars() > maxTotalChars
        ) {
            absorbOldestExchange()
        }

        return snapshot()
    }

    fun clear() {
        exchanges.clear()
        rollingSummary = ""
    }

    private fun absorbOldestExchange() {
        if (exchanges.isEmpty()) return

        val removed = exchanges.removeAt(0)
        val userLine =
            compact(
                removed.user,
                ConversationMemoryPolicy.MAX_SUMMARY_USER_CHARS,
            )
        val assistantLine =
            compact(
                removed.assistant,
                ConversationMemoryPolicy.MAX_SUMMARY_ASSISTANT_CHARS,
            )

        val entry =
            "User: $userLine\nHARU: $assistantLine"

        rollingSummary =
            buildString {
                if (rollingSummary.isNotBlank()) {
                    append(rollingSummary)
                    append("\n")
                }
                append(entry)
            }
                .takeLast(maxSummaryChars)
                .trim()
    }

    private fun compact(
        value: String,
        maxChars: Int,
    ): String =
        value
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { clean ->
                if (clean.length <= maxChars) {
                    clean
                } else {
                    clean.take(maxChars - 1).trimEnd() + "…"
                }
            }

    private fun totalChars(): Int =
        exchanges.sumOf {
            it.user.length + it.assistant.length
        }
}
