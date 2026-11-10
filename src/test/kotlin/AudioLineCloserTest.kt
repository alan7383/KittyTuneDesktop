import com.alananasss.kittytune.audio.AudioLineCloser
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.SourceDataLine
import org.junit.Assert.*
import org.junit.Test

class AudioLineCloserTest {
    @Test
    fun releaseDrainsPendingClosesAndTerminatesWorker() {
        val closer = AudioLineCloser()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val secondClosed = CountDownLatch(1)
        val worker = AtomicReference<Thread>()
        try {
            closer.closeLater(line {
                worker.set(Thread.currentThread())
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS))
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            closer.closeLater(line { secondClosed.countDown() })
            closer.close()
            assertEquals(1L, secondClosed.count)
            unblock.countDown()
            assertTrue(secondClosed.await(5, TimeUnit.SECONDS))
            worker.get().join(2_000)
            assertFalse("Released engines must not leave a closing thread behind", worker.get().isAlive)
        } finally {
            unblock.countDown()
            closer.close()
        }
    }

    @Test
    fun oneFailedCloseDoesNotDiscardTheNextLine() {
        val closer = AudioLineCloser()
        val closed = CountDownLatch(1)
        try {
            closer.closeLater(line { throw IllegalStateException("Device disconnected") })
            closer.closeLater(line { closed.countDown() })
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        } finally {
            closer.close()
        }
    }

    private fun line(onClose: () -> Unit): SourceDataLine = Proxy.newProxyInstance(
        SourceDataLine::class.java.classLoader,
        arrayOf(SourceDataLine::class.java),
    ) { _, method, _ ->
        check(method.name == "close")
        onClose()
        null
    } as SourceDataLine
}
