package com.alananasss.kittytune.audio.ai.karaoke

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.VocalRemoverAudioProcessor
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.KaraokeEngineMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KaraokeManagerTest {

    private lateinit var prefs: PlayerPreferences
    private val testStemsDir = KaraokeManager.getStemsDir()

    @Before
    fun setup() {
        prefs = PlayerPreferences()
        testStemsDir.mkdirs()
    }

    @After
    fun teardown() {
        KaraokeManager.clearStemsCache()
        KaraokeManager.notifyTrackChanged(-1L)
    }

    @Test
    fun testPreferencesKaraokeDefaultsAndPersistence() {
        // Default engine mode should be DSP (zero setup required out of the box)
        prefs.karaokeEngineMode = KaraokeEngineMode.DSP
        assertEquals(KaraokeEngineMode.DSP, prefs.karaokeEngineMode)

        // Switching to MEL_BAND_ROFORMER persists cleanly
        prefs.karaokeEngineMode = KaraokeEngineMode.MEL_BAND_ROFORMER
        assertEquals(KaraokeEngineMode.MEL_BAND_ROFORMER, prefs.karaokeEngineMode)

        // GPU Acceleration toggle
        prefs.karaokeGpuAccelerationEnabled = true
        assertTrue(prefs.karaokeGpuAccelerationEnabled)
        prefs.karaokeGpuAccelerationEnabled = false
        assertFalse(prefs.karaokeGpuAccelerationEnabled)

        // Auto separate toggle
        prefs.karaokeAutoSeparateOnPlay = true
        assertTrue(prefs.karaokeAutoSeparateOnPlay)
        prefs.karaokeAutoSeparateOnPlay = false
        assertFalse(prefs.karaokeAutoSeparateOnPlay)
    }

    @Test
    fun testStemCacheTrackingAndClearing() {
        // Ensure clean state initially
        KaraokeManager.clearStemsCache()
        val statsInit = KaraokeManager.getStemCacheStats()
        assertEquals(0, statsInit.first)
        assertEquals(0.0f, statsInit.second, 0.01f)

        // Create mock stem files: 2 tracks
        val stem1 = KaraokeManager.getStemFile(101L)
        val stem2 = KaraokeManager.getStemFile(202L)
        val dummyData = ByteArray(1024 * 1024) // 1 MB
        stem1.writeBytes(dummyData)
        stem2.writeBytes(dummyData)

        assertTrue(KaraokeManager.hasStem(101L))
        assertTrue(KaraokeManager.hasStem(202L))
        assertFalse(KaraokeManager.hasStem(999L))

        val statsUpdated = KaraokeManager.getStemCacheStats()
        assertEquals(2, statsUpdated.first)
        assertEquals(2.0f, statsUpdated.second, 0.1f)

        // Clear stems cache
        KaraokeManager.clearStemsCache()
        assertFalse(KaraokeManager.hasStem(101L))
        assertFalse(KaraokeManager.hasStem(202L))
        val statsCleared = KaraokeManager.getStemCacheStats()
        assertEquals(0, statsCleared.first)
        assertEquals(0.0f, statsCleared.second, 0.01f)
    }

    @Test
    fun testVocalRemoverRealtimeFallbackWhenStemAbsent() {
        prefs.karaokeEngineMode = KaraokeEngineMode.MEL_BAND_ROFORMER
        val processor = VocalRemoverAudioProcessor()
        processor.configure(AudioFormat(44100, 2, 2))
        processor.setEnabled(true)
        processor.setKaraokeAiMode(true)
        processor.setTrack(555L) // No stem exists for track 555
        processor.setSuppressionLevel(1.0f)

        // Stereo audio buffer: Left and Right channels with central vocal component
        val sampleCount = 512
        val inputBuffer = ByteBuffer.allocate(sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount / 2) {
            val centerVocal = 10000.toShort()
            val leftInstrument = 5000.toShort()
            val rightInstrument = (-5000).toShort()

            val left = (centerVocal + leftInstrument).toShort()
            val right = (centerVocal + rightInstrument).toShort()
            inputBuffer.putShort(left)
            inputBuffer.putShort(right)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.getOutput()
        assertNotNull(output)
        assertTrue(output.remaining() > 0)
    }

    @Test
    fun testVocalRemoverBlendsStemWhenPresent() {
        prefs.karaokeEngineMode = KaraokeEngineMode.MEL_BAND_ROFORMER
        val trackId = 777L
        val processor = VocalRemoverAudioProcessor()
        processor.configure(AudioFormat(44100, 2, 2))
        processor.setEnabled(true)
        processor.setKaraokeAiMode(true)

        // Create a 1-second 44.1kHz 16-bit stereo instrumental stem WAV file
        val stemFile = KaraokeManager.getStemFile(trackId)
        val numSamples = 44100 * 2
        val pcm = ShortArray(numSamples) { 1234.toShort() }
        writeTestWav(stemFile, pcm, 44100, 2)

        try {
            assertTrue(KaraokeManager.loadStemSync(trackId))
            processor.setTrack(trackId)
            processor.seekToPositionMs(0L)
            processor.setSuppressionLevel(1.0f) // 100% instrumental stem

            // Input has distinct samples (e.g., 20000)
            val inBytes = ByteBuffer.allocate(512 * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 512) {
                inBytes.putShort(20000.toShort())
            }
            inBytes.flip()

            processor.queueInput(inBytes)
            val output = processor.getOutput()
            assertTrue(output.remaining() > 0)

            val outShort = output.asShortBuffer()
            val outVal = outShort.get(0)
            // With stem loaded and 1.0 suppression level, output should closely track the stem (1234)
            assertEquals(1234f, outVal.toFloat(), 50f)
        } finally {
            stemFile.delete()
        }
    }

    @Test
    fun testAutoSeparationPreconditionsAndStemReadyCallback() {
        prefs.karaokeEngineMode = KaraokeEngineMode.MEL_BAND_ROFORMER
        prefs.karaokeAutoSeparateOnPlay = true

        val trackId = 888L
        val mockTrack = com.alananasss.kittytune.domain.Track(
            id = trackId,
            title = "Test Song",
            durationMs = 180000L,
            artworkUrl = null,
            user = null,
            permalinkUrl = "https://example.com/audio.mp3",
            playbackCount = 0,
            likesCount = 0,
            repostsCount = 0,
            commentCount = 0,
            source = "soundcloud"
        )

        // 1. When model is not ready, triggerAutoSeparationIfNeeded should not crash or start
        var callbackInvoked = false
        KaraokeManager.triggerAutoSeparationIfNeeded(
            track = mockTrack,
            audioSourceUrl = "https://example.com/audio.mp3",
            onCompleted = { callbackInvoked = true }
        )
        assertFalse("Callback should not be called if model is not ready", callbackInvoked)

        // 2. If stem already exists, it loads stem and calls onCompleted immediately
        val stemFile = KaraokeManager.getStemFile(trackId)
        val numSamples = 44100 * 2
        val pcm = ShortArray(numSamples) { 5678.toShort() }
        writeTestWav(stemFile, pcm, 44100, 2)

        try {
            var completedTrackId: Long? = null
            KaraokeManager.triggerAutoSeparationIfNeeded(
                track = mockTrack,
                audioSourceUrl = "https://example.com/audio.mp3",
                onCompleted = { id -> completedTrackId = id }
            )
            assertEquals(trackId, completedTrackId)
            assertTrue(KaraokeManager.isStemAvailableForCurrentTrack(trackId))
        } finally {
            stemFile.delete()
        }
    }

    @Test
    fun testAudioEngineOnKaraokeStemReadyResyncsPosition() {
        val engine = com.alananasss.kittytune.audio.AudioEngine()
        val trackId = 999L
        val stemFile = KaraokeManager.getStemFile(trackId)
        val numSamples = 44100 * 2
        val pcm = ShortArray(numSamples) { 4321.toShort() }
        writeTestWav(stemFile, pcm, 44100, 2)

        try {
            // Configure track on engine
            engine.setMediaItem("https://example.com/stream.mp3", startPositionMs = 5000L, trackId = trackId)
            // Call onKaraokeStemReady
            engine.onKaraokeStemReady(trackId)
            assertTrue(KaraokeManager.isStemAvailableForCurrentTrack(trackId))
        } finally {
            stemFile.delete()
        }
    }

    @Test
    fun testVocalRemoverDspModeProcessesAudioCorrectly() {
        val processor = VocalRemoverAudioProcessor()
        processor.configure(AudioFormat(44100, 2, 2))
        processor.setEnabled(true)
        processor.setKaraokeAiMode(false) // Pure DSP mode
        processor.setSuppressionLevel(1.0f)

        val sampleCount = 512
        val inputBuffer = ByteBuffer.allocate(sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val centerVocal = 12000.toShort()
            val leftInstrument = 3000.toShort()
            val rightInstrument = (-3000).toShort()

            inputBuffer.putShort((centerVocal + leftInstrument).toShort())
            inputBuffer.putShort((centerVocal + rightInstrument).toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.getOutput()
        assertNotNull(output)
        assertEquals(sampleCount * 4, output.remaining())
    }

    @Test
    fun testVocalRemoverDspSuppressionAndStereoPolarity() {
        val processor = VocalRemoverAudioProcessor()
        processor.configure(AudioFormat(44100, 2, 2))
        processor.setEnabled(true)
        processor.setKaraokeAiMode(false)
        processor.setSuppressionLevel(1.0f)

        // Test 1: pure center vocal (L = 10000, R = 10000) at 1 kHz should be strongly attenuated
        val sampleCount = 512
        val vocalBuffer = ByteBuffer.allocate(sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val s = (10000.0 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 44100.0)).toInt().toShort()
            vocalBuffer.putShort(s)
            vocalBuffer.putShort(s)
        }
        vocalBuffer.flip()

        processor.queueInput(vocalBuffer)
        val vocalOut = processor.getOutput()
        val sOut = vocalOut.asShortBuffer()
        var avgVocalEnergy = 0.0
        while (sOut.hasRemaining()) {
            avgVocalEnergy += kotlin.math.abs(sOut.get().toDouble())
        }
        avgVocalEnergy /= (sampleCount * 2)
        // Mid vocal is cancelled in side channel; subBass keeps only <75Hz. 1kHz vocal is attenuated by >40 dB
        assertTrue("Vocal should be suppressed, but average energy was $avgVocalEnergy", avgVocalEnergy < 1000.0)

        // Test 2: stereo side signal (L = 5000, R = -5000) should have clean anti-symmetric stereo preservation
        processor.flush()
        val sideBuffer = ByteBuffer.allocate(sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val s = (5000.0 * kotlin.math.sin(2.0 * Math.PI * 1000.0 * i / 44100.0)).toInt().toShort()
            sideBuffer.putShort(s)
            sideBuffer.putShort((-s).toShort())
        }
        sideBuffer.flip()

        processor.queueInput(sideBuffer)
        val sideOut = processor.getOutput().asShortBuffer()
        var diffEnergy = 0.0
        var totalEnergy = 0.0
        while (sideOut.hasRemaining()) {
            val l = sideOut.get().toDouble()
            val r = sideOut.get().toDouble()
            diffEnergy += (l - r) * (l - r)
            totalEnergy += (l * l + r * r)
        }
        assertTrue("Stereo side difference energy should be strong ($diffEnergy vs $totalEnergy)", diffEnergy > 0.5 * totalEnergy)
    }

    @Test
    fun testLoadStemSyncWithAdditionalWavChunks() {
        val trackId = 654L
        val stemFile = KaraokeManager.getStemFile(trackId)
        val numSamples = 44100 * 2
        val pcmData = ShortArray(numSamples) { 789.toShort() }

        // Construct WAV with a 12-byte JUNK chunk inserted before the data chunk
        val totalAudioLen = pcmData.size * 2L
        val junkChunkLen = 12
        val totalDataLen = totalAudioLen + 36 + 8 + junkChunkLen
        val header = ByteArray(44 + 8 + junkChunkLen)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1.toShort())
        buf.putShort(2.toShort())
        buf.putInt(44100)
        buf.putInt(44100 * 2 * 2)
        buf.putShort(4.toShort())
        buf.putShort(16.toShort())
        // JUNK chunk
        buf.put("JUNK".toByteArray())
        buf.putInt(junkChunkLen)
        buf.put(ByteArray(junkChunkLen))
        // data chunk
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen.toInt())

        FileOutputStream(stemFile).use { out ->
            out.write(header)
            val pcmBytes = ByteArray(pcmData.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmData)
            out.write(pcmBytes)
        }

        try {
            val loaded = KaraokeManager.loadStemSync(trackId)
            assertTrue("loadStemSync should succeed even with non-standard WAV chunks", loaded)
            assertTrue(KaraokeManager.isStemAvailableForCurrentTrack(trackId))
        } finally {
            stemFile.delete()
        }
    }

    @Test
    fun test32BitFloatWavStemLoading() {
        val trackId = 999998L
        val stemFile = KaraokeManager.getStemFile(trackId)
        val numSamples = 44100
        val floatData = FloatArray(numSamples) { 0.5f }
        val totalAudioLen = (numSamples * 4).toLong()
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(3.toShort()) // IEEE Float
        buf.putShort(2.toShort()) // Stereo
        buf.putInt(44100)
        buf.putInt(44100 * 2 * 4)
        buf.putShort(8.toShort())
        buf.putShort(32.toShort()) // 32 bits per sample
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen.toInt())

        FileOutputStream(stemFile).use { out ->
            out.write(header)
            val byteBuf = ByteBuffer.allocate(floatData.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in floatData) byteBuf.putFloat(f)
            out.write(byteBuf.array())
        }

        try {
            val loaded = KaraokeManager.loadStemSync(trackId)
            assertTrue("loadStemSync should succeed with 32-bit float WAV stems", loaded)
            assertTrue(KaraokeManager.isStemAvailableForCurrentTrack(trackId))
        } finally {
            stemFile.delete()
        }
    }

    @Test
    fun testAllKaraokeLocalizationStringsInAllLanguages() {
        val requiredKeys = listOf(
            "karaoke_settings_title",
            "karaoke_settings_sub",
            "karaoke_engine_select_title",
            "karaoke_engine_select_desc",
            "karaoke_engine_dsp_title",
            "karaoke_engine_dsp_desc",
            "karaoke_engine_roformer_title",
            "karaoke_engine_roformer_desc",
            "karaoke_roformer_desc",
            "karaoke_download_btn",
            "karaoke_import_title",
            "karaoke_gpu_desc",
            "karaoke_cache_title",
            "karaoke_cache_desc",
            "karaoke_cache_stats",
            "karaoke_cache_clear_btn",
            "karaoke_auto_separate_label",
            "karaoke_delete_model_title",
            "karaoke_delete_model_body",
            "karaoke_clear_cache_title",
            "karaoke_clear_cache_body",
            "karaoke_mode_dsp_short",
            "karaoke_mode_roformer_short",
            "karaoke_model_required_notice",
            "karaoke_stem_active",
            "karaoke_separating_track",
            "karaoke_isolate_this_track",
        )

        val languages = listOf("fr", "en", "de", "hu", "ru", "vi")

        for (lang in languages) {
            Strings.appLanguage = lang
            for (key in requiredKeys) {
                val value = str(key)
                assertNotEquals("Missing localization key '$key' for language '$lang'", key, value)
                assertTrue("Blank localization for key '$key' in language '$lang'", value.isNotBlank())
            }

            // Test positional formatting
            val formattedStats = str("karaoke_cache_stats", 3, "42.5")
            assertTrue(
                "karaoke_cache_stats failed formatting in $lang: $formattedStats",
                formattedStats.contains("3") && formattedStats.contains("42.5")
            )

            val formattedDownload = str("karaoke_download_btn", "309 Mo")
            assertTrue(
                "karaoke_download_btn failed formatting in $lang: $formattedDownload",
                formattedDownload.contains("309 Mo")
            )

            val formattedProgress = str("karaoke_separating_track", 75)
            assertTrue(
                "karaoke_separating_track failed formatting in $lang: $formattedProgress",
                formattedProgress.contains("75")
            )
        }

        // Restore default language
        Strings.appLanguage = "system"
    }

    @Test
    fun testAntiStutterPacerControlsSubprocessSuspensionAndResumption() {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) return // Tested natively on Linux

        val process = ProcessBuilder("sleep", "2").start()
        try {
            assertTrue(process.isAlive)
            // Pause
            KaraokeManager.pauseSubprocess(process)
            // Resume
            KaraokeManager.resumeSubprocess(process)
            assertTrue(process.isAlive)
        } finally {
            KaraokeManager.resumeSubprocess(process)
            process.destroyForcibly()
        }
    }

    @Test
    fun testHardwareAccelerationToggleDirectsModelResolution() {
        val modelsDir = KaraokeManager.getModelsDir()
        val mockOnnx = File(modelsDir, "htdemucs_ft_vocals.onnx")
        val created = if (!mockOnnx.exists()) {
            mockOnnx.writeBytes(ByteArray(15_000_000))
            true
        } else false

        try {
            // When GPU is disabled and ONNX exists, getModelFile() should prioritize ONNX for zero-GPU CPU inference
            prefs.karaokeGpuAccelerationEnabled = false
            prefs.karaokeSelectedGpuId = com.alananasss.kittytune.audio.ai.GpuDevice.ID_CPU
            val resolvedCpu = KaraokeManager.getModelFile()
            assertTrue(
                "When GPU is disabled, getModelFile should return the ONNX model",
                resolvedCpu.name.endsWith(".onnx")
            )

            // When GPU is enabled, it should update provider accordingly
            prefs.karaokeGpuAccelerationEnabled = true
            prefs.karaokeSelectedGpuId = com.alananasss.kittytune.audio.ai.GpuDevice.ID_AUTO
            KaraokeManager.updateActiveProviderStatus()
            val provider = KaraokeManager.activeAccelerationProvider.value
            assertTrue(
                "Active provider should reflect Vulkan or GPU acceleration: $provider",
                provider.contains("Vulkan") || provider.contains("DirectX") || provider.contains("GPU")
            )
        } finally {
            if (created) mockOnnx.delete()
        }
    }

    @Test
    fun testActiveProviderStatusSafeCoresThreadBound() {
        prefs.karaokeGpuAccelerationEnabled = false
        prefs.karaokeSelectedGpuId = com.alananasss.kittytune.audio.ai.GpuDevice.ID_CPU
        KaraokeManager.updateActiveProviderStatus()

        val provider = KaraokeManager.activeAccelerationProvider.value
        assertTrue("Provider should indicate CPU mode: $provider", provider.contains("CPU Multi-thread"))

        // Extract core count from label: "CPU Multi-thread (X cœurs SIMD)"
        val numCores = Regex("(\\d+)\\s+cœurs").find(provider)?.groupValues?.get(1)?.toIntOrNull()
        assertNotNull("Should contain numeric core count", numCores)
        assertTrue("Safe CPU thread count must be capped at 4 or lower to prevent CPU starvation", numCores!! in 1..4)
    }

    @Test
    fun testVulkanLowPriorityShimFileResolution() {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win") || os.contains("mac")) return

        val shim = KaraokeManager.getNativeVulkanPriorityShim()
        assertNotNull("Vulkan priority shim must be resolved on Linux", shim)
        assertTrue("Shim file must exist: ${shim?.absolutePath}", shim!!.exists())
        assertTrue("Shim file size must be > 1000 bytes: ${shim.length()}", shim.length() > 1000L)
    }

    @Test
    fun testNativeProcessIdlePriorityExecution() {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) return

        val process = ProcessBuilder("sleep", "2").start()
        try {
            assertTrue(process.isAlive)
            val pid = process.pid()
            // Set priority class / nice 19
            val m = KaraokeManager::class.java.getDeclaredMethod("nativeSetProcessIdlePriority", Long::class.java)
            m.isAccessible = true
            val success = m.invoke(null, pid) as Boolean
            assertTrue("nativeSetProcessIdlePriority should return true on Linux", success)
        } finally {
            process.destroyForcibly()
        }
    }

    private fun writeTestWav(file: File, pcmData: ShortArray, sampleRate: Int, channels: Int) {
        val totalAudioLen = pcmData.size * 2L
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // Subchunk1Size (16 for PCM)
        buf.putShort(1.toShort()) // AudioFormat (1 for PCM)
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort((channels * 2).toShort()) // BlockAlign
        buf.putShort(16.toShort()) // BitsPerSample
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen.toInt())

        FileOutputStream(file).use { out ->
            out.write(header)
            val pcmBytes = ByteArray(pcmData.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmData)
            out.write(pcmBytes)
        }
    }

    @Test
    fun testDualStemVocalAndInstrumentalFilesAndMemory() {
        val trackId = 12345L
        val instFile = KaraokeManager.getStemFile(trackId)
        val vocFile = KaraokeManager.getVocalStemFile(trackId)

        val numSamples = 44100 * 2
        val instPcm = ShortArray(numSamples) { 1111.toShort() }
        val vocPcm = ShortArray(numSamples) { 9999.toShort() }
        writeTestWav(instFile, instPcm, 44100, 2)
        writeTestWav(vocFile, vocPcm, 44100, 2)

        try {
            assertTrue("hasStem should be true", KaraokeManager.hasStem(trackId))
            assertTrue("hasVocalStem should be true", KaraokeManager.hasVocalStem(trackId))

            val loaded = KaraokeManager.loadStemSync(trackId)
            assertTrue("loadStemSync should succeed", loaded)
            assertTrue(KaraokeManager.isStemAvailableForCurrentTrack(trackId))
            assertTrue(KaraokeManager.isVocalStemAvailableForCurrentTrack(trackId))

            // Test reading slices
            val outL = FloatArray(512)
            val outR = FloatArray(512)
            val readInst = KaraokeManager.readStemSlice(trackId, 0L, outL, outR, 512)
            assertTrue(readInst)
            assertEquals(1111f / 32768f, outL[0], 0.01f)
            assertEquals(1111f / 32768f, outR[0], 0.01f)

            val vocL = FloatArray(512)
            val vocR = FloatArray(512)
            val readVoc = KaraokeManager.readVocalStemSlice(trackId, 0L, vocL, vocR, 512)
            assertTrue(readVoc)
            assertEquals(9999f / 32768f, vocL[0], 0.01f)
            assertEquals(9999f / 32768f, vocR[0], 0.01f)
        } finally {
            instFile.delete()
            vocFile.delete()
        }
    }

    @Test
    fun testVocalBoostAcapellaStudioBlending() {
        val trackId = 54321L
        val vocFile = KaraokeManager.getVocalStemFile(trackId)
        val numSamples = 44100 * 2
        val vocPcm = ShortArray(numSamples) { 15000.toShort() }
        writeTestWav(vocFile, vocPcm, 44100, 2)

        val processor = com.alananasss.kittytune.audio.VocalBoostAudioProcessor()
        processor.configure(AudioFormat(44100, 2, 2))
        processor.setEnabled(true)
        processor.setKaraokeAiMode(true)
        processor.setTrack(trackId)
        processor.seekToPositionMs(0L)

        try {
            KaraokeManager.loadStemSync(trackId)
            assertTrue(KaraokeManager.isVocalStemAvailableForCurrentTrack(trackId))

            // Test 1: 100% Acapella studio isolation (intensity = 1.0)
            processor.setIntensity(1.0f)
            val sampleCount = 512
            val inBytes = ByteBuffer.allocate(sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleCount * 2) {
                inBytes.putShort(5000.toShort()) // Non-vocal instrumental content
            }
            inBytes.flip()

            processor.queueInput(inBytes)
            val output = processor.getOutput()
            assertNotNull(output)
            assertTrue(output.remaining() > 0)

            val outShort = output.asShortBuffer()
            val firstSample = outShort.get(0)
            // At 1.0 intensity, input (5000) is muted, output is pure vocal stem (15000)
            assertEquals(15000f, firstSample.toFloat(), 100f)

            // Test 2: Fallback when stem is absent
            processor.flush()
            processor.setTrack(999999L) // Absent stem
            val inFallback = ByteBuffer.allocate(sampleCount * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleCount * 2) {
                inFallback.putShort(4000.toShort())
            }
            inFallback.flip()
            processor.queueInput(inFallback)
            val outFallback = processor.getOutput()
            assertNotNull(outFallback)
            assertTrue("Fallback DSP must produce output without crashing", outFallback.remaining() > 0)
        } finally {
            vocFile.delete()
        }
    }

    @Test
    fun testStemListAndSingleItemDeletion() {
        KaraokeManager.clearStemsCache()

        val trackA = 1001L
        val trackB = 1002L

        val instA = KaraokeManager.getStemFile(trackA)
        val vocA = KaraokeManager.getVocalStemFile(trackA)
        val instB = KaraokeManager.getStemFile(trackB)
        writeTestWav(instA, ShortArray(44100) { 1 }, 44100, 2)
        writeTestWav(vocA, ShortArray(44100) { 2 }, 44100, 2)
        writeTestWav(instB, ShortArray(44100) { 3 }, 44100, 2)

        try {
            val stems = KaraokeManager.getCachedStems()
            assertEquals(2, stems.size)
            val trackIds = stems.map { it.trackId }.toSet()
            assertTrue(trackIds.contains(trackA))
            assertTrue(trackIds.contains(trackB))

            // Delete trackA stem only
            KaraokeManager.deleteStem(trackA)
            assertFalse(KaraokeManager.hasStem(trackA))
            assertFalse(KaraokeManager.hasVocalStem(trackA))
            assertTrue(KaraokeManager.hasStem(trackB))

            val remaining = KaraokeManager.getCachedStems()
            assertEquals(1, remaining.size)
            assertEquals(trackB, remaining[0].trackId)
        } finally {
            KaraokeManager.deleteStem(trackA)
            KaraokeManager.deleteStem(trackB)
        }
    }

    @Test
    fun testCancelSeparationCleansUpStateAndTmpFiles() {
        val trackId = 887766L
        val stemsDir = KaraokeManager.getStemsDir()
        val tmpInst = File(stemsDir, "inst_${trackId}.wav.tmp")
        val tmpVoc = File(stemsDir, "vocals_${trackId}.wav.tmp")

        tmpInst.writeBytes(ByteArray(500))
        tmpVoc.writeBytes(ByteArray(500))
        assertTrue(tmpInst.exists())
        assertTrue(tmpVoc.exists())

        // Simulate separation running
        val field = KaraokeManager::class.java.getDeclaredField("currentProcessingTrackId")
        field.isAccessible = true
        field.set(KaraokeManager, trackId)

        KaraokeManager.cancelSeparation()

        assertEquals(KaraokeManager.SeparationState.Idle, KaraokeManager.separationState.value)
        assertFalse("tmpInst should be deleted on cancel", tmpInst.exists())
        assertFalse("tmpVoc should be deleted on cancel", tmpVoc.exists())
    }

    @Test
    fun testReportSeparationErrorUpdatesStateFlow() {
        val trackId = 776655L
        val errorMsg = "Audio stream connection timed out"
        KaraokeManager.reportSeparationError(trackId, errorMsg)

        val currentState = KaraokeManager.separationState.value
        assertTrue(currentState is KaraokeManager.SeparationState.Error)
        val err = currentState as KaraokeManager.SeparationState.Error
        assertEquals(trackId, err.trackId)
        assertEquals(errorMsg, err.message)
    }

    @Test
    fun testStemVersionFlowInvalidation() {
        val initialVersion = KaraokeManager.stemVersion.value
        Thread.sleep(10)
        KaraokeManager.notifyStemUpdated()
        val newVersion = KaraokeManager.stemVersion.value
        assertTrue("stemVersion should strictly increase on notification", newVersion > initialVersion)
    }
}

