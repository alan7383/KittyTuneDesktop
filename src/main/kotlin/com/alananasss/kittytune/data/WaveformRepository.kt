package com.alananasss.kittytune.data

import com.alananasss.kittytune.audio.automix.FullTrackWaveformDecoder
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Full-track waveforms for the transition editor (port of Android PR #83).
 *
 * Lookup chain, cheapest first: the decoded memory cache, the disk cache (`cacheDir/waveforms/wfdec_<id>.bin`,
 * header-checked, partial waves never stored), SoundCloud's own waveform JSON (real peaks, coarse), then a
 * full decode with [FullTrackWaveformDecoder] for YouTube and everything else, up to three attempts with a
 * freshly resolved stream. Concurrent callers for one track share a single decode.
 */
object WaveformRepository {

    private const val TAG = "WaveformRepository"

    private val decodedCache = BoundedCache<Long, FullTrackWaveformDecoder.DecodedWaveform>(12)
    private val inFlightDecodes = ConcurrentHashMap<Long, Boolean>()

    /** Disk format version of `wfdec_*.bin`; a file of another version is ignored. */
    private const val DECODED_CACHE_VERSION = 2

    private val httpClient get() = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()

    /** The track's waveform at full editor resolution, or null when none could be had. */
    suspend fun getDecodedWaveform(
        track: Track,
        onProgress: ((Float) -> Unit)? = null,
    ): FullTrackWaveformDecoder.DecodedWaveform? {
        val trackId = track.id
        if (trackId <= 0L) return null
        decodedCache[trackId]?.let { return it }

        val diskFile = decodedCacheFile(trackId)
        if (diskFile.exists() && diskFile.length() > 0) {
            val decoded = withContext(Dispatchers.IO) { runCatching { readDecoded(diskFile) }.getOrNull() }
            if (decoded != null && decoded.peaks.isNotEmpty()) {
                decodedCache[trackId] = decoded
                return decoded
            }
        }

        // SoundCloud's waveform JSON: real peaks, just coarse. Good enough for the editor.
        val cloud = runCatching { soundCloudSamples(track) }.getOrNull()
        val durationMs = track.durationMs ?: 0L
        if (cloud != null && cloud.isNotEmpty() && durationMs > 0L) {
            val decoded = FullTrackWaveformDecoder.DecodedWaveform(
                peaks = cloud,
                durationMs = durationMs,
                peaksPerSecond = ((cloud.size * 1000f) / durationMs).toInt().coerceAtLeast(1),
                complete = true,
            )
            decodedCache[trackId] = decoded
            return decoded
        }

        return decodeAndCache(track, onProgress)
    }

    private suspend fun decodeAndCache(
        track: Track,
        onProgress: ((Float) -> Unit)?,
    ): FullTrackWaveformDecoder.DecodedWaveform? = withContext(Dispatchers.IO) {
        if (inFlightDecodes.putIfAbsent(track.id, true) != null) {
            // Another caller is already decoding this track: wait for its result.
            val startedAt = System.currentTimeMillis()
            while (currentCoroutineContext().isActive) {
                decodedCache[track.id]?.let { return@withContext it }
                if (System.currentTimeMillis() - startedAt > 100_000L || !inFlightDecodes.containsKey(track.id)) break
                delay(250L)
            }
            return@withContext decodedCache[track.id]
        }
        try {
            var best: FullTrackWaveformDecoder.DecodedWaveform? = null
            var attempts = 0
            // Up to three passes, each with a freshly resolved stream: the URL can expire between resolve and
            // decode, and a read can stall mid-song.
            while (attempts < 3 && currentCoroutineContext().isActive) {
                attempts++
                if (attempts > 1) delay(1_500L)
                val local = runCatching { com.alananasss.kittytune.data.local.AppDatabase.downloadDao.getTrack(track.id) }
                    .getOrNull()?.localAudioPath?.takeIf { it.isNotBlank() && File(it).exists() }
                val source = local ?: StreamResolver.resolveStream(track)?.let { raw ->
                    if (raw.startsWith("metrofuse-deezer://")) {
                        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(raw)
                    } else raw
                }
                if (source.isNullOrBlank()) {
                    Logger.w(TAG, "No stream to decode a waveform from for track ${track.id} (attempt $attempts)")
                    continue
                }
                val decoded = FullTrackWaveformDecoder.decodePeaks(
                    source = source,
                    headers = if (local != null) emptyMap() else MusicManager.player.buildHeaders(track),
                    durationHintMs = track.durationMs ?: 0L,
                    shouldCancel = { false },
                    onProgress = onProgress,
                ) ?: continue
                if (decoded.complete) { best = decoded; break }
                // A partial pass is still worth showing if nothing better arrives.
                if (best == null || decoded.durationMs > best.durationMs) best = decoded
            }
            // Only a complete pass may become the track's waveform.
            if (best != null && best.complete) {
                decodedCache[track.id] = best
                runCatching { saveDecoded(decodedCacheFile(track.id), best) }
            }
            best
        } catch (e: Exception) {
            Logger.w(TAG, "Waveform decode failed for track ${track.id}: ${e.message}")
            null
        } finally {
            inFlightDecodes.remove(track.id)
        }
    }

    /** SoundCloud's own waveform for the track, looked up by id when the track came without its url. */
    private suspend fun soundCloudSamples(track: Track): FloatArray? = withContext(Dispatchers.IO) {
        var url = track.waveformUrl
        if (url.isNullOrBlank() && (track.source == null || track.source == "soundcloud")) {
            url = runCatching { RetrofitClient.create().getTracksByIds(track.id.toString()).firstOrNull()?.waveformUrl }.getOrNull()
        }
        if (url.isNullOrBlank()) return@withContext null
        val body = httpClient.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
            ?: return@withContext null
        parseSoundCloudWaveform(body)
    }

    /** Peaks from SoundCloud's waveform JSON, shaped by pow(1.5) like the Android repository. */
    internal fun parseSoundCloudWaveform(json: String): FloatArray? {
        val obj = JSONObject(json)
        val height = obj.optDouble("height", 140.0)
        val arr = obj.optJSONArray("samples") ?: return null
        if (arr.length() == 0) return null
        return FloatArray(arr.length()) { i ->
            (arr.getDouble(i) / height).coerceIn(0.0, 1.0).pow(1.5).toFloat().coerceIn(0.02f, 1f)
        }
    }

    private fun decodedCacheFile(trackId: Long): File =
        File(File(AppDirs.cacheDir, "waveforms").apply { mkdirs() }, "wfdec_$trackId.bin")

    internal fun saveDecoded(file: File, decoded: FullTrackWaveformDecoder.DecodedWaveform) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(DECODED_CACHE_VERSION)
            out.writeLong(decoded.durationMs)
            out.writeInt(decoded.peaksPerSecond)
            out.writeInt(decoded.peaks.size)
            decoded.peaks.forEach { out.writeFloat(it) }
        }
    }

    internal fun readDecoded(file: File): FullTrackWaveformDecoder.DecodedWaveform? =
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != DECODED_CACHE_VERSION) return null
            val durationMs = input.readLong()
            val pps = input.readInt()
            val count = input.readInt()
            if (count <= 0 || count > FullTrackWaveformDecoder.MAX_DURATION_MS / 1000 * 200) return null
            val peaks = FloatArray(count) { input.readFloat() }
            FullTrackWaveformDecoder.DecodedWaveform(peaks, durationMs, pps, complete = true)
        }
}
