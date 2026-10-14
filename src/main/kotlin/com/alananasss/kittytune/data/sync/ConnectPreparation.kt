package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicBoolean

/** Wait for player callbacks, not a repeating 100 ms task; always unregister on completion/cancellation. */
internal suspend fun awaitConnectPreparation(
    ready: () -> Boolean,
    failure: () -> Throwable?,
    observe: (() -> Unit) -> (() -> Unit),
    timeoutMs: Long = 20_000L,
) {
    var removeObserver: (() -> Unit)? = null
    try {
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val finished = AtomicBoolean()
                val changed = {
                    if (continuation.isActive) {
                        val error = failure()
                        if (error != null && finished.compareAndSet(false, true))
                            continuation.resumeWithException(IllegalStateException("Unable to prepare this track", error))
                        else if (error == null && ready() && finished.compareAndSet(false, true)) continuation.resume(Unit)
                    }
                }
                removeObserver = observe(changed)
                // Covers both an already prepared player and readiness changing during registration.
                changed()
            }
        }
    } finally { removeObserver?.invoke() }
}
