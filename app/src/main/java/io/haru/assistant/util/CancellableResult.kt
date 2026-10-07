package io.haru.assistant.util

import kotlinx.coroutines.CancellationException

/** Network failures are results; lifecycle cancellation must still stop work. */
internal suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
