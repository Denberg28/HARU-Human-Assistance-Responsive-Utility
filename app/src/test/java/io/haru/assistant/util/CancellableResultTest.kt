package io.haru.assistant.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CancellableResultTest {
    @Test fun lifecycleCancellationIsNotReportedAsNetworkFailure() = runBlocking {
        try { cancellableResult<Unit> { throw CancellationException("Stopped") }; fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
        assertTrue(cancellableResult<Unit> { throw IllegalStateException("Offline") }.isFailure)
    }
}
