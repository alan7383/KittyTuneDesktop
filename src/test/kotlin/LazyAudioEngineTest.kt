import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.audio.AudioProcessor
import com.alananasss.kittytune.audio.TimeStretcher
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import org.junit.Test
import kotlin.test.*

class LazyAudioEngineTest {
    private val deferredNames = setOf("fx", "equalizer", "normalization", "reverb", "vocalRemover", "vocalBoost", "stretcher")

    private fun initialized(engine: AudioEngine): Set<String> = engine.javaClass.declaredFields.mapNotNull { field ->
        val name = field.name.removeSuffix("\$delegate").removeSuffix("State")
        if (name !in deferredNames) return@mapNotNull null
        field.isAccessible = true
        when (val value = field.get(engine)) {
            is Lazy<*> -> name.takeIf { value.isInitialized() }
            is AudioProcessor, is TimeStretcher -> name
            else -> null
        }
    }.toSet() + engine.javaClass.declaredFields.filter { it.name == "effectNodes" }.flatMap { field ->
        field.isAccessible = true
        val holder = field.get(engine)
        val nodes = if (holder is Lazy<*>) {
            if (holder.isInitialized()) holder.value else null
        } else holder
        nodes?.javaClass?.declaredFields.orEmpty().mapNotNull { node ->
            node.isAccessible = true
            node.name.takeIf { node.get(nodes) is AudioProcessor && node.name in deferredNames }
        }
    }

    private fun stretcher(engine: AudioEngine): TimeStretcher {
        val field = engine.javaClass.declaredFields.first { it.name == "stretcher" || it.name == "stretcher\$delegate" || it.name == "stretcherState" }
        field.isAccessible = true
        val value = field.get(engine)
        return (if (value is Lazy<*>) value.value else value) as TimeStretcher
    }

    private fun beginBlock(engine: AudioEngine): List<*> {
        val method = engine.javaClass.getDeclaredMethod("configuredProcessors")
        method.isAccessible = true
        return method.invoke(engine) as List<*>
    }

    private fun process(engine: AudioEngine, input: ShortArray, processors: List<*> = beginBlock(engine)): ShortArray {
        val method = engine.javaClass.getDeclaredMethod(
            "pushThroughDsp", ShortArray::class.java, Int::class.javaPrimitiveType, List::class.java,
        )
        method.isAccessible = true
        method.invoke(engine, input, input.size, processors)
        val output = ShortArray(input.size * 8)
        return output.copyOf(stretcher(engine).drain(output, output.size))
    }

    @Test fun idlePreferencesAndCloseDoNotBuildTheDspGraph() {
        val engine = AudioEngine(isolated = true)
        try {
            engine.applyEffects(AudioEffectsState(isMonoEnabled = true, speed = 1.25f))
            engine.applyEqualizer(EqualizerState(isEnabled = true))
            engine.dspStages()
            assertTrue(initialized(engine).isEmpty(), "Idle engine allocated: ${initialized(engine)}")
            engine.stop()
            assertTrue(initialized(engine).isEmpty(), "Stopping an idle engine allocated DSP")
        } finally { engine.release() }
        assertTrue(initialized(engine).isEmpty(), "Releasing an idle engine allocated DSP")
    }

    @Test fun selectingAndSeekingAnUnplayedItemDoesNotBuildTheDspGraph() {
        val engine = AudioEngine(isolated = true)
        try {
            assertFalse(engine.isSeekPending)
            engine.setMediaItem("file:///unused.wav", startPositionMs = 1_000L)
            engine.seekTo(2_000L)
            assertTrue(engine.isSeekPending)
            assertEquals(2_000L, engine.positionMs)
            engine.seekTo(3_000L)
            assertEquals(3_000L, engine.positionMs)
            engine.setTrackLoudness(-12f, -1f)
            assertEquals(-70f, engine.getIntegratedLoudness())
            assertEquals(-120f, engine.getMaxTruePeakDb())
            assertTrue(initialized(engine).isEmpty(), "Preparing an unplayed item allocated DSP")
            engine.stop()
            assertFalse(engine.isSeekPending)
        } finally { engine.release() }
    }

    @Test fun settingsPublishedAfterBlockStartWaitForTheNextBlock() {
        val engine = AudioEngine(isolated = true)
        try {
            val input = shortArrayOf(1000, -2000, 3000, 1000)
            val processors = beginBlock(engine)
            engine.applyEffects(AudioEffectsState(isMonoEnabled = true))
            assertContentEquals(input, process(engine, input, processors))
            assertContentEquals(shortArrayOf(-500, -500, 2000, 2000), process(engine, input))
        } finally { engine.release() }
    }

    @Test fun savedMonoReachesTheFirstPcmBlock() {
        val engine = AudioEngine(isolated = true)
        try {
            engine.applyEffects(AudioEffectsState(isMonoEnabled = true))
            val input = shortArrayOf(1000, -2000, 3000, 1000, -8000, 4000)
            assertContentEquals(shortArrayOf(-500, -500, 2000, 2000, -2000, -2000), process(engine, input))
        } finally { engine.release() }
    }

    @Test fun latestSettingsApplyBeforeAudioAndBetweenBlocks() {
        val engine = AudioEngine(isolated = true)
        try {
            val input = shortArrayOf(1000, -2000, 3000, 1000)
            engine.applyEffects(AudioEffectsState(isMonoEnabled = true))
            engine.applyEffects(AudioEffectsState())
            assertContentEquals(input, process(engine, input))
            engine.applyEffects(AudioEffectsState(isMonoEnabled = true))
            assertContentEquals(shortArrayOf(-500, -500, 2000, 2000), process(engine, input))
            engine.applyEffects(AudioEffectsState())
            assertContentEquals(input, process(engine, input))
        } finally { engine.release() }
    }

    @Test fun savedEqualizerReachesTheFirstPcmBlock() {
        val engine = AudioEngine(isolated = true)
        val reference = com.alananasss.kittytune.audio.EqualizerAudioProcessor()
        try {
            val state = EqualizerState(isEnabled = true, preampDb = -6f, bandGainsDb = List(16) { if (it < 4) 3f else 0f })
            val input = ShortArray(512) { ((it * 41 % 12000) - 6000).toShort() }
            engine.applyEqualizer(state)
            reference.configure(com.alananasss.kittytune.audio.AudioFormat(44100, 2))
            reference.setParameters(true, state.preampDb, state.bandGainsDb.toFloatArray())
            val buffer = java.nio.ByteBuffer.allocateDirect(input.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.asShortBuffer().put(input)
            reference.queueInput(buffer)
            val output = reference.getOutput().asShortBuffer()
            val expected = ShortArray(output.remaining()).also { output.get(it) }
            assertContentEquals(expected, process(engine, input))
        } finally { engine.release(); reference.reset() }
    }

    @Test fun releasedIdleEngineCannotCreateAnAudioGraph() {
        val engine = AudioEngine(isolated = true)
        engine.release()
        val failure = assertFailsWith<java.lang.reflect.InvocationTargetException> {
            process(engine, shortArrayOf(1000, -1000))
        }
        assertTrue(failure.cause is InterruptedException)
        assertTrue(initialized(engine).isEmpty())
    }

    @Test fun automixRatioSurvivesDeferredGraphInitialization() {
        val engine = AudioEngine(isolated = true)
        try {
            val input = ShortArray(8192) { ((it * 97 % 16000) - 8000).toShort() }
            engine.applyEffects(AudioEffectsState())
            engine.setStretcherRatio(1.2f, 0.8f)
            val reference = TimeStretcher(44100, 2)
            reference.setParameters(1.2f, 0.8f)
            reference.queue(input, input.size)
            val expected = ShortArray(input.size * 8)
            val count = reference.drain(expected, expected.size)
            assertContentEquals(expected.copyOf(count), process(engine, input))
        } finally { engine.release() }
    }
}
