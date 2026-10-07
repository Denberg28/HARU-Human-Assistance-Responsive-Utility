package io.haru.assistant.onlineai

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

// A single create call, bounded GET polling, and best-effort server cancellation.
// Retries never recreate an interaction.
internal class AntigravityRunner(
    private val create: suspend () -> JSONObject,
    private val read: suspend (String) -> JSONObject,
    private val cancel: suspend (String) -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val timeoutMs: Long = 120_000L,
) {
    suspend fun run(): JSONObject {
        var id: String? = null
        var terminal = false
        try {
            return withTimeout(timeoutMs) {
                var response = create()
                id = response.optString("id").takeIf { SAFE_ID.matches(it) }
                var pollDelay = 2_000L
                var polls = 0
                while (response.optString("status") == "in_progress") {
                    val interactionId = id ?: error("Agent did not return a valid interaction ID.")
                    check(polls < 15) { "Agent polling limit reached. HARU requested cancellation." }
                    pause(pollDelay)
                    pollDelay = minOf(10_000L, pollDelay * 2L)
                    response = read(interactionId)
                    polls += 1
                }
                terminal = response.optString("status") in setOf("completed", "failed", "cancelled", "incomplete")
                response
            }
        } catch (_: TimeoutCancellationException) {
            error("Agent time limit reached. HARU requested cancellation; shorten the task before trying again.")
        } finally {
            val interactionId = id
            if (!terminal && interactionId != null) {
                withContext(NonCancellable) {
                    try { withTimeout(5_000L) { cancel(interactionId) } }
                    catch (_: Exception) { /* No retry; the request may already be stopped. */ }
                }
            }
        }
    }

    companion object {
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,512}$")
    }
}
