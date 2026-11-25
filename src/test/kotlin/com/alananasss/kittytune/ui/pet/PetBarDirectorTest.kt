package com.alananasss.kittytune.ui.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetBarDirectorTest {
    private val button = 0.5f

    /** Drives the director like the overlay does: it walks to every target before the next tick. */
    private class Sim(val d: PetBarDirector = PetBarDirector(Random(1))) {
        var now = 1_000_000L
        var at = PetBarDirector.HOME
        var playing = true
        var lastMusic = now
        val log = mutableListOf<PetActivity>()
        var last: PetBarScene? = null

        fun tick(ms: Long = 250L, input: (PetBarInput) -> PetBarInput = { it }): PetBarScene {
            now += ms
            if (playing) lastMusic = now
            val scene = d.update(
                input(
                    PetBarInput(
                        now = now, playing = playing, hasTrack = true, trackId = 7L,
                        sinceMusicMs = now - lastMusic, button = 0.5f, at = at,
                    )
                )
            )
            at = scene.target // arrives at once
            log += scene.activity
            last = scene
            return scene
        }

        fun run(ms: Long) { repeat((ms / 250L).toInt()) { tick() } }
    }

    @Test
    fun `music stops - it stands and watches, walks to the play button, waits and asks`() {
        val s = Sim()
        s.run(2_000)
        assertTrue(s.log.last().isDance)
        s.playing = false
        s.tick()
        // Not lying down at once: on its feet, watching.
        assertEquals(PetActivity.STAND, s.last!!.activity)
        s.run(3_000)
        assertEquals(PetActivity.STAND, s.last!!.activity)
        s.run(1_000)
        assertEquals(button, s.at, 0.001f)
        assertEquals(PetActivity.WAIT_BUTTON, s.last!!.activity)
        assertNull(s.last!!.hint)
        s.run(6_000)
        assertEquals(PetHint.PLAY, s.last!!.hint)
    }

    @Test
    fun `nothing comes - it goes home and lies down step by step, never in one go`() {
        val s = Sim()
        s.run(1_000)
        s.playing = false
        s.run(46_000)
        assertEquals(PetBarDirector.HOME, s.at, 0.001f)
        val settle = mutableListOf<PetActivity>()
        repeat(120) { settle += s.tick().activity }
        val order = settle.distinct()
        assertEquals(listOf(PetActivity.IDLE, PetActivity.YAWN, PetActivity.LOAF, PetActivity.DOZE, PetActivity.SLEEP), order)
    }

    @Test
    fun `music again - asleep it yawns and stands before dancing, at the button it is delighted`() {
        val s = Sim()
        s.playing = false
        s.run(80_000)
        assertEquals(PetActivity.SLEEP, s.last!!.activity)
        s.playing = true
        assertEquals(PetActivity.YAWN, s.tick().activity)
        s.run(1_500)
        assertTrue(s.last!!.activity.isDance)

        val t = Sim()
        t.playing = false
        t.run(10_000)
        assertEquals(PetActivity.WAIT_BUTTON, t.last!!.activity)
        t.playing = true
        assertEquals(PetActivity.BOOP_DANCE, t.tick().activity)
        t.run(1_500)
        // Then back to its corner on the right, dancing.
        assertTrue(t.at >= PetBarDirector.DEFAULT_ZONE.start)
    }

    @Test
    fun `it reacts to likes, dislikes and skips`() {
        val s = Sim()
        s.run(1_000)
        s.d.onEvent(PetEvent.Like, s.now, 7L)
        assertEquals(PetActivity.LOVE, s.tick().activity)
        s.run(3_000)
        s.d.onEvent(PetEvent.Dislike, s.now, 7L)
        assertEquals(PetActivity.GRUMPY, s.tick().activity)
        s.run(3_000)
        s.d.onEvent(PetEvent.Skip(7L, 5_000L, favourite = false), s.now, 7L)
        assertEquals(PetActivity.SURPRISED, s.tick().activity)
        s.run(2_000)
        assertTrue(s.last!!.activity.isDance)
    }

    @Test
    fun `it suggests going back to a favourite skipped by mistake, once`() {
        val s = Sim()
        s.run(1_000)
        s.d.onEvent(PetEvent.Skip(7L, 4_000L, favourite = true), s.now, 7L)
        s.run(1_500)
        assertEquals(PetHint.PREVIOUS, s.last!!.hint)
        s.d.onHintDone(PetHint.PREVIOUS, 7L)
        assertNull(s.tick().hint)
    }

    @Test
    fun `it suggests repeating a favourite near its end, and skipping a song always skipped`() {
        val s = Sim()
        val end = s.tick { it.copy(favourite = true, durationMs = 200_000L, positionMs = 170_000L) }
        assertEquals(PetHint.REPEAT, end.hint)
        assertEquals(PetActivity.POINT, end.activity)
        assertNull(s.tick { it.copy(favourite = true, durationMs = 200_000L, positionMs = 171_000L, repeatOn = true) }.hint)

        val t = Sim()
        val skip = t.tick { it.copy(skips = 3, plays = 1, positionMs = 8_000L, durationMs = 200_000L) }
        assertEquals(PetHint.NEXT, skip.hint)
        assertEquals(PetActivity.GRUMPY, skip.activity)
    }

    @Test
    fun `while dancing it stays in its corner on the right`() {
        val s = Sim()
        repeat(800) {
            s.tick()
            assertTrue(s.at in PetBarDirector.DEFAULT_ZONE)
        }
    }

    @Test
    fun `the mood follows the song`() {
        assertEquals(MusicMood.SAD, PetMood.classify("Piano", "sad ballad", "Tears", 128f, null))
        assertEquals(MusicMood.DANCE, PetMood.classify("House", null, null, 124f, false))
        assertEquals(MusicMood.CALM, PetMood.classify("Lo-Fi", "chill study", null, 80f, null))
        assertEquals(MusicMood.DANCE, PetMood.classify(null, null, null, 140f, null))
        assertEquals(MusicMood.SAD, PetMood.classify(null, null, null, 72f, true))
        // A word inside another is not that word: "crystal" is not crying, "brain" is not rain.
        assertNotEquals(MusicMood.SAD, PetMood.classify(null, "crystal brainstorm", null, 105f, false))
        assertEquals(PetActivity.SAD_PHONES, PetMood.danceFor(MusicMood.SAD, 1L, false))
        assertEquals(PetActivity.DANCE_SWAY, PetMood.danceFor(MusicMood.CALM, 1L, false))
    }

    @Test
    fun `the app-coloured coat is white with a hint of the theme`() {
        val purple = 0xFF7C5CE0.toInt()
        val p = PetThemeTint.palette(purple)
        fun r(c: Int) = (c shr 16) and 0xFF
        fun b(c: Int) = c and 0xFF
        assertTrue("fur stays nearly white", r(p.fur) > 240 && b(p.fur) > 245)
        assertTrue("marks carry the theme", b(p.mark) > r(p.mark))
        assertEquals(PetColor.AUTO.id, PetSettings().colorId)
        assertEquals(p, PetSettings(themeSeed = purple).palette)
        assertNotEquals(PetSettings(themeSeed = purple), PetSettings(themeSeed = 0xFF2E7D32.toInt()))
    }
}
