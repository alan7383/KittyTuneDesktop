package com.alananasss.kittytune.music.recognition

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WasapiLoopbackRecorder {
    private val CLSID_MMDeviceEnumerator = CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
    private val IID_IMMDeviceEnumerator = IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
    private val IID_IAudioClient = IID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}")
    private val IID_IAudioCaptureClient = IID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}")

    private const val CLSCTX_ALL = 23

    fun isSupported(): Boolean {
        return System.getProperty("os.name").contains("Windows", ignoreCase = true)
    }

    suspend fun recordDesktopAudio(
        durationMs: Long,
        control: RecordControl? = null,
        onProgress: ((ByteArray) -> Unit)? = null
    ): ByteArray? {
        if (!isSupported()) return null

        try {
            val coHr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
            println("DEBUG: [WASAPI Step 1] CoInitializeEx HRESULT: ${coHr.toInt()}")
        } catch (e: Exception) {
            println("DEBUG: [WASAPI Step 1] CoInitializeEx threw: ${e.message}")
        }

        val ppv = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            CLSID_MMDeviceEnumerator,
            null,
            CLSCTX_ALL,
            IID_IMMDeviceEnumerator,
            ppv
        )
        println("DEBUG: [WASAPI Step 2] CoCreateInstance HRESULT: ${hr.toInt()}, ptr: ${ppv.value}")
        if (hr.toInt() != 0 || ppv.value == null) return null

        val enumerator = MMDeviceEnumerator(ppv.value)
        val pDevice = PointerByReference()
        val epHr = enumerator.GetDefaultAudioEndpoint(0, 0, pDevice)
        println("DEBUG: [WASAPI Step 3] GetDefaultAudioEndpoint HRESULT: $epHr, ptr: ${pDevice.value}")
        if (epHr != 0 || pDevice.value == null) return null

        val device = MMDevice(pDevice.value)
        val pAudioClient = PointerByReference()
        val actHr = device.Activate(IID_IAudioClient, CLSCTX_ALL, null, pAudioClient)
        println("DEBUG: [WASAPI Step 4] device.Activate HRESULT: $actHr, ptr: ${pAudioClient.value}")
        if (actHr != 0 || pAudioClient.value == null) return null

        val audioClient = AudioClient(pAudioClient.value)
        val ppWfx = PointerByReference()
        val mixHr = audioClient.GetMixFormat(ppWfx)
        println("DEBUG: [WASAPI Step 5] GetMixFormat HRESULT: $mixHr, ptr: ${ppWfx.value}")
        if (mixHr != 0 || ppWfx.value == null) return null

        val initHr = audioClient.Initialize(0, 0x00020000, 10000000L, 0L, ppWfx.value, null)
        println("DEBUG: [WASAPI Step 6] Initialize HRESULT: $initHr")
        if (initHr != 0) return null

        val pCaptureClient = PointerByReference()
        val srvHr = audioClient.GetService(IID_IAudioCaptureClient, pCaptureClient)
        println("DEBUG: [WASAPI Step 7] GetService HRESULT: $srvHr, ptr: ${pCaptureClient.value}")
        if (srvHr != 0 || pCaptureClient.value == null) return null

        val captureClient = AudioCaptureClient(pCaptureClient.value)
        val startHr = audioClient.Start()
        println("DEBUG: [WASAPI Step 8] Start HRESULT: $startHr")
        if (startHr != 0) return null

        val rawStream = ByteArrayOutputStream()
        val startTime = System.currentTimeMillis()
        var lastCheckTime = startTime

        val pFormat = ppWfx.value
        val rawTag = pFormat.getShort(0).toInt() and 0xFFFF
        val channels = pFormat.getShort(2).toInt().coerceAtLeast(1)
        val sampleRate = pFormat.getInt(4).coerceAtLeast(8000)
        val blockAlign = pFormat.getShort(12).toInt().coerceAtLeast(2)
        val bitsPerSample = pFormat.getShort(14).toInt().coerceAtLeast(16)
        val cbSize = pFormat.getShort(16).toInt() and 0xFFFF

        val formatTag = if (rawTag == 0xFFFE && cbSize >= 22) {
            pFormat.getInt(24)
        } else {
            rawTag
        }

        println("DEBUG: [WASAPI Capture] Started loopback. rate=$sampleRate, channels=$channels, bits=$bitsPerSample, align=$blockAlign, tag=$rawTag, formatTag=$formatTag")

        try {
            while ((System.currentTimeMillis() - startTime) < durationMs) {
                if (control?.shouldStop == true) break

                val packetSize = IntByReference()
                captureClient.GetNextPacketSize(packetSize)

                while (packetSize.value > 0) {
                    val pData = PointerByReference()
                    val numFramesToRead = IntByReference()
                    val flags = IntByReference()
                    val devPos = PointerByReference()
                    val qpcPos = PointerByReference()

                    if (captureClient.GetBuffer(pData, numFramesToRead, flags, devPos, qpcPos) == 0) {
                        val numFrames = numFramesToRead.value
                        val bytesToRead = numFrames * blockAlign

                        if (bytesToRead > 0 && pData.value != null) {
                            val buf = ByteArray(bytesToRead)
                            if ((flags.value and 1) == 0) {
                                pData.value.read(0, buf, 0, bytesToRead)
                            } else {
                                java.util.Arrays.fill(buf, 0.toByte())
                            }
                            rawStream.write(buf)
                        }
                        captureClient.ReleaseBuffer(numFrames)
                    }
                    captureClient.GetNextPacketSize(packetSize)
                }

                Thread.sleep(20)

                if (onProgress != null && (System.currentTimeMillis() - lastCheckTime) >= 3000L) {
                    lastCheckTime = System.currentTimeMillis()
                    val pcm16k = processPcmTo16kHzMono(rawStream.toByteArray(), sampleRate, channels, bitsPerSample, formatTag)
                    onProgress(pcm16k)
                }
            }
        } finally {
            try { audioClient.Stop() } catch (_: Exception) {}
        }

        return processPcmTo16kHzMono(rawStream.toByteArray(), sampleRate, channels, bitsPerSample, formatTag)
    }

    private fun processPcmTo16kHzMono(raw: ByteArray, srcRate: Int, channels: Int, bitsPerSample: Int, formatTag: Int = 3): ByteArray {
        if (raw.isEmpty()) {
            println("DEBUG: [WASAPI Process] Raw input PCM is EMPTY (0 bytes)")
            return ByteArray(0)
        }
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = channels * bytesPerSample
        val totalFrames = raw.size / frameSize
        if (totalFrames == 0) {
            println("DEBUG: [WASAPI Process] totalFrames is 0 (raw size=${raw.size}, frameSize=$frameSize)")
            return ByteArray(0)
        }

        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
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
                    4 -> {
                        if (formatTag == 1) {
                            bb.int.toDouble() / 65536.0
                        } else {
                            val f = bb.float
                            if (f.isNaN() || f.isInfinite()) {
                                0.0
                            } else {
                                (f.coerceIn(-1.0f, 1.0f) * 32767.0)
                            }
                        }
                    }
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

        println("DEBUG: [WASAPI Process] Raw PCM bytes=${raw.size}, totalFrames=$totalFrames, srcRate=$srcRate, channels=$channels, bits=$bitsPerSample, formatTag=$formatTag, Peak Amplitude=$maxSample")

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
        val finalBytes = resultBuffer.array()
        println("DEBUG: [WASAPI Process] Final Resampled 16kHz Mono: ${outShorts.size} samples, ${finalBytes.size} bytes")
        return finalBytes
    }

    class MMDeviceEnumerator(p: Pointer) : Unknown(p) {
        fun GetDefaultAudioEndpoint(dataFlow: Int, role: Int, ppEndpoint: PointerByReference): Int {
            return _invokeNativeInt(4, arrayOf(pointer, dataFlow, role, ppEndpoint))
        }
    }

    class MMDevice(p: Pointer) : Unknown(p) {
        fun Activate(iid: IID, dwClsCtx: Int, pActivationParams: Pointer?, ppInterface: PointerByReference): Int {
            return _invokeNativeInt(3, arrayOf(pointer, iid, dwClsCtx, pActivationParams, ppInterface))
        }
    }

    class AudioClient(p: Pointer) : Unknown(p) {
        fun Initialize(shareMode: Int, streamFlags: Int, hnsBufferDuration: Long, hnsPeriodicity: Long, pFormat: Pointer, audioSessionGuid: Pointer?): Int {
            return _invokeNativeInt(3, arrayOf(pointer, shareMode, streamFlags, hnsBufferDuration, hnsPeriodicity, pFormat, audioSessionGuid))
        }
        fun GetMixFormat(ppDeviceFormat: PointerByReference): Int {
            return _invokeNativeInt(8, arrayOf(pointer, ppDeviceFormat))
        }
        fun Start(): Int {
            return _invokeNativeInt(10, arrayOf(pointer))
        }
        fun Stop(): Int {
            return _invokeNativeInt(11, arrayOf(pointer))
        }
        fun GetService(riid: IID, ppv: PointerByReference): Int {
            return _invokeNativeInt(14, arrayOf(pointer, riid, ppv))
        }
    }

    class AudioCaptureClient(p: Pointer) : Unknown(p) {
        fun GetBuffer(ppData: PointerByReference, pNumFramesToRead: IntByReference, pdwFlags: IntByReference, pu64DevicePosition: PointerByReference, pu64QPCPosition: PointerByReference): Int {
            return _invokeNativeInt(3, arrayOf(pointer, ppData, pNumFramesToRead, pdwFlags, pu64DevicePosition, pu64QPCPosition))
        }
        fun ReleaseBuffer(numFramesRead: Int): Int {
            return _invokeNativeInt(4, arrayOf(pointer, numFramesRead))
        }
        fun GetNextPacketSize(pNumFramesInNextPacket: IntByReference): Int {
            return _invokeNativeInt(5, arrayOf(pointer, pNumFramesInNextPacket))
        }
    }

    @Structure.FieldOrder("wFormatTag", "nChannels", "nSamplesPerSec", "nAvgBytesPerSec", "nBlockAlign", "wBitsPerSample", "cbSize")
    open class WAVEFORMATEX : Structure {
        @JvmField var wFormatTag: Short = 0
        @JvmField var nChannels: Short = 0
        @JvmField var nSamplesPerSec: Int = 0
        @JvmField var nAvgBytesPerSec: Int = 0
        @JvmField var nBlockAlign: Short = 0
        @JvmField var wBitsPerSample: Short = 0
        @JvmField var cbSize: Short = 0

        constructor(p: Pointer) : super(p)
    }
}
