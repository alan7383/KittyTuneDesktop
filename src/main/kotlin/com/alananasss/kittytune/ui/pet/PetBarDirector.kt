package com.alananasss.kittytune.ui.pet

import kotlin.math.abs
import kotlin.random.Random

/** A suggestion the cat makes with a bubble over its head; clicking it does the thing. */
enum class PetHint { PLAY, REPEAT, NEXT, PREVIOUS }

/** How fast it goes to where it wants to be. */
enum class PetPace { WALK, RUN, STROLL }

/** What the cat on the player bar knows at one moment. Positions are shares of the bar's room (0 left, 1 right). */
internal data class PetBarInput(
    val now: Long,
    val playing: Boolean,
    val hasTrack: Boolean,
    val trackId: Long?,
    /** Liked, or played through often: a song it knows the listener loves. */
    val favourite: Boolean = false,
    val plays: Int = 0,
    val skips: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatOn: Boolean = false,
    val mood: MusicMood = MusicMood.GROOVE,
    val quiet: Boolean = false,
    /** Since music last played, as remembered across launches. */
    val sinceMusicMs: Long = 0L,
    /** The daily streak waits for a restore: it mopes instead of sleeping peacefully. */
    val restorable: Boolean = false,
    /** Its corner of the bar, and the spot beside the play button (null when the button is not laid out). */
    val zone: ClosedFloatingPointRange<Float> = PetBarDirector.DEFAULT_ZONE,
    val button: Float? = null,
    /** Where it is now. */
    val at: Float = PetBarDirector.HOME,
)

internal data class PetBarScene(
    val activity: PetActivity,
    val target: Float,
    val pace: PetPace = PetPace.WALK,
    val hint: PetHint? = null,
)

/**
 * The cat's behaviour on the player bar, as a pure state machine: the bar overlay feeds it what is going on every
 * few hundred milliseconds and draws what it answers. It lives in its own corner on the right of the bar (the left
 * is the cover and the song's name), and moves in steps a real cat would take:
 *
 * - **Music**: it dances in its corner, the way the song feels (sad songs in its headphones, calm ones swaying,
 *   dance tracks bouncing), stepping somewhere else in the corner now and then.
 * - **Music stops**: it does not drop. It stands up and watches for a few seconds, then — if there is a song to
 *   come back to — walks to the play button and sits by it, looking at it, hoping, and asks with a bubble.
 * - **Nothing comes**: after a while it walks back to its corner, sits, yawns, lies down, dozes, falls asleep.
 * - **Music again**: woken, it yawns and gets up before it dances; waiting by the button, it is delighted.
 * - **What the listener does**: hearts for a like, a frown for a dislike, a start at a skip, a look back for
 *   "previous". It also suggests: repeat a favourite that is ending, skip a song that is always skipped, go back to
 *   a favourite skipped by mistake.
 */
internal class PetBarDirector(private val random: Random = Random.Default) {

    private enum class Phase { DANCE, AWAY }

    private var phase = Phase.AWAY
    private var wasPlaying: Boolean? = null
    private var stoppedAt = 0L
    private var arrivedAt = 0L
    private var arrivedFor = Float.NaN
    private var wakeUntil = 0L
    private var reaction: Pair<PetActivity, Long>? = null
    private var roamTarget = HOME
    private var nextRoamAt = 0L
    private var previousHintUntil = 0L
    private var previousHintTrack: Long? = null
    private var hintFirstShownAt = 0L
    private var lastHint: PetHint? = null
    private var lastHintTrack: Long? = null
    private val hintsDone = HashSet<Pair<Long?, PetHint>>()

    /** The listener did something. */
    fun onEvent(event: PetEvent, now: Long, currentTrack: Long?) {
        when (event) {
            PetEvent.Like -> react(PetActivity.LOVE, now, 2_400)
            PetEvent.Unlike -> react(PetActivity.SAD, now, 1_800)
            PetEvent.Dislike -> react(PetActivity.GRUMPY, now, 2_600)
            PetEvent.Previous -> {
                react(PetActivity.LOOK_BACK, now, 1_400)
                previousHintUntil = 0L
            }
            is PetEvent.Repeat -> {
                if (event.on) react(PetActivity.PURR, now, 1_800)
                hintsDone += currentTrack to PetHint.REPEAT
            }
            is PetEvent.Skip -> {
                react(PetActivity.SURPRISED, now, 1_300)
                // A favourite skipped in its first seconds: probably a slip of the finger.
                if (event.favourite && event.playedMs < 30_000L) {
                    previousHintUntil = now + PREVIOUS_HINT_MS
                    previousHintTrack = null
                }
            }
        }
    }

    /** The listener used (or waved away) a hint: not again for this song. */
    fun onHintDone(hint: PetHint, trackId: Long?) {
        hintsDone += trackId to hint
        if (hint == PetHint.PREVIOUS) previousHintUntil = 0L
    }

    private fun react(activity: PetActivity, now: Long, ms: Long) {
        reaction = activity to now + ms
    }

    fun update(i: PetBarInput): PetBarScene {
        val now = i.now
        val started = wasPlaying == false && i.playing
        val stopped = wasPlaying == true && !i.playing
        if (wasPlaying == null && !i.playing) {
            // A fresh start: pick up from how long the music has been off, asleep if it has been a while.
            stoppedAt = now - i.sinceMusicMs
            if (i.sinceMusicMs >= SETTLE_AFTER_MS) {
                arrivedFor = i.zone.clamp(i.at)
                arrivedAt = now - 60 * 60_000L
            }
        }
        if (wasPlaying == null && i.playing) {
            // Music already on when the cat appears: it is simply dancing, nothing to wake up from.
            phase = Phase.DANCE
            roamTarget = i.zone.clamp(if (i.at in i.zone) i.at else HOME)
            nextRoamAt = now + random.nextLong(15_000L, 35_000L)
        }
        wasPlaying = i.playing

        return if (i.playing) {
            if (started || phase != Phase.DANCE) {
                val sleeping = phase == Phase.AWAY && now - stoppedAt >= SETTLE_AFTER_MS
                val waitingAtButton = phase == Phase.AWAY && i.button != null && abs(i.at - i.button) < NEAR
                when {
                    sleeping -> wakeUntil = now + WAKE_MS
                    waitingAtButton -> react(PetActivity.BOOP_DANCE, now, 1_200)
                }
                phase = Phase.DANCE
                roamTarget = i.zone.clamp(if (i.at in i.zone) i.at else HOME)
                nextRoamAt = now + random.nextLong(15_000L, 35_000L)
            }
            val reacting = reaction?.takeIf { now < it.second }?.first
            // Now and then, a few steps elsewhere in its corner.
            if (now >= nextRoamAt && reacting == null) {
                roamTarget = i.zone.clamp(roamTarget + (random.nextFloat() - 0.5f) * 0.16f)
                nextRoamAt = now + random.nextLong(18_000L, 40_000L)
            }
            val target = if (reacting != null) i.at else roamTarget
            val hint = playingHint(i)
            val activity = when {
                reacting != null -> reacting
                now < wakeUntil -> if (wakeUntil - now > WAKE_STAND_MS) PetActivity.YAWN else PetActivity.STAND
                hint == PetHint.NEXT -> PetActivity.GRUMPY
                (hint == PetHint.REPEAT || hint == PetHint.PREVIOUS) && now - hintFirstShownAt < POINT_MS -> PetActivity.POINT
                else -> PetMood.danceFor(i.mood, i.trackId, i.quiet)
            }
            val pace = if (abs(i.at - target) > 0.2f) PetPace.RUN else PetPace.WALK
            PetBarScene(activity, if (now < wakeUntil) i.at else target, pace, hint)
        } else {
            if (stopped || phase == Phase.DANCE) {
                stoppedAt = now
                phase = Phase.AWAY
                arrivedFor = Float.NaN
                lastHint = null
            }
            awayScene(i, reaction?.takeIf { now < it.second }?.first)
        }
    }

    private fun awayScene(i: PetBarInput, reacting: PetActivity?): PetBarScene {
        val now = i.now
        val t = now - stoppedAt
        fun arrive(target: Float): Long {
            if (abs(i.at - target) >= NEAR) {
                arrivedFor = Float.NaN
                return -1L
            }
            if (arrivedFor.isNaN() || abs(arrivedFor - target) > NEAR) {
                arrivedFor = target
                arrivedAt = now
            }
            return now - arrivedAt
        }
        if (reacting != null) return PetBarScene(reacting, i.at)

        // First: on its feet, watching where the music went.
        if (t < WATCH_MS) return PetBarScene(PetActivity.STAND, i.at)

        // Then by the play button, waiting for it.
        val button = i.button
        if (t < SETTLE_AFTER_MS && i.hasTrack && button != null) {
            val waited = arrive(button)
            if (waited < 0) return PetBarScene(PetActivity.STAND, button, PetPace.WALK)
            val hint = if (waited >= ASK_AFTER_MS && (null to PetHint.PLAY) !in hintsDone) PetHint.PLAY else null
            return PetBarScene(PetActivity.WAIT_BUTTON, button, hint = hint)
        }

        // Nothing to wait for yet: it pads about its corner, curious.
        if (t < SETTLE_AFTER_MS) {
            if (now >= nextRoamAt) {
                roamTarget = i.zone.clamp(i.zone.start + random.nextFloat() * (i.zone.endInclusive - i.zone.start))
                nextRoamAt = now + random.nextLong(6_000L, 12_000L)
            }
            return PetBarScene(if ((t / 4_000L) % 3L == 2L) PetActivity.STAND_CURIOUS else PetActivity.STAND, roamTarget, PetPace.STROLL)
        }

        // At last: home, and to bed, one step after the other.
        val home = i.zone.clamp(HOME)
        val settled = arrive(home)
        if (settled < 0) return PetBarScene(PetActivity.STAND, home, PetPace.STROLL)
        hintsDone.remove(null to PetHint.PLAY)
        val activity = when {
            settled < 2_500L -> PetActivity.IDLE
            settled < 5_500L -> PetActivity.YAWN
            settled < 14_000L -> PetActivity.LOAF
            settled < 24_000L -> PetActivity.DOZE
            i.restorable -> PetActivity.SAD_LOAF
            else -> PetActivity.SLEEP
        }
        return PetBarScene(activity, home)
    }

    private fun playingHint(i: PetBarInput): PetHint? {
        val now = i.now
        fun done(h: PetHint) = (i.trackId to h) in hintsDone
        val hint = when {
            now < previousHintUntil && !done(PetHint.PREVIOUS) -> PetHint.PREVIOUS
            i.skips >= 2 && i.skips >= i.plays && i.positionMs in 3_000L..40_000L && !done(PetHint.NEXT) -> PetHint.NEXT
            !i.repeatOn && i.favourite && i.durationMs > 60_000L && i.positionMs >= i.durationMs * 0.8 &&
                i.positionMs < i.durationMs - 1_500L && !done(PetHint.REPEAT) -> PetHint.REPEAT
            else -> null
        }
        if (hint != lastHint || (hint != null && i.trackId != lastHintTrack)) {
            // A hint that went away on its own (the moment passed) is not shown again for that song.
            lastHint?.let { hintsDone += lastHintTrack to it }
            lastHint = hint
            lastHintTrack = i.trackId
            hintFirstShownAt = now
        }
        return hint
    }

    companion object {
        const val HOME = 0.88f
        val DEFAULT_ZONE = 0.72f..0.98f
        /** Close enough to count as there, as a share of the bar. */
        const val NEAR = 0.012f
        const val WATCH_MS = 3_500L
        const val ASK_AFTER_MS = 5_000L
        const val SETTLE_AFTER_MS = 45_000L
        const val WAKE_MS = 1_300L
        const val WAKE_STAND_MS = 450L
        const val POINT_MS = 3_500L
        const val PREVIOUS_HINT_MS = 9_000L

        private fun ClosedFloatingPointRange<Float>.clamp(v: Float) = v.coerceIn(start, endInclusive)
    }
}
