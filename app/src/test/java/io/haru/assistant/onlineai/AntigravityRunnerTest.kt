package io.haru.assistant.onlineai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AntigravityRunnerTest {
    @Test fun pollingReadsOriginalInteractionWithoutRecreation() = runBlocking {
        var creates = 0
        var reads = 0
        var cancels = 0
        val pauses = mutableListOf<Long>()
        val runner = AntigravityRunner(
            create = { creates++; JSONObject("""{"id":"v1_test","status":"in_progress"}""") },
            read = { id -> assertEquals("v1_test", id); reads++
                JSONObject("""{"id":"v1_test","status":"__STATUS__"}""".replace("__STATUS__", if (reads == 2) "completed" else "in_progress")) },
            cancel = { cancels++ },
            pause = { pauses += it },
        )
        assertEquals("completed", runner.run().getString("status"))
        assertEquals(1, creates)
        assertEquals(2, reads)
        assertEquals(listOf(2_000L, 4_000L), pauses)
        assertEquals(0, cancels)
    }

    @Test fun localCancellationRequestsOneServerCancel() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var cancels = 0
        val runner = AntigravityRunner(
            create = { JSONObject("""{"id":"v1_test","status":"in_progress"}""") },
            read = { error("Must cancel before read") },
            cancel = { assertEquals("v1_test", it); cancels++ },
            pause = { entered.complete(Unit); awaitCancellation() },
        )
        val job = async { runner.run() }
        entered.await()
        job.cancel()
        try { job.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(1, cancels)
    }

    @Test fun pollingLimitCancelsInsteadOfStartingAnotherAgent() = runBlocking {
        var reads = 0
        var cancels = 0
        val runner = AntigravityRunner(
            create = { JSONObject("""{"id":"v1_test","status":"in_progress"}""") },
            read = { reads++; JSONObject("""{"status":"in_progress"}""") },
            cancel = { cancels++ }, pause = {},
        )
        try { runner.run(); fail("Expected bounded polling") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("polling limit")) }
        assertEquals(15, reads)
        assertEquals(1, cancels)
    }
}
