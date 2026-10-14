package com.alananasss.kittytune.audio.ai.karaoke

import com.alananasss.kittytune.audio.ai.GpuDetector
import com.alananasss.kittytune.audio.ai.GpuDevice
import com.alananasss.kittytune.audio.ai.GpuVendor
import com.alananasss.kittytune.audio.ai.OnnxHelper
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.data.local.KaraokeEngineMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.audio.HlsStreamAdapter
import com.alananasss.kittytune.data.cache.AudioCache
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Logger
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import okhttp3.Request
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Manages HTDemucs v4 AI Source Separation and Karaoke mode:
 * - Model lifecycle: in-app manual downloading, integrity checking, deletion, import.
 * - Hardware acceleration via native Demucs-RS (Vulkan on Linux / Windows).
 * - Background stem extraction: isolates vocals & instrumentals into local cache.
 * - Playback synchronization: supplies studio-quality instrumental audio to [VocalRemoverAudioProcessor].
 */
object KaraokeManager {

    private const val TAG = "KaraokeManager"
    const val MODEL_FILE_NAME = "htdemucs.safetensors"
    const val MODEL_TOTAL_BYTES = 84_000_000L // ~84 MB

    const val DOWNLOAD_URL = "https://huggingface.co/nikhilunni/demucs-rs/resolve/main/htdemucs.safetensors"
    val FALLBACK_URLS = listOf(
        "https://huggingface.co/nikhilunni/demucs-rs/resolve/main/htdemucs.safetensors",
    )

    sealed class ModelDownloadState {
        data object NotDownloaded : ModelDownloadState()
        data class Downloading(val progress: Float, val bytesRead: Long, val totalBytes: Long) : ModelDownloadState()
        data object Ready : ModelDownloadState()
        data class Error(val message: String) : ModelDownloadState()
    }

    sealed class SeparationState {
        data object Idle : SeparationState()
        data class Processing(
            val progress: Float,
            val trackId: Long,
            val trackTitle: String? = null,
            val statusDetail: String? = null
        ) : SeparationState()
        data class Completed(val trackId: Long) : SeparationState()
        data class Error(val trackId: Long, val message: String) : SeparationState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var downloadJob: Job? = null
    private var separationJob: Job? = null

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val _separationState = MutableStateFlow<SeparationState>(SeparationState.Idle)
    val separationState: StateFlow<SeparationState> = _separationState.asStateFlow()

    private val _stemVersion = MutableStateFlow(0L)
    val stemVersion: StateFlow<Long> = _stemVersion.asStateFlow()

    fun notifyStemUpdated() {
        _stemVersion.value = System.currentTimeMillis()
    }

    fun reportSeparationError(trackId: Long, message: String) {
        _separationState.value = SeparationState.Error(trackId, message)
    }

    private val _activeAccelerationProvider = MutableStateFlow("DirectML · CPU")
    val activeAccelerationProvider: StateFlow<String> = _activeAccelerationProvider.asStateFlow()

    @Volatile
    private var currentProcessingTrackId: Long? = null
    @Volatile
    private var activeProcess: Process? = null
    private val completionListeners = java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.CopyOnWriteArrayList<(Long) -> Unit>>()

    // Stem playback synchronization. Stems are file-backed readers swapped per track:
    // the WAV stays on disk and slices are read on demand out of the OS page cache
    // (evictable, not heap) instead of living as full FloatArrays in the JVM.
    @Volatile
    private var currentActiveStemTrackId: Long? = null
    @Volatile
    private var currentStemAccess: StemFileAccess? = null
    @Volatile
    private var currentVocalStemAccess: StemFileAccess? = null

    init {
        com.alananasss.kittytune.audio.NormalizationAudioProcessor.loadNativeLibrary()
        checkModelStatus()
        updateActiveProviderStatus()
    }

    @JvmStatic
    private external fun nativePauseProcess(pid: Long): Boolean
    @JvmStatic
    private external fun nativeResumeProcess(pid: Long): Boolean
    @JvmStatic
    private external fun nativeSetProcessIdlePriority(pid: Long): Boolean

    private val isChrtAvailable by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win") || os.contains("mac")) false
        else try {
            ProcessBuilder("chrt", "-i", "0", "true").start().waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private val isIoniceAvailable by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win") || os.contains("mac")) false
        else try {
            ProcessBuilder("ionice", "-c", "3", "true").start().waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun pauseSubprocess(process: Process) {
        val pid = runCatching { process.pid() }.getOrNull() ?: return
        val ok = runCatching { nativePauseProcess(pid) }.getOrDefault(false)
        if (!ok) {
            val os = System.getProperty("os.name", "").lowercase()
            if (!os.contains("win")) {
                runCatching { Runtime.getRuntime().exec(arrayOf("kill", "-STOP", pid.toString())) }
            }
        }
    }

    fun resumeSubprocess(process: Process) {
        val pid = runCatching { process.pid() }.getOrNull() ?: return
        val ok = runCatching { nativeResumeProcess(pid) }.getOrDefault(false)
        if (!ok) {
            val os = System.getProperty("os.name", "").lowercase()
            if (!os.contains("win")) {
                runCatching { Runtime.getRuntime().exec(arrayOf("kill", "-CONT", pid.toString())) }
            }
        }
    }

    /**
     * Closes and clears any cached ONNX session so that settings changes (like switching GPU/CPU) take effect immediately.
     */
    fun invalidateSession() {
        synchronized(sessionLock) {
            runCatching { cachedSession?.close() }
            cachedSession = null
            cachedSessionModelPath = null
            cachedSessionGpuKey = null
        }
    }

    /**
     * Refreshes the display string for the active acceleration provider based on OS,
     * available GPU hardware, and user settings.
     */
    fun updateActiveProviderStatus() {
        val prefs = PlayerPreferences()
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        _activeAccelerationProvider.value = if (prefs.karaokeGpuAccelerationEnabled && prefs.karaokeSelectedGpuId != GpuDevice.ID_CPU) {
            val gpus = GpuDetector.getDetectedGpus()
            val target = if (prefs.karaokeSelectedGpuId == GpuDevice.ID_AUTO) {
                gpus.firstOrNull { it.isDiscrete } ?: gpus.firstOrNull()
            } else {
                gpus.firstOrNull { it.id == prefs.karaokeSelectedGpuId }
            }
            val gpuName = target?.name ?: if (isWindows) "DirectX 12 / Vulkan" else "Vulkan"
            if (isWindows) {
                "Vulkan / DirectX 12 (GPU: $gpuName)"
            } else {
                "Vulkan (GPU: $gpuName)"
            }
        } else {
            val safeThreads = (availableCores - 2).coerceIn(1, 4)
            "CPU Multi-thread ($safeThreads cœurs SIMD)"
        }
    }

    fun getModelsDir(): File {
        val dir = File(AppDirs.dataDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getOnnxModelFile(): File? {
        val f1 = File(getModelsDir(), "htdemucs_ft_vocals.onnx")
        if (f1.exists() && f1.length() > 10_000_000L) return f1
        val f2 = File(getModelsDir(), "mel_band_roformer_vocals.onnx")
        if (f2.exists() && f2.length() > 10_000_000L) return f2
        return null
    }

    fun getModelFile(): File {
        val onnx = getOnnxModelFile()
        val prefs = PlayerPreferences()
        val useGpu = prefs.karaokeGpuAccelerationEnabled && prefs.karaokeSelectedGpuId != GpuDevice.ID_CPU

        // If CPU-only execution is selected and an ONNX model is available, use ONNX for 100% software CPU inference
        if (!useGpu && onnx != null) {
            return onnx
        }

        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val userCache = if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            File(localAppData, "demucs-rs/$MODEL_FILE_NAME")
        } else {
            val userHome = System.getProperty("user.home")
            File(userHome, ".cache/demucs-rs/$MODEL_FILE_NAME")
        }
        if (userCache.exists() && userCache.length() > 50_000_000L) return userCache

        val primary = File(getModelsDir(), MODEL_FILE_NAME)
        if (primary.exists() && primary.length() > 50_000_000L) return primary

        if (onnx != null) return onnx

        return userCache
    }

    fun getStemsDir(): File {
        val dir = File(AppDirs.cacheDir, "karaoke_stems")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun checkModelStatus() {
        if (isModelReady()) {
            _downloadState.value = ModelDownloadState.Ready
        } else if (_downloadState.value !is ModelDownloadState.Downloading) {
            _downloadState.value = ModelDownloadState.NotDownloaded
        }
    }

    fun isModelReady(): Boolean {
        if (getOnnxModelFile() != null) return true
        val file = getModelFile()
        return file.exists() && file.length() > 10_000_000L
    }

    /**
     * Extracts and returns the native demucs executable bundled with KittyTune.
     */
    fun getNativeDemucsBinary(): File? {
        val os = System.getProperty("os.name", "").lowercase()
        val isWindows = os.contains("win")
        val isMac = os.contains("mac")
        val binName = if (isWindows) "demucs.exe" else "demucs"
        val resourceSubdir = when {
            isWindows -> "windows-x86_64"
            isMac -> "macos-aarch64"
            else -> "linux-x86_64"
        }
        val resourcePath = "/native/$resourceSubdir/$binName"

        val binDir = File(AppDirs.dataDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        val destFile = File(binDir, binName)

        val devFile = File("src/main/resources/native/$resourceSubdir/$binName")
        if (devFile.exists() && devFile.length() > 1_000_000L) {
            devFile.setExecutable(true, false)
            if (!destFile.exists() || destFile.lastModified() < devFile.lastModified() || destFile.length() != devFile.length()) {
                runCatching {
                    Files.copy(devFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    destFile.setExecutable(true, false)
                }
            }
            return destFile
        }

        if (destFile.exists() && destFile.length() > 1_000_000L) {
            destFile.setExecutable(true, false)
            return destFile
        }

        val inStream = KaraokeManager::class.java.getResourceAsStream(resourcePath)
        if (inStream != null) {
            try {
                inStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.setExecutable(true, false)
                return destFile
            } catch (e: Exception) {
                Logger.e(TAG, "Failed extracting bundled demucs binary: ${e.message}")
            }
        }

        return null
    }

    /**
     * Extracts and returns the native Vulkan low priority interception shim on Linux.
     * When loaded via LD_PRELOAD, this shim forces Demucs to create its Vulkan queues
     * with VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR, allowing the AMDGPU / Linux kernel scheduler
     * to grant instant hardware priority to Hyprland/Mutter/KWin, eliminating compositor stutter.
     */
    fun getNativeVulkanPriorityShim(): File? {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win") || os.contains("mac")) return null
        val libName = "libkt_vk_priority.so"
        val resourcePath = "/native/linux-x86_64/$libName"

        val binDir = File(AppDirs.dataDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        val destFile = File(binDir, libName)

        val devFile = File("src/main/resources/native/linux-x86_64/$libName")
        if (devFile.exists() && devFile.length() > 1000L) {
            if (!destFile.exists() || destFile.lastModified() < devFile.lastModified() || destFile.length() != devFile.length()) {
                runCatching {
                    Files.copy(devFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            return destFile
        }

        if (destFile.exists() && destFile.length() > 1000L) {
            return destFile
        }

        val inStream = KaraokeManager::class.java.getResourceAsStream(resourcePath)
        if (inStream != null) {
            try {
                inStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return destFile
            } catch (e: Exception) {
                Logger.e(TAG, "Failed extracting bundled Vulkan priority shim: ${e.message}")
            }
        }
        return null
    }

    /**
     * Download Mel-Band RoFormer ONNX model in background with progress.
     */
    fun startModelDownload() {
        if (_downloadState.value is ModelDownloadState.Downloading || isModelReady()) return

        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            _downloadState.value = ModelDownloadState.Downloading(0f, 0L, MODEL_TOTAL_BYTES)

            val modelFinal = getModelFile()
            modelFinal.parentFile?.mkdirs()
            val modelTmp = File(modelFinal.parentFile ?: getModelsDir(), "$MODEL_FILE_NAME.tmp")
            val urlsToTry = listOf(DOWNLOAD_URL) + FALLBACK_URLS

            var downloadSuccess = false
            var lastError: Exception? = null

            for (url in urlsToTry) {
                var totalDownloaded = 0L
                try {
                    val client = ProxyManager.getOkHttpClient().newBuilder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw RuntimeException("HTTP ${response.code} downloading RoFormer model")
                        }
                        val body = response.body ?: throw RuntimeException("Empty response body")
                        val totalBytes = body.contentLength().takeIf { it > 0 } ?: MODEL_TOTAL_BYTES

                        FileOutputStream(modelTmp).use { out ->
                            val buf = ByteArray(32768)
                            body.byteStream().use { input ->
                                while (true) {
                                    val read = input.read(buf)
                                    if (read <= 0) break
                                    out.write(buf, 0, read)
                                    totalDownloaded += read
                                    val progress = (totalDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    _downloadState.value = ModelDownloadState.Downloading(progress, totalDownloaded, totalBytes)
                                }
                            }
                            out.flush()
                        }
                    }

                    if (!modelTmp.exists() || modelTmp.length() < 10_000_000L) {
                        throw RuntimeException("Downloaded model is incomplete or empty")
                    }

                    try {
                        Files.move(modelTmp.toPath(), modelFinal.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: Exception) {
                        Files.move(modelTmp.toPath(), modelFinal.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    downloadSuccess = true
                    _downloadState.value = ModelDownloadState.Ready
                    break
                } catch (e: Exception) {
                    lastError = e
                    runCatching { modelTmp.delete() }
                }
            }

            if (!downloadSuccess) {
                _downloadState.value = ModelDownloadState.Error(lastError?.message ?: "Download failed")
            }
        }
    }

    fun cancelModelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val modelTmp = File(getModelsDir(), "$MODEL_FILE_NAME.tmp")
        runCatching { modelTmp.delete() }
        _downloadState.value = ModelDownloadState.NotDownloaded
    }

    fun deleteModel() {
        downloadJob?.cancel()
        synchronized(sessionLock) {
            runCatching { cachedSession?.close() }
            cachedSession = null
            cachedSessionModelPath = null
            cachedSessionGpuKey = null
        }
        val file = getModelFile()
        runCatching { file.delete() }
        _downloadState.value = ModelDownloadState.NotDownloaded
    }

    fun importModelFile(sourceFile: File): Result<Unit> {
        if (!sourceFile.exists() || sourceFile.length() < 10_000_000L) {
            return Result.failure(IllegalArgumentException("Invalid model file: missing or smaller than 10 MB"))
        }
        return try {
            synchronized(sessionLock) {
                runCatching { cachedSession?.close() }
                cachedSession = null
                cachedSessionModelPath = null
                cachedSessionGpuKey = null
            }
            val target = getModelFile()
            Files.copy(sourceFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            _downloadState.value = ModelDownloadState.Ready
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Stem Cache Management ────────────────────────────────────────────────

    fun getStemFile(trackId: Long): File = File(getStemsDir(), "inst_$trackId.wav")

    fun getVocalStemFile(trackId: Long): File = File(getStemsDir(), "vocals_$trackId.wav")

    fun hasStem(trackId: Long): Boolean {
        val f = getStemFile(trackId)
        return f.exists() && f.length() > 44100L
    }

    fun hasVocalStem(trackId: Long): Boolean {
        val f = getVocalStemFile(trackId)
        return f.exists() && f.length() > 44100L
    }

    fun getCacheSizeBytes(): Long {
        val files = getStemsDir().listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }

    fun getCachedStemsCount(): Int {
        val files = getStemsDir().listFiles() ?: return 0
        return files.count { it.name.startsWith("inst_") && it.name.endsWith(".wav") }
    }

    fun clearCache() {
        releaseCurrentStems()
        val files = getStemsDir().listFiles() ?: return
        for (f in files) {
            runCatching { f.delete() }
        }
        notifyStemUpdated()
    }

    fun clearStemsCache() = clearCache()

    fun deleteStem(trackId: Long) {
        if (currentActiveStemTrackId == trackId) {
            releaseCurrentStems()
        }
        val inst = getStemFile(trackId)
        val voc = getVocalStemFile(trackId)
        runCatching { inst.delete() }
        runCatching { voc.delete() }
        notifyStemUpdated()
    }

    data class CachedStemInfo(
        val trackId: Long,
        val instrumentalFile: File?,
        val vocalFile: File?,
        val totalSizeBytes: Long,
        val lastModified: Long,
    )

    fun getCachedStems(): List<CachedStemInfo> {
        val files = getStemsDir().listFiles() ?: return emptyList()
        val stemMap = mutableMapOf<Long, Pair<File?, File?>>()
        for (f in files) {
            if (f.name.endsWith(".wav")) {
                if (f.name.startsWith("inst_")) {
                    val id = f.name.removePrefix("inst_").removeSuffix(".wav").toLongOrNull()
                    if (id != null) {
                        val cur = stemMap[id] ?: (null to null)
                        stemMap[id] = f to cur.second
                    }
                } else if (f.name.startsWith("vocals_")) {
                    val id = f.name.removePrefix("vocals_").removeSuffix(".wav").toLongOrNull()
                    if (id != null) {
                        val cur = stemMap[id] ?: (null to null)
                        stemMap[id] = cur.first to f
                    }
                }
            }
        }
        return stemMap.map { (id, pair) ->
            val totalSize = (pair.first?.length() ?: 0L) + (pair.second?.length() ?: 0L)
            val modified = maxOf(pair.first?.lastModified() ?: 0L, pair.second?.lastModified() ?: 0L)
            CachedStemInfo(id, pair.first, pair.second, totalSize, modified)
        }.sortedByDescending { it.lastModified }
    }

    fun getStemCacheStats(): Pair<Int, Float> {
        val count = getCachedStemsCount()
        val mb = getCacheSizeBytes() / (1024f * 1024f)
        return Pair(count, mb)
    }

    fun cancelSeparation() {
        val trackId = currentProcessingTrackId
        currentProcessingTrackId = null
        val job = separationJob
        separationJob = null
        job?.cancel()

        activeProcess?.let { proc ->
            runCatching {
                resumeSubprocess(proc)
                proc.destroyForcibly()
            }
            activeProcess = null
        }

        if (trackId != null) {
            val stemTmp = File(getStemsDir(), "inst_${trackId}.wav.tmp")
            val vocTmp = File(getStemsDir(), "vocals_${trackId}.wav.tmp")
            runCatching { stemTmp.delete() }
            runCatching { vocTmp.delete() }
            val callbacks = completionListeners.remove(trackId)
            callbacks?.clear()
        }
        _separationState.value = SeparationState.Idle
        Logger.i(TAG, "Separation cancelled by user for track $trackId")
    }

    // ─── Playback Stem Coordination ───────────────────────────────────────────

    fun notifyTrackChanged(trackId: Long?) {
        if (trackId == null) {
            releaseCurrentStems()
            return
        }
        if (currentActiveStemTrackId == trackId && currentStemAccess != null) return

        if (hasStem(trackId) || hasVocalStem(trackId)) {
            loadStemIntoMemory(trackId)
        } else {
            releaseCurrentStems()
        }
    }

    private fun releaseCurrentStems() {
        currentActiveStemTrackId = null
        currentStemAccess?.close()
        currentStemAccess = null
        currentVocalStemAccess?.close()
        currentVocalStemAccess = null
    }

    /**
     * Opens a stem WAV for on-demand slice reads. Same header parsing as the old
     * full-file reader (fixed fields at 22/24/34, then the chunk walk from offset 12),
     * but the samples stay on disk instead of being materialised as a FloatArray:
     * one ~85 MB anonymous heap array per 4-minute stem, two stems while karaoke
     * plays, was the single biggest controllable RAM cost in the app (issue #66).
     */
    private fun openStemAccess(file: File): StemFileAccess? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                raf.seek(0)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt()
                val sampleRate = bb.getInt(24)
                val bitsPerSample = bb.getShort(34).toInt()
                if ((bitsPerSample != 16 && bitsPerSample != 32) || (channels != 1 && channels != 2)) return null

                val fileLen = file.length()
                var dataOffset = 44L
                var dataSize = fileLen - 44L
                var pos = 12L
                val id = ByteArray(4)
                val sizeBytes = ByteArray(4)
                while (pos + 8 <= fileLen) {
                    raf.seek(pos)
                    if (raf.read(id) < 4) break
                    raf.readFully(sizeBytes)
                    val chunkSize = ((sizeBytes[0].toInt() and 0xFF) or ((sizeBytes[1].toInt() and 0xFF) shl 8)
                        or ((sizeBytes[2].toInt() and 0xFF) shl 16) or (sizeBytes[3].toInt() shl 24)).toLong() and 0xFFFFFFFFL
                    if (id[0] == 'd'.code.toByte() && id[1] == 'a'.code.toByte() &&
                        id[2] == 't'.code.toByte() && id[3] == 'a'.code.toByte()
                    ) {
                        dataOffset = pos + 8
                        dataSize = if (chunkSize in 1..(fileLen - dataOffset)) chunkSize else (fileLen - dataOffset)
                        break
                    }
                    if (chunkSize <= 0L || pos + 8 + chunkSize > fileLen) break
                    pos += 8 + chunkSize
                }

                val clampedLen = minOf(dataSize, fileLen - dataOffset)
                StemFileAccess(file, sampleRate, channels, bitsPerSample, dataOffset, clampedLen)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error opening stem file ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * File-backed stem reader. Slices are read on demand — one seek plus one bulk read
     * per audio block — straight out of the OS page cache, instead of holding the entire
     * stem in the JVM heap. Touched pages are file-backed and evictable, so the karaoke
     * engine's steady-state footprint on the heap drops from ~170 MB to kilobytes, and
     * [close] runs before every delete so stem removal keeps working on Windows.
     */
    private class StemFileAccess(
        file: File,
        val sampleRate: Int,
        private val channels: Int,
        private val bitsPerSample: Int,
        private val dataOffset: Long,
        private val dataLen: Long,
    ) : AutoCloseable {
        private val bytesPerSample = bitsPerSample / 8
        private val frameSize = bytesPerSample * channels
        internal val totalFrames: Long = if (frameSize > 0) dataLen / frameSize else 0L
        private val raf = RandomAccessFile(file, "r")
        private var scratch = ByteArray(16 * 1024)

        /**
         * Fills [outLeft]/[outRight] with [numFrames] frames starting at [frame];
         * out-of-range frames are silence, matching the old in-RAM reader exactly.
         * Synchronized: the audio thread(s) read while [close] may swap the accessor
         * out on a track change — a close then waits for the in-flight block instead
         * of invalidating the handle under it, and the scratch buffer stays single-owner.
         */
        @Synchronized
        fun readSlice(frame: Long, outLeft: FloatArray, outRight: FloatArray, numFrames: Int) {
            if (frameSize <= 0 || numFrames <= 0) return
            val first = frame.coerceAtLeast(0L)
            val end = (frame + numFrames).coerceAtMost(totalFrames)
            if (end <= first) {
                java.util.Arrays.fill(outLeft, 0, numFrames, 0f)
                java.util.Arrays.fill(outRight, 0, numFrames, 0f)
                return
            }
            if (frame < 0) {
                val head = (first - frame).coerceAtMost(numFrames.toLong()).toInt()
                java.util.Arrays.fill(outLeft, 0, head, 0f)
                java.util.Arrays.fill(outRight, 0, head, 0f)
            }
            val framesToRead = (end - first).toInt()
            val outStart = (first - frame).toInt()
            val byteLen = framesToRead * frameSize
            if (scratch.size < byteLen) scratch = ByteArray(byteLen)
            try {
                raf.seek(dataOffset + first * frameSize)
                raf.readFully(scratch, 0, byteLen)
                decode(framesToRead, outStart, outLeft, outRight)
            } catch (_: Exception) {
                // The old accessor was swapped out mid-read (track change): one silent
                // block, masked by the crossfade, then the new track's stems take over.
                java.util.Arrays.fill(outLeft, outStart, outStart + framesToRead, 0f)
                java.util.Arrays.fill(outRight, outStart, outStart + framesToRead, 0f)
            }
            val tailStart = outStart + framesToRead
            if (tailStart < numFrames) {
                java.util.Arrays.fill(outLeft, tailStart, numFrames, 0f)
                java.util.Arrays.fill(outRight, tailStart, numFrames, 0f)
            }
        }

        private fun decode(frames: Int, outStart: Int, outLeft: FloatArray, outRight: FloatArray) {
            var p = 0
            if (channels == 1) {
                var i = 0
                if (bitsPerSample == 32) {
                    while (i < frames) {
                        val v = Float.fromBits(le32(scratch, p)).coerceIn(-1f, 1f)
                        outLeft[outStart + i] = v
                        outRight[outStart + i] = v
                        i++; p += 4
                    }
                } else {
                    while (i < frames) {
                        val v = le16(scratch, p) / 32768f
                        outLeft[outStart + i] = v
                        outRight[outStart + i] = v
                        i++; p += 2
                    }
                }
            } else {
                var i = 0
                if (bitsPerSample == 32) {
                    while (i < frames) {
                        outLeft[outStart + i] = Float.fromBits(le32(scratch, p))
                        outRight[outStart + i] = Float.fromBits(le32(scratch, p + 4))
                        i++; p += 8
                    }
                } else {
                    while (i < frames) {
                        outLeft[outStart + i] = le16(scratch, p) / 32768f
                        outRight[outStart + i] = le16(scratch, p + 2) / 32768f
                        i++; p += 4
                    }
                }
            }
        }

        private fun le16(b: ByteArray, p: Int): Int =
            (((b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)).toShort()).toInt()

        private fun le32(b: ByteArray, p: Int): Int =
            (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
                ((b[p + 2].toInt() and 0xFF) shl 16) or (b[p + 3].toInt() shl 24)

        @Synchronized
        override fun close() {
            try { raf.close() } catch (_: Exception) {}
        }
    }

    fun loadStemSync(trackId: Long): Boolean {
        val instFile = getStemFile(trackId)
        val vocalFile = getVocalStemFile(trackId)
        if (!instFile.exists() && !vocalFile.exists()) return false

        // Swap in the new file-backed readers before closing the old ones: an
        // in-flight audio block on the old track either fails its availability
        // check or catches the closed file (one silent block) instead of racing
        // an unprotected close.
        val oldInst = currentStemAccess
        val oldVoc = currentVocalStemAccess
        val inst = openStemAccess(instFile)
        val voc = openStemAccess(vocalFile)
        currentStemAccess = inst
        currentVocalStemAccess = voc
        val loadedAny = inst != null || voc != null
        currentActiveStemTrackId = if (loadedAny) trackId else null
        oldInst?.close()
        oldVoc?.close()
        if (loadedAny) {
            Logger.d(TAG, "Loaded stems for track $trackId (inst: ${inst != null}, vocal: ${voc != null})")
        }
        return loadedAny
    }

    private fun loadStemIntoMemory(trackId: Long) {
        scope.launch(Dispatchers.IO) {
            loadStemSync(trackId)
        }
    }

    fun isStemAvailableForCurrentTrack(trackId: Long?): Boolean {
        if (trackId == null) return false
        return currentActiveStemTrackId == trackId && currentStemAccess != null
    }

    fun isVocalStemAvailableForCurrentTrack(trackId: Long?): Boolean {
        if (trackId == null) return false
        return currentActiveStemTrackId == trackId && currentVocalStemAccess != null
    }

    /**
     * Reads a slice of the pre-separated instrumental stem corresponding to sample range.
     * Returns true if stem samples were copied into [outLeft] and [outRight].
     */
    fun readStemSlice(
        trackId: Long,
        samplePosition: Long,
        outLeft: FloatArray,
        outRight: FloatArray,
        numFrames: Int
    ): Boolean {
        val access = currentStemAccess ?: return false
        if (currentActiveStemTrackId != trackId) return false
        access.readSlice(samplePosition, outLeft, outRight, numFrames)
        return true
    }

    /**
     * Reads a slice of the pre-separated pure vocal stem corresponding to sample range.
     * Returns true if vocal stem samples were copied into [outLeft] and [outRight].
     */
    fun readVocalStemSlice(
        trackId: Long,
        samplePosition: Long,
        outLeft: FloatArray,
        outRight: FloatArray,
        numFrames: Int
    ): Boolean {
        val access = currentVocalStemAccess ?: return false
        if (currentActiveStemTrackId != trackId) return false
        access.readSlice(samplePosition, outLeft, outRight, numFrames)
        return true
    }

    // ─── Separation Execution Pipeline ─────────────────────────────────────────

    /**
     * Checks prerequisites and automatically starts stem separation in background if needed.
     */
    fun triggerAutoSeparationIfNeeded(
        track: Track,
        audioSourceUrl: String,
        headers: Map<String, String> = emptyMap(),
        force: Boolean = false,
        requireVocalStem: Boolean = false,
        onCompleted: ((Long) -> Unit)? = null
    ) {
        val alreadyReady = if (requireVocalStem) (hasStem(track.id) && hasVocalStem(track.id)) else hasStem(track.id)
        if (!force && alreadyReady) {
            // Already separated, ensure it's loaded into memory
            _separationState.value = SeparationState.Completed(track.id)
            loadStemSync(track.id)
            notifyTrackChanged(track.id)
            notifyStemUpdated()
            onCompleted?.invoke(track.id)
            return
        }
        val prefs = PlayerPreferences()
        if (!isModelReady()) return
        if (audioSourceUrl.isEmpty()) return

        if (prefs.karaokeEngineMode != KaraokeEngineMode.MEL_BAND_ROFORMER) {
            prefs.karaokeEngineMode = KaraokeEngineMode.MEL_BAND_ROFORMER
        }

        separateTrackAsync(track, audioSourceUrl, headers, force, requireVocalStem, onCompleted)
    }

    /**
     * Separate vocals & instrumentals in background for [track].
     */
    fun separateTrackAsync(
        track: Track,
        audioSourceUrl: String,
        headers: Map<String, String> = emptyMap(),
        force: Boolean = false,
        requireVocalStem: Boolean = false,
        onCompleted: ((Long) -> Unit)? = null
    ) {
        val trackId = track.id
        val alreadyReady = if (requireVocalStem) (hasStem(trackId) && hasVocalStem(trackId)) else hasStem(trackId)
        if (!force && alreadyReady) {
            _separationState.value = SeparationState.Completed(trackId)
            loadStemSync(trackId)
            notifyTrackChanged(trackId)
            notifyStemUpdated()
            onCompleted?.invoke(trackId)
            return
        }

        if (!isModelReady()) {
            _separationState.value = SeparationState.Error(trackId, "Model not downloaded")
            return
        }

        // If this track is ALREADY being separated, do NOT restart or reset progress!
        if (currentProcessingTrackId == trackId && separationJob?.isActive == true) {
            if (onCompleted != null) {
                completionListeners.computeIfAbsent(trackId) { java.util.concurrent.CopyOnWriteArrayList() }.add(onCompleted)
            }
            return
        }

        if (onCompleted != null) {
            completionListeners.computeIfAbsent(trackId) { java.util.concurrent.CopyOnWriteArrayList() }.add(onCompleted)
        }

        currentProcessingTrackId = trackId
        val prevJob = separationJob
        separationJob = null
        prevJob?.cancel()

        separationJob = scope.launch(Dispatchers.IO) {
            var lastProgress = 0f

            fun updateProgress(newProgress: Float, statusDetail: String? = null) {
                if (currentProcessingTrackId != trackId || !isActive) return
                val safe = maxOf(lastProgress, newProgress.coerceIn(0f, 0.99f))
                lastProgress = safe
                _separationState.value = SeparationState.Processing(safe, trackId, track.title, statusDetail)
            }

            _separationState.value = SeparationState.Processing(0f, trackId, track.title, "Préparation de l'audio...")
            val stemTmp = File(getStemsDir(), "inst_${trackId}.wav.tmp")
            val stemFinal = getStemFile(trackId)

            var grabber: FFmpegFrameGrabber? = null
            var hlsAdapter: HlsStreamAdapter? = null
            try {
                // 1. Prioritize local cached or downloaded file first (offline, instantaneous, zero network failures)
                val localFile = AudioCache.lookup(trackId)
                    ?: DownloadManager.getLocalTrack(trackId)?.localAudioPath?.let { File(it) }?.takeIf { it.exists() }
                    ?: (if (audioSourceUrl.startsWith("file:") || File(audioSourceUrl).exists()) {
                        File(audioSourceUrl.removePrefix("file://"))
                    } else null)

                if (localFile != null && localFile.exists()) {
                    Logger.i(TAG, "Decoding from local cached file: ${localFile.absolutePath}")
                    updateProgress(0.02f, "Lecture du fichier local...")
                    grabber = FFmpegFrameGrabber(localFile).apply {
                        sampleRate = 44100
                        audioChannels = 2
                        sampleMode = FrameGrabber.SampleMode.FLOAT
                    }
                } else if (audioSourceUrl.contains(".m3u8")) {
                    Logger.i(TAG, "Decoding HLS stream via HlsStreamAdapter: $audioSourceUrl")
                    updateProgress(0.02f, "Connexion au flux HLS...")
                    val adapter = HlsStreamAdapter(audioSourceUrl, headers)
                    hlsAdapter = adapter
                    val inStream = adapter.getInputStream(0L)
                    grabber = FFmpegFrameGrabber(inStream, 0).apply {
                        format = "mp4"
                        setOption("probesize", "32768")
                        setOption("analyzeduration", "0")
                        sampleRate = 44100
                        audioChannels = 2
                        sampleMode = FrameGrabber.SampleMode.FLOAT
                    }
                } else {
                    Logger.i(TAG, "Decoding remote stream: $audioSourceUrl")
                    updateProgress(0.02f, "Connexion au flux audio distant...")
                    grabber = FFmpegFrameGrabber(audioSourceUrl).apply {
                        if (headers.isNotEmpty()) {
                            val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                            setOption("headers", headerBlob + "\r\n")
                        }
                        setOption("reconnect", "1")
                        setOption("reconnect_streamed", "1")
                        setOption("reconnect_delay_max", "5")
                        setOption("rw_timeout", "15000000") // 15s timeout
                        sampleRate = 44100
                        audioChannels = 2
                        sampleMode = FrameGrabber.SampleMode.FLOAT
                    }
                }

                updateProgress(0.05f, "Décodage audio haute fidélité...")
                grabber.start()

                val durationUs = grabber.lengthInTime
                val totalSec = if (durationUs > 0) durationUs / 1_000_000L else ((track.durationMs ?: 0L) / 1000L).coerceAtLeast(60L)
                val estimatedFrames = (totalSec * 44100).toInt().coerceAtLeast(44100 * 10)

                // Accumulate decoded PCM
                val pcmBuffer = ArrayList<FloatArray>()
                var totalSamplesCollected = 0

                var frame = grabber.grabSamples()
                while (frame != null) {
                    if (currentProcessingTrackId != trackId || !isActive) {
                        throw CancellationException("Separation superseded or cancelled")
                    }
                    val samples = frame.samples
                    if (samples != null && samples.isNotEmpty()) {
                        val interleaved = extractInterleavedFloats(samples, frame.audioChannels)
                        if (interleaved != null && interleaved.isNotEmpty()) {
                            pcmBuffer.add(interleaved)
                            totalSamplesCollected += interleaved.size
                            val decodeProgress = 0.05f + (((totalSamplesCollected.toFloat() / (estimatedFrames * 2)).coerceIn(0f, 1f)) * 0.20f)
                            updateProgress(decodeProgress, "Décodage audio haute fidélité...")
                        }
                    }
                    frame = grabber.grabSamples()
                }

                if (pcmBuffer.isEmpty() || totalSamplesCollected < 44100 * 2) {
                    throw IllegalStateException("Audio decoding produced insufficient samples")
                }

                // Flatten decoded stereo PCM
                val fullPcm = FloatArray(totalSamplesCollected)
                var offset = 0
                for (chunk in pcmBuffer) {
                    System.arraycopy(chunk, 0, fullPcm, offset, chunk.size)
                    offset += chunk.size
                }

                val providerName = _activeAccelerationProvider.value.substringBefore('·').trim()
                updateProgress(0.26f, "Initialisation de l'IA ($providerName)...")

                val prefs = PlayerPreferences()
                val useGpu = prefs.karaokeGpuAccelerationEnabled && prefs.karaokeSelectedGpuId != GpuDevice.ID_CPU
                val nativeBinary = getNativeDemucsBinary()
                val onnxFile = getOnnxModelFile()
                val currentModel = getModelFile()
                val isModelSafetensors = currentModel.extension.equals("safetensors", ignoreCase = true)

                var processedWithDemucs = false

                // If user disabled GPU acceleration and we have a native ONNX model, prefer ONNX CPU execution
                val shouldRunDemucs = nativeBinary != null && (useGpu || onnxFile == null) && (isModelSafetensors || currentModel.length() > 50_000_000L)

                if (shouldRunDemucs) {
                    val tempInWav = File.createTempFile("kt_in_${trackId}_", ".wav")
                    try {
                        writeWavFile(tempInWav, fullPcm, 44100, 2)
                        processSeparationWithDemucs(nativeBinary, tempInWav, stemTmp, trackId, useGpu) { inferProgress, detail ->
                            val overallProgress = 0.26f + (inferProgress * 0.70f)
                            updateProgress(overallProgress, detail ?: (if (useGpu) "Séparation neuronale GPU..." else "Séparation neuronale économique..."))
                        }
                        processedWithDemucs = true
                    } catch (c: CancellationException) {
                        throw c
                    } catch (demucsErr: Throwable) {
                        Logger.w(TAG, "Demucs separation failed (${demucsErr.message}), falling back to CPU ONNX...", demucsErr)
                    } finally {
                        runCatching { tempInWav.delete() }
                    }
                }

                if (!processedWithDemucs) {
                    val separatedPcm = processSeparationWithOnnx(fullPcm, trackId) { inferProgress, chunkDetail ->
                        val overallProgress = 0.26f + (inferProgress * 0.69f)
                        updateProgress(overallProgress, chunkDetail ?: "Séparation neuronale CPU...")
                    }
                    updateProgress(0.96f, "Sauvegarde des stems studio...")
                    writeWavFile(stemTmp, separatedPcm.first, 44100, 2)
                    val vocTmp = File(getStemsDir(), "vocals_${trackId}.wav.tmp")
                    writeWavFile(vocTmp, separatedPcm.second, 44100, 2)
                    val vocFinal = getVocalStemFile(trackId)
                    Files.move(vocTmp.toPath(), vocFinal.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }

                updateProgress(0.99f, "Finalisation...")
                Files.move(stemTmp.toPath(), stemFinal.toPath(), StandardCopyOption.REPLACE_EXISTING)

                loadStemSync(trackId)
                notifyTrackChanged(trackId)
                notifyStemUpdated()
                _separationState.value = SeparationState.Completed(trackId)

                val callbacks = completionListeners.remove(trackId)
                callbacks?.forEach { runCatching { it.invoke(trackId) } }
                Logger.i(TAG, "Separation completed successfully for track ${track.title} ($trackId)")
            } catch (e: kotlinx.coroutines.CancellationException) {
                Logger.d(TAG, "Separation cancelled for track $trackId")
                runCatching { stemTmp.delete() }
                if (currentProcessingTrackId == trackId) {
                    _separationState.value = SeparationState.Idle
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Separation failed for track $trackId: ${e.message}", e)
                runCatching { stemTmp.delete() }
                if (currentProcessingTrackId == trackId) {
                    _separationState.value = SeparationState.Error(trackId, e.message ?: "Separation failed")
                }
                completionListeners.remove(trackId)
            } finally {
                if (currentProcessingTrackId == trackId) {
                    currentProcessingTrackId = null
                }
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
            }
        }
    }

    /**
     * Executes HTDemucs source separation via native Demucs-RS (Vulkan on Linux & Windows).
     * Automatically applies anti-stutter duty-cycle pacing, background process priority (`nice -n 19`, `chrt -i 0`),
     * and idle I/O to keep desktop compositor and whole PC 100% fluid without dropped frames.
     */
    private suspend fun processSeparationWithDemucs(
        binary: File,
        inputFile: File,
        outputStemFile: File,
        trackId: Long,
        useGpu: Boolean,
        onProgress: (Float, String?) -> Unit
    ) {
        val tempOutputDir = File(AppDirs.cacheDir, "demucs_tmp_${trackId}_${System.currentTimeMillis()}")
        tempOutputDir.mkdirs()

        var process: Process? = null
        var pacerJob: Job? = null

        try {
            val cmd = mutableListOf<String>()
            val os = System.getProperty("os.name", "").lowercase()
            val isWindows = os.contains("win")

            if (!isWindows) {
                if (isChrtAvailable) {
                    cmd.addAll(listOf("chrt", "-i", "0"))
                }
                cmd.addAll(listOf("nice", "-n", "19"))
                if (isIoniceAvailable) {
                    cmd.addAll(listOf("ionice", "-c", "3"))
                }
                cmd.add(binary.absolutePath)
            } else {
                cmd.add(binary.absolutePath)
            }
            cmd.addAll(listOf(
                "-m", "htdemucs",
                "-s", "vocals",
                "-o", tempOutputDir.absolutePath,
                inputFile.absolutePath
            ))

            val processBuilder = ProcessBuilder(cmd)
            val env = processBuilder.environment()

            // Respect user-selected GPU if multiple graphics adapters are present
            val prefs = PlayerPreferences()
            val selectedGpu = prefs.karaokeSelectedGpuId
            if (selectedGpu != GpuDevice.ID_AUTO && selectedGpu.startsWith("gpu:")) {
                val idx = selectedGpu.removePrefix("gpu:")
                env["DRI_PRIME"] = idx
                env["MESA_VK_DEVICE_SELECT"] = idx
            }

            val homeDir = System.getProperty("user.home") ?: "/home/alan"
            env["XDG_CACHE_HOME"] = File(homeDir, ".cache").absolutePath

            val vkShim = getNativeVulkanPriorityShim()
            if (vkShim != null && vkShim.exists()) {
                env["LD_PRELOAD"] = vkShim.absolutePath
                Logger.i(TAG, "Demucs launched with native Vulkan low priority shim: ${vkShim.absolutePath}")
            }

            val proc = withContext(Dispatchers.IO) { processBuilder.start() }
            process = proc
            activeProcess = proc

            runCatching { nativeSetProcessIdlePriority(proc.pid()) }

            // Anti-stutter duty-cycle pacer:
            // High-intensity GPU compute saturates Vulkan queues and starves the desktop compositor (Hyprland/KWin/Mutter).
            // Combining hardware queue priority (VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR via libkt_vk_priority.so) with
            // a balanced duty-cycle pacer prevents compositor frame drops across high refresh rates (60Hz to 240Hz).
            // In GPU mode: 80ms run, 16ms yield (~83% duty cycle, guarantees a full 16.6ms window for 60Hz and 4x 240Hz flips without kernel scheduler churn).
            // In Low Power / CPU mode: 40ms run, 40ms yield (50% duty cycle, minimal background consumption).
            val runDurationMs = if (useGpu) 80L else 40L
            val pauseDurationMs = if (useGpu) 16L else 40L

            pacerJob = scope.launch(Dispatchers.Default) {
                try {
                    while (isActive && proc.isAlive) {
                        delay(runDurationMs)
                        if (!isActive || !proc.isAlive) break
                        pauseSubprocess(proc)
                        delay(pauseDurationMs)
                        if (!proc.isAlive) break
                        resumeSubprocess(proc)
                    }
                } finally {
                    resumeSubprocess(proc)
                }
            }

            val stdoutReader = BufferedReader(InputStreamReader(proc.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(proc.errorStream))

            // Drain stderr asynchronously to avoid buffer blocking
            scope.launch(Dispatchers.IO) {
                runCatching {
                    var errLine = stderrReader.readLine()
                    while (errLine != null) {
                        Logger.d(TAG, "[demucs err] $errLine")
                        errLine = stderrReader.readLine()
                    }
                }
            }

            withContext(Dispatchers.IO) {
                var line = stdoutReader.readLine()
                while (line != null) {
                    if (currentProcessingTrackId != trackId) {
                        pacerJob.cancel()
                        resumeSubprocess(proc)
                        proc.destroyForcibly()
                        throw CancellationException("Separation superseded or cancelled")
                    }
                    if (line.startsWith("PROGRESS:")) {
                        val pct = line.removePrefix("PROGRESS:").trim().toFloatOrNull()
                        if (pct != null) {
                            val pctInt = (pct * 100).toInt()
                            val label = if (useGpu) "Séparation neuronale GPU : $pctInt%" else "Séparation neuronale économique : $pctInt%"
                            onProgress(pct, label)
                        }
                    }
                    line = stdoutReader.readLine()
                }

                val exitCode = proc.waitFor()
                if (exitCode != 0) {
                    throw RuntimeException("Demucs exited with error code $exitCode")
                }
            }

            val generatedInst = File(tempOutputDir, "instrumental.wav")
            if (!generatedInst.exists() || generatedInst.length() < 1000L) {
                throw RuntimeException("Demucs did not produce instrumental.wav")
            }

            Files.move(generatedInst.toPath(), outputStemFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

            val generatedVoc = File(tempOutputDir, "vocals.wav")
            if (generatedVoc.exists() && generatedVoc.length() >= 1000L) {
                val outputVocFile = getVocalStemFile(trackId)
                Files.move(generatedVoc.toPath(), outputVocFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Logger.i(TAG, "Demucs vocal stem saved: ${outputVocFile.absolutePath}")
            }
            Logger.i(TAG, "Demucs separation completed successfully for track $trackId")
        } finally {
            activeProcess = null
            pacerJob?.cancel()
            process?.let {
                resumeSubprocess(it)
                runCatching { it.destroyForcibly() }
            }
            runCatching {
                tempOutputDir.listFiles()?.forEach { it.delete() }
                tempOutputDir.delete()
            }
        }
    }

    /**
     * Executes RoFormer neural network separation over audio PCM chunks.
     * Uses ONNX Runtime with DirectML on Windows or multi-threaded SIMD CPU on Linux/macOS.
     */
    private val sessionLock = Any()
    @Volatile private var cachedSession: OrtSession? = null
    @Volatile private var cachedSessionModelPath: String? = null
    @Volatile private var cachedSessionGpuKey: String? = null

    private fun getOrCreateSession(
        env: OrtEnvironment,
        modelFile: File,
        useGpu: Boolean,
        selectedGpuId: String?
    ): OrtSession {
        val gpuKey = if (useGpu) (selectedGpuId ?: GpuDevice.ID_AUTO) else "cpu"
        synchronized(sessionLock) {
            val existing = cachedSession
            if (existing != null && cachedSessionModelPath == modelFile.absolutePath && cachedSessionGpuKey == gpuKey) {
                return existing
            }
            runCatching { existing?.close() }
            cachedSession = null

            val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
            val availableCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

            val sessionOpts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setMemoryPatternOptimization(true)
                setCPUArenaAllocator(true)
            }

            var session: OrtSession? = null

            if (isWindows && useGpu && selectedGpuId != GpuDevice.ID_CPU) {
                val gpus = GpuDetector.getDetectedGpus()
                val targetGpu = if (selectedGpuId == GpuDevice.ID_AUTO) {
                    gpus.firstOrNull { it.isDiscrete } ?: gpus.firstOrNull() ?: GpuDevice.AUTO
                } else {
                    gpus.firstOrNull { it.id == selectedGpuId } ?: GpuDevice.AUTO
                }

                val gpuIndex = if (targetGpu.id.startsWith("gpu:")) {
                    targetGpu.id.removePrefix("gpu:").toIntOrNull() ?: 0
                } else 0

                val appliedProvider = OnnxHelper.applyGpuProvider(sessionOpts, targetGpu, gpuIndex)
                if (appliedProvider != null) {
                    _activeAccelerationProvider.value = appliedProvider
                    try {
                        session = env.createSession(modelFile.absolutePath, sessionOpts)
                    } catch (gpuErr: Throwable) {
                        Logger.w(TAG, "GPU session creation failed (${gpuErr.message}), falling back to CPU")
                        sessionOpts.close()
                    }
                }
            }

            if (session == null) {
                val safeThreads = (availableCores - 2).coerceIn(1, 4)
                val cpuOpts = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setMemoryPatternOptimization(true)
                    setCPUArenaAllocator(true)
                    setIntraOpNumThreads(safeThreads)
                    setInterOpNumThreads(2)
                }
                _activeAccelerationProvider.value = "CPU Multi-thread ($safeThreads cœurs SIMD)"
                session = env.createSession(modelFile.absolutePath, cpuOpts)
            }

            cachedSession = session
            cachedSessionModelPath = modelFile.absolutePath
            cachedSessionGpuKey = gpuKey
            return session
        }
    }

    /**
     * Slices the decoded PCM audio into overlapping windows, executes ONNX neural inference
     * through HT-Demucs/RoFormer, and cross-fades the isolated vocal frames into an instrumental stem.
     */
    private suspend fun processSeparationWithOnnx(
        inputStereoPcm: FloatArray,
        trackId: Long,
        onProgress: (Float, String?) -> Unit
    ): Pair<FloatArray, FloatArray> {
        val totalFrames = inputStereoPcm.size / 2
        val outputInstrumental = FloatArray(inputStereoPcm.size)
        val outputVocals = FloatArray(inputStereoPcm.size)

        val modelFile = getModelFile()
        val prefs = PlayerPreferences()
        val useGpu = prefs.karaokeGpuAccelerationEnabled
        val selectedGpuId = prefs.karaokeSelectedGpuId

        OnnxHelper.ensureNativeLibrariesLoaded()
        val env = OrtEnvironment.getEnvironment()
        val session = getOrCreateSession(env, modelFile, useGpu, selectedGpuId)
        val originalPriority = Thread.currentThread().priority
        Thread.currentThread().priority = Thread.MIN_PRIORITY

        try {

            val inputNames = session.inputNames
            val inputName = inputNames.firstOrNull() ?: "mix"

            // HT-Demucs / Demucs segment: 7.8s @ 44.1 kHz = 343980 frames
            val chunkSize = 343980
            val overlap = chunkSize / 4
            val stride = chunkSize - overlap
            val numChunks = maxOf(1, (totalFrames + stride - 1) / stride)

            val transition = (chunkSize * 0.25f).toInt()
            val window = FloatArray(chunkSize)
            for (i in 0 until transition) {
                window[i] = i.toFloat() / transition
            }
            for (i in transition until chunkSize - transition) {
                window[i] = 1.0f
            }
            for (i in 0 until transition) {
                window[chunkSize - transition + i] = (transition - 1 - i).toFloat() / transition
            }

            val outVocL = FloatArray(totalFrames)
            val outVocR = FloatArray(totalFrames)
            val weight = FloatArray(totalFrames)

            val chunkL = FloatArray(chunkSize)
            val chunkR = FloatArray(chunkSize)

            val inputBuffer = ByteBuffer.allocateDirect(2 * chunkSize * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()

            val vocLChunk = FloatArray(chunkSize)
            val vocRChunk = FloatArray(chunkSize)

            for (chunkIdx in 0 until numChunks) {
                if (currentProcessingTrackId != trackId || !kotlinx.coroutines.currentCoroutineContext().isActive) {
                    throw CancellationException("Separation superseded or cancelled")
                }

                val start = chunkIdx * stride
                val end = minOf(start + chunkSize, totalFrames)
                val chunkLen = end - start
                if (chunkLen <= 0) break

                for (i in 0 until chunkLen) {
                    chunkL[i] = inputStereoPcm[(start + i) * 2]
                    chunkR[i] = inputStereoPcm[(start + i) * 2 + 1]
                }
                for (i in chunkLen until chunkSize) {
                    chunkL[i] = 0f
                    chunkR[i] = 0f
                }

                inputBuffer.clear()
                inputBuffer.put(chunkL)
                inputBuffer.put(chunkR)
                inputBuffer.flip()

                val tensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(1, 2, chunkSize.toLong()))

                try {
                    val outputs = session.run(mapOf(inputName to tensor))
                    outputs.use { res ->
                        val outTensor = res[0] as OnnxTensor
                        val outBuf = outTensor.floatBuffer
                        val shape = outTensor.info.shape

                        val vocLOffset: Int
                        val vocROffset: Int
                        if (shape.size == 4 && shape[1] >= 4L) {
                            // HT-Demucs: [1, 4, 2, N] -> stem 3 is vocals
                            vocLOffset = 6 * chunkSize
                            vocROffset = 7 * chunkSize
                        } else if (shape.size == 4 && shape[1] == 2L) {
                            // [1, 2, 2, N] -> stem 1 is vocals
                            vocLOffset = 2 * chunkSize
                            vocROffset = 3 * chunkSize
                        } else if (shape.size == 3) {
                            // [1, 2, N] -> pure vocals
                            vocLOffset = 0
                            vocROffset = chunkSize
                        } else {
                            vocLOffset = (shape.getOrNull(1)?.toInt()?.minus(1) ?: 0) * 2 * chunkSize
                            vocROffset = vocLOffset + chunkSize
                        }

                        outBuf.position(vocLOffset)
                        outBuf.get(vocLChunk, 0, chunkLen)
                        outBuf.position(vocROffset)
                        outBuf.get(vocRChunk, 0, chunkLen)

                        for (i in 0 until chunkLen) {
                            val w = window[i]
                            val vl = vocLChunk[i]
                            val vr = vocRChunk[i]
                            outVocL[start + i] += vl * w
                            outVocR[start + i] += vr * w
                            weight[start + i] += w
                        }
                    }
                } finally {
                    tensor.close()
                }

                val chunkProgress = (chunkIdx + 1).toFloat() / numChunks
                val chunkPct = (chunkProgress * 100).toInt()
                val detail = "Séparation neuronale : fenêtre ${chunkIdx + 1}/$numChunks ($chunkPct%)"
                onProgress(chunkProgress, detail)
                delay(35)
                Logger.d(TAG, "Separation chunk ${chunkIdx + 1}/$numChunks ($chunkPct%) complete")
            }

            // Synthesize pristine instrumental and vocal stems
            for (i in 0 until totalFrames) {
                val w = maxOf(weight[i], 1e-8f)
                val vocL = (outVocL[i] / w).coerceIn(-1.0f, 1.0f)
                val vocR = (outVocR[i] / w).coerceIn(-1.0f, 1.0f)
                val inL = inputStereoPcm[i * 2]
                val inR = inputStereoPcm[i * 2 + 1]
                outputVocals[i * 2] = vocL
                outputVocals[i * 2 + 1] = vocR
                outputInstrumental[i * 2] = (inL - vocL).coerceIn(-1.0f, 1.0f)
                outputInstrumental[i * 2 + 1] = (inR - vocR).coerceIn(-1.0f, 1.0f)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "ONNX neural vocal separation failed: ${e.message}", e)
            throw e
        } finally {
            Thread.currentThread().priority = originalPriority
        }

        return Pair(outputInstrumental, outputVocals)
    }

    private fun computeHann(i: Int, n: Int): Float {
        return (0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / (n - 1)))).toFloat()
    }

    private fun writeWavFile(file: File, pcmFloats: FloatArray, sampleRate: Int, channels: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val totalAudioLen = (pcmFloats.size * 2).toLong()
            val totalDataLen = totalAudioLen + 36

            val header = ByteArray(44)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray())
            bb.putInt(totalDataLen.toInt())
            bb.put("WAVE".toByteArray())
            bb.put("fmt ".toByteArray())
            bb.putInt(16) // Subchunk1Size for PCM
            bb.putShort(1) // AudioFormat 1 = PCM
            bb.putShort(channels.toShort())
            bb.putInt(sampleRate)
            bb.putInt(sampleRate * channels * 2) // ByteRate
            bb.putShort((channels * 2).toShort()) // BlockAlign
            bb.putShort(16) // BitsPerSample
            bb.put("data".toByteArray())
            bb.putInt(totalAudioLen.toInt())

            raf.write(header)

            val pcmBytes = ByteArray(4096)
            val byteBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

            var offset = 0
            while (offset < pcmFloats.size) {
                byteBuf.clear()
                val chunk = minOf(pcmBytes.size / 2, pcmFloats.size - offset)
                for (i in 0 until chunk) {
                    val s = (pcmFloats[offset + i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
                    byteBuf.putShort(s)
                }
                raf.write(pcmBytes, 0, chunk * 2)
                offset += chunk
            }
        }
    }

    private fun extractInterleavedFloats(samples: Array<java.nio.Buffer>, channels: Int): FloatArray? {
        if (samples.isEmpty()) return null
        val first = samples[0]
        val planar = samples.size == channels && channels > 1
        val len = first.remaining()
        if (len <= 0) return null

        if (planar) {
            val out = FloatArray(len * 2)
            val b0 = samples[0] as? java.nio.FloatBuffer
            val b1 = samples[1] as? java.nio.FloatBuffer
            if (b0 != null && b1 != null) {
                val pos0 = b0.position()
                val pos1 = b1.position()
                for (i in 0 until len) {
                    out[i * 2] = b0.get(pos0 + i)
                    out[i * 2 + 1] = b1.get(pos1 + i)
                }
                b0.position(pos0 + len)
                b1.position(pos1 + len)
                return out
            }
            val s0 = samples[0] as? java.nio.ShortBuffer
            val s1 = samples[1] as? java.nio.ShortBuffer
            if (s0 != null && s1 != null) {
                val pos0 = s0.position()
                val pos1 = s1.position()
                for (i in 0 until len) {
                    out[i * 2] = s0.get(pos0 + i) / 32768f
                    out[i * 2 + 1] = s1.get(pos1 + i) / 32768f
                }
                s0.position(pos0 + len)
                s1.position(pos1 + len)
                return out
            }
        } else {
            if (first is java.nio.FloatBuffer) {
                if (channels == 1) {
                    val out = FloatArray(len * 2)
                    val pos = first.position()
                    for (i in 0 until len) {
                        val s = first.get(pos + i)
                        out[i * 2] = s
                        out[i * 2 + 1] = s
                    }
                    first.position(pos + len)
                    return out
                } else {
                    val out = FloatArray(len)
                    first.get(out)
                    return out
                }
            } else if (first is java.nio.ShortBuffer) {
                if (channels == 1) {
                    val out = FloatArray(len * 2)
                    val pos = first.position()
                    for (i in 0 until len) {
                        val s = first.get(pos + i) / 32768f
                        out[i * 2] = s
                        out[i * 2 + 1] = s
                    }
                    first.position(pos + len)
                    return out
                } else {
                    val out = FloatArray(len)
                    val pos = first.position()
                    for (i in 0 until len) {
                        out[i] = first.get(pos + i) / 32768f
                    }
                    first.position(pos + len)
                    return out
                }
            }
        }
        return null
    }
}
