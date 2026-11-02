package com.alananasss.kittytune.ui.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BeatInfoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The desktop counterpart of the Android widget's Glance state and its refresh logic
 * (ui/widget/PetWidget.kt): what the cat is fed with, what it rolled to do, what it knows about the
 * user's music and the live tempo it dances to. The pet window and the settings preview read it.
 *
 * - Music playing: headphones on, dancing to the beat (BPM from the live tracker, automix analysis
 *   as a fallback).
 * - Recently fed: it lives its life, rolling an activity for the time of day and its personality,
 *   with the odd random event and, very rarely, a rare moment that goes into the collection.
 * - Hungry for music: sad in a rainy room, then fast asleep with the lights off.
 * - Click it: booped (>ω<)♡ (or whatever the tap action is set to).
 *
 * Android refreshed it on every playback change (piggybacking on the music widget) and every
 * 30 minutes (the widget's update period); here a watcher does both while the app runs.
 */
object PetState {

    class Snapshot(
        val playing: Boolean,
        val lastMusicAt: Long,
        val title: String,
        /** Automix analysis BPM of the current track, 0 when unknown. */
        val bpm: Float,
        val idle: PetActivity,
        val dance: PetActivity,
        val facts: PetFacts,
        val boopUntil: Long,
        /** Tempo heard right now (live beat tracker), 0 when unknown. */
        val liveBpm: Float,
        /** The music calmed down (intro, break, outro): the cat sways gently until the drop. */
        val quiet: Boolean,
        val tick: Long,
    )

    const val BOOP_MS = 2_600L
    private const val MIN_BEAT_CONFIDENCE = 0.3f
    private const val PERIODIC_MS = 30 * 60_000L
    private const val WATCH_EVERY_MS = 500L
    private const val BEAT_EVERY_MS = 400L

    var snapshot by mutableStateOf(
        Snapshot(
            playing = false, lastMusicAt = System.currentTimeMillis(), title = "", bpm = 0f,
            idle = PetActivity.IDLE, dance = PetActivity.DANCE_BOB, facts = PetFacts(), boopUntil = 0L,
            liveBpm = 0f, quiet = false, tick = 0L,
        )
    )
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcher: Job? = null
    private var lastSignature: String? = null
    private var beatJob: Job? = null
    private var beatTrackId: Long? = null
    @Volatile private var offlineBeat: BeatInfoEntity? = null

    /** Starts watching playback (idempotent). Called once the app is up. */
    fun start() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            refresh(force = true, reroll = snapshot.tick == 0L)
            var lastPeriodic = System.currentTimeMillis()
            while (isActive) {
                delay(WATCH_EVERY_MS)
                val now = System.currentTimeMillis()
                if (now - lastPeriodic >= PERIODIC_MS) {
                    lastPeriodic = now
                    refresh(force = true, reroll = true)
                } else {
                    refresh(force = false, reroll = true)
                }
            }
        }
    }

    private fun isPlayingNow(): Boolean = try {
        MusicManager.player.isPlaying || (MusicManager.isLoadingTrack && MusicManager.playWhenReadyRequested)
    } catch (e: Exception) {
        false
    }

    /**
     * Pushes fresh state. [force] = false skips it when nothing about playback changed; [reroll]
     * picks a new idle activity.
     */
    fun refresh(force: Boolean = true, reroll: Boolean = true) {
        scope.launch { refreshNow(force, reroll) }
    }

    @Synchronized
    private fun signatureChanged(signature: String, force: Boolean): Pair<Boolean, Boolean> {
        val wasPlaying = lastSignature?.startsWith("true") == true
        if (!force && signature == lastSignature) return false to wasPlaying
        lastSignature = signature
        return true to wasPlaying
    }

    private suspend fun refreshNow(force: Boolean, reroll: Boolean) {
        val playing = isPlayingNow()
        val track = try { MusicManager.currentTrack } catch (e: Exception) { null }

        val (go, wasPlaying) = signatureChanged("$playing|${track?.id}", force)
        if (!go) return

        val now = System.currentTimeMillis()
        var lastMusicAt = PetConfig.lastMusicAt()
        if (lastMusicAt == 0L || playing || wasPlaying) {
            lastMusicAt = now
            PetConfig.setLastMusicAt(now)
        }

        // the automix beat analysis, only when it is confident enough to be trusted
        val beat = if (playing && track != null) {
            withContext(Dispatchers.IO) {
                runCatching { AppDatabase.beatInfoDao.getBeatInfo(track.id.toString()) }.getOrNull()
            }?.takeIf { it.bpm in 30f..400f && it.confidence >= MIN_BEAT_CONFIDENCE }
        } else null
        val bpm = beat?.bpm ?: 0f

        val settings = PetConfig.load()
        PetNewYear.schedule()
        val dance = PetBrain.danceFor(track?.id)
        val facts = if (settings.exclusives) withContext(Dispatchers.IO) { readFacts(track?.title) } else PetFacts()

        val prev = snapshot
        var rolled: PetActivity? = null
        val idle = if (reroll || prev.tick == 0L) {
            PetBrain.pickIdle(settings, avoid = prev.idle, facts = facts).also { rolled = it }
        } else prev.idle

        snapshot = Snapshot(
            playing = playing,
            lastMusicAt = lastMusicAt,
            title = track?.title.orEmpty(),
            bpm = bpm,
            idle = idle,
            dance = dance,
            facts = facts,
            boopUntil = prev.boopUntil,
            liveBpm = if (playing) prev.liveBpm else 0f,
            quiet = if (playing) prev.quiet else false,
            tick = now,
        )
        offlineBeat = beat
        if (playing && track != null) startBeatSync(track.id) else stopBeatSync()
        // A rare moment only counts when it can actually be seen (fed, not sleeping).
        rolled?.takeIf { it.isRare && !playing }?.let { PetConfig.addRareSeen(it) }
    }

    /** Boop the kitty: a few seconds of (>ω<)♡, then it wanders off to do something else. */
    fun boop() {
        PetConfig.addBoop()
        val until = System.currentTimeMillis() + BOOP_MS
        snapshot = copy(snapshot, boopUntil = until)
        scope.launch {
            delay(BOOP_MS + 100L)
            refresh(force = true, reroll = true)
        }
    }

    private fun copy(s: Snapshot, boopUntil: Long = s.boopUntil, liveBpm: Float = s.liveBpm, quiet: Boolean = s.quiet) = Snapshot(
        s.playing, s.lastMusicAt, s.title, s.bpm, s.idle, s.dance, s.facts, boopUntil, liveBpm, quiet, System.currentTimeMillis(),
    )

    private fun stopBeatSync() {
        beatJob?.cancel(); beatJob = null; beatTrackId = null
    }

    /**
     * Follows the tempo and the energy actually heard while music plays. The dance steps
     * themselves are timed by the sprite on each heard beat ([nextHeardBeat]); this only keeps the
     * BPM shown and the "quiet" sway (intro, break, outro) up to date. On Android this loop also
     * had to restart the launcher's ViewFlipper on a beat; the desktop window draws itself.
     */
    private fun startBeatSync(trackId: Long) {
        if (beatJob?.isActive == true && beatTrackId == trackId) return
        stopBeatSync()
        beatTrackId = trackId
        beatJob = scope.launch {
            while (isActive) {
                val stillPlaying = runCatching { MusicManager.player.isPlaying && MusicManager.currentTrack?.id == trackId }.getOrDefault(false)
                if (!stillPlaying) break
                val snap = runCatching { MusicManager.liveBeat.snapshot }.getOrNull()
                val live = snap?.takeIf { it.confident && System.currentTimeMillis() - it.madeAt < 3_000 }
                val bpm = live?.bpm ?: 0f
                val quiet = snap?.quiet == true
                val cur = snapshot
                if (kotlin.math.abs(cur.liveBpm - bpm) > 0.5f || cur.quiet != quiet) {
                    snapshot = copy(cur, liveBpm = bpm, quiet = quiet)
                }
                delay(BEAT_EVERY_MS)
            }
        }
    }

    /**
     * (ms until the next beat the listener will hear, beat index) while music plays: from the live
     * tracker, or the automix analysis for the first seconds of a track. Null otherwise.
     */
    fun nextHeardBeat(minAheadMs: Long = 30): Pair<Long, Long>? = runCatching {
        val player = MusicManager.player
        if (!player.isPlaying) return null
        val pos = player.audiblePosition
        MusicManager.liveBeat.nextBeat(pos, minAheadMs)?.let { return it }
        val offline = offlineBeat?.takeIf { beatTrackId != null && MusicManager.currentTrack?.id == beatTrackId } ?: return null
        val periodMs = 60_000.0 / offline.bpm
        val anchor = offline.firstBeatOffsetMs.toDouble()
        val target = pos + minAheadMs.toDouble()
        val k = kotlin.math.ceil((target - anchor) / periodMs)
        (anchor + k * periodMs - pos).toLong() to k.toLong()
    }.getOrNull()

    /** Likes, a random liked song, this month's top artist and the last song played. */
    private suspend fun readFacts(currentTitle: String?): PetFacts = try {
        val likes = if (LikeRepository.isReady) LikeRepository.likedTracks.value else null
        val topArtist = runCatching {
            ListeningStatsRepository.init()
            ListeningStatsRepository.getTopArtists(System.currentTimeMillis() - 30L * 24 * 3_600_000, 1)
                .firstOrNull()?.artistName
        }.getOrNull()
        val last = currentTitle ?: PetConfig.lastTitle()
        if (!currentTitle.isNullOrBlank()) PetConfig.setLastTitle(currentTitle)
        PetFacts(
            known = likes != null,
            likes = likes?.size ?: 0,
            likedTitle = likes?.randomOrNull()?.title,
            topArtist = topArtist?.takeIf { it.isNotBlank() },
            lastTitle = last?.takeIf { it.isNotBlank() },
        )
    } catch (e: Exception) {
        PetFacts()
    }
}
