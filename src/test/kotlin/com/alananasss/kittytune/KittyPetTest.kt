package com.alananasss.kittytune

import com.alananasss.kittytune.audio.beat.LiveBeatTracker
import com.alananasss.kittytune.ui.pet.PetActivity
import com.alananasss.kittytune.ui.pet.PetBrain
import com.alananasss.kittytune.ui.pet.PetCoat
import com.alananasss.kittytune.ui.pet.PetComposer
import com.alananasss.kittytune.ui.pet.PetCoatRenderer
import com.alananasss.kittytune.ui.pet.PetFrames
import com.alananasss.kittytune.ui.pet.PetHat
import com.alananasss.kittytune.ui.pet.PetIvy
import com.alananasss.kittytune.ui.pet.PetLayers
import com.alananasss.kittytune.ui.pet.PetNeck
import com.alananasss.kittytune.ui.pet.PetNewYear
import com.alananasss.kittytune.ui.pet.PetPattern
import com.alananasss.kittytune.ui.pet.PetPhotoAnalyzer
import com.alananasss.kittytune.ui.pet.PetSettings
import com.alananasss.kittytune.ui.pet.PetSprites
import com.alananasss.kittytune.ui.pet.jpegOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin

/** The desktop port of the Android Kitty pet: assets, rules and the live beat tracker. */
class KittyPetTest {

    @Test
    fun everyFrameOfEveryActivityExistsWithAllItsLayers() {
        val now = System.currentTimeMillis()
        val names = PetActivity.entries.flatMap { PetBrain.framesFor(it, now) + it.frames }.toSet() +
            (0..10).map { "count_$it" } + (2027..2030).flatMap { y -> (1..3).map { "ny_${y}_$it" } }
        for (name in names) {
            val frame = PetFrames.frames[name]
            assertNotNull("missing frame $name", frame)
            frame!!
            for (layer in listOf(frame.fur, frame.shade, frame.eye, frame.ink) + frame.marks + frame.whites) {
                if (layer != null) assertNotNull("frame $name: missing sprite $layer", PetSprites[layer])
            }
        }
    }

    @Test
    fun roomsDecorAccessoriesAndIconsAreInTheAtlas() {
        (PetFrames.rooms.values + PetFrames.decor.values).forEach { assertNotNull(it, PetSprites[it]) }
        (PetFrames.hats.values + PetFrames.necks.values).flatMap { it.toList() }.filterNotNull().forEach {
            assertNotNull(it, PetSprites[it])
        }
        listOf("widget_pet_heart_full", "widget_pet_heart_empty", "widget_pet_sparkle").forEach { assertNotNull(it, PetSprites[it]) }
        val room = PetSprites["room_day"]!!
        assertEquals(46, room.width)
        assertEquals(32, room.height)
    }

    @Test
    fun spritesDecodeToTheirPixels() {
        // idle_open_f: white fur silhouette; its first rect is "M15,8h2v1h-2z"
        val fur = PetSprites["idle_open_f"]!!
        assertEquals(-1, fur.pixels[8 * 46 + 15])
        assertEquals(-1, fur.pixels[8 * 46 + 16])
        assertEquals(0, fur.pixels[8 * 46 + 14])
    }

    @Test
    fun layersTintTheCoatAndStackAccessories() {
        val s = PetSettings(hat = PetHat.CROWN, neck = PetNeck.BELL)
        val layers = PetLayers.of("idle_open", s)
        assertEquals(PetLayers.COUNT, layers.size)
        assertEquals(s.palette.fur, layers[0].tint)
        assertEquals(s.eyeColor, layers[4].tint)
        assertNotNull(layers[6].sprite) // crown
        assertNotNull(layers[7].sprite) // bell
        val cat = PetComposer.cat("idle_open", s)
        // a fur pixel takes the cream coat colour
        assertEquals(s.palette.fur or 0xFF000000.toInt(), cat[20 * 46 + 20] or 0xFF000000.toInt())
    }

    @Test
    fun customCoatMasksFollowThePartMap() {
        val ivy = PetIvy.apply(PetSettings())
        assertEquals(PetPattern.CUSTOM, ivy.pattern)
        val (mark, white) = PetCoatRenderer.layers("idle_open", ivy.coat!!)
        assertNotNull(mark)
        assertNotNull(white)
        assertTrue(white!!.count { it != 0 } > 20)
        val round = PetCoat.parse(ivy.coat!!.serialize())
        assertEquals(ivy.coat, round)
    }

    @Test
    fun easterAndHolidays() {
        val e = PetBrain.easterSunday(2026)
        assertEquals(Calendar.APRIL, e.get(Calendar.MONTH))
        assertEquals(5, e.get(Calendar.DAY_OF_MONTH))
        val e2 = PetBrain.easterSunday(2027)
        assertEquals(Calendar.MARCH, e2.get(Calendar.MONTH))
        assertEquals(28, e2.get(Calendar.DAY_OF_MONTH))
        val halloween = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 31) }
        assertEquals("pumpkin", PetBrain.decorFor(PetSettings(), halloween))
        val christmas = Calendar.getInstance().apply { clear(); set(2026, Calendar.DECEMBER, 25) }
        assertEquals("tree", PetBrain.decorFor(PetSettings(), christmas))
        assertEquals(PetHat.SANTA.id, PetBrain.seasonalHat(PetSettings(), christmas))
        assertEquals("snow", PetBrain.decorFor(PetSettings(holidays = false), christmas))
    }

    @Test
    fun newYearCountdown() {
        val eve = Calendar.getInstance().apply { clear(); set(2026, Calendar.DECEMBER, 31, 23, 59, 55) }.timeInMillis
        assertEquals(PetNewYear.Phase.COUNTDOWN, PetNewYear.phase(eve))
        assertEquals(2027, PetNewYear.celebratedYear(eve))
        val frames = PetNewYear.countdownFrames(eve)
        assertEquals("count_5", frames.first())
        assertEquals(8, frames.size)
        val day = Calendar.getInstance().apply { clear(); set(2027, Calendar.JANUARY, 1, 0, 3, 0) }.timeInMillis
        assertEquals(PetNewYear.Phase.FIREWORKS, PetNewYear.phase(day))
    }

    @Test
    fun heartsAndMoods() {
        val s = PetSettings(sadHours = 2f, sleepHours = 8f)
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        assertEquals(5, PetBrain.hearts(true, now - 20 * hour, now, s))
        assertEquals(0, PetBrain.hearts(false, now - 9 * hour, now, s))
        val (asleep, room) = PetBrain.resolve(false, now - 9 * hour, 0L, now, PetActivity.PLAY, PetActivity.DANCE_BOB, s)
        if (PetNewYear.phase(now) == PetNewYear.Phase.NONE) {
            assertEquals(PetActivity.SLEEP, asleep)
            assertEquals("sleep", room)
            val (sad, rain) = PetBrain.resolve(false, now - 3 * hour, 0L, now, PetActivity.PLAY, PetActivity.DANCE_BOB, s)
            assertTrue(sad in PetActivity.SAD_MOODS)
            assertEquals("rain", rain)
            assertEquals(PetActivity.DANCE_DJ, PetBrain.resolve(true, now, 0L, now, PetActivity.PLAY, PetActivity.DANCE_DJ, s).first)
        }
        assertEquals(500, PetBrain.frameMsForBpm(120f))
        assertEquals(500, PetBrain.frameMsForBpm(240f))
        assertEquals(PetBrain.danceFor(42L), PetBrain.danceFor(42L))
    }

    @Test
    fun photoAnalysisOfAPlainGingerCat() {
        val img = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 200) for (x in 0 until 200) {
            val inside = (x - 100) * (x - 100) + (y - 100) * (y - 100) < 80 * 80
            img.setRGB(x, y, if (inside) 0xFFE8963C.toInt() else 0xFF203040.toInt())
        }
        val result = PetPhotoAnalyzer.analyze(img)
        val r = result.palette.fur shr 16 and 0xFF
        val b = result.palette.fur and 0xFF
        assertTrue("ginger fur expected, got ${Integer.toHexString(result.palette.fur)}", r > b + 60)
    }

    @Test
    fun exifOrientationIsRead() {
        // FFD8, APP1 "Exif\0\0", little endian TIFF, one IFD entry: orientation = 6 (90° clockwise)
        val tiff = byteArrayOf(
            'I'.code.toByte(), 'I'.code.toByte(), 42, 0, 8, 0, 0, 0,
            1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0,
        )
        val app1 = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val len = app1.size + 2
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + app1 +
            byteArrayOf(0xFF.toByte(), 0xDA.toByte())
        assertEquals(90, jpegOrientation(jpeg))
        assertEquals(0, jpegOrientation(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun liveBeatTrackerLocksOnA120BpmClickTrack() {
        val rate = 44_100
        val tracker = LiveBeatTracker()
        tracker.configure(rate)
        tracker.onFlush(0.0)
        val period = rate / 2 // 120 BPM
        val seconds = 12
        for (i in 0 until rate * seconds) {
            val sinceBeat = i % period
            // a short decaying kick on every beat, plus a little noise floor
            val kick = if (sinceBeat < 2_000) (sin(2 * Math.PI * 60.0 * sinceBeat / rate) * (1.0 - sinceBeat / 2_000.0)).toFloat() * 0.9f else 0f
            tracker.push(kick + ((i * 7919) % 1000 - 500) / 50_000f)
            if (i % 1024 == 0) tracker.onBufferDone()
        }
        tracker.onBufferDone()
        val snap = tracker.snapshot
        assertNotNull("no tempo after $seconds s", snap)
        assertTrue("bpm ${snap!!.bpm}", abs(snap.bpm - 120f) < 3f)
        // the next beat after 12 s of a grid starting at 0 lands on a multiple of 500 ms
        val next = tracker.nextBeat(seconds * 1000L - 200, minAheadMs = 0)
        assertNotNull(next)
        val at = seconds * 1000L - 200 + next!!.first
        val off = ((at % 500) + 500) % 500
        assertTrue("beat lands $off ms off the grid", off < 60 || off > 440)
    }
}
