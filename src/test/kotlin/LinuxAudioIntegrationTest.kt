import com.alananasss.kittytune.audio.LinuxAudioIntegration
import com.alananasss.kittytune.core.LinuxAppIdHelper
import com.alananasss.kittytune.util.LinuxAudioManager
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import javax.sound.sampled.AudioFormat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LinuxAudioIntegrationTest {
    @Test
    fun `routing selects streams by exact PID rather than application name`() {
        val input = """[
            {"index":1,"properties":{"application.process.id":"42","application.name":"KittyTune"}},
            {"index":2,"properties":{"application.process.id":"43","application.name":"KittyTune"}},
            {"index":3,"properties":{"application.name":"KittyTune"}},
            {"index":4,"properties":{"application.process.id":42,"application.name":"Java"}}
        ]"""
        assertEquals(listOf("1", "4"), LinuxAudioIntegration.ownStreamIds(input, "42"))
    }

    @Test
    fun `native audio environment identifies KittyTune on both Linux sound servers`() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        LinuxAudioIntegration.initialize()
        val props = LinuxAudioIntegration.nativeEnvironment("PIPEWIRE_ALSA").orEmpty()
        assertTrue(props.contains("application.name=KittyTune"))
        assertTrue(props.contains("application.icon-name=${LinuxAppIdHelper.APP_ID}"))
        assertEquals("KittyTune", LinuxAudioIntegration.nativeEnvironment("PULSE_PROP_application.name"))
        assertEquals(LinuxAppIdHelper.APP_ID, LinuxAudioIntegration.nativeEnvironment("PULSE_PROP_application.icon_name"))
    }

    @Test
    fun `real output carries identity and routes only this app without changing default sink`() {
        assumeTrue("Set KITTYTUNE_LINUX_AUDIO_TEST=1 for the live silent-output check",
            System.getenv("KITTYTUNE_LINUX_AUDIO_TEST") == "1")
        val originalDefault = assertNotNull(LinuxAudioManager.getDefaultSinkId())
        val modules = mutableListOf<String>()
        fun run(vararg args: String) = assertNotNull(LinuxAudioIntegration.command(*args), args.joinToString(" "))
        fun inputs(): JsonArray = JsonParser.parseString(run("pactl", "--format=json", "list", "sink-inputs")).asJsonArray
        fun own() = inputs().filter { it.asJsonObject.getAsJsonObject("properties")
            ?.get("application.process.id")?.asString == ProcessHandle.current().pid().toString() }
        try {
            val names = listOf("kittytune_test_a_${ProcessHandle.current().pid()}", "kittytune_test_b_${ProcessHandle.current().pid()}")
            for (name in names) modules += run("pactl", "load-module", "module-null-sink", "sink_name=$name",
                "sink_properties=\"device.description=KittyTuneTest priority.session=0\"").trim()
            val baseline = assertNotNull(LinuxAudioManager.getDefaultSinkId())
            val foreign = inputs().associate { it.asJsonObject.get("index").asString to it.asJsonObject.get("sink").asString }
            val sinks = JsonParser.parseString(run("pactl", "--format=json", "list", "sinks")).asJsonArray
                .associate { it.asJsonObject.get("name").asString to it.asJsonObject.get("index").asString }
            for (destination in names + "") {
                LinuxAudioIntegration.openLine(AudioFormat(48_000f, 16, 2, true, false), 19_200, destination).use { line ->
                    line.write(ByteArray(4096), 0, 4096) // Silence; explicit devices are virtual test sinks.
                    repeat(10) { if (own().isEmpty()) Thread.sleep(50) }
                    val streams = own()
                    assertTrue(streams.isNotEmpty(), "Audio server did not expose the test process")
                    for (item in streams) {
                        val stream = item.asJsonObject
                        val props = stream.getAsJsonObject("properties")
                        assertEquals("KittyTune", props.get("application.name")?.asString)
                        assertEquals(LinuxAppIdHelper.APP_ID,
                            (props.get("application.icon_name") ?: props.get("application.icon-name"))?.asString)
                        assertEquals(sinks[destination.ifEmpty { baseline }], stream.get("sink").asString)
                    }
                    assertEquals(baseline, LinuxAudioManager.getDefaultSinkId(), "The app changed the system default")
                    for (item in inputs()) {
                        val stream = item.asJsonObject
                        foreign[stream.get("index").asString]?.let { previous ->
                            assertEquals(previous, stream.get("sink").asString, "Another application's stream moved")
                        }
                    }
                }
            }
        } finally {
            for (module in modules.asReversed()) LinuxAudioIntegration.command("pactl", "unload-module", module)
            assertEquals(originalDefault, LinuxAudioManager.getDefaultSinkId())
        }
    }
}
