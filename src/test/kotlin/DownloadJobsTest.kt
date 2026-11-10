import com.alananasss.kittytune.data.DownloadJobs
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DownloadJobsTest {
    @Test
    fun oldCancellationCannotUnregisterItsReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jobs = DownloadJobs(scope)
        val releaseCleanup = CompletableDeferred<Unit>()
        try {
            val old = jobs.launch(42) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { releaseCleanup.await() }
                }
            }
            jobs.cancel(42)
            val replacement = jobs.launch(42) { awaitCancellation() }
            releaseCleanup.complete(Unit)
            old.join()
            assertTrue("The replacement must remain visible and cancellable", jobs.contains(42))
            jobs.cancel(42)
            replacement.join()
            assertTrue(replacement.isCancelled)
            assertFalse(jobs.contains(42))
        } finally {
            releaseCleanup.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun immediateCompletionCannotLeaveAStaleEntry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DownloadJobs(scope)
            val job = jobs.launch(42) {}
            assertTrue(job.isCompleted)
            assertFalse(jobs.contains(42))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failureDoesNotPreventRetry() {
        val handler = CoroutineExceptionHandler { _, _ -> }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
        try {
            val jobs = DownloadJobs(scope)
            assertTrue(jobs.launch(42) { error("Storage unavailable") }.isCancelled)
            assertFalse(jobs.contains(42))
            assertTrue(jobs.launch(42) {}.isCompleted)
            assertFalse(jobs.contains(42))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelledScopeCannotLeaveAStaleEntry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.cancel()
        val jobs = DownloadJobs(scope)
        assertTrue(jobs.launch(42) {}.isCancelled)
        assertFalse(jobs.contains(42))
    }
}
