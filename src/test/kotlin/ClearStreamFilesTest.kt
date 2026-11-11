import com.alananasss.kittytune.audio.ClearStreamFiles
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ClearStreamFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val audio = ByteArray(70 * 1024) { 42 }

    @Test fun concurrentReadersReuseOneCompleteDownload() = runBlocking {
        val files = ClearStreamFiles { temporary.root }
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var writes = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            files.ensure("same") { partial ->
                writes++
                entered.complete(Unit)
                finish.await()
                partial.writeBytes(audio)
            }
        }
        entered.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            files.ensure("same") { partial -> writes++; partial.writeBytes(audio) }
        }
        assertFalse(second.isCompleted)
        assertNull(files.cached("same"))
        finish.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, writes)
        assertArrayEquals(audio, first.await()!!.readBytes())
    }

    @Test fun cancelledWriterDoesNotLeaveAPartialOrBlockRetry() = runBlocking {
        val files = ClearStreamFiles { temporary.root }
        val entered = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            files.ensure("same") { partial ->
                partial.writeBytes(audio)
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        first.cancelAndJoin()
        assertNull(files.cached("same"))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
        assertNotNull(files.ensure("same") { it.writeBytes(audio) })
    }

    @Test fun failedWriterCanBeRetried() = runBlocking {
        val files = ClearStreamFiles { temporary.root }
        try {
            files.ensure("same") { it.writeBytes(audio); throw IOException("offline") }
            fail("download should fail")
        } catch (_: IOException) { }
        assertNull(files.cached("same"))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
        assertNotNull(files.ensure("same") { it.writeBytes(audio) })
    }

    @Test fun tinyOutputIsNotPublished() = runBlocking {
        val files = ClearStreamFiles { temporary.root }
        assertNull(files.ensure("same") { it.writeText("incomplete") })
        assertNull(files.cached("same"))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
}
