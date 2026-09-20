package io.haru.assistant.memory

data class ConversationExchange(
    val user: String,
    val assistant: String,
)

object ConversationMemoryPolicy {
    const val MAX_EXCHANGES = 10
    const val MAX_USER_CHARS = 8_000
    const val MAX_ASSISTANT_CHARS = 12_000
    const val MAX_TOTAL_CHARS = 48_000

    fun sanitizeUser(value: String): String =
        sanitize(value, MAX_USER_CHARS)

    fun sanitizeAssistant(value: String): String =
        sanitize(value, MAX_ASSISTANT_CHARS)

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
    private val maxExchanges: Int = ConversationMemoryPolicy.MAX_EXCHANGES,
    private val maxTotalChars: Int = ConversationMemoryPolicy.MAX_TOTAL_CHARS,
) {
    private val exchanges = mutableListOf<ConversationExchange>()

    init {
        initial.forEach { exchange ->
            append(exchange.user, exchange.assistant)
        }
    }

    fun snapshot(): List<ConversationExchange> =
        exchanges.toList()

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
            exchanges.removeAt(0)
        }

        while (
            exchanges.size > 1 &&
            totalChars() > maxTotalChars
        ) {
            exchanges.removeAt(0)
        }

        return snapshot()
    }

    fun clear() {
        exchanges.clear()
    }

    private fun totalChars(): Int =
        exchanges.sumOf {
            it.user.length + it.assistant.length
        }
}
