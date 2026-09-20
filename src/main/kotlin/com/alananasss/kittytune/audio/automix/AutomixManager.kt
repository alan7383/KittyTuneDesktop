package com.alananasss.kittytune.audio.automix

import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

object AutomixManager {

    private const val TAG = "AutomixManager"

    private data class BeatAnalysisHandle(val priority: BeatAnalysisPriority, val job: Job)
    private val beatAnalysisJobs = ConcurrentHashMap<String, BeatAnalysisHandle>()
    private val immediateAnalysisMutex = Mutex()
    private val lookaheadAnalysisMutex = Mutex()
    private val failedSongIds = ConcurrentHashMap<String, Long>()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _automixDebugInfo = MutableStateFlow<AutomixDebugInfo?>(null)
    val automixDebugInfo = _automixDebugInfo.asStateFlow()

    private val _isAutomixing = MutableStateFlow(false)
    val isAutomixing = _isAutomixing.asStateFlow()

    private val _mixBeatsLeft = MutableStateFlow<Int?>(null)
    val mixBeatsLeft = _mixBeatsLeft.asStateFlow()

    @Volatile
    var currentAutomixPlan: AutomixPlan? = null
        private set

    private val _isDebugOverlayVisible = MutableStateFlow(PlayerPreferences().getAutomixDebugOverlayEnabled())
    val isDebugOverlayVisible = _isDebugOverlayVisible.asStateFlow()

    fun setDebugOverlayEnabled(enabled: Boolean) {
        _isDebugOverlayVisible.value = enabled
    }

    fun keyName(pitchClass: Int?, isMinor: Boolean?): String? {
        if (pitchClass == null) return null
        val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        val name = names.getOrNull((pitchClass % 12 + 12) % 12) ?: return null
        return if (isMinor == true) "$name min" else "$name Maj"
    }

    fun camelotCode(pitchClass: Int?, isMinor: Boolean?): String? {
        if (pitchClass == null) return null
        val idx = (pitchClass % 12 + 12) % 12
        val majorCamelot = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
        val minorCamelot = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")
        return if (isMinor == true) minorCamelot.getOrNull(idx) else majorCamelot.getOrNull(idx)
    }

    fun init() {
        failedSongIds.clear()
        scope.launch {
            try {
                AppDatabase.beatInfoDao.clearFailedBeatInfo()
            } catch (_: Exception) {}
        }
    }

    fun setIsAutomixing(active: Boolean) {
        _isAutomixing.value = active
    }

    fun setMixBeatsLeft(beats: Int?) {
        _mixBeatsLeft.value = beats
    }

    fun clearPlan() {
        currentAutomixPlan = null
        _mixBeatsLeft.value = null
        val cur = _automixDebugInfo.value
        if (cur != null && !_isAutomixing.value) {
            _automixDebugInfo.value = cur.copy(
                triggerTimeMs = null,
                incomingStartMs = null,
                tempoRatio = null,
                pitchRatio = null,
                overlapMs = null,
                status = if (cur.status.startsWith("plan")) "standby" else cur.status
            )
        }
    }

    fun maybeAnalyzeBeat(track: Track, priority: BeatAnalysisPriority = BeatAnalysisPriority.IMMEDIATE) {
        val songId = track.id.toString()

        val lastFailed = failedSongIds[songId]
        if (lastFailed != null && System.currentTimeMillis() - lastFailed < 45_000L) {
            return
        }

        synchronized(beatAnalysisJobs) {
            val existing = beatAnalysisJobs[songId]
            if (existing != null) {
                if (priority == BeatAnalysisPriority.IMMEDIATE && existing.priority == BeatAnalysisPriority.LOOKAHEAD) {
                    beatAnalysisJobs[songId] = existing.copy(priority = BeatAnalysisPriority.IMMEDIATE)
                }
                return
            }

            val job = scope.launch {
                val mutex = when (priority) {
                    BeatAnalysisPriority.IMMEDIATE -> immediateAnalysisMutex
                    BeatAnalysisPriority.LOOKAHEAD -> lookaheadAnalysisMutex
                }
                mutex.withLock {
                    try {
                        runBeatAnalysis(track, priority)
                    } catch (e: CancellationException) {
                        Logger.d(TAG, "Beat analysis cancelled for $songId ($priority)")
                        throw e
                    } catch (e: Exception) {
                        Logger.w(TAG, "Beat analysis failed for $songId: ${e.message}")
                    } finally {
                        synchronized(beatAnalysisJobs) {
                            beatAnalysisJobs.remove(songId)
                        }
                    }
                }
            }
            beatAnalysisJobs[songId] = BeatAnalysisHandle(priority, job)
        }
    }

    private suspend fun runBeatAnalysis(track: Track, priority: BeatAnalysisPriority) {
        val songId = track.id.toString()
        val isCurrent = MusicManager.currentTrack?.id == track.id
        val currentDbg = _automixDebugInfo.value ?: AutomixDebugInfo("standby")

        val existing = AppDatabase.beatInfoDao.getBeatInfo(songId)
        if (existing != null && existing.bpm > 0f) {
            if (isCurrent) {
                _automixDebugInfo.value = currentDbg.copy(
                    outTitle = track.title,
                    outBpm = existing.bpm,
                    outConfidence = existing.confidence,
                    outMixOutMs = existing.mixOutPointMs,
                    outKey = keyName(existing.keyPitchClass, existing.keyIsMinor),
                    outCamelot = camelotCode(existing.keyPitchClass, existing.keyIsMinor),
                )
            } else {
                _automixDebugInfo.value = currentDbg.copy(
                    inTitle = track.title,
                    inBpm = existing.bpm,
                    inConfidence = existing.confidence,
                    inMixInMs = existing.mixInPointMs,
                    inKey = keyName(existing.keyPitchClass, existing.keyIsMinor),
                    inCamelot = camelotCode(existing.keyPitchClass, existing.keyIsMinor),
                )
            }
            return
        }

        _automixDebugInfo.value = currentDbg.copy(
            status = if (isCurrent) "Analyzing '${track.title}'..." else "Analyzing next: '${track.title}'...",
            outTitle = if (isCurrent) track.title else currentDbg.outTitle,
            inTitle = if (!isCurrent) track.title else currentDbg.inTitle,
        )

        Logger.d(TAG, "Beat analysis starting for $songId - ${track.title} ($priority)")
        val startedAt = System.currentTimeMillis()
        val timeoutMs = if (priority == BeatAnalysisPriority.IMMEDIATE) 45_000L else 30_000L
        fun timedOutOrCancelled(): Boolean =
            System.currentTimeMillis() - startedAt > timeoutMs

        var result: BeatAnalyzer.Result? = null

        // 1. Check if track is local / downloaded
        var localPath: String? = null
        try {
            val localTrack = AppDatabase.downloadDao.getTrack(track.id)
            if (localTrack != null && localTrack.localAudioPath.isNotBlank()) {
                localPath = localTrack.localAudioPath
            }
        } catch (_: Exception) {}

        val trackDurationMs = track.durationMs ?: 0L
        if (localPath != null && File(localPath).exists()) {
            result = BeatAnalyzer.analyzeFile(localPath, shouldCancel = ::timedOutOrCancelled)
        }

        // 2. If not local, resolve stream URL for analysis
        if (result == null && !timedOutOrCancelled()) {
            var stream: com.alananasss.kittytune.data.ResolvedStream? = null
            try {
                stream = StreamResolver.resolveStreamForBeatAnalysis(track)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed resolving stream for analysis of $songId: ${e.message}")
            }

            if (stream != null) {
                val headers = mutableMapOf("User-Agent" to "SoundCloud/2025.12.10-release (Android 10; Android)")
                val fetched = BeatAnalyzer.analyzeStream(
                    url = stream.url,
                    headers = headers,
                    totalDurationMs = trackDurationMs,
                    shouldCancel = ::timedOutOrCancelled
                )
                result = fetched?.result
            }
        }

        Logger.d(
            TAG,
            "Beat analysis done for $songId: " +
                (result?.let { "bpm=%.1f conf=%.2f mixIn=%s mixOut=%s key=%d isMinor=%s".format(it.bpm, it.confidence, it.mixInPointMs, it.mixOutPointMs, it.keyPitchClass, it.keyIsMinor) }
                    ?: "failed")
        )

        if (result == null) {
            failedSongIds[songId] = System.currentTimeMillis()
            return
        }

        val entity = BeatInfoEntity(
            songId = songId,
            bpm = result.bpm,
            firstBeatOffsetMs = result.firstBeatOffsetMs,
            confidence = result.confidence,
            mixInPointMs = result.mixInPointMs,
            mixOutPointMs = result.mixOutPointMs,
            keyPitchClass = result.keyPitchClass,
            keyIsMinor = result.keyIsMinor,
        )

        withContext(Dispatchers.IO) {
            AppDatabase.beatInfoDao.upsert(entity)
        }

        val updatedDbg = _automixDebugInfo.value ?: currentDbg
        if (isCurrent) {
            _automixDebugInfo.value = updatedDbg.copy(
                outTitle = track.title,
                outBpm = result.bpm,
                outConfidence = result.confidence,
                outMixOutMs = result.mixOutPointMs,
                outKey = keyName(result.keyPitchClass, result.keyIsMinor),
                outCamelot = camelotCode(result.keyPitchClass, result.keyIsMinor),
            )
        } else {
            _automixDebugInfo.value = updatedDbg.copy(
                inTitle = track.title,
                inBpm = result.bpm,
                inConfidence = result.confidence,
                inMixInMs = result.mixInPointMs,
                inKey = keyName(result.keyPitchClass, result.keyIsMinor),
                inCamelot = camelotCode(result.keyPitchClass, result.keyIsMinor),
            )
        }
    }

    suspend fun computeAutomixPlan(
        currentTrack: Track,
        nextTrack: Track,
        currentPosition: Long,
        trackDuration: Long,
        prefs: PlayerPreferences,
    ): AutomixPlanResult {
        val currentId = currentTrack.id.toString()
        val nextId = nextTrack.id.toString()

        val (outBeat, inBeat) = withContext(Dispatchers.IO) {
            AppDatabase.beatInfoDao.getBeatInfo(currentId) to AppDatabase.beatInfoDao.getBeatInfo(nextId)
        }

        if (outBeat == null) maybeAnalyzeBeat(currentTrack, BeatAnalysisPriority.IMMEDIATE)
        if (inBeat == null && nextId != currentId) maybeAnalyzeBeat(nextTrack, BeatAnalysisPriority.IMMEDIATE)

        val partialDebug = AutomixDebugInfo(
            status = "",
            outTitle = currentTrack.title,
            outBpm = outBeat?.bpm,
            outConfidence = outBeat?.confidence,
            outMixOutMs = outBeat?.mixOutPointMs,
            outKey = keyName(outBeat?.keyPitchClass, outBeat?.keyIsMinor),
            outCamelot = camelotCode(outBeat?.keyPitchClass, outBeat?.keyIsMinor),
            inTitle = nextTrack.title,
            inBpm = inBeat?.bpm,
            inConfidence = inBeat?.confidence,
            inMixInMs = inBeat?.mixInPointMs,
            inKey = keyName(inBeat?.keyPitchClass, inBeat?.keyIsMinor),
            inCamelot = camelotCode(inBeat?.keyPitchClass, inBeat?.keyIsMinor),
        )

        val isOutFailed = failedSongIds.containsKey(currentId) || (outBeat != null && outBeat.bpm <= 0f)
        val isInFailed = failedSongIds.containsKey(nextId) || (inBeat != null && inBeat.bpm <= 0f)

        if (isOutFailed || isInFailed) {
            _automixDebugInfo.value = partialDebug.copy(
                status = "fallback: stream unsupported"
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        if (outBeat == null || inBeat == null) {
            _automixDebugInfo.value = partialDebug.copy(
                status = "Analyzing: " +
                    (if (outBeat == null) "'${currentTrack.title}'" else "") +
                    (if (outBeat == null && inBeat == null) " + " else "") +
                    (if (inBeat == null) "'${nextTrack.title}'" else "")
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = false)
        }

        if (outBeat.confidence < 0.25f || inBeat.confidence < 0.25f || outBeat.bpm <= 0f || inBeat.bpm <= 0f) {
            _automixDebugInfo.value = partialDebug.copy(status = "fallback: low confidence")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        val periodMs = (60_000f / outBeat.bpm).toDouble()

        val overlapMode = prefs.getAutomixOverlapMode() // 0=Auto (4 bars), 1=2 bars, 2=4 bars, 3=8 bars, 4=Custom
        val baseOverlapMs = when (overlapMode) {
            1 -> (8 * periodMs).toLong().coerceIn(4_000L, 10_000L) // 2 bars
            2 -> (16 * periodMs).toLong().coerceIn(6_000L, 16_000L) // 4 bars
            3 -> (32 * periodMs).toLong().coerceIn(10_000L, 20_000L) // 8 bars
            4 -> prefs.getCrossfadeDuration() * 1000L
            else -> (16 * periodMs).toLong().coerceIn(6_000L, 16_000L) // Auto
        }
        val overlapMs = baseOverlapMs.coerceIn(4_000L, 20_000L)

        val latestTrigger = trackDuration - overlapMs
        val mixOut = if (prefs.getAutomixDynamicMixPointsEnabled()) {
            outBeat.mixOutPointMs?.takeIf { it > 0 }
        } else null
        val effectiveTrigger = mixOut?.coerceAtMost(latestTrigger) ?: latestTrigger

        val phraseMs = periodMs * 8
        val anchor = max(effectiveTrigger, currentPosition + 1000)
        val k = ((anchor - outBeat.firstBeatOffsetMs) / phraseMs).toLong()
        var triggerTime = (outBeat.firstBeatOffsetMs + k * phraseMs).toLong()
        if (triggerTime < anchor) triggerTime = (outBeat.firstBeatOffsetMs + (k + 1) * phraseMs).toLong()

        val roomMs = trackDuration - 500 - triggerTime
        val effectiveOverlapMs = overlapMs.coerceAtMost(roomMs)
        if (effectiveOverlapMs < 3000L || triggerTime >= trackDuration - 3000) {
            _automixDebugInfo.value = partialDebug.copy(status = "fallback: trigger out of range")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        var tempoRatio = 1f
        if (prefs.getAutomixTempoMatchEnabled()) {
            tempoRatio = outBeat.bpm / inBeat.bpm
            while (tempoRatio > 1.5f) tempoRatio /= 2f
            while (tempoRatio < 0.667f) tempoRatio *= 2f
            if (tempoRatio !in 0.92f..1.08f) tempoRatio = 1f
        }

        var pitchRatio = 1f
        if (prefs.getAutomixHarmonicMixEnabled()) {
            val outKeyClass = outBeat.keyPitchClass
            val inKeyClass = inBeat.keyPitchClass
            if (outKeyClass != null && inKeyClass != null) {
                val outEffective = if (outBeat.keyIsMinor == true) (outKeyClass + 3) % 12 else outKeyClass
                val inEffective = if (inBeat.keyIsMinor == true) (inKeyClass + 3) % 12 else inKeyClass
                var semitoneShift = (outEffective - inEffective) % 12
                if (semitoneShift > 6) semitoneShift -= 12
                if (semitoneShift < -6) semitoneShift += 12
                if (semitoneShift != 0 && abs(semitoneShift) <= 3) {
                    pitchRatio = 2.0.pow(semitoneShift / 12.0).toFloat()
                }
            }
        }

        val inPeriodMs = (60_000f / inBeat.bpm).toDouble()
        val rawStart = if (prefs.getAutomixDynamicMixPointsEnabled()) {
            inBeat.mixInPointMs?.takeIf { it > 0 } ?: inBeat.firstBeatOffsetMs
        } else inBeat.firstBeatOffsetMs
        val inPhraseMs = inPeriodMs * 8
        val inK = ceil((rawStart - inBeat.firstBeatOffsetMs) / inPhraseMs).toLong().coerceAtLeast(0)
        val incomingStart = (inBeat.firstBeatOffsetMs + inK * inPhraseMs).toLong()

        val plan = AutomixPlan(
            currentId = currentId,
            nextId = nextId,
            triggerTimeMs = triggerTime,
            incomingStartMs = incomingStart,
            tempoRatio = tempoRatio,
            pitchRatio = pitchRatio,
            overlapMs = effectiveOverlapMs,
        )

        currentAutomixPlan = plan
        _automixDebugInfo.value = partialDebug.copy(
            status = "plan ready",
            triggerTimeMs = plan.triggerTimeMs,
            incomingStartMs = plan.incomingStartMs,
            tempoRatio = plan.tempoRatio,
            pitchRatio = plan.pitchRatio,
            overlapMs = plan.overlapMs,
        )

        return AutomixPlanResult(plan = plan, pairAnalyzed = true)
    }
}
