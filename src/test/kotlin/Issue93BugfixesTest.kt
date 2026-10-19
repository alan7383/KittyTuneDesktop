import com.alananasss.kittytune.audio.ai.OnnxHelper
import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.utils.makeTimeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Issue93BugfixesTest {

    // --- Issue 1: Timecode & Position formatting/clamping ---
    @Test
    fun testMakeTimeStringNegativeAndBoundaryValues() {
        assertEquals("00:00", makeTimeString(-1000L))
        assertEquals("00:00", makeTimeString(0L))
        assertEquals("00:01", makeTimeString(1000L))
        assertEquals("04:04", makeTimeString(244_000L))

        // Absurd huge values (e.g. 56,235,960,000L nanoseconds leaked as ms)
        // must be capped to safe limit rather than showing 15,621 hours
        val safeStr = makeTimeString(56_235_960_000L)
        assertFalse("Should not format thousands of hours", safeStr.contains("15621"))
    }

    @Test
    fun testPositionClampingToDuration() {
        val duration = 244_000L // 04:04
        val leakedRawPosition = 56_235_960_000L // from glitch
        val clampedPosition = leakedRawPosition.coerceIn(0L, duration)
        assertEquals(duration, clampedPosition)
        assertEquals("04:04", makeTimeString(clampedPosition))

        val negativePosition = -5000L
        val clampedNegative = negativePosition.coerceIn(0L, duration)
        assertEquals(0L, clampedNegative)
        assertEquals("00:00", makeTimeString(clampedNegative))
    }

    // --- Issue 2: Lyrics YAML dump, duplicate lines, and scroll calculations ---
    @Test
    fun testLyricsYamlPlainExtraction() {
        val sampleYaml = """
            version: '1.0'
            metadata:
              title: Song Title
              artist: Artist Name
            lines: []
            plain: |-
              Line one of lyrics
              Line two of lyrics
        """.trimIndent()

        val extracted = LyricsUtils.extractPlainLyricsFromYaml(sampleYaml)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("Line one of lyrics"))
        assertTrue(extracted.contains("Line two of lyrics"))
        assertFalse(extracted.contains("version: '1.0'"))
        assertFalse(extracted.contains("lines: []"))

        // YAML with lines: [] should parse to empty list without error
        val parsedLines = LyricsUtils.parseLyricsContent(sampleYaml, 180_000L)
        assertTrue(parsedLines.isEmpty())

        // Non-YAML should return null
        val lrc = "[00:10.00]Hello world\n[00:15.00]Second line"
        assertNull(LyricsUtils.extractPlainLyricsFromYaml(lrc))
    }

    @Test
    fun testYamlWithLeadingDashesAndIsYamlContent() {
        val yamlWithDashes = """
            ---
            version: '1.0'
            metadata:
              title: Song
            lines: []
            plain: |-
              Actual lyric text
        """.trimIndent()

        assertTrue(LyricsUtils.isYamlContent(yamlWithDashes))
        val extracted = LyricsUtils.extractPlainLyricsFromYaml(yamlWithDashes)
        assertNotNull(extracted)
        assertEquals("Actual lyric text", extracted!!.trim())

        assertFalse(LyricsUtils.isYamlContent("[00:01.00] Plain LRC line"))
        assertFalse(LyricsUtils.isYamlContent("Just plain text lyric"))
        assertFalse(LyricsUtils.isYamlContent(null))
        assertFalse(LyricsUtils.isYamlContent(""))
    }

    @Test
    fun testDuplicateTranslationFiltering() {
        val originalText = "Я помню чудное мгновенье"
        val exactSame = "Я помню чудное мгновенье"
        val trailingPunctuation = "Я помню чудное мгновенье."
        val exclamation = "Я помню чудное мгновенье!"
        val casingDiff = "я помню чудное мгновенье"
        val foreignTranslation = "I remember a wonderful moment"

        // Identical or punctuation/casing variants must be detected as duplicates
        assertTrue(LyricsUtils.isDuplicateTranslation(originalText, exactSame))
        assertTrue(LyricsUtils.isDuplicateTranslation(originalText, trailingPunctuation))
        assertTrue(LyricsUtils.isDuplicateTranslation(originalText, exclamation))
        assertTrue(LyricsUtils.isDuplicateTranslation(originalText, casingDiff))

        // Actual foreign translation must NOT be detected as duplicate
        assertFalse(LyricsUtils.isDuplicateTranslation(originalText, foreignTranslation))

        // Null and blank safety
        assertFalse(LyricsUtils.isDuplicateTranslation(null, foreignTranslation))
        assertFalse(LyricsUtils.isDuplicateTranslation(originalText, null))
        assertFalse(LyricsUtils.isDuplicateTranslation("", ""))
    }

    // --- Issue 3: In-app Updater detached script validation ---
    @Test
    fun testWindowsUpdaterScriptConstructionMsi() {
        val targetPid = 12345L
        val installer = File("C:\\Temp\\installer.msi")
        val kittyExe = File("C:\\Users\\User\\AppData\\Local\\Programs\\KittyTune\\KittyTune.exe")
        val logFile = File("C:\\Temp\\kittytune_updater.log")
        val msiLog = File("C:\\Temp\\kittytune_msi.log")

        val script = UpdateManager.buildWindowsUpdaterScript(
            targetPid = targetPid,
            installerFile = installer,
            kittyExe = kittyExe,
            logFile = logFile,
            msiLogFile = msiLog
        )

        assertTrue(script.contains("\$targetPid = 12345"))
        assertTrue(script.contains("WaitForExit(30000)"))
        assertTrue(script.contains("Stop-Process -Force"))
        val escapedInstaller = installer.absolutePath.replace("'", "''")
        assertTrue(script.contains("-ArgumentList @('/i', '$escapedInstaller'"))
        assertTrue(script.contains("Start-Process -FilePath \$exePath"))
        assertFalse("Must not pass literal nested double quotes to Start-Process", script.contains("`\"`\""))
    }

    @Test
    fun testWindowsUpdaterScriptConstructionExe() {
        val targetPid = 54321L
        val installer = File("C:\\Temp\\installer.exe")
        val kittyExe = File("C:\\Program Files\\KittyTune\\KittyTune.exe")
        val logFile = File("C:\\Temp\\kittytune_updater.log")
        val msiLog = File("C:\\Temp\\kittytune_msi.log")

        val script = UpdateManager.buildWindowsUpdaterScript(
            targetPid = targetPid,
            installerFile = installer,
            kittyExe = kittyExe,
            logFile = logFile,
            msiLogFile = msiLog
        )

        assertTrue(script.contains("\$targetPid = 54321"))
        val escapedInstaller = installer.absolutePath.replace("'", "''")
        assertTrue(script.contains("Start-Process -FilePath '$escapedInstaller'"))
        assertTrue(script.contains("'/VERYSILENT'"))
        assertTrue(script.contains("Start-Process -FilePath \$exePath"))
    }

    // --- Issue 4: ONNX lazy loading ---
    @Test
    fun testOnnxTelemetryDisableDoesNotBlock() {
        // Calling disableNativeTelemetry must run immediately without throwing
        val start = System.currentTimeMillis()
        OnnxHelper.disableNativeTelemetry()
        val duration = System.currentTimeMillis() - start
        assertTrue("disableNativeTelemetry should take < 500ms, took ${duration}ms", duration < 500L)
    }

    // --- Issue 5: Empty YAML plain and LyricsCache sanitization ---
    @Test
    fun testIssueScreenshotExactYamlExtraction() {
        // Exact structure from Issue 93 screenshot
        val issueYaml = """
            version: '1.0'
            metadata:
              title: Капитан Америка (Не берет трубу)
              artist: Noize MC
              album: Неразбериха (Explicit version)
              duration_ms: 190000
              instrumental: false
            lines: []
            plain: |-
        """.trimIndent()

        // When plain is empty, it must return null so UI doesn't render raw YAML or empty lines
        val extracted = LyricsUtils.extractPlainLyricsFromYaml(issueYaml)
        assertNull(extracted)

        // LyricsCache sanitization on this entry should leave plain as null
        val entry = com.alananasss.kittytune.data.LyricsCache.Entry(
            found = true,
            lines = emptyList(),
            plain = issueYaml
        )
        val sanitized = entry.sanitized()
        assertNull(sanitized.plain)
        assertTrue(sanitized.lines.isEmpty())
    }

    @Test
    fun testPlayheadClockSafeStepProgression() {
        val clock = com.alananasss.kittytune.ui.player.slider.PlayheadClock()
        val t0 = 10_000_000_000L // 10s into uptime
        val pos0 = clock.step(reported = 1000L, key = "track1", direct = false, running = true, now = t0)
        assertEquals(1000L, pos0)

        // Advance 100ms
        val t1 = t0 + 100_000_000L
        val pos1 = clock.step(reported = 1100L, key = "track1", direct = false, running = true, now = t1)
        assertTrue("Playhead must advance smoothly (~1100ms), was $pos1", pos1 in 1050L..1200L)

        // Sudden nanosecond-scale leap in reported or time should not cause runaway drawn value
        val pos2 = clock.step(reported = 1200L, key = "track1", direct = false, running = true, now = t1 + 50_000_000L)
        assertTrue("Playhead must not jump to millions of ms: $pos2", pos2 < 5000L)
    }
}
