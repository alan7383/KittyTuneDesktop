import com.alananasss.kittytune.audio.TimeStretcher
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class TimeStretcherTest {
    companion object {
        fun render(scenario: String): String {
            val channels = if (scenario == "mono") 1 else 2
            val stretcher = TimeStretcher(44100, channels)
            val hash = MessageDigest.getInstance("SHA-256")
            val output = ShortArray(777)
            var total = 0L
            fun drain() {
                while (stretcher.available() > 0) {
                    val count = stretcher.drain(output, output.size)
                    total += count
                    for (i in 0 until count) {
                        hash.update(output[i].toByte())
                        hash.update((output[i].toInt() shr 8).toByte())
                    }
                }
            }
            var sample = 0
            repeat(48) { block ->
                when (scenario) {
                    "pitch" -> stretcher.setParameters(1.25f, 1.25f)
                    "stretch" -> stretcher.setParameters(1.25f, 1f)
                    "switch" -> when (block / 6 % 4) {
                        0 -> stretcher.setParameters(1f, 1f)
                        1 -> stretcher.setParameters(0.75f, 1f)
                        2 -> stretcher.setParameters(1.25f, 1.25f)
                        else -> stretcher.setParameters(1f, 1f)
                    }
                    "flush" -> {
                        stretcher.setParameters(if (block % 5 == 0) 1.25f else 1f, 1f)
                        if (block % 7 == 0) stretcher.flush()
                    }
                    "near-unity" -> stretcher.setParameters(1.0002f, 1.0001f)
                }
                val frames = if (scenario == "growth") 40000 else 1024 + block % 3
                val pcm = ShortArray(frames * channels + if (scenario == "partial-frame") 1 else 0) {
                    val n = sample++
                    when (n % 127) {
                        0 -> Short.MIN_VALUE
                        1 -> Short.MAX_VALUE
                        else -> (StrictMath.sin(n * 0.071) * 30000).toInt().toShort()
                    }
                }
                stretcher.queue(pcm, pcm.size)
                if (scenario != "wrap" || block % 3 == 2) drain()
                stretcher.queue(pcm, 0)
            }
            drain()
            return "$total:" + hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }

        @JvmStatic fun main(args: Array<String>) {
            for (scenario in listOf("unity", "mono", "pitch", "stretch", "switch", "flush", "growth", "wrap", "near-unity", "partial-frame"))
                println("$scenario=${render(scenario)}")
        }
    }

    @Test fun unityMatchesOriginalPcm() = assertEquals("98400:f8af54a26930317066b5b80e6e62d1503967340645d9e7ff11572349ad3771fb", render("unity"))
    @Test fun monoMatchesOriginalPcm() = assertEquals("49200:ce881b5d3b30e8d0d7770d5a167b70c049e535c747aab00a68985909908844d7", render("mono"))
    @Test fun pitchMatchesOriginalPcm() = assertEquals("78696:03901f111f2c8a82f0e7b7db358ff4c40af6169ae95eaa835a9dcf5381dd241f", render("pitch"))
    @Test fun stretchMatchesOriginalPcm() = assertEquals("75354:09150d3070a1e87fe25fe83e3101024f56b0c93c73e44735ae321ecc07a98cab", render("stretch"))
    @Test fun switchMatchesOriginalPcm() = assertEquals("96708:6833feba56d41eba2cd5d057c2aea8e6eff4abae3206cc4bb40d1978df087521", render("switch"))
    @Test fun flushMatchesOriginalPcm() = assertEquals("96348:a1dbaa72072e4c59ed3d447f40ad339364e0a704ec029d5db0f01620ffae4db7", render("flush"))
    @Test fun growthMatchesOriginalPcm() = assertEquals("3840000:c0396b071e9b793dfb5d8f50fe0091339b19f6040c018afe46bff3db61bbf4e4", render("growth"))
    @Test fun wrapMatchesOriginalPcm() = assertEquals("98400:f8af54a26930317066b5b80e6e62d1503967340645d9e7ff11572349ad3771fb", render("wrap"))
    @Test fun nearUnityMatchesOriginalPcm() = assertEquals("98400:f8af54a26930317066b5b80e6e62d1503967340645d9e7ff11572349ad3771fb", render("near-unity"))
    @Test fun partialFrameMatchesOriginalPcm() = assertEquals("98400:1cb6dc80974705051486fd06f1369a45762a16e1c47da2d6197e8770beed6fec", render("partial-frame"))

    @Test fun growingWrappedOutputPreservesUnreadSamples() {
        val stretcher = TimeStretcher(44100, 2)
        val input = ShortArray(48000) { (it * 173).toShort() }
        stretcher.queue(input, 24000)
        val prefix = ShortArray(16000)
        assertEquals(prefix.size, stretcher.drain(prefix, prefix.size))
        assertArrayEquals(input.copyOfRange(0, prefix.size), prefix)

        val wrapped = input.copyOfRange(24000, 40000)
        stretcher.queue(wrapped, wrapped.size)
        val tail = ShortArray(2000) { Short.MIN_VALUE }
        val extra = input.copyOfRange(40000, input.size) + tail
        stretcher.queue(extra, extra.size)

        val expected = input.copyOfRange(prefix.size, input.size) + tail
        val output = ShortArray(expected.size)
        assertEquals(expected.size, stretcher.drain(output, output.size))
        assertArrayEquals(expected, output)
        assertEquals(0, stretcher.available())
    }

    @Test fun neutralPlaybackAndFlushDoNotCreateStretchWorkspaces() {
        val stretcher = TimeStretcher(44100, 2)
        stretcher.flush()
        stretcher.queue(ShortArray(4096) { (it * 31).toShort() }, 4096)
        assertEquals(4096, stretcher.available())
        val out = ShortArray(4096)
        assertEquals(4096, stretcher.drain(out, out.size))
        assertArrayEquals(ShortArray(4096) { (it * 31).toShort() }, out)
        stretcher.flush()
        val initialized = stretcher.javaClass.declaredFields.mapNotNull { field ->
            val name = field.name.removeSuffix("\$delegate").removeSuffix("State")
            if (name !in setOf("input", "mid", "ramp", "kernel")) return@mapNotNull null
            field.isAccessible = true
            val value = field.get(stretcher)
            name.takeIf { value !is Lazy<*> || value.isInitialized() }
        }
        assertTrue("Allocated stretch workspaces: $initialized", initialized.isEmpty())
    }
}
