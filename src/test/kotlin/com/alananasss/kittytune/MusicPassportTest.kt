package com.alananasss.kittytune

import com.alananasss.kittytune.data.stats.MusicPassport
import com.alananasss.kittytune.ui.profile.passport.MusicPassportRender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

class MusicPassportTest {

    @Test
    fun passportNumberFormatsDeterministically() {
        val ts2025 = 1_740_000_000_000L // Feb 2025
        val no1 = MusicPassport.passportNumber(userId = 42L, firstEventMs = ts2025, plays = 1234)
        val no2 = MusicPassport.passportNumber(userId = 42L, firstEventMs = ts2025, plays = 1234)
        assertEquals(no1, no2)
        assertTrue("Expected KT·YYYY·XXXXXX format but got $no1", no1.matches(Regex("^KT·\\d{4}·\\d{6}$")))
    }

    @Test
    fun mrzLinesHaveExactTd3LengthAndValidCheckDigits() {
        val passport = MusicPassport(
            holderName = "Alan Anasss",
            passportNo = "KT·2026·123456",
            issuedAtMs = 1_740_000_000_000L,
            avatarUrl = null,
            citizenships = listOf(
                MusicPassport.Citizenship("Electronic", 48),
                MusicPassport.Citizenship("Hip-Hop", 32),
            ),
            visas = listOf(
                MusicPassport.Visa("SOUNDCLOUD", 420, "2024"),
                MusicPassport.Visa("SPOTIFY", 180, "2025"),
            ),
            stamps = listOf(
                MusicPassport.Stamp("Veteran", "15 FEB 2026"),
                MusicPassport.Stamp("Explorer", "10 JAN 2026"),
            ),
            totalMinutes = 14_520L,
            tracks = 890,
            artists = 215,
            longestStreakDays = 45,
            travellerClass = "Night Voyager",
        )

        val (line1, line2) = MusicPassportRender.mrzLines(passport)
        assertEquals(44, line1.length)
        assertEquals(44, line2.length)
        assertTrue(line1.startsWith("P<KTNALAN<ANASSS<<NIGHT<VOYAGER"))

        // Verify ICAO 9303 7-3-1 check digit on document number field (first 9 chars of line 2)
        val docField = line2.substring(0, 9)
        val docCheck = line2[9]
        assertEquals(MusicPassportRender.mrzCheckDigit(docField), docCheck)
    }

    @Test
    fun renderProduces1080x1920DocumentImage() {
        val passport = MusicPassport(
            holderName = "alan",
            passportNo = "KT·2026·784325",
            issuedAtMs = 1_793_461_900_000L,
            avatarUrl = null,
            citizenships = listOf(
                MusicPassport.Citizenship("MURDERCLUB", 59),
                MusicPassport.Citizenship("Mix", 26),
                MusicPassport.Citizenship("SPOOKKORE", 4),
                MusicPassport.Citizenship("Electronic", 4),
            ),
            visas = listOf(
                MusicPassport.Visa("SoundCloud", 17, "2026"),
            ),
            stamps = listOf(
                MusicPassport.Stamp("Heart of Gold", "29 OCT 2026"),
                MusicPassport.Stamp("Enthusiast", "13 MAR 2026"),
                MusicPassport.Stamp("Warm-up", "30 OCT 2026"),
                MusicPassport.Stamp("First Step", "30 OCT 2026"),
            ),
            totalMinutes = 128L,
            tracks = 17,
            artists = 14,
            longestStreakDays = 1,
            travellerClass = "Free Wanderer",
            accentArgb = 0xFFA8C7FA.toInt(),
        )

        val portrait = BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = java.awt.Color(45, 42, 55)
            g.fillRect(0, 0, 120, 120)
            g.dispose()
        }

        val image = MusicPassportRender.draw(passport, portrait)
        val previewOut = java.io.File("C:/Users/alan/.gemini/antigravity/brain/190ddb10-703a-4921-af52-2e603d91bdbb/scratch/passport_preview.png")
        previewOut.parentFile?.mkdirs()
        javax.imageio.ImageIO.write(image, "png", previewOut)
        assertEquals(MusicPassportRender.WIDTH, image.width)
        assertEquals(MusicPassportRender.HEIGHT, image.height)
        // Center pixel inside the paper document must be opaque
        val centerAlpha = (image.getRGB(MusicPassportRender.WIDTH / 2, MusicPassportRender.HEIGHT / 2) ushr 24) and 0xFF
        assertEquals(255, centerAlpha)
        assertNotEquals(0, image.getRGB(540, 960))
    }
}
