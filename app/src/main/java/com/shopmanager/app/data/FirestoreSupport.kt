package com.shopmanager.app.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val QUEUED_WRITE_WAIT_MS = 4_000L
private const val RETRY_STEP_MS = 2_000L
private const val RETRY_MAX_MS = 30_000L

suspend fun <T> Task<T>.awaitQueued(waitMs: Long = QUEUED_WRITE_WAIT_MS) {
    try {
        withTimeout(waitMs) { await() }
    } catch (_: TimeoutCancellationException) {
    }
}

fun <T> Flow<T>.retryOnError(onError: (Throwable) -> Unit): Flow<T> =
    retryWhen { cause, attempt ->
        if (cause is CancellationException) {
            false
        } else {
            onError(cause)
            delay(minOf(RETRY_MAX_MS, RETRY_STEP_MS * (attempt + 1)))
            true
        }
    }
