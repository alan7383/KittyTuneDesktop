package com.alananasss.kittytune.audio.ai

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AiModelType
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Base64
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

/**
 * Tests AI music detection architectures:
 * - ArtifactNet v94 (TFLite / LiteRT + XNNPACK)
 * - Audio Transformer (ONNX Runtime + DirectML / Vulkan / GPU acceleration)
 * - GPU hardware detection & device enumeration
 * - Mel-Spectrogram feature extraction
 */
class AiDetectionManagerTest {

    companion object {
        // Self-contained synthetic ONNX model conforming to audio_input -> [1, 176400] -> ai_score
        private const val SYNTHETIC_ONNX_BASE64 =
            "CAoSCWtpdHR5dHVuZTqzAQobCgthdWRpb19pbnB1dBIHYWJzX3ZhbCIDQWJzCjAKB2Fic192YWwSCG1lYW5fdmFsIgpSZWR1Y2VNZWFuKg8KCGtlZXBkaW1zGACgAQIKHQoIbWVhbl92YWwSCGFpX3Njb3JlIgdTaWdtb2lkEg5hdWRpb19kZXRlY3RvclofCgthdWRpb19pbnB1dBIQCg4IARIKCgIIAQoECJDiCmISCghhaV9zY29yZRIGCgQIARIAQgQKABAN"

        private lateinit var tempTestDir: File
        private val testModelFile get() = File(tempTestDir, "synthetic.onnx")
        private val artifactNetFile get() = File(tempTestDir, AiDetectionManager.ARTIFACTNET_ONNX)

        @BeforeClass
        @JvmStatic
        fun beforeAll() {
            tempTestDir = Files.createTempDirectory("kittytune_ai_test").toFile()
            val bytes = Base64.getDecoder().decode(SYNTHETIC_ONNX_BASE64)
            // ONNX ignores this unknown protobuf field. Its payload meets the model import size check.
            val padding = byteArrayOf(0xc2.toByte(), 0x3e, 0x80.toByte(), 0x80.toByte(), 0x40) + ByteArray(1_048_576)
            testModelFile.writeBytes(bytes + padding)
            testModelFile.copyTo(artifactNetFile)
            AiDetectionManager.setCustomModelsDirForTesting(tempTestDir)
        }

        @AfterClass
        @JvmStatic
        fun afterAll() {
            AiDetectionManager.setCustomModelsDirForTesting(null)
            if (::tempTestDir.isInitialized) tempTestDir.deleteRecursively()
        }
    }

    private lateinit var previousPrefs: Map<String, JsonElement>
    private var usingSampleModel = false

    @Before
    fun setup() {
        previousPrefs = Prefs.snapshot()
        val prefs = PlayerPreferences()
        prefs.aiSelectedModel = AiModelType.ARTIFACTNET
        prefs.aiGpuAccelerationEnabled = false
        prefs.aiSelectedGpuId = GpuDevice.ID_CPU
        prefs.aiScoreThreshold = 0.70f
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.init()
    }

    @After
    fun tearDown() {
        try {
            if (usingSampleModel) AiDetectionManager.deleteModel()
            if (!artifactNetFile.isFile) {
                AiDetectionManager.importModelFile(AiModelType.ARTIFACTNET, testModelFile).getOrThrow()
            }
        } finally {
            AiDetectionManager.resetResult()
            Prefs.restore(previousPrefs)
        }
    }

    private fun fixture(name: String): File {
        val fixtureLocation = System.getenv("KITTYTUNE_AI_FIXTURES")
        assumeTrue("Set KITTYTUNE_AI_FIXTURES to the directory containing the real model and audio samples",
            !fixtureLocation.isNullOrBlank())
        return File(checkNotNull(fixtureLocation), name).also {
            assertTrue("Missing AI fixture: $name", it.isFile)
        }
    }

    private fun sampleTrack(name: String): File {
        val track = fixture(name)
        val model = fixture(AiDetectionManager.ARTIFACTNET_ONNX)
        usingSampleModel = true
        AiDetectionManager.importModelFile(AiModelType.ARTIFACTNET, model).getOrThrow()
        assertTrue("ArtifactNet fixture must load", AiDetectionManager.isModelLoaded())
        return track
    }

    private fun sine440(sr: Int = 44100, durationSec: Int = 4): ByteArray {
        val n = sr * durationSec
        val out = ByteArray(n * 2 * 2) // stereo 16-bit
        val buf = java.nio.ByteBuffer.wrap(out).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        repeat(n) { i ->
            val s = (0.5 * kotlin.math.sin(2 * Math.PI * 440 * i / sr) * 32767).toInt().toShort()
            buf.putShort(s)
            buf.putShort(s)
        }
        return out
    }

    @Test
    fun inferenceMatchesReference() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.init()
        assertTrue("model should be loaded", AiDetectionManager.isModelLoaded())
        assertEquals(
            AiDetectionManager.ModelDownloadState.Ready,
            AiDetectionManager.downloadState.value,
        )
        AiDetectionManager.analyzeAsync(sine440(), 44100, 2)
        val result = runBlocking {
            withTimeout(120_000) {
                AiDetectionManager.result.filterNotNull()
                    .first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        println("P(AI) sine440 = ${result.score}")
        assertTrue("score in range", result.score in 0f..1f)
        // The synthetic model computes sigmoid(mean(abs(samples))). A sine of amplitude 0.5
        // has a mean absolute value of 1 / pi before 16-bit quantization.
        val expected = (1.0 / (1.0 + kotlin.math.exp(-1.0 / Math.PI))).toFloat()
        assertEquals(expected, result.score, 0.0001f)
    }

    @Test
    fun testOnnxRuntimeEnvironment() {
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        assertNotNull(env)
        val opts = ai.onnxruntime.OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(2)
        try {
            assertNotNull(opts)
        } finally {
            opts.close()
        }
    }


    @Test
    fun testAiDetectionManagerInitWithOnnxModel() {
        AiDetectionManager.init()
        assertTrue(AiDetectionManager.isModelLoaded())
        assertTrue(AiDetectionManager.activeAccelerationProvider.value.contains("CPU"))
    }

    @Test
    fun testCompareTfliteAndOnnx() {
        val tfliteFile = fixture(AiDetectionManager.ARTIFACTNET_TFLITE)
        val onnxFile = fixture(AiDetectionManager.ARTIFACTNET_ONNX)
        usingSampleModel = true
        val pcm = sine440()

        AiDetectionManager.importModelFile(AiModelType.ARTIFACTNET, tfliteFile).getOrThrow()
        assertTrue(AiDetectionManager.activeAccelerationProvider.value.startsWith("LiteRT"))
        val tfliteScore = checkNotNull(AiDetectionManager.analyzePcm(pcm))

        AiDetectionManager.deleteModel()
        AiDetectionManager.importModelFile(AiModelType.ARTIFACTNET, onnxFile).getOrThrow()
        assertTrue(AiDetectionManager.isModelLoaded())
        val onnxScore = checkNotNull(AiDetectionManager.analyzePcm(pcm))

        assertTrue("Both model scores must be probabilities", tfliteScore in 0f..1f && onnxScore in 0f..1f)
        assertEquals("The two ArtifactNet runtimes should give similar scores", tfliteScore, onnxScore, 0.03f)
    }

    @Test
    fun testGpuDetection() {
        val gpus = GpuDetector.getDetectedGpus()
        println("Detected GPUs: $gpus")
        val resolved = GpuDetector.resolveGpu(GpuDevice.ID_AUTO)
        println("Resolved Auto GPU: $resolved")
        assertNotNull(resolved)

        val cpuDevice = GpuDetector.resolveGpu(GpuDevice.ID_CPU)
        assertEquals(GpuDevice.ID_CPU, cpuDevice.id)

        // Non-existent ID resolves gracefully to AUTO
        val unknown = GpuDetector.resolveGpu("non_existent_gpu_99")
        assertNotNull(unknown)
    }

    @Test
    fun testMelSpectrogramExtraction() {
        val pcm = FloatArray(44100) { i -> (0.3 * kotlin.math.sin(2 * Math.PI * 440 * i / 44100)).toFloat() }
        val mel = MelSpectrogram.computeLogMel(pcm, sampleRate = 44100, nMels = 128)
        assertEquals(1, mel.size)
        assertEquals(128, mel[0].size)
        assertTrue("frames should be computed", mel[0][0].isNotEmpty())

        // 440 Hz frequency peak energy should lie around Mel bin 17-19
        val numFrames = mel[0][0].size
        var maxEnergyBin = 0
        var maxEnergy = Float.NEGATIVE_INFINITY
        for (m in 0 until 128) {
            val avg = mel[0][m].average().toFloat()
            if (avg > maxEnergy) {
                maxEnergy = avg
                maxEnergyBin = m
            }
        }
        println("440Hz peak Mel bin: $maxEnergyBin")
        assertTrue("Peak energy for 440 Hz should be near bin 18 (was $maxEnergyBin)", maxEnergyBin in 15..22)
    }

    @Test
    fun testModelSelectionAndState() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        assertEquals(AiModelType.ARTIFACTNET, AiDetectionManager.currentModel)
        assertTrue(AiDetectionManager.isModelReady(AiModelType.ARTIFACTNET))

        // Ensure preferences reflect selection
        val prefs = PlayerPreferences()
        assertEquals(AiModelType.ARTIFACTNET, prefs.aiSelectedModel)
        assertTrue(AiDetectionManager.isModelLoaded())

        // Run inference with standard 44.1 kHz audio
        AiDetectionManager.analyzeAsync(sine440(44100), 44100, 2)
        val result44k = runBlocking {
            withTimeout(10_000) {
                AiDetectionManager.result.filterNotNull()
                    .first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertTrue("score in [0, 1]", result44k.score in 0f..1f)

        // Run inference with 48 kHz audio to verify resampling pipeline
        AiDetectionManager.resetResult()
        AiDetectionManager.analyzeAsync(sine440(48000), 48000, 2)
        val result48k = runBlocking {
            withTimeout(10_000) {
                AiDetectionManager.result.filterNotNull()
                    .first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertTrue("score in [0, 1]", result48k.score in 0f..1f)

        // Test hardware acceleration reload
        AiDetectionManager.reloadHardwareAcceleration()
        assertTrue(AiDetectionManager.isModelLoaded())
        assertEquals(AiModelType.ARTIFACTNET, AiDetectionManager.currentModel)
    }

    @Test
    fun testImportModelFile() {
        AiDetectionManager.deleteModel()
        assertFalse("model starts uninstalled", AiDetectionManager.isModelReady())
        AiDetectionManager.importModelFile(AiModelType.ARTIFACTNET, testModelFile).getOrThrow()
        assertTrue("artifactnet ready after import", AiDetectionManager.isModelReady())
        assertTrue("artifactnet loaded after import", AiDetectionManager.isModelLoaded())
    }

    @Test
    fun testArtifactNetHardwareAccelerationReload() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        assertTrue(AiDetectionManager.isModelLoaded())

        // 1. Test with GPU acceleration enabled
        val prefs = PlayerPreferences()
        prefs.aiGpuAccelerationEnabled = true
        prefs.aiSelectedGpuId = GpuDevice.ID_AUTO

        AiDetectionManager.reloadHardwareAcceleration()
        assertTrue("ArtifactNet must be loaded after GPU reload", AiDetectionManager.isModelLoaded())
        val gpuProvider = AiDetectionManager.activeAccelerationProvider.value
        println("ArtifactNet GPU provider: $gpuProvider")
        assertTrue("Provider should reflect GPU or GPU fallback", gpuProvider.contains("GPU") || gpuProvider.contains("DirectML") || gpuProvider.contains("ROCm") || gpuProvider.contains("LiteRT") || gpuProvider.contains("CPU"))

        // Run inference through active provider
        AiDetectionManager.analyzeAsync(sine440(44100, durationSec = 4), 44100, 2)
        val gpuResult = runBlocking {
            withTimeout(30_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertTrue("GPU result score in [0, 1]", gpuResult.score in 0f..1f)

        // 2. Test with CPU-only mode
        prefs.aiGpuAccelerationEnabled = false
        prefs.aiSelectedGpuId = GpuDevice.ID_CPU
        AiDetectionManager.reloadHardwareAcceleration()
        assertTrue("ArtifactNet must be loaded after CPU reload", AiDetectionManager.isModelLoaded())
        val cpuProvider = AiDetectionManager.activeAccelerationProvider.value
        println("ArtifactNet CPU provider: $cpuProvider")
        assertTrue("Provider should reflect CPU", cpuProvider.contains("CPU") || cpuProvider.contains("XNNPACK"))

        // Run inference through CPU provider
        AiDetectionManager.analyzeAsync(sine440(44100, durationSec = 4), 44100, 2)
        val cpuResult = runBlocking {
            withTimeout(30_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertTrue("CPU result score in [0, 1]", cpuResult.score in 0f..1f)
    }

    @Test
    fun testSilenceAndPeakNormalization() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        assertTrue(AiDetectionManager.isModelLoaded())

        // 1. Pure silence: should not trigger DONE result (RMS < 0.001)
        AiDetectionManager.resetResult()
        val silentPcm = ByteArray(44100 * 4 * 2) // 4s stereo silence (0)
        AiDetectionManager.analyzeAsync(silentPcm, 44100, 2)
        // Ensure result stays ANALYZING or null, not DONE
        val currentStatus = AiDetectionManager.result.value?.status
        assertTrue(currentStatus == null || currentStatus == AiDetectionManager.Status.ANALYZING)

        // 2. High-amplitude clipped audio (peak > 0.95): should be normalized and process cleanly without NaN
        val clippedAudio = ByteArray(44100 * 4 * 4) // stereo 16-bit
        val buf = java.nio.ByteBuffer.wrap(clippedAudio).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        repeat(44100 * 4) {
            // max int16 amplitude = 32767 (~1.0 float)
            buf.putShort(32767.toShort())
            buf.putShort(32767.toShort())
        }
        AiDetectionManager.analyzeAsync(clippedAudio, 44100, 2)
        val peakResult = runBlocking {
            withTimeout(30_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertNotNull(peakResult)
        assertFalse("Score must not be NaN", peakResult.score.isNaN())
        assertTrue("Normalized score in [0, 1]", peakResult.score in 0f..1f)
    }

    @Test
    fun testModelSizeFormattingAndLocalization() {
        val origLang = Strings.appLanguage

        try {
            // French
            Strings.appLanguage = "fr"
            assertEquals("16,2 Mo", com.alananasss.kittytune.ui.profile.formatModelSize(AiModelType.ARTIFACTNET.totalBytes))

            // English
            Strings.appLanguage = "en"
            assertEquals("16.2 MB", com.alananasss.kittytune.ui.profile.formatModelSize(AiModelType.ARTIFACTNET.totalBytes))

            // Russian
            Strings.appLanguage = "ru"
            assertEquals("16.2 МБ", com.alananasss.kittytune.ui.profile.formatModelSize(AiModelType.ARTIFACTNET.totalBytes))
        } finally {
            Strings.appLanguage = origLang
        }
    }

    @Test
    fun testStringsCleanlinessNoHardcodedSizes() {
        val xmlFiles = listOf("strings-en.xml", "strings-fr.xml", "strings-de.xml", "strings-hu.xml", "strings-ru.xml", "strings-vi.xml")
        val baseDir = File("src/main/resources/i18n")

        for (fileName in xmlFiles) {
            val file = File(baseDir, fileName)
            assertTrue("File $fileName should exist", file.exists())
            val text = file.readText()

            // block_model_install_btn must not contain hardcoded size tokens like (17 Mo), (17 MB), (214 MB)
            val installBtnMatch = Regex("<string name=\"block_model_install_btn\">(.*?)</string>").find(text)
            assertNotNull("block_model_install_btn must exist in $fileName", installBtnMatch)
            val installBtnContent = installBtnMatch!!.groupValues[1]
            assertFalse(
                "block_model_install_btn in $fileName should not have hardcoded size tokens: $installBtnContent",
                installBtnContent.contains("17") ||
                    installBtnContent.contains("214") ||
                    Regex("\\(\\s*\\d+").containsMatchIn(installBtnContent),
            )

            // block_model_download_btn must not contain hardcoded size tokens like (17 Mo), (17 MB), (214 MB)
            val downloadBtnMatch = Regex("<string name=\"block_model_download_btn\">(.*?)</string>").find(text)
            assertNotNull("block_model_download_btn must exist in $fileName", downloadBtnMatch)
            val downloadBtnContent = downloadBtnMatch!!.groupValues[1]
            assertFalse(
                "block_model_download_btn in $fileName should not have hardcoded size tokens: $downloadBtnContent",
                downloadBtnContent.contains("17") ||
                    downloadBtnContent.contains("214") ||
                    Regex("\\(\\s*\\d+").containsMatchIn(downloadBtnContent),
            )
        }
    }

    @Test
    fun testAllModelUrlsAccessible() {
        assumeTrue("Set KITTYTUNE_TEST_LIVE_SERVICES=1 to check model downloads",
            System.getenv("KITTYTUNE_TEST_LIVE_SERVICES") == "1")
        for (model in AiModelType.entries) {
            val allUrls = listOf(model.downloadUrl) + model.fallbackUrls
            for (urlStr in allUrls) {
                val url = java.net.URI(urlStr).toURL()
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                conn.disconnect()
                assertTrue(
                    "URL $urlStr for model ${model.name} must be accessible (got HTTP $code)",
                    code in 200..399,
                )
            }
        }
    }

    @Test
    fun testPeakScoreRetentionNeverRegresses() {
        AiDetectionManager.resetResult()

        // 1. Initial low score (intro)
        AiDetectionManager.recordCheckpointScore(0.014f)
        assertEquals(0.014f, AiDetectionManager.getPeakScore(), 0.001f)
        assertEquals(0.014f, AiDetectionManager.result.value?.score ?: 0f, 0.001f)
        assertFalse(AiDetectionManager.isAiConfirmed())

        // 2. Strong AI detection in second window
        AiDetectionManager.recordCheckpointScore(0.963f)
        assertEquals(0.963f, AiDetectionManager.getPeakScore(), 0.001f)
        assertEquals(0.963f, AiDetectionManager.result.value?.score ?: 0f, 0.001f)
        assertTrue(AiDetectionManager.isAiConfirmed())
        assertTrue(AiDetectionManager.result.value?.isAi == true)

        // 3. Subsequent lower scores must not regress peak score or flip isAi back to false
        AiDetectionManager.recordCheckpointScore(0.200f)
        assertEquals(0.963f, AiDetectionManager.getPeakScore(), 0.001f)
        assertEquals(0.963f, AiDetectionManager.result.value?.score ?: 0f, 0.001f)
        assertTrue(AiDetectionManager.isAiConfirmed())

        AiDetectionManager.recordCheckpointScore(0.005f)
        assertEquals(0.963f, AiDetectionManager.getPeakScore(), 0.001f)
        assertEquals(0.963f, AiDetectionManager.result.value?.score ?: 0f, 0.001f)
        assertTrue(AiDetectionManager.isAiConfirmed())
    }

    @Test
    fun testProgressiveSamplingInAudioProcessor() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val processor = AiDetectionAudioProcessor()
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        // 1 second of audio @ 44.1 kHz stereo 16-bit = 176,400 bytes
        val oneSecSine = sine440(sr = 44100, durationSec = 1)

        // Push 4 seconds (0 to 4s) -> triggers checkpoint 0
        repeat(4) {
            val buf = ByteBuffer.wrap(oneSecSine)
            processor.queueInput(buf)
        }

        // Wait for first checkpoint inference
        val result0 = runBlocking {
            withTimeout(15_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        println("Checkpoint 0 result: score=${result0.score}, isAi=${result0.isAi}")
        assertTrue("Synthetic sine score is below the threshold", result0.score < 0.70f)
        assertFalse(AiDetectionManager.isAiConfirmed())

        // Push 4 seconds (4s to 8s) -> gap between checkpoints, direct pass-through
        repeat(4) {
            val buf = ByteBuffer.wrap(oneSecSine)
            processor.queueInput(buf)
        }

        // Push 4 seconds (8s to 12s) -> triggers checkpoint 1!
        repeat(4) {
            val buf = ByteBuffer.wrap(oneSecSine)
            processor.queueInput(buf)
        }

        // Peak score is retained and still valid
        assertTrue(AiDetectionManager.getPeakScore() in 0f..1f)
    }

    @Test
    fun testProcessorNotifySeekResetsAndSamplesAtSeek() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val processor = AiDetectionAudioProcessor()
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        val oneSecSine = sine440(sr = 44100, durationSec = 1)

        // Stream 2 seconds
        repeat(2) {
            processor.queueInput(ByteBuffer.wrap(oneSecSine))
        }

        // Seek forward to 25s
        processor.notifySeek(25_000L)

        // Pushing audio after seek should immediately collect for the new checkpoint at 25s
        repeat(4) {
            processor.queueInput(ByteBuffer.wrap(oneSecSine))
        }

        val resultSeek = runBlocking {
            withTimeout(15_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertNotNull(resultSeek)
        assertTrue("Seek sample score in [0, 1]", resultSeek.score in 0f..1f)
    }

    private fun streamMp3ThroughProcessor(
        file: File,
        processor: AiDetectionAudioProcessor,
        durationSeconds: Int = 25,
    ) {
        val grabber = FFmpegFrameGrabber(file).apply {
            sampleRate = 44100
            audioChannels = 2
            sampleMode = FrameGrabber.SampleMode.FLOAT
        }
        grabber.start()
        val decodeBuffer = FloatArray(32768)
        val byteBuf = ByteBuffer.allocateDirect(65536).order(ByteOrder.LITTLE_ENDIAN)

        var totalSamplesRead = 0L
        val maxSamples = durationSeconds * 44100L * 2

        while (totalSamplesRead < maxSamples) {
            val frame = try { grabber.grabSamples() } catch (_: Exception) { null } ?: break
            val buffers = frame.samples ?: continue
            if (buffers.isEmpty()) continue
            val first = buffers[0]
            val channels = frame.audioChannels.coerceAtLeast(1)
            val planar = buffers.size == channels && channels > 1
            val len = first.remaining()
            if (len <= 0) continue

            val numSamples = if (planar) len * channels else len
            if (numSamples > decodeBuffer.size) continue

            if (!planar && first is java.nio.FloatBuffer) {
                val pos = first.position()
                first.get(decodeBuffer, 0, len)
                first.position(pos)
            } else if (planar) {
                for (ch in 0 until channels) {
                    val b = buffers[ch] as java.nio.FloatBuffer
                    val bPos = b.position()
                    for (i in 0 until len) {
                        decodeBuffer[i * channels + ch] = b.get(bPos + i)
                    }
                }
            }

            byteBuf.clear()
            for (i in 0 until numSamples) {
                val s = (decodeBuffer[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                byteBuf.putShort(s)
            }
            byteBuf.flip()
            processor.queueInput(byteBuf)
            totalSamplesRead += numSamples
        }
        grabber.stop()
        grabber.release()
    }

    @Test
    fun testOllyRodwellAirForcesHumanTrackCleanDetection() {
        val testMp3 = sampleTrack("Olly Rodwell, DJ Love - air forces.mp3")

        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val prefs = PlayerPreferences()
        prefs.aiScoreThreshold = 0.50f // User's 50% setting

        val processor = AiDetectionAudioProcessor(prefs)
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        println("Streaming Olly Rodwell through AiDetectionAudioProcessor (25 seconds)...")
        streamMp3ThroughProcessor(testMp3, processor, durationSeconds = 25)

        val result = runBlocking {
            withTimeout(30_000) {
                AiDetectionManager.result.filterNotNull().first {
                    it.status == AiDetectionManager.Status.DONE
                }
            }
        }

        val peakScore = AiDetectionManager.getPeakScore()
        println("Olly Rodwell clean stream result: score=${result.score}, peak=$peakScore, isAi=${result.isAi}")

        assertFalse("Clean audio from Olly Rodwell must NOT be confirmed as AI", AiDetectionManager.isAiConfirmed())
        assertFalse("Verdict must be human audio (isAi=false)", result.isAi)
        assertTrue("Clean forensic score must be very low (< 0.20, was $peakScore)", peakScore < 0.20f)
    }

    @Test
    fun testJustinAllevatoAiTrackDetection() {
        val testMp3 = sampleTrack("Justin Allevato - ME VUELVES LOCO.mp3")

        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val prefs = PlayerPreferences()
        prefs.aiScoreThreshold = 0.70f

        val processor = AiDetectionAudioProcessor(prefs)
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        // Stream 14 seconds through processor:
        // - Checkpoint 0 (0-4s) covers clean guitar intro (score ~0.005)
        // - Checkpoint 1 (4-8s) transition into vocals (score ~0.360)
        // - Checkpoint 2 (8-12s) captures AI vocal onset (score ~0.935 >= 0.70 threshold)
        streamMp3ThroughProcessor(testMp3, processor, durationSeconds = 14)

        val result = runBlocking {
            withTimeout(30_000) {
                AiDetectionManager.result.filterNotNull().first {
                    it.status == AiDetectionManager.Status.DONE && it.isAi
                }
            }
        }

        val peakScore = AiDetectionManager.getPeakScore()
        println("Justin Allevato clean stream result: score=${result.score}, peak=$peakScore, isAi=${result.isAi}")

        assertTrue("Justin Allevato track must be confirmed as AI within first 14 seconds", AiDetectionManager.isAiConfirmed())
        assertTrue("Verdict must be AI audio", result.isAi)
    }

    @Test
    fun testKaspiyskiyGruzHumanTrackCleanDetection() {
        val testMp3 = sampleTrack("Каспийский Груз - Детство (п.у. L из IzReal).mp3")

        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val prefs = PlayerPreferences()
        prefs.aiScoreThreshold = 0.70f

        val processor = AiDetectionAudioProcessor(prefs)
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        // Stream 65 seconds through processor covering all checkpoints (0s, 6s, 15s, 30s, 45s, 60s):
        // Consensus across windows ensures isolated spikes (at 12s and 52s) are filtered out,
        // so verdict remains human (isAi=false) and score remains low.
        streamMp3ThroughProcessor(testMp3, processor, durationSeconds = 65)

        val result = runBlocking {
            withTimeout(30_000) {
                AiDetectionManager.result.filterNotNull().first {
                    it.status == AiDetectionManager.Status.DONE
                }
            }
        }

        val peakScore = AiDetectionManager.getPeakScore()
        println("Kaspiyskiy Gruz stream result: score=${result.score}, peak=$peakScore, isAi=${result.isAi}")

        assertFalse("Kaspiyskiy Gruz must NOT be confirmed as AI", AiDetectionManager.isAiConfirmed())
        assertFalse("Verdict must remain human (isAi=false) throughout playback", result.isAi)
        assertTrue("Processor must complete all scheduled checkpoints and lock pass-through", processor.isAllCheckpointsDone())
        assertTrue("Score must remain low human probability (< 0.10, was $peakScore)", peakScore < 0.10f)
    }

    @Test
    fun testMejiAcousticIntroHumanDetection() {
        val testMp3 = sampleTrack("Meji - des id33s n0ires!! ˙◠˙ + kysmelo [lostsoundwtf].mp3")

        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val grabber = FFmpegFrameGrabber(testMp3).apply {
            sampleRate = 44100
            audioChannels = 1
            sampleMode = FrameGrabber.SampleMode.FLOAT
        }
        grabber.start()
        val allSamples = mutableListOf<Float>()
        val buf = FloatArray(4096)
        while (allSamples.size < 176400) {
            val f = grabber.grabSamples() ?: break
            val b = f.samples?.getOrNull(0) as? java.nio.FloatBuffer ?: continue
            val len = b.remaining()
            val pos = b.position()
            b.get(buf, 0, len)
            b.position(pos)
            for (i in 0 until len) allSamples.add(buf[i])
        }
        grabber.stop()
        grabber.release()

        val introChunk = allSamples.take(176400).toFloatArray()
        val score = AiDetectionManager.analyzePcmSamples(introChunk)
        assertNotNull("Intro chunk score should not be null", score)
        assertTrue("Meji acoustic intro must score very low AI probability (< 0.10, was $score)", (score ?: 1f) < 0.10f)
    }

    @Test
    fun testSeekAfterAllCheckpointsCompletedReEnablesSampling() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.resetResult()

        val processor = AiDetectionAudioProcessor()
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        val oneSecSine = sine440(sr = 44100, durationSec = 1)

        // Push audio through all 6 checkpoints: 0s, 8s, 18s, 30s, 45s, 60s
        // Total audio stream duration ~65 seconds
        repeat(65) {
            processor.queueInput(ByteBuffer.wrap(oneSecSine))
        }

        assertTrue("All checkpoints should be completed after 65 seconds", processor.isAllCheckpointsDone())

        // User scrubs/seeks forward to 95s
        processor.notifySeek(95_000L)
        assertFalse("notifySeek must re-enable checkpoint sampling", processor.isAllCheckpointsDone())
        assertEquals(95.0, processor.getCurrentPositionSec(), 0.001)

        // Pushing audio after seek should collect and trigger inference at 95s
        repeat(4) {
            processor.queueInput(ByteBuffer.wrap(oneSecSine))
        }

        val resultSeek = runBlocking {
            withTimeout(15_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertNotNull(resultSeek)
        assertTrue("Seek result score in [0, 1]", resultSeek.score in 0f..1f)
    }

    @Test
    fun testAudioEngineStartPositionNotifiesSeek() {
        val engine = com.alananasss.kittytune.audio.AudioEngine()
        engine.setMediaItem("test_nonexistent.mp3", startPositionMs = 35_000L)
        assertEquals(35.0, engine.aiDetection.getCurrentPositionSec(), 0.001)
        engine.stop()
        assertEquals(0.0, engine.aiDetection.getCurrentPositionSec(), 0.001)
        engine.release()
    }

    @Test
    fun testInitAsyncDoesNotBlock() {
        AiDetectionManager.setCustomModelsDirForTesting(tempTestDir)
        assertFalse(AiDetectionManager.isModelLoaded())
        AiDetectionManager.initAsync()
        val loaded = runBlocking {
            withTimeout(15_000) {
                while (!AiDetectionManager.isModelLoaded()) {
                    kotlinx.coroutines.delay(50)
                }
                true
            }
        }
        assertTrue("Model must be loaded via initAsync", loaded)
    }

    @Test
    fun testAudioProcessorNeverBlocksDuringInference() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        AiDetectionManager.init()
        assertTrue("Model must be loaded", AiDetectionManager.isModelLoaded())

        val processor = AiDetectionAudioProcessor()
        val fmt = AudioFormat(44100, 2)
        processor.configure(fmt)
        processor.resetForNewTrack()

        val fourSecSine = sine440(sr = 44100, durationSec = 4)
        val smallChunk = ByteBuffer.wrap(fourSecSine, 0, 4096)

        // Trigger analysis in background
        AiDetectionManager.analyzeAsync(fourSecSine, 44100, 2)

        // Concurrently stream 100 audio chunks through processor
        // None of these should ever block on the inference thread
        var maxDurationMs = 0L
        repeat(100) {
            smallChunk.rewind()
            val t0 = System.nanoTime()
            processor.queueInput(smallChunk)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000L
            if (elapsedMs > maxDurationMs) maxDurationMs = elapsedMs
        }

        println("Max queueInput duration during concurrent inference: ${maxDurationMs}ms")
        assertTrue("queueInput must never block for ML inference (max was ${maxDurationMs}ms)", maxDurationMs < 10L)
    }

    @Test
    fun testModelDownloadStates() {
        AiDetectionManager.selectModel(AiModelType.ARTIFACTNET)
        assertEquals(AiModelType.ARTIFACTNET, AiDetectionManager.currentModel)
        assertTrue(AiDetectionManager.isModelReady(AiModelType.ARTIFACTNET))
        assertEquals(
            AiDetectionManager.ModelDownloadState.Ready,
            AiDetectionManager.downloadState.value,
        )
    }

    @Test
    fun testCalculateVerdictFromScoresConsensus() {
        val threshold = 0.70f

        // 1. AI track with clean acoustic intro (Justin Allevato profile)
        val aiWithIntroScores = listOf(0.001f, 0.892f, 0.995f, 0.384f)
        val aiVerdict = AiDetectionManager.calculateVerdictFromScores(aiWithIntroScores, threshold)
        assertTrue("Track with AI vocals must be classified as AI", aiVerdict.isAi)
        assertEquals(AiDetectionManager.Status.DONE, aiVerdict.status)
        assertTrue("AI score must be high (was ${aiVerdict.score})", aiVerdict.score >= 0.70f)

        // 2. Clean Human track (Meji profile)
        val cleanHumanScores = listOf(0.0001f, 0.0004f, 0.0005f, 0.0001f)
        val humanVerdict = AiDetectionManager.calculateVerdictFromScores(cleanHumanScores, threshold)
        assertFalse("Clean track must be classified as Human", humanVerdict.isAi)
        assertEquals(AiDetectionManager.Status.DONE, humanVerdict.status)
        assertTrue("Human score must be near zero (was ${humanVerdict.score})", humanVerdict.score < 0.01f)

        // 3. Human track with isolated scratch / autotune (Kaspiyskiy Gruz profile)
        val humanWithScratch = listOf(0.02f, 0.44f, 0.39f, 0.05f)
        val scratchVerdict = AiDetectionManager.calculateVerdictFromScores(humanWithScratch, threshold)
        assertFalse("Track with isolated autotune/scratch must remain Human", scratchVerdict.isAi)
        assertEquals(AiDetectionManager.Status.DONE, scratchVerdict.status)
        assertTrue("Score must reflect median (was ${scratchVerdict.score})", scratchVerdict.score < threshold)
    }

    @Test
    fun testRecordCheckpointScoreNoFluctuationWhenVerdictIsFinal() {
        AiDetectionManager.resetResult()
        assertFalse(AiDetectionManager.isVerdictFinal())

        val wav = File(tempTestDir, "sine440.wav")
        val pcm = sine440(durationSec = 12)
        val format = javax.sound.sampled.AudioFormat(44_100f, 16, 2, true, false)
        AudioInputStream(pcm.inputStream(), format, (pcm.size / format.frameSize).toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, wav)
        }
        AiDetectionManager.analyzeTrackUpfront(wav.absolutePath)
        val locked = runBlocking {
            withTimeout(15_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertTrue("Verdict must be locked as final", AiDetectionManager.isVerdictFinal())
        assertFalse("Synthetic sine is below the classification threshold", locked.isAi)

        AiDetectionManager.recordCheckpointScore(0.99f)
        assertEquals("A later checkpoint must preserve the final verdict", locked, AiDetectionManager.result.value)
    }

    @Test
    fun testHalseyTrackHumanNotAi() {
        val file = sampleTrack("Halsey - Without Me.m4a")
        AiDetectionManager.analyzeTrackUpfront(file.absolutePath)
        val res = runBlocking {
            withTimeout(20_000) {
                AiDetectionManager.result.filterNotNull().first { it.status == AiDetectionManager.Status.DONE }
            }
        }
        assertFalse("Halsey - Without Me must be classified as Human", res.isAi)
        assertTrue("Score must be low (< 0.20), was ${res.score}", res.score < 0.20f)
    }
}




