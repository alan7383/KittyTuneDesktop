package com.alananasss.kittytune.audio.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.openDirectoryInFileManager
import com.alananasss.kittytune.core.openUrl
import com.alananasss.kittytune.data.local.AiModelType
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.ProxyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import com.alananasss.kittytune.audio.HlsStreamAdapter
import org.bytedeco.tensorflowlite.BuiltinOpResolver
import org.bytedeco.tensorflowlite.BuiltinOpResolverWithXNNPACK
import org.bytedeco.tensorflowlite.FlatBufferModel
import org.bytedeco.tensorflowlite.Interpreter
import org.bytedeco.tensorflowlite.InterpreterBuilder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * AI Music Detection Manager (issue #41, multi-model & GPU acceleration support).
 *
 * Supports two distinct model architectures:
 * 1. [AiModelType.ARTIFACTNET] (17.4 MB):
 *    Ultra-fast forensic residual detector via LiteRT (TensorFlow Lite) + XNNPACK (CPU).
 *    Detects microscopic audio synthesis anomalies across 22+ generators in ~20ms.
 *
 * 2. [AiModelType.AUDIO_TRANSFORMER] (~214 MB):
 *    Contextual Audio Spectrogram Transformer (AST / CLAP) via ONNX Runtime.
 *    Leverages dedicated PC graphics cards via DirectML (Windows) or Vulkan / GPU compute (Linux/macOS)
 *    with multi-threaded CPU fallback for deep attention across complex arrangements.
 */
object AiDetectionManager {

    const val CHUNK_SAMPLES = 176_400
    const val TARGET_SR = 44_100
    const val ARTIFACTNET_TFLITE = "artifactnet_v94_full.tflite"
    const val ARTIFACTNET_ONNX = "artifactnet_v94_full.onnx"

    enum class Status { IDLE, ANALYZING, DONE, ERROR }

    data class Result(
        val score: Float,
        val isAi: Boolean,
        val status: Status,
    )

    sealed class ModelDownloadState {
        object NotDownloaded : ModelDownloadState()
        data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState()
        object Ready : ModelDownloadState()
        data class Error(val message: String) : ModelDownloadState()
    }

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result.asStateFlow()

    private val _activeAccelerationProvider = MutableStateFlow("LiteRT CPU (XNNPACK)")
    val activeAccelerationProvider: StateFlow<String> = _activeAccelerationProvider.asStateFlow()

    private val _currentModelFlow = MutableStateFlow(AiModelType.ARTIFACTNET)
    val currentModelFlow: StateFlow<AiModelType> = _currentModelFlow.asStateFlow()

    val currentModel: AiModelType get() = _currentModelFlow.value

    // Per-model download states
    private val downloadStates = mutableMapOf(
        AiModelType.ARTIFACTNET to MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded),
    )

    // Legacy / active model downloadState flow for backward compatibility
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineLock = Any()
    private val resultLock = Any()

    // TFLite engine
    @Volatile private var tfliteModel: FlatBufferModel? = null
    @Volatile private var tfliteInterpreter: Interpreter? = null

    // ONNX Runtime engine
    @Volatile private var onnxEnv: OrtEnvironment? = null
    @Volatile private var onnxSession: OrtSession? = null

    @Volatile private var isInitializing: Boolean = false

    // Constrained single-threaded background executor with low thread priority so AI inference
    // never starves or contends with the real-time audio playback thread (AudioEngine SourceDataLine).
    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kittytune-ai-inference").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()

    private var analysisJob: Job? = null
    @Volatile private var peakScore: Float = 0f
    @Volatile private var isAiConfirmed: Boolean = false

    fun isAiConfirmed(): Boolean = isAiConfirmed
    fun getPeakScore(): Float = synchronized(resultLock) { peakScore }

    @Volatile
    private var customModelsDir: File? = null

    internal fun setCustomModelsDirForTesting(dir: File?) {
        customModelsDir = dir
    }

    private fun getModelsDir(): File =
        customModelsDir ?: File(AppDirs.dataDir, "models").apply { mkdirs() }

    fun getModelFile(modelType: AiModelType = currentModel): File {
        val dir = getModelsDir()
        val onnx = File(dir, ARTIFACTNET_ONNX)
        val tflite = File(dir, ARTIFACTNET_TFLITE)
        val prefs = PlayerPreferences()
        val targetGpu = GpuDetector.resolveGpu(prefs.aiSelectedGpuId)
        val detectedGpus = GpuDetector.getDetectedGpus()
        val hasGpuTarget = prefs.aiGpuAccelerationEnabled &&
            targetGpu.id != GpuDevice.ID_CPU &&
            (targetGpu.id != GpuDevice.ID_AUTO || detectedGpus.isNotEmpty())

        return if (hasGpuTarget && onnx.exists() && onnx.length() > 1_000_000L) {
            onnx
        } else if (tflite.exists() && tflite.length() > 1_000_000L) {
            tflite
        } else if (onnx.exists() && onnx.length() > 1_000_000L) {
            onnx
        } else {
            onnx
        }
    }

    fun getModelDownloadState(modelType: AiModelType): StateFlow<ModelDownloadState> =
        downloadStates.getOrPut(modelType) { MutableStateFlow(ModelDownloadState.NotDownloaded) }.asStateFlow()

    fun isModelReady(modelType: AiModelType = currentModel): Boolean {
        val dir = getModelsDir()
        val onnx = File(dir, ARTIFACTNET_ONNX)
        val tflite = File(dir, ARTIFACTNET_TFLITE)
        return (onnx.exists() && onnx.length() > 1_000_000L) || (tflite.exists() && tflite.length() > 1_000_000L)
    }

    fun isModelLoaded(): Boolean = tfliteInterpreter != null || onnxSession != null

    /**
     * Initializes the detector asynchronously in the background so callers
     * never block or stall the real-time audio playback thread.
     */
    fun initAsync() {
        if (isModelLoaded() || isInitializing) return
        isInitializing = true
        scope.launch(Dispatchers.IO) {
            try {
                init()
            } finally {
                isInitializing = false
            }
        }
    }

    /**
     * Initializes the detector according to preferences and warms up GPU discovery.
     */
    fun init() {
        // Pre-warm GPU detection asynchronously to avoid UI frame drop in settings
        scope.launch(Dispatchers.IO) {
            GpuDetector.getDetectedGpus()
        }

        val prefs = PlayerPreferences()
        val preferredModel = prefs.aiSelectedModel
        _currentModelFlow.value = preferredModel

        // Update download state statuses for both models
        for (m in AiModelType.entries) {
            val stateFlow = downloadStates.getOrPut(m) { MutableStateFlow(ModelDownloadState.NotDownloaded) }
            if (isModelReady(m)) {
                stateFlow.value = ModelDownloadState.Ready
            } else if (stateFlow.value !is ModelDownloadState.Downloading) {
                stateFlow.value = ModelDownloadState.NotDownloaded
            }
        }
        _downloadState.value = downloadStates[preferredModel]?.value ?: ModelDownloadState.NotDownloaded

        if (isModelLoaded()) return

        if (!isModelReady(preferredModel)) {
            _downloadState.value = ModelDownloadState.NotDownloaded
            _activeAccelerationProvider.value = "${preferredModel.runtimeTag} (Not Downloaded)"
            return
        }

        initArtifactNet(prefs)
    }

    /**
     * Switch active model architecture and hydrate engine if downloaded.
     */
    fun selectModel(newModel: AiModelType) {
        resetResult()

        synchronized(engineLock) {
            if (_currentModelFlow.value == newModel && isModelLoaded() == isModelReady(newModel)) return
            closeEngines()
            _currentModelFlow.value = newModel
            PlayerPreferences().aiSelectedModel = newModel
        }

        val state = if (isModelReady(newModel)) {
            val s = downloadStates.getOrPut(newModel) { MutableStateFlow(ModelDownloadState.Ready) }
            if (s.value !is ModelDownloadState.Downloading) {
                s.value = ModelDownloadState.Ready
            }
            s.value
        } else {
            val s = downloadStates.getOrPut(newModel) { MutableStateFlow(ModelDownloadState.NotDownloaded) }
            if (s.value is ModelDownloadState.Ready) {
                s.value = ModelDownloadState.NotDownloaded
            }
            s.value
        }
        _downloadState.value = state

        if (isModelReady(newModel)) {
            val prefs = PlayerPreferences()
            initArtifactNet(prefs)
        } else {
            _activeAccelerationProvider.value = "${newModel.runtimeTag} (Not Downloaded)"
        }
    }

    /**
     * Re-creates the active session with updated GPU options without restarting the app.
     */
    fun reloadHardwareAcceleration() {
        resetResult()

        val prefs = PlayerPreferences()
        if (isModelReady(AiModelType.ARTIFACTNET)) {
            synchronized(engineLock) {
                closeEngines()
            }
            initArtifactNet(prefs)
        }
    }

    private fun initArtifactNet(prefs: PlayerPreferences) {
        val dir = getModelsDir()
        val onnxFile = File(dir, ARTIFACTNET_ONNX)
        val tfliteFile = File(dir, ARTIFACTNET_TFLITE)

        val useGpu = prefs.aiGpuAccelerationEnabled
        val targetGpu = GpuDetector.resolveGpu(prefs.aiSelectedGpuId)
        val detectedGpus = GpuDetector.getDetectedGpus()
        val hasGpuTarget = useGpu &&
            targetGpu.id != GpuDevice.ID_CPU &&
            (targetGpu.id != GpuDevice.ID_AUTO || detectedGpus.isNotEmpty())

        if (hasGpuTarget && onnxFile.exists() && onnxFile.length() > 100L) {
            // Attempt ONNX GPU session first
            val res = createOnnxSession(onnxFile, prefs, allowCpuFallback = false)
            if (res != null) {
                synchronized(engineLock) {
                    closeEngines()
                    onnxEnv = OrtEnvironment.getEnvironment()
                    onnxSession = res.first
                }
                _activeAccelerationProvider.value = res.second
                setDownloadReady(AiModelType.ARTIFACTNET)
                return
            }
        }

        // LiteRT / CPU fallback
        if (tfliteFile.exists() && tfliteFile.length() > 15_000_000L) {
            initTflite()
            if (hasGpuTarget) {
                _activeAccelerationProvider.value = "LiteRT CPU (XNNPACK) · GPU: ${targetGpu.name} fallback"
            }
            return
        }

        if (onnxFile.exists() && onnxFile.length() > 100L) {
            val res = createOnnxSession(onnxFile, prefs, allowCpuFallback = true)
            if (res != null) {
                synchronized(engineLock) {
                    closeEngines()
                    onnxEnv = OrtEnvironment.getEnvironment()
                    onnxSession = res.first
                }
                _activeAccelerationProvider.value = res.second
                setDownloadReady(AiModelType.ARTIFACTNET)
                return
            }
        }

        setDownloadError(AiModelType.ARTIFACTNET, "No valid ArtifactNet model file found")
    }

    private fun initTflite() {
        try {
            val modelFile = File(getModelsDir(), ARTIFACTNET_TFLITE)
            if (!modelFile.exists() || modelFile.length() < 15_000_000L) {
                setDownloadError(AiModelType.ARTIFACTNET, "ArtifactNet TFLite file not found")
                return
            }
            val loaded = FlatBufferModel.BuildFromFile(modelFile.absolutePath)
            if (loaded == null || loaded.isNull) {
                setDownloadError(AiModelType.ARTIFACTNET, "Failed to parse ArtifactNet model file")
                return
            }
            val built: Interpreter? = tryBuildTflite(loaded, useXnnpack = true)
                ?: tryBuildTflite(loaded, useXnnpack = false)
            if (built == null) {
                runCatching { loaded.close() }
                setDownloadError(AiModelType.ARTIFACTNET, "Failed to initialize LiteRT interpreter")
                return
            }
            synchronized(engineLock) {
                closeEngines()
                tfliteModel = loaded
                tfliteInterpreter = built
            }
            _activeAccelerationProvider.value = "LiteRT CPU (XNNPACK)"
            setDownloadReady(AiModelType.ARTIFACTNET)
        } catch (e: Throwable) {
            setDownloadError(AiModelType.ARTIFACTNET, e.message ?: "Failed to initialize interpreter")
        }
    }

    private fun tryBuildTflite(loaded: FlatBufferModel, useXnnpack: Boolean): Interpreter? {
        return try {
            val resolver: BuiltinOpResolver = if (useXnnpack) {
                BuiltinOpResolverWithXNNPACK()
            } else {
                BuiltinOpResolver()
            }
            val interp = Interpreter()
            val builder = InterpreterBuilder(loaded, resolver)
            builder.SetNumThreads(2)
            if (builder.apply(interp) != 0) {
                runCatching { interp.close() }
                return null
            }
            if (interp.AllocateTensors() != 0) {
                runCatching { interp.close() }
                return null
            }
            interp
        } catch (_: Throwable) {
            null
        }
    }

    private fun createOnnxSession(
        modelFile: File,
        prefs: PlayerPreferences,
        allowCpuFallback: Boolean = true,
    ): Pair<OrtSession, String>? {
        OnnxHelper.ensureNativeLibrariesLoaded()
        val env = OrtEnvironment.getEnvironment()
        val useGpu = prefs.aiGpuAccelerationEnabled
        val selectedGpuId = prefs.aiSelectedGpuId
        val targetGpu = GpuDetector.resolveGpu(selectedGpuId)
        val detectedGpus = GpuDetector.getDetectedGpus()

        val hasGpuTarget = useGpu &&
            targetGpu.id != GpuDevice.ID_CPU &&
            (targetGpu.id != GpuDevice.ID_AUTO || detectedGpus.isNotEmpty())

        if (hasGpuTarget) {
            val gpuIndex = if (targetGpu.id.startsWith("gpu:")) {
                targetGpu.id.removePrefix("gpu:").toIntOrNull() ?: 0
            } else 0

            val sessionOpts = OrtSession.SessionOptions()
            sessionOpts.setIntraOpNumThreads(2)

            val appliedProvider = OnnxHelper.applyGpuProvider(sessionOpts, targetGpu, gpuIndex)
            if (appliedProvider != null) {
                try {
                    val session = env.createSession(modelFile.absolutePath, sessionOpts)
                    return Pair(session, appliedProvider)
                } catch (_: Throwable) {
                    // GPU session creation failed at runtime
                } finally {
                    runCatching { sessionOpts.close() }
                }
            } else {
                runCatching { sessionOpts.close() }
            }

            if (!allowCpuFallback) {
                return null
            }

            val cpuOpts = OrtSession.SessionOptions()
            try {
                cpuOpts.setIntraOpNumThreads(2)
                val session = env.createSession(modelFile.absolutePath, cpuOpts)
                return Pair(session, "CPU Fallback (SIMD) · GPU: ${targetGpu.name}")
            } catch (_: Throwable) {
                return null
            } finally {
                runCatching { cpuOpts.close() }
            }
        }

        if (!allowCpuFallback) {
            return null
        }

        val cpuOpts = OrtSession.SessionOptions()
        try {
            cpuOpts.setIntraOpNumThreads(2)
            val session = env.createSession(modelFile.absolutePath, cpuOpts)
            return Pair(session, "CPU Only (Software SIMD)")
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { cpuOpts.close() }
        }
    }

    private fun setDownloadReady(modelType: AiModelType) {
        val flow = downloadStates.getOrPut(modelType) { MutableStateFlow(ModelDownloadState.Ready) }
        flow.value = ModelDownloadState.Ready
        if (currentModel == modelType) {
            _downloadState.value = ModelDownloadState.Ready
        }
    }

    private fun setDownloadError(modelType: AiModelType, message: String) {
        val flow = downloadStates.getOrPut(modelType) { MutableStateFlow(ModelDownloadState.Error(message)) }
        flow.value = ModelDownloadState.Error(message)
        if (currentModel == modelType) {
            _downloadState.value = ModelDownloadState.Error(message)
        }
    }

    /**
     * Download the specified model file into application storage, trying primary and fallback URLs.
     */
    fun downloadModel(modelType: AiModelType = currentModel) {
        val stateFlow = downloadStates.getOrPut(modelType) { MutableStateFlow(ModelDownloadState.NotDownloaded) }
        if (stateFlow.value is ModelDownloadState.Downloading) return

        val initialDownloading = ModelDownloadState.Downloading(0f, 0L, modelType.totalBytes)
        stateFlow.value = initialDownloading
        if (currentModel == modelType) {
            _downloadState.value = initialDownloading
        }

        scope.launch(Dispatchers.IO) {
            val dir = getModelsDir()
            val urlsToTry = listOf(modelType.downloadUrl) + modelType.fallbackUrls
            var downloadSuccess = false
            var lastError: Exception? = null

            for (url in urlsToTry) {
                val fileName = when {
                    url.endsWith(".tflite", ignoreCase = true) -> ARTIFACTNET_TFLITE
                    url.endsWith(".onnx", ignoreCase = true) && modelType == AiModelType.ARTIFACTNET -> ARTIFACTNET_ONNX
                    else -> modelType.modelFileName
                }
                val modelTmp = File(dir, "$fileName.tmp")
                val modelFinal = File(dir, fileName)
                var totalDownloaded = 0L

                try {
                    downloadFile(url, modelTmp) { bytesRead, totalBytes ->
                        totalDownloaded += bytesRead
                        val total = if (totalBytes > 0L) totalBytes else modelType.totalBytes
                        val progress = (totalDownloaded.toFloat() / total).coerceIn(0f, 1f)
                        val downloadingState = ModelDownloadState.Downloading(progress, totalDownloaded, total)
                        stateFlow.value = downloadingState
                        if (currentModel == modelType) {
                            _downloadState.value = downloadingState
                        }
                    }

                    if (!modelTmp.exists() || modelTmp.length() == 0L) {
                        throw RuntimeException("Downloaded model file is empty or missing")
                    }

                    try {
                        Files.move(modelTmp.toPath(), modelFinal.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: Exception) {
                        Files.move(modelTmp.toPath(), modelFinal.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    downloadSuccess = true
                    withContext(Dispatchers.Default) {
                        setDownloadReady(modelType)
                        if (currentModel == modelType) {
                            init()
                        }
                    }
                    break
                } catch (e: Exception) {
                    lastError = e
                    runCatching { modelTmp.delete() }
                }
            }

            if (!downloadSuccess) {
                setDownloadError(modelType, lastError?.message ?: "Download failed")
            }
        }
    }

    private fun downloadFile(url: String, targetFile: File, onBytesRead: (Long, Long) -> Unit) {
        val client = ProxyManager.getOkHttpClient().newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} downloading model asset")
            }
            val body = response.body
            val totalBytes = body.contentLength()
            FileOutputStream(targetFile).use { out ->
                val buf = ByteArray(16384)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        onBytesRead(read.toLong(), totalBytes)
                    }
                }
                out.flush()
            }
        }
    }

    /**
     * Delete the specified model file and release engine memory.
     */
    fun deleteModel(modelType: AiModelType = currentModel) {
        analysisJob?.cancel()
        analysisJob = null

        synchronized(engineLock) {
            if (currentModel == modelType) {
                closeEngines()
            }
        }

        // Force GC to help release memory mapped / JNI file handles on Windows & Linux
        System.gc()

        fun robustDelete(file: File) {
            if (!file.exists()) return
            runCatching { Files.deleteIfExists(file.toPath()) }
            if (file.exists()) {
                runCatching { file.delete() }
            }
        }

        val dir = getModelsDir()
        robustDelete(File(dir, ARTIFACTNET_ONNX))
        robustDelete(File(dir, "$ARTIFACTNET_ONNX.tmp"))
        robustDelete(File(dir, ARTIFACTNET_TFLITE))
        robustDelete(File(dir, "$ARTIFACTNET_TFLITE.tmp"))

        val flow = downloadStates.getOrPut(modelType) { MutableStateFlow(ModelDownloadState.NotDownloaded) }
        flow.value = ModelDownloadState.NotDownloaded
        if (currentModel == modelType) {
            _downloadState.value = ModelDownloadState.NotDownloaded
            _result.value = null
            _activeAccelerationProvider.value = "${modelType.runtimeTag} (Uninstalled)"
        }
    }

    /**
     * Import a model file (.onnx or .tflite) from disk into the application models directory.
     */
    fun importModelFile(modelType: AiModelType, sourceFile: File): kotlin.Result<Unit> {
        val minSize = 1_000_000L
        if (!sourceFile.exists() || sourceFile.length() < minSize) {
            return kotlin.Result.failure(IllegalArgumentException("Invalid model file: missing or smaller than required size"))
        }
        return try {
            val dir = getModelsDir()
            val targetName = if (sourceFile.name.endsWith(".tflite", ignoreCase = true)) {
                ARTIFACTNET_TFLITE
            } else {
                ARTIFACTNET_ONNX
            }
            val targetFile = File(dir, targetName)
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

            setDownloadReady(modelType)
            if (currentModel == modelType) {
                synchronized(engineLock) {
                    closeEngines()
                }
                init()
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            setDownloadError(modelType, e.message ?: "Failed to import model")
            kotlin.Result.failure(e)
        }
    }

    fun openModelsDirectory() {
        openDirectoryInFileManager(getModelsDir())
    }

    fun openManualDownloadPage(modelType: AiModelType = currentModel) {
        openUrl(modelType.manualDownloadUrl)
    }

    fun refreshModels() {
        val prefs = PlayerPreferences()
        val preferredModel = prefs.aiSelectedModel
        for (m in AiModelType.entries) {
            val stateFlow = downloadStates.getOrPut(m) { MutableStateFlow(ModelDownloadState.NotDownloaded) }
            if (isModelReady(m)) {
                stateFlow.value = ModelDownloadState.Ready
            } else if (stateFlow.value !is ModelDownloadState.Downloading) {
                stateFlow.value = ModelDownloadState.NotDownloaded
            }
        }
        _downloadState.value = downloadStates[preferredModel]?.value ?: ModelDownloadState.NotDownloaded
        if (isModelReady(preferredModel) && !isModelLoaded()) {
            init()
        }
    }

    private fun closeEngines() {
        closeTflite()
        closeOnnxSession()
    }

    private fun closeTflite() {
        analysisJob?.cancel()
        synchronized(engineLock) {
            try {
                tfliteInterpreter?.close()
            } catch (_: Throwable) {
            }
            try {
                tfliteModel?.close()
            } catch (_: Throwable) {
            }
            tfliteInterpreter = null
            tfliteModel = null
        }
    }

    private fun closeOnnxSession() {
        analysisJob?.cancel()
        synchronized(engineLock) {
            try {
                onnxSession?.close()
            } catch (_: Throwable) {
            }
            onnxSession = null
        }
    }

    @Volatile private var isVerdictFinal = false
    fun isVerdictFinal(): Boolean = synchronized(resultLock) { isVerdictFinal }

    @Volatile private var currentAnalysisEpoch = 0L

    fun isAnalyzingUpfront(): Boolean = analysisJob?.isActive == true

    private val checkpointScores = mutableListOf<Float>()

    @Volatile private var hasSeekedDeep = false

    fun recordCheckpointScore(score: Float) {
        if (score.isNaN()) return
        synchronized(resultLock) {
            if (isVerdictFinal) return // Ignore if verdict is already locked!

            val clamped = score.coerceIn(0f, 1f)
            checkpointScores.add(clamped)
            val threshold = PlayerPreferences().aiScoreThreshold
            val n = checkpointScores.size

            val sortedScores = checkpointScores.sorted()
            val median = sortedScores[n / 2]
            val highCount = checkpointScores.count { it >= threshold }

            // Consecutive AI chunks: 2 adjacent windows both elevated >= threshold
            val hasConsecutiveAi = checkpointScores.zipWithNext().any { (a, b) ->
                a >= threshold && b >= threshold
            }

            // Transition detection: high chunk with elevated neighbor (e.g. intro build-up)
            val hasTransitionAi = checkpointScores.indices.any { i ->
                val s = checkpointScores[i]
                if (s < threshold) return@any false
                val prev = checkpointScores.getOrNull(i - 1) ?: 0f
                val next = checkpointScores.getOrNull(i + 1) ?: 0f
                val neighborMax = maxOf(prev, next)
                neighborMax >= 0.35f && (s + neighborMax) / 2f >= 0.50f
            }

            val isConfirmedAi = isAiConfirmed ||
                highCount >= 2 ||
                hasConsecutiveAi ||
                hasTransitionAi ||
                clamped >= 0.90f ||
                (n >= 4 && checkpointScores.count { it >= 0.50f } >= (n / 2 + 1))

            if (isConfirmedAi) {
                isAiConfirmed = true
                isVerdictFinal = true
                val aiScores = checkpointScores.filter { it >= 0.30f }
                val representativeAiScore = if (aiScores.isNotEmpty()) {
                    aiScores.max()
                } else clamped
                peakScore = maxOf(peakScore, representativeAiScore)
                println("AI Confirmed (n=$n): peak=$peakScore, highCount=$highCount, chunks=$checkpointScores")
                _result.value = Result(peakScore, true, Status.DONE)
            } else {
                val displayScore = median
                peakScore = displayScore
                if (n >= AiDetectionAudioProcessor.MAX_CHECKPOINTS || hasSeekedDeep) {
                    isVerdictFinal = true
                }
                println("Human verdict (n=$n): score=$displayScore, median=$median, chunks=$checkpointScores")
                _result.value = Result(displayScore, false, Status.DONE)
            }
        }
    }

    fun notifySeek(seekPositionSec: Double = 0.0) {
        synchronized(resultLock) {
            if (!isAiConfirmed && !isVerdictFinal) {
                hasSeekedDeep = seekPositionSec >= 10.0
            }
        }
    }

    /**
     * Upfront multi-point analysis across the track (issue #41).
     * Samples 4 strategic locations (10%, 35%, 60%, 85%) across the track
     * in the background, calculates the representative median/average score,
     * and publishes one final, immutable verdict without any playback fluctuation.
     */
    fun analyzeTrackUpfront(
        urlOrPath: String,
        headers: Map<String, String> = emptyMap(),
        hlsAdapter: HlsStreamAdapter? = null,
    ) {
        if (!isModelReady()) return
        initAsync()

        analysisJob?.cancel()
        val epoch: Long
        synchronized(resultLock) {
            currentAnalysisEpoch++
            epoch = currentAnalysisEpoch
            isVerdictFinal = false
            isAiConfirmed = false
            peakScore = 0f
            checkpointScores.clear()
            _result.value = Result(0f, false, Status.ANALYZING)
        }

        analysisJob = scope.launch(inferenceDispatcher) {
            try {
                // Wait briefly if model is still loading asynchronously
                var retries = 0
                while (!isModelLoaded() && retries < 25 && isActive) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                if (!isModelLoaded() || !isActive || epoch != currentAnalysisEpoch) return@launch

                val isLocalFile = File(urlOrPath).exists() || urlOrPath.startsWith("file:")
                val isHls = urlOrPath.contains(".m3u8") || hlsAdapter != null
                val isHttp = urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")

                val chunkScores = mutableListOf<Float>()

                if (isLocalFile) {
                    val filePath = if (urlOrPath.startsWith("file://")) urlOrPath.removePrefix("file://") else urlOrPath
                    val file = File(filePath)
                    if (!file.exists()) return@launch

                    val grabber = FFmpegFrameGrabber(file).apply {
                        sampleRate = TARGET_SR
                        audioChannels = 2
                        sampleMode = FrameGrabber.SampleMode.FLOAT
                    }
                    try {
                        grabber.start()
                        val durationUs = grabber.lengthInTime
                        val ratios = listOf(0.10, 0.35, 0.60, 0.85)

                        for (r in ratios) {
                            if (!isActive || epoch != currentAnalysisEpoch) break
                            val targetUs = (durationUs * r).toLong()
                            if (durationUs > 16_000_000L) {
                                grabber.timestamp = targetUs
                                discardSeekTransients(grabber)
                            }
                            val samples = extractFourSecondsMono(grabber)
                            if (samples != null) {
                                val s = analyzePcmSamples(samples)
                                if (s != null) chunkScores.add(s)
                            }
                        }
                    } finally {
                        grabber.stop()
                        runCatching { grabber.release() }
                    }
                } else if (isHls) {
                    val adapter = hlsAdapter ?: try {
                        HlsStreamAdapter(urlOrPath, headers)
                    } catch (_: Exception) {
                        null
                    }
                    if (adapter != null) {
                        val durationMs = adapter.totalDurationMs
                        val ratios = listOf(0.10, 0.35, 0.60, 0.85)

                        for (r in ratios) {
                            if (!isActive || epoch != currentAnalysisEpoch) break
                            val startMs = if (durationMs > 16_000L) (durationMs * r).toLong() else 0L
                            val inStream = try { adapter.getInputStream(startMs) } catch (_: Exception) { null } ?: continue
                            val grabber = FFmpegFrameGrabber(inStream, 0).apply {
                                format = "mp4"
                                setOption("probesize", "32768")
                                setOption("analyzeduration", "0")
                                sampleRate = TARGET_SR
                                audioChannels = 2
                                sampleMode = FrameGrabber.SampleMode.FLOAT
                            }
                            try {
                                grabber.start()
                                val samples = extractFourSecondsMono(grabber)
                                if (samples != null) {
                                    val s = analyzePcmSamples(samples)
                                    if (s != null) chunkScores.add(s)
                                }
                            } catch (_: Exception) {
                                runCatching {
                                    val altStream = adapter.getInputStream(startMs)
                                    val altGrabber = FFmpegFrameGrabber(altStream, 0).apply {
                                        sampleRate = TARGET_SR
                                        audioChannels = 2
                                        sampleMode = FrameGrabber.SampleMode.FLOAT
                                    }
                                    altGrabber.start()
                                    val samples = extractFourSecondsMono(altGrabber)
                                    altGrabber.stop()
                                    altGrabber.release()
                                    if (samples != null) {
                                        val s = analyzePcmSamples(samples)
                                        if (s != null) chunkScores.add(s)
                                    }
                                }
                            } finally {
                                grabber.stop()
                                runCatching { grabber.release() }
                            }
                        }
                    }
                } else if (isHttp) {
                    val grabber = FFmpegFrameGrabber(urlOrPath).apply {
                        if (headers.isNotEmpty()) {
                            val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                            setOption("headers", headerBlob + "\r\n")
                        }
                        setOption("probesize", "32768")
                        setOption("analyzeduration", "0")
                        setOption("http_seekable", "1")
                        sampleRate = TARGET_SR
                        audioChannels = 2
                        sampleMode = FrameGrabber.SampleMode.FLOAT
                    }
                    try {
                        grabber.start()
                        val durationUs = grabber.lengthInTime
                        val ratios = listOf(0.10, 0.35, 0.60, 0.85)

                        for (r in ratios) {
                            if (!isActive || epoch != currentAnalysisEpoch) break
                            val targetUs = (durationUs * r).toLong()
                            if (durationUs > 16_000_000L) {
                                grabber.timestamp = targetUs
                                discardSeekTransients(grabber)
                            }
                            val samples = extractFourSecondsMono(grabber)
                            if (samples != null) {
                                val s = analyzePcmSamples(samples)
                                if (s != null) chunkScores.add(s)
                            }
                        }
                    } finally {
                        grabber.stop()
                        runCatching { grabber.release() }
                    }
                }

                if (!isActive || epoch != currentAnalysisEpoch) return@launch

                if (chunkScores.isNotEmpty()) {
                    val threshold = PlayerPreferences().aiScoreThreshold
                    val verdict = calculateVerdictFromScores(chunkScores, threshold)
                    synchronized(resultLock) {
                        if (epoch != currentAnalysisEpoch) return@synchronized
                        isVerdictFinal = true
                        isAiConfirmed = verdict.isAi
                        peakScore = verdict.score
                        checkpointScores.clear()
                        checkpointScores.addAll(chunkScores)
                        _result.value = verdict
                        println("AiDetectionManager: Upfront scan locked (${chunkScores.size} points). Verdict: score=${verdict.score}, isAi=${verdict.isAi}, chunks=$chunkScores")
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                println("AiDetectionManager: Upfront scan error: ${e.message}")
            }
        }
    }

    fun calculateVerdictFromScores(scores: List<Float>, threshold: Float): Result {
        val clamped = scores.map { it.coerceIn(0f, 1f) }
        val sorted = clamped.sorted()
        val n = sorted.size
        val median = if (n > 0) sorted[n / 2] else 0f
        val maxScore = sorted.lastOrNull() ?: 0f
        val highCount = clamped.count { it >= threshold }

        // AI confirmation criteria:
        // - At least 2 points >= threshold (e.g. 0.70)
        // - OR 1 point >= 0.90 with another point elevated >= 0.35 (vocal transition)
        // - OR any point with extreme forensic confidence >= 0.95
        val isConfirmed = highCount >= 2 ||
            (maxScore >= 0.90f && clamped.count { it >= 0.35f } >= 2) ||
            maxScore >= 0.95f

        return if (isConfirmed) {
            val aiScores = clamped.filter { it >= 0.30f }
            val representativeScore = if (aiScores.isNotEmpty()) aiScores.average().toFloat() else maxScore
            Result(representativeScore, isAi = true, status = Status.DONE)
        } else {
            Result(median, isAi = false, status = Status.DONE)
        }
    }

    private fun discardSeekTransients(grabber: FFmpegFrameGrabber) {
        var warmUpSamples = TARGET_SR / 3 // ~0.33s of audio
        while (warmUpSamples > 0) {
            val frame = try { grabber.grabSamples() } catch (_: Exception) { null } ?: break
            val buffers = frame.samples ?: continue
            val len = buffers[0]?.remaining() ?: 0
            if (len <= 0) break
            val ch = frame.audioChannels.coerceAtLeast(1)
            val isPlanar = buffers.size >= ch && ch > 1
            val framesInBuf = if (isPlanar || ch <= 1) len else len / ch
            warmUpSamples -= framesInBuf
        }
    }

    private fun extractFourSecondsMono(grabber: FFmpegFrameGrabber): FloatArray? {
        val targetSamples = TARGET_SR * 4 // 176400 samples
        val samples = FloatArray(targetSamples)
        var samplesCollected = 0
        var emptyCount = 0

        while (samplesCollected < targetSamples && emptyCount < 50) {
            val frame = try { grabber.grabSamples() } catch (_: Exception) { null }
            if (frame == null) {
                emptyCount++
                continue
            }
            val buffers = frame.samples
            if (buffers == null || buffers.isEmpty()) {
                emptyCount++
                continue
            }
            val first = buffers[0] ?: continue
            val len = first.remaining()
            if (len <= 0) {
                emptyCount++
                continue
            }
            val ch = frame.audioChannels.coerceAtLeast(1)
            val isPlanar = buffers.size >= ch && ch > 1
            val pos = first.position()

            if (isPlanar) {
                // Planar audio: separate buffer for each channel
                val toCopy = minOf(len, targetSamples - samplesCollected)
                val invCh = 1f / ch
                for (i in 0 until toCopy) {
                    var sum = 0f
                    for (c in 0 until ch) {
                        val b = buffers[c] as? java.nio.FloatBuffer
                        sum += b?.get(pos + i) ?: 0f
                    }
                    samples[samplesCollected++] = sum * invCh
                }
            } else if (ch > 1) {
                // Interleaved audio: single buffer with [L0, R0, L1, R1, ...]
                val b = first as? java.nio.FloatBuffer ?: continue
                val framesAvailable = len / ch
                val toCopyFrames = minOf(framesAvailable, targetSamples - samplesCollected)
                val invCh = 1f / ch
                for (i in 0 until toCopyFrames) {
                    var sum = 0f
                    val frameOffset = pos + i * ch
                    for (c in 0 until ch) {
                        sum += b.get(frameOffset + c)
                    }
                    samples[samplesCollected++] = sum * invCh
                }
            } else {
                // Mono: single channel
                val b = first as? java.nio.FloatBuffer ?: continue
                val toCopy = minOf(len, targetSamples - samplesCollected)
                for (i in 0 until toCopy) {
                    samples[samplesCollected++] = b.get(pos + i)
                }
            }
        }
        return if (samplesCollected >= targetSamples) samples else null
    }

    fun analyzeAsync(pcmBytes: ByteArray, sampleRate: Int, channelCount: Int) {
        if (!isModelLoaded()) return

        if (_result.value == null) {
            _result.value = Result(0f, false, Status.ANALYZING)
        }
        val epoch = currentAnalysisEpoch
        scope.launch(inferenceDispatcher) {
            try {
                if (epoch != currentAnalysisEpoch) return@launch
                val score = analyzePcm(pcmBytes, sampleRate, channelCount) ?: return@launch
                if (epoch != currentAnalysisEpoch) return@launch
                recordCheckpointScore(score)
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (e: OutOfMemoryError) {
                System.gc()
                if (epoch == currentAnalysisEpoch && (_result.value == null || _result.value?.status == Status.ANALYZING)) {
                    _result.value = Result(peakScore, isAiConfirmed, Status.ERROR)
                }
            } catch (e: Exception) {
                if (epoch == currentAnalysisEpoch && (_result.value == null || _result.value?.status == Status.ANALYZING)) {
                    _result.value = Result(peakScore, isAiConfirmed, Status.ERROR)
                }
            }
        }
    }

    fun resetResult() {
        analysisJob?.cancel()
        analysisJob = null
        synchronized(resultLock) {
            currentAnalysisEpoch++
            peakScore = 0f
            isAiConfirmed = false
            isVerdictFinal = false
            hasSeekedDeep = false
            checkpointScores.clear()
            _result.value = null
        }
    }

    fun analyzePcm(pcmBytes: ByteArray, sampleRate: Int = TARGET_SR, channelCount: Int = 2): Float? {
        val resampled = preparePcmForInference(pcmBytes, sampleRate, channelCount) ?: return null
        return analyzeMonoSamples(resampled)
    }

    fun analyzePcmSamples(samples: FloatArray): Float? {
        if (samples.isEmpty()) return null

        var sumSq = 0f
        var maxPeak = 0f
        for (sample in samples) {
            sumSq += sample * sample
            val absVal = kotlin.math.abs(sample)
            if (absVal > maxPeak) maxPeak = absVal
        }
        val rms = kotlin.math.sqrt(sumSq / samples.size)
        if (rms < 0.001f) {
            return null
        }

        val resampled = if (maxPeak > 1.0f) {
            val scale = 1.0f / maxPeak
            FloatArray(samples.size) { samples[it] * scale }
        } else {
            samples
        }

        return analyzeMonoSamples(resampled)
    }

    fun analyzeMonoSamples(resampled: FloatArray): Float? {
        if (!isModelLoaded()) return null
        return when (currentModel) {
            AiModelType.ARTIFACTNET -> {
                val onnx = onnxSession
                val tflite = tfliteInterpreter
                when {
                    onnx != null -> runOnnxDetection(onnx, resampled)
                    tflite != null -> runTfliteDetection(tflite, resampled)
                    else -> null
                }
            }
        }
    }

    // ─── Shared Audio Processing Pipeline ───────────────────────────────────

    /**
     * Decodes 16-bit little-endian PCM to mono float samples [-1.0, 1.0],
     * resamples to [TARGET_SR] (44.1 kHz) if needed, and checks energy gate.
     * Applies peak normalization (0.95) to prevent numerical overflow in STFT/UNet.
     * Returns null if audio is pure silence or invalid.
     */
    fun preparePcmForInference(
        pcmBytes: ByteArray,
        srcSr: Int,
        channels: Int,
    ): FloatArray? {
        val numFrames = pcmBytes.size / (2 * channels)
        if (numFrames == 0) return null

        val buf = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val mono = FloatArray(numFrames)
        if (channels == 1) {
            for (i in 0 until numFrames) {
                mono[i] = buf.short.toFloat() / 32768f
            }
        } else {
            val invChannels = 1f / channels
            for (i in 0 until numFrames) {
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += buf.short.toFloat() / 32768f
                }
                mono[i] = sum * invChannels
            }
        }

        val resampled = if (srcSr == TARGET_SR) mono else {
            val ratio = srcSr.toDouble() / TARGET_SR
            val outLen = (mono.size / ratio).toInt()
            FloatArray(outLen) { i ->
                val srcPos = i * ratio
                val lo = srcPos.toInt().coerceAtMost(mono.size - 1)
                val hi = (lo + 1).coerceAtMost(mono.size - 1)
                mono[lo] * (1f - (srcPos - lo).toFloat()) + mono[hi] * (srcPos - lo).toFloat()
            }
        }

        // Check audio energy (RMS) — if lead-in is pure silence (RMS < 0.001), wait for music
        var sumSq = 0f
        var maxPeak = 0f
        for (sample in resampled) {
            sumSq += sample * sample
            val absVal = kotlin.math.abs(sample)
            if (absVal > maxPeak) maxPeak = absVal
        }
        val rms = kotlin.math.sqrt(sumSq / resampled.size)
        if (rms < 0.001f) {
            return null
        }

        // Peak normalization: prevent numerical overflow / NaN if audio is clipped (> 1.0)
        if (maxPeak > 1.0f) {
            val scale = 1.0f / maxPeak
            for (i in resampled.indices) {
                resampled[i] *= scale
            }
        }

        return resampled
    }

    // ─── TFLite Inference Pipeline ──────────────────────────────────────────

    private fun runTfliteDetection(
        session: Interpreter,
        resampled: FloatArray,
    ): Float {
        val inputSamples: FloatArray
        val numChunks: Int
        if (resampled.size < CHUNK_SAMPLES) {
            val n = resampled.size
            val fadeLen = minOf(882, n / 10)
            if (fadeLen > 1 && n > fadeLen) {
                val loopLen = n - fadeLen
                val loopable = FloatArray(loopLen)
                for (k in 0 until fadeLen) {
                    val alpha = k.toFloat() / fadeLen
                    loopable[k] = alpha * resampled[k] + (1f - alpha) * resampled[n - fadeLen + k]
                }
                for (k in fadeLen until loopLen) {
                    loopable[k] = resampled[k]
                }
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    loopable[i % loopLen]
                }
            } else {
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    resampled[i % n]
                }
            }
            numChunks = 1
        } else {
            inputSamples = resampled
            numChunks = resampled.size / CHUNK_SAMPLES
        }

        val scores = mutableListOf<Float>()
        var offset = 0
        for (c in 0 until numChunks) {
            val chunk = inputSamples.copyOfRange(offset, offset + CHUNK_SAMPLES)
            val prob: Float = synchronized(engineLock) {
                val active = tfliteInterpreter ?: return 0f
                val inputPtr: FloatPointer = active.typed_input_tensor_float(0)
                inputPtr.put(chunk, 0, chunk.size)
                if (active.Invoke() != 0) return 0f
                active.typed_output_tensor_float(0).get(0)
            }
            if (!prob.isNaN()) {
                scores.add(prob.coerceIn(0f, 1f))
            }
            offset += CHUNK_SAMPLES
        }

        if (scores.isEmpty()) return 0f

        scores.sort()
        return if (scores.size % 2 == 0) {
            (scores[scores.size / 2 - 1] + scores[scores.size / 2]) / 2f
        } else {
            scores[scores.size / 2]
        }
    }

    // ─── ONNX Inference Pipeline ─────────────────────────────────────────────

    private fun runOnnxDetection(
        session: OrtSession,
        resampled: FloatArray,
    ): Float = synchronized(engineLock) {
        if (onnxSession != session) return 0f

        val env = OrtEnvironment.getEnvironment()
        val inputNames = session.inputNames
        val inputName = inputNames.firstOrNull { name ->
            val l = name.lowercase(Locale.ROOT)
            !l.contains("mask")
        } ?: inputNames.firstOrNull() ?: "audio_input"

        val inputInfo = session.inputInfo[inputName]
        val tensorInfo = inputInfo?.info as? TensorInfo
        val shape = tensorInfo?.shape

        val isSpectrogramModel = shape != null && shape.size >= 3

        if (isSpectrogramModel) {
            val inputSamples = if (resampled.size < CHUNK_SAMPLES) {
                FloatArray(CHUNK_SAMPLES) { i -> resampled[i % resampled.size] }
            } else {
                resampled.copyOfRange(0, CHUNK_SAMPLES)
            }

            val tensor: OnnxTensor = if (shape.size == 3) {
                // Audio Spectrogram: [1, MelBins, Frames] or [1, Frames, MelBins]
                val spectrogram = MelSpectrogram.computeLogMel(inputSamples, TARGET_SR)
                if (shape[2] == 128L || (shape[1] > 0 && shape[1] != 128L && shape[2] == -1L)) {
                    // Transpose to [1, Frames, MelBins] (standard AST convention)
                    val nMels = spectrogram[0].size
                    val numFrames = spectrogram[0][0].size
                    val transposed = Array(1) { Array(numFrames) { FloatArray(nMels) } }
                    for (f in 0 until numFrames) {
                        for (m in 0 until nMels) {
                            transposed[0][f][m] = spectrogram[0][m][f]
                        }
                    }
                    OnnxTensor.createTensor(env, transposed)
                } else {
                    OnnxTensor.createTensor(env, spectrogram)
                }
            } else {
                // 4D tensor input: [1, 1, MelBins, Frames] or [1, 1, Frames, MelBins]
                val spectrogram = MelSpectrogram.computeLogMel(inputSamples, TARGET_SR)
                if (shape.size == 4 && shape[3] == 128L) {
                    val nMels = spectrogram[0].size
                    val numFrames = spectrogram[0][0].size
                    val transposed = Array(1) { Array(1) { Array(numFrames) { FloatArray(nMels) } } }
                    for (f in 0 until numFrames) {
                        for (m in 0 until nMels) {
                            transposed[0][0][f][m] = spectrogram[0][m][f]
                        }
                    }
                    OnnxTensor.createTensor(env, transposed)
                } else {
                    OnnxTensor.createTensor(env, Array(1) { spectrogram })
                }
            }

            val inputsToFeed = mutableMapOf<String, OnnxTensor>()
            val extraTensors = mutableListOf<OnnxTensor>()

            try {
                for (name in inputNames) {
                    if (name == inputName) {
                        inputsToFeed[name] = tensor
                    } else if (name.lowercase(Locale.ROOT).contains("mask")) {
                        // Attention mask with all 1s
                        val maskLen = if (shape.size >= 3) 344 else 1024
                        val maskTensor = OnnxTensor.createTensor(env, Array(1) { LongArray(maskLen) { 1L } })
                        extraTensors.add(maskTensor)
                        inputsToFeed[name] = maskTensor
                    }
                }
                if (inputsToFeed.isEmpty()) {
                    inputsToFeed[inputName] = tensor
                }

                val outputResult = synchronized(engineLock) {
                    if (onnxSession == null || session != onnxSession) return 0f
                    session.run(inputsToFeed)
                }

                return try {
                    extractScoreFromOnnxOutput(outputResult[0].value)
                } finally {
                    outputResult.close()
                }
            } finally {
                tensor.close()
                for (t in extraTensors) {
                    runCatching { t.close() }
                }
            }
        }

        // Waveform model (shape.size <= 2 or null) -> multi-chunk processing
        val inputSamples: FloatArray
        val numChunks: Int
        if (resampled.size < CHUNK_SAMPLES) {
            val n = resampled.size
            val fadeLen = minOf(882, n / 10)
            if (fadeLen > 1 && n > fadeLen) {
                val loopLen = n - fadeLen
                val loopable = FloatArray(loopLen)
                for (k in 0 until fadeLen) {
                    val alpha = k.toFloat() / fadeLen
                    loopable[k] = alpha * resampled[k] + (1f - alpha) * resampled[n - fadeLen + k]
                }
                for (k in fadeLen until loopLen) {
                    loopable[k] = resampled[k]
                }
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    loopable[i % loopLen]
                }
            } else {
                inputSamples = FloatArray(CHUNK_SAMPLES) { i ->
                    resampled[i % n]
                }
            }
            numChunks = 1
        } else {
            inputSamples = resampled
            numChunks = resampled.size / CHUNK_SAMPLES
        }

        val scores = mutableListOf<Float>()
        var offset = 0
        for (c in 0 until numChunks) {
            val chunk = inputSamples.copyOfRange(offset, offset + CHUNK_SAMPLES)
            val tensor: OnnxTensor = if (shape != null && shape.size == 1) {
                val expectedLen = if (shape[0] > 0) shape[0].toInt() else chunk.size
                val adjusted = if (chunk.size == expectedLen) chunk else chunk.copyOf(expectedLen)
                OnnxTensor.createTensor(env, adjusted)
            } else {
                val expectedLen = if (shape != null && shape.size >= 2 && shape[1] > 0) shape[1].toInt() else chunk.size
                val adjusted = if (chunk.size == expectedLen) chunk else chunk.copyOf(expectedLen)
                OnnxTensor.createTensor(env, Array(1) { adjusted })
            }

            val inputsToFeed = mapOf(inputName to tensor)
            val chunkScore: Float = try {
                val outputResult = synchronized(engineLock) {
                    if (onnxSession == null || session != onnxSession) return 0f
                    session.run(inputsToFeed)
                }
                try {
                    extractScoreFromOnnxOutput(outputResult[0].value)
                } finally {
                    outputResult.close()
                }
            } finally {
                tensor.close()
            }

            if (!chunkScore.isNaN()) {
                scores.add(chunkScore.coerceIn(0f, 1f))
            }
            offset += CHUNK_SAMPLES
        }

        if (scores.isEmpty()) return 0f

        scores.sort()
        return if (scores.size % 2 == 0) {
            (scores[scores.size / 2 - 1] + scores[scores.size / 2]) / 2f
        } else {
            scores[scores.size / 2]
        }
    }

    private fun extractScoreFromOnnxOutput(raw: Any?): Float {
        return when (raw) {
            is Float -> raw.coerceIn(0f, 1f)
            is FloatArray -> {
                if (raw.size == 1) {
                    sigmoid(raw[0])
                } else if (raw.size >= 2) {
                    softmaxBinary(raw[0], raw[1])
                } else 0f
            }
            is Array<*> -> {
                if (raw.isNotEmpty()) {
                    val first = raw[0]
                    when (first) {
                        is FloatArray -> {
                            if (first.size == 1) sigmoid(first[0])
                            else if (first.size >= 2) softmaxBinary(first[0], first[1])
                            else 0f
                        }
                        is Float -> first.coerceIn(0f, 1f)
                        is Array<*> -> extractScoreFromOnnxOutput(first)
                        else -> 0f
                    }
                } else 0f
            }
            is Double -> raw.toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
    }

    private fun sigmoid(x: Float): Float {
        return if (x in 0f..1f) x else (1f / (1f + kotlin.math.exp(-x))).coerceIn(0f, 1f)
    }

    private fun softmaxBinary(human: Float, ai: Float): Float {
        val maxVal = maxOf(human, ai)
        val expHuman = kotlin.math.exp(human - maxVal)
        val expAi = kotlin.math.exp(ai - maxVal)
        val sum = expHuman + expAi
        return if (sum > 0f) (expAi / sum).coerceIn(0f, 1f) else 0.5f
    }
}
