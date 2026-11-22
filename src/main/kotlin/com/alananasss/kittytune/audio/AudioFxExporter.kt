package com.alananasss.kittytune.audio

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.TrackLoudnessRepository
import com.alananasss.kittytune.data.YoutubeAudioResolver
import com.alananasss.kittytune.data.drm.ProtectedTracksNotice
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.ShortBuffer

/**
 * Saves the track that is playing as a file, with the sound the listener hears: every effect, the
 * equalizer, the speed and the pitch, rendered offline through the same chain as playback.
 *
 * One export at a time, owned by the app rather than by a dialog: closing the dialog leaves it running,
 * and the effects panel shows its progress.
 */
object AudioFxExporter {

    enum class Format(val extension: String, val ffmpegFormat: String, val codec: Int, val bitrate: Int) {
        MP3("mp3", "mp3", avcodec.AV_CODEC_ID_MP3, 320_000),
        FLAC("flac", "flac", avcodec.AV_CODEC_ID_FLAC, 0),
        WAV("wav", "wav", avcodec.AV_CODEC_ID_PCM_S16LE, 0);

        /** Rough output size per second of audio, for the estimate shown before exporting. */
        val bytesPerSecond: Long
            get() = when (this) {
                MP3 -> 320_000L / 8
                FLAC -> 44_100L * 4 * 6 / 10
                WAV -> 44_100L * 4
            }
    }

    enum class Failure { PROTECTED, UNAVAILABLE, WRITE, OTHER }

    sealed interface State {
        data object Idle : State
        data class Running(val title: String, val format: Format, val progress: Float) : State
        data class Done(val file: File) : State
        data class Failed(val failure: Failure, val detail: String?) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while the export dialog is on screen; a finished export then needs no toast. */
    @Volatile var dialogOpen = false

    @Volatile private var job: Job? = null
    @Volatile private var cancelled = false

    private const val KEY_FORMAT = "fx_export_format"
    private const val KEY_DIR = "fx_export_dir"

    var lastFormat: Format
        get() = Prefs.getString(KEY_FORMAT, null)?.let { name -> Format.entries.find { it.name == name } } ?: Format.MP3
        set(value) = Prefs.putString(KEY_FORMAT, value.name)

    /** The folder exports go to: the one chosen last, else an "Effects" folder beside the downloads. */
    var directory: File
        get() = Prefs.getString(KEY_DIR, null)?.let(::File)
            ?: File(PlayerPreferences().getDownloadLocation()?.let(::File) ?: AppDirs.defaultDownloadDir, "Effects")
        set(value) = Prefs.putString(KEY_DIR, value.absolutePath)

    /** "Artist - Title (FX)", stripped of what a file name cannot hold. */
    fun suggestedName(track: Track): String {
        val base = listOf(track.displayArtist, track.title.orEmpty()).filter { it.isNotBlank() }.joinToString(" - ")
        return sanitize("${base.ifBlank { "KittyTune" }} (FX)")
    }

    internal fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(150).ifBlank { "KittyTune (FX)" }

    /** A file in [dir] called [name] with [format]'s extension that does not overwrite anything. */
    internal fun uniqueFile(dir: File, name: String, format: Format): File {
        var candidate = File(dir, "$name.${format.extension}")
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$name ($n).${format.extension}").also { n++ }
        return candidate
    }

    fun dismiss() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    fun export(
        track: Track,
        effects: AudioEffectsState,
        equalizer: EqualizerState,
        format: Format,
        directory: File,
        fileName: String,
    ) {
        if (_state.value is State.Running) return
        lastFormat = format
        this.directory = directory
        cancelled = false
        _state.value = State.Running(track.title.orEmpty(), format, 0f)
        job = scope.launch {
            var target: File? = null
            try {
                val source = resolveSource(track)
                if (source == null) {
                    _state.value = State.Failed(Failure.UNAVAILABLE, null)
                    return@launch
                }
                if (source.protected) {
                    _state.value = State.Failed(Failure.PROTECTED, null)
                    return@launch
                }
                if (!directory.isDirectory && !directory.mkdirs()) {
                    _state.value = State.Failed(Failure.WRITE, directory.absolutePath)
                    return@launch
                }
                val file = uniqueFile(directory, sanitize(fileName), format)
                val partial = File(directory, ".${file.name}.part")
                target = partial
                render(track, source, effects, equalizer, format, partial) { progress ->
                    _state.value = State.Running(track.title.orEmpty(), format, progress)
                }
                if (!partial.renameTo(file)) {
                    partial.copyTo(file, overwrite = false)
                    partial.delete()
                }
                target = null
                AudioFxExportTags.write(file, track, effects)
                _state.value = State.Done(file)
            } catch (e: CancellationException) {
                _state.value = State.Idle
            } catch (e: Throwable) {
                Logger.e("AudioFxExporter", "export failed: ${e.message}", e)
                _state.value = if (cancelled) State.Idle else State.Failed(
                    if (e is java.io.IOException && target != null && !target.parentFile.canWrite()) Failure.WRITE else Failure.OTHER,
                    e.message,
                )
            } finally {
                target?.delete()
            }
        }
    }

    private class Source(val url: String, val headers: Map<String, String>, val protected: Boolean)

    /**
     * What the player would read for [track]. Protected (DRM) tracks are refused: their licence allows
     * playback, not a copy, and the decrypted cache must not leave the app as a file.
     */
    private suspend fun resolveSource(track: Track): Source? {
        val protectedOnly = ProtectedTracksNotice.onlyProtected(track.media?.transcodings.orEmpty())
        val resolved = StreamResolver.resolveStreamWithDrm(track) ?: return null
        var url = resolved.url
        if (url.startsWith("metrofuse-deezer://")) {
            url = com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(url)
        }
        val decryptedCache = File(url).name.startsWith("clear_")
        val headers = YoutubeAudioResolver.requestHeaders(url).ifEmpty {
            com.alananasss.kittytune.data.MusicManager.player.buildHeaders(track)
        }
        val local = !url.startsWith("http")
        return Source(
            url = url,
            headers = if (local) emptyMap() else headers,
            protected = resolved.isDrmProtected || decryptedCache || (protectedOnly && resolved.source == "soundcloud"),
        )
    }

    private suspend fun render(
        track: Track,
        source: Source,
        effects: AudioEffectsState,
        equalizer: EqualizerState,
        format: Format,
        output: File,
        onProgress: (Float) -> Unit,
    ) {
        val engine = AudioEngine(isolated = true)
        try {
            engine.applyEffects(effects)
            engine.applyEqualizer(equalizer)
            val loudness = runCatching { TrackLoudnessRepository.getLoudness(track.id) }.getOrNull()
            if (loudness != null) engine.setTrackLoudness(loudness.integratedLufs, loudness.truePeakDb)
            val recorder = FFmpegFrameRecorder(output, 2).apply {
                this.format = format.ffmpegFormat
                audioCodec = format.codec
                sampleRate = engine.outputRateHz
                if (format.bitrate > 0) audioBitrate = format.bitrate
                setMetadata(
                    mapOf(
                        "title" to track.title.orEmpty(),
                        "artist" to track.displayArtist,
                        "comment" to "KittyTune FX",
                    ),
                )
            }
            recorder.start()
            try {
                engine.renderOffline(
                    url = source.url,
                    headers = source.headers,
                    trackId = track.id,
                    sink = { samples, count -> recorder.recordSamples(engine.outputRateHz, 2, ShortBuffer.wrap(samples, 0, count)) },
                    onProgress = onProgress,
                    isCancelled = { cancelled },
                )
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        } finally {
            engine.release()
        }
    }
}
