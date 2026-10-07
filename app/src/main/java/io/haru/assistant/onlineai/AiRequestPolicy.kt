package io.haru.assistant.onlineai

import io.haru.assistant.memory.ConversationExchange
import io.haru.assistant.memory.ConversationMemoryPolicy
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

internal object AiRequestPolicy {
    const val HISTORY_CHARS = 6_000
    const val SUMMARY_CHARS = 1_200

    fun boundedHistory(history: List<ConversationExchange>): List<ConversationExchange> {
        var remaining = HISTORY_CHARS
        return history.takeLast(3).asReversed().mapNotNull { exchange ->
            if (remaining < 2) return@mapNotNull null
            val user = ConversationMemoryPolicy.sanitizeUser(exchange.user).take(minOf(1_000, remaining / 2))
            val assistant = ConversationMemoryPolicy.sanitizeAssistant(exchange.assistant).take(minOf(2_000, remaining - user.length))
            if (user.isBlank() || assistant.isBlank()) return@mapNotNull null
            remaining -= user.length + assistant.length
            ConversationExchange(user, assistant)
        }.asReversed()
    }

    fun thinkingLevel(modelId: String): String? = when (modelId) {
        "gemini-3.7-flash", "gemini-3.8-flash" -> "low"
        "gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash" -> "minimal"
        else -> null // Future and legacy models keep their documented defaults.
    }

    // Inspect structured metadata only; provider text may echo credentials or prompts.
    fun quotaCooldownMs(raw: String, retryAfter: String?, nowMs: Long): Long {
        val error = runCatching { JSONObject(raw).optJSONObject("error") }.getOrNull()
        val details = error?.optJSONArray("details")
        var seconds = retryAfter?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        if (seconds == 0.0 && !retryAfter.isNullOrBlank()) {
            seconds = runCatching {
                (ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMs) / 1000.0
            }.getOrDefault(0.0).coerceAtLeast(0.0)
        }
        var daily = false
        for (i in 0 until (details?.length() ?: 0)) {
            val detail = details?.optJSONObject(i) ?: continue
            if (detail.optString("@type").endsWith("google.rpc.RetryInfo")) {
                val delay = detail.optString("retryDelay").removeSuffix("s").toDoubleOrNull()
                if (delay != null && delay.isFinite()) seconds = maxOf(seconds, delay)
            }
            val violations = detail.optJSONArray("violations")
            for (j in 0 until (violations?.length() ?: 0)) {
                val violation = violations?.optJSONObject(j) ?: continue
                val metric = violation.optString("quotaId") + violation.optString("quotaMetric")
                daily = daily || metric.contains("PerDay", true) || metric.contains("per_day", true)
            }
        }
        if (daily) {
            val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of("America/Los_Angeles"))
            return maxOf(60_000L, now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli() - nowMs)
        }
        return (maxOf(60.0, seconds).coerceAtMost(86_400.0) * 1000).toLong()
    }

    fun waitMessage(scope: String, remainingMs: Long): String {
        val provider = if (scope == "gemini") "Gemini/Antigravity" else "Groq"
        val wait = ceil(remainingMs / 1000.0).toLong()
        return "$provider quota or rate limit reached. Wait ${wait}s before another request." +
            if (scope == "gemini") " Check project limits in Google AI Studio; other apps and keys share this project's quota." else ""
    }
}

internal class AiRequestGate(private val elapsedMs: () -> Long) {
    private val mutex = Mutex()
    private val lastStarts = mutableMapOf<String, Long>()

    suspend fun <T> run(scope: String, block: suspend () -> T): T {
        check(mutex.tryLock()) { "An AI request is already running. Wait for it to finish." }
        try {
            val now = elapsedMs()
            val last = lastStarts[scope]
            check(last == null || now - last >= 3_000L) {
                "Please wait 3 seconds between AI requests or connection tests."
            }
            lastStarts[scope] = now
            return block()
        } finally { mutex.unlock() }
    }
}

internal data class AiHttpResponse(val code: Int, val body: String, val retryAfter: String? = null)

internal class AiRequestStats {
    @Volatile var generations = 0
    @Volatile var polls = 0
    @Volatile var searches = 0
    @Volatile var tokens = 0L
    @Volatile var quotaErrors = 0
    @Volatile var cancellations = 0
    fun description(): String =
        "This app session: $generations generation request(s), $polls poll(s), $searches reported search query(s), $tokens reported tokens, $quotaErrors quota error(s), $cancellations cancel request(s)."
    fun recordUsage(response: JSONObject) {
        val usage = response.optJSONObject("usageMetadata") ?: response.optJSONObject("usage")
        tokens += (usage?.optLong("totalTokenCount", usage.optLong("total_tokens", 0L)) ?: 0L).coerceAtLeast(0L)
        val queries = response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("groundingMetadata")?.optJSONArray("webSearchQueries")
        searches += queries?.length() ?: 0
    }
}
