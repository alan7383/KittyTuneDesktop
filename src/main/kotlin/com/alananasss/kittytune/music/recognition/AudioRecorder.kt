package com.alananasss.kittytune.music.recognition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

import com.alananasss.kittytune.core.str

class RecordControl {
    @Volatile var shouldStop: Boolean = false
}

data class AudioInputDevice(
    val id: String,
    val name: String,
    val isDesktopAudio: Boolean = false
)

object AudioDeviceManager {
    fun getAvailableInputDevices(): List<AudioInputDevice> {
        val list = mutableListOf<AudioInputDevice>()
        list.add(AudioInputDevice("desktop_audio", str("audio_source_desktop"), isDesktopAudio = true))

        try {
            val mixerInfos = AudioSystem.getMixerInfo()
            val seenNames = mutableSetOf<String>()

            for (info in mixerInfos) {
                try {
                    val mixer = AudioSystem.getMixer(info)
                    val lineInfo = DataLine.Info(TargetDataLine::class.java, null)
                    if (mixer.isLineSupported(lineInfo)) {
                        val rawName = info.name.trim()
                        val isIgnored = rawName.contains("Port", ignoreCase = true) ||
                                        rawName.contains("Pilote de capture", ignoreCase = true) ||
                                        rawName.contains("Primary Sound", ignoreCase = true) ||
                                        rawName.contains("Primary capture", ignoreCase = true)
                        if (rawName.isNotEmpty() && !seenNames.contains(rawName) && !isIgnored) {
                            seenNames.add(rawName)
                            val isDesktop = rawName.contains("Mix", ignoreCase = true) ||
                                            rawName.contains("Stereo", ignoreCase = true) ||
                                            rawName.contains("Stéréo", ignoreCase = true) ||
                                            rawName.contains("What U Hear", ignoreCase = true) ||
                                            rawName.contains("Loopback", ignoreCase = true) ||
                                            rawName.contains("Bureau", ignoreCase = true)
                            val label = if (isDesktop) "$rawName (${str("audio_source_desktop_tag")})" else rawName
                            list.add(AudioInputDevice(id = rawName, name = label, isDesktopAudio = isDesktop))
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return list.distinctBy { it.id }
    }
}

/**
 * Records audio in 16-bit PCM mono at 16kHz, which is the required format for Shazam DejaVu signatures.
 */
class AudioRecorder {
    private val targetSampleRate = 16000f

    suspend fun recordAudio(
        durationMs: Long,
        control: RecordControl? = null,
        selectedDeviceName: String? = null,
        onProgress: ((ByteArray) -> Unit)? = null
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            val candidateFormats = listOf(
                AudioFormat(16000f, 16, 1, true, false),
                AudioFormat(44100f, 16, 1, true, false),
                AudioFormat(48000f, 16, 1, true, false),
                AudioFormat(44100f, 16, 2, true, false),
                AudioFormat(48000f, 16, 2, true, false)
            )

            var targetDataLine: TargetDataLine? = null
            var actualFormat: AudioFormat? = null

            // Handle explicit Desktop Audio selection via WASAPI Loopback on Windows out-of-the-box
            if (selectedDeviceName == "desktop_audio" || selectedDeviceName == null) {
                try {
                    println("DEBUG: [AudioRecorder] Attempting WASAPI Loopback capture for device: $selectedDeviceName")
                    val wasapiPcm = WasapiLoopbackRecorder.recordDesktopAudio(durationMs, control, onProgress)
                    if (wasapiPcm != null && wasapiPcm.isNotEmpty()) {
                        println("DEBUG: [AudioRecorder] WASAPI Loopback capture returned ${wasapiPcm.size} bytes successfully")
                        saveWavFile(wasapiPcm, File("last_recorded_audio.wav"))
                        return@withContext wasapiPcm
                    }
                    println("DEBUG: [AudioRecorder] WASAPI Loopback returned null or empty, falling back to Java Sound mixers...")
                } catch (e: Exception) {
                    println("DEBUG: [AudioRecorder] WASAPI Loopback threw exception: ${e.message}")
                }

                val mixerInfos = AudioSystem.getMixerInfo()
                val desktopInfo = mixerInfos.firstOrNull { info ->
                    val name = info.name
                    name.contains("Mix", ignoreCase = true) ||
                    name.contains("Stereo", ignoreCase = true) ||
                    name.contains("Stéréo", ignoreCase = true) ||
                    name.contains("What U Hear", ignoreCase = true) ||
                    name.contains("Loopback", ignoreCase = true) ||
                    name.contains("Bureau", ignoreCase = true) ||
                    name.contains("Monitor", ignoreCase = true) ||
                    name.contains("PulseAudio", ignoreCase = true) ||
                    name.contains("PipeWire", ignoreCase = true) ||
                    name.contains("default", ignoreCase = true)
                }
                if (desktopInfo != null) {
                    val mixer = AudioSystem.getMixer(desktopInfo)
                    for (fmt in candidateFormats) {
                        try {
                            val info = DataLine.Info(TargetDataLine::class.java, fmt)
                            if (mixer.isLineSupported(info)) {
                                val line = mixer.getLine(info) as TargetDataLine
                                line.open(fmt)
                                targetDataLine = line
                                actualFormat = fmt
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // If a specific device is selected by name, search mixers for it
            if (targetDataLine == null && !selectedDeviceName.isNullOrBlank() && selectedDeviceName != "default" && selectedDeviceName != "desktop_audio") {
                val mixerInfos = AudioSystem.getMixerInfo()
                val targetInfo = mixerInfos.firstOrNull { it.name.trim() == selectedDeviceName.trim() }
                if (targetInfo != null) {
                    val mixer = AudioSystem.getMixer(targetInfo)
                    for (fmt in candidateFormats) {
                        try {
                            val info = DataLine.Info(TargetDataLine::class.java, fmt)
                            if (mixer.isLineSupported(info)) {
                                val line = mixer.getLine(info) as TargetDataLine
                                line.open(fmt)
                                targetDataLine = line
                                actualFormat = fmt
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    if (targetDataLine == null) {
                        println("DEBUG: [AudioRecorder] Requested device '$selectedDeviceName' unsupported, fallback to default")
                    }
                } else {
                    println("DEBUG: [AudioRecorder] Requested device '$selectedDeviceName' not found, fallback to default")
                }
            }

            // Fallback: Default input line
            if (targetDataLine == null) {
                for (fmt in candidateFormats) {
                    try {
                        val info = DataLine.Info(TargetDataLine::class.java, fmt)
                        if (AudioSystem.isLineSupported(info)) {
                            val line = AudioSystem.getLine(info) as TargetDataLine
                            line.open(fmt)
                            targetDataLine = line
                            actualFormat = fmt
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            val line = targetDataLine ?: throw IllegalStateException("Aucune ligne d'entrée audio n'est disponible sur cet ordinateur")
            val fmt = actualFormat ?: candidateFormats.first()

            line.start()

            val rawStream = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            var lastCheckTime = startTime

            try {
                while (coroutineContext.isActive && (System.currentTimeMillis() - startTime) < durationMs) {
                    if (control?.shouldStop == true) {
                        break
                    }
                    val readResult = line.read(buffer, 0, buffer.size)
                    if (readResult > 0) {
                        rawStream.write(buffer, 0, readResult)
                    }

                    if (onProgress != null && (System.currentTimeMillis() - lastCheckTime) >= 3000L) {
                        lastCheckTime = System.currentTimeMillis()
                        val resampled = convertTo16kHzMono(rawStream.toByteArray(), fmt)
                        onProgress(resampled)
                    }
                }
            } finally {
                try {
                    line.stop()
                    line.close()
                } catch (_: Exception) {}
            }

            val finalPcm = convertTo16kHzMono(rawStream.toByteArray(), fmt)
            saveWavFile(finalPcm, File("last_recorded_audio.wav"))
            finalPcm
        }
    }

    private fun convertTo16kHzMono(inputPcm: ByteArray, sourceFormat: AudioFormat): ByteArray {
        if (inputPcm.isEmpty()) return ByteArray(0)

        val srcRate = sourceFormat.sampleRate.toInt().coerceAtLeast(8000)
        val channels = sourceFormat.channels.coerceAtLeast(1)
        val sampleSizeInBits = sourceFormat.sampleSizeInBits
        val bytesPerSample = (sampleSizeInBits / 8).coerceAtLeast(1)
        val isBigEndian = sourceFormat.isBigEndian
        val frameSize = channels * bytesPerSample

        val totalFrames = inputPcm.size / frameSize
        if (totalFrames == 0) return ByteArray(0)

        val bb = ByteBuffer.wrap(inputPcm).order(if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val monoShorts = ShortArray(totalFrames)

        for (i in 0 until totalFrames) {
            var sumChan = 0.0
            for (c in 0 until channels) {
                if (!bb.hasRemaining()) break
                val sampleValue: Double = when (bytesPerSample) {
                    2 -> bb.short.toDouble()
                    3 -> {
                        val b1 = bb.get().toInt() and 0xFF
                        val b2 = bb.get().toInt() and 0xFF
                        val b3 = bb.get().toInt()
                        val sample24 = (b3 shl 16) or (b2 shl 8) or b1
                        val signed24 = if ((sample24 and 0x800000) != 0) sample24 or -0x1000000 else sample24
                        signed24 / 256.0
                    }
                    4 -> bb.int.toDouble() / 65536.0
                    else -> {
                        repeat(bytesPerSample) { if (bb.hasRemaining()) bb.get() }
                        0.0
                    }
                }
                sumChan += sampleValue
            }
            monoShorts[i] = (sumChan / channels).toInt().coerceIn(-32768, 32767).toShort()
        }

        var maxSample = 0
        for (s in monoShorts) {
            val absS = kotlin.math.abs(s.toInt())
            if (absS > maxSample) maxSample = absS
        }

        if (maxSample in 10..25999) {
            val gain = 28000.0 / maxSample.toDouble()
            for (i in monoShorts.indices) {
                monoShorts[i] = (monoShorts[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
            }
        }

        val targetRate = 16000
        val outShorts: ShortArray = if (srcRate == targetRate) {
            monoShorts
        } else {
            val targetCount = ((totalFrames.toLong() * targetRate) / srcRate).toInt()
            if (targetCount <= 0) return ByteArray(0)

            val resampled = ShortArray(targetCount)
            val ratio = srcRate.toDouble() / targetRate.toDouble()

            for (i in 0 until targetCount) {
                val srcIndexDouble = i * ratio
                val srcIndex = srcIndexDouble.toInt()
                val frac = srcIndexDouble - srcIndex

                if (srcIndex + 1 < monoShorts.size) {
                    val s1 = monoShorts[srcIndex].toDouble()
                    val s2 = monoShorts[srcIndex + 1].toDouble()
                    resampled[i] = (s1 + frac * (s2 - s1)).toInt().coerceIn(-32768, 32767).toShort()
                } else if (srcIndex < monoShorts.size) {
                    resampled[i] = monoShorts[srcIndex]
                }
            }
            resampled
        }

        val resultBuffer = ByteBuffer.allocate(outShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in outShorts) {
            resultBuffer.putShort(s)
        }
        val finalPcm = resultBuffer.array()
        saveWavFile(finalPcm, File("last_recorded_audio.wav"))
        return finalPcm
    }

    companion object {
        fun saveWavFile(pcmData: ByteArray, outputFile: File) {
            if (pcmData.isEmpty()) return
            try {
                outputFile.outputStream().use { os ->
                    val bw = DataOutputStream(os)
                    val sampleRate = 16000
                    val channels = 1
                    val bitsPerSample = 16
                    val byteRate = sampleRate * channels * bitsPerSample / 8
                    val blockAlign = channels * bitsPerSample / 8

                    bw.writeBytes("RIFF")
                    bw.writeInt(Integer.reverseBytes(36 + pcmData.size))
                    bw.writeBytes("WAVE")
                    bw.writeBytes("fmt ")
                    bw.writeInt(Integer.reverseBytes(16))
                    bw.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
                    bw.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
                    bw.writeInt(Integer.reverseBytes(sampleRate))
                    bw.writeInt(Integer.reverseBytes(byteRate))
                    bw.writeShort(java.lang.Short.reverseBytes(blockAlign.toShort()).toInt())
                    bw.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())
                    bw.writeBytes("data")
                    bw.writeInt(Integer.reverseBytes(pcmData.size))
                    bw.write(pcmData)
                    bw.flush()
                }
                println("DEBUG: [AudioRecorder] Saved recorded audio WAV file: ${outputFile.absolutePath}")
            } catch (e: Exception) {
                println("DEBUG: [AudioRecorder] Failed to save WAV file: ${e.message}")
            }
        }
    }
}
