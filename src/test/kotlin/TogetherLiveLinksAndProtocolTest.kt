package com.alananasss.kittytune

import com.alananasss.kittytune.core.LinuxDesktopIdentity
import com.alananasss.kittytune.core.ProtocolHandlerInstaller
import com.alananasss.kittytune.core.TogetherUriRequests
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherWire
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TogetherLiveLinksAndProtocolTest {

    private val sampleTrack = SharedTrack(
        id = 777L,
        title = "Midnight City",
        artist = "M83",
        artworkUrl = "https://example.com/m83.jpg",
        durationMs = 243_000L,
        permalinkUrl = "https://soundcloud.com/m83/midnight-city",
        source = "soundcloud",
        userId = 10L,
        username = "M83",
    )

    @Before
    fun setUp() {
        Together.resetForTesting()
    }

    @After
    fun tearDown() {
        Together.resetForTesting()
    }

    @Test
    fun testInviteUrlAndDeepLinkParsing() {
        val code = "ABCDEFGH"
        val inviteUrl = TogetherLinks.inviteUrl(code)
        assertEquals("https://alan7383.github.io/kittytune-website/?together=ABCD-EFGH", inviteUrl)
        assertEquals(code, TogetherLinks.extractCodeFromUri(inviteUrl))
        assertFalse(TogetherLinks.isAutoJoinUri(inviteUrl))

        val autoJoinDeepLink = TogetherLinks.appDeepLink(code, autoJoin = true)
        assertEquals("kittytune://together/ABCD-EFGH?join=1", autoJoinDeepLink)
        assertEquals(code, TogetherLinks.extractCodeFromUri(autoJoinDeepLink))
        assertTrue(TogetherLinks.isAutoJoinUri(autoJoinDeepLink))
        assertTrue(TogetherLinks.isCustomSchemeUri(autoJoinDeepLink))
        assertTrue(TogetherLinks.isTogetherUri(autoJoinDeepLink))

        val previewDeepLink = TogetherLinks.appDeepLink(code, autoJoin = false)
        assertEquals("kittytune://together/ABCD-EFGH", previewDeepLink)
        assertEquals(code, TogetherLinks.extractCodeFromUri(previewDeepLink))
        assertFalse(TogetherLinks.isAutoJoinUri(previewDeepLink))

        val multilineShare = "Écoute ma musique en direct sur KittyTune !\n$inviteUrl\nCode : ABCD-EFGH"
        assertEquals(code, TogetherLinks.extractCode(multilineShare, allowRawCode = true))
        assertNull(TogetherLinks.extractCode("just a normal search query", allowRawCode = false))
    }

    @Test
    fun testStateWireIncludesHostLockedAndHostNameAndRemainsBackwardCompatible() {
        val state = TogetherMessage.State(
            from = "host-1",
            sentAt = 5000L,
            track = sampleTrack,
            positionMs = 30_000L,
            isPlaying = true,
            automix = true,
            queueVersion = 4,
            hostLocked = true,
            hostName = "Alan",
        )
        val sealed = TogetherWire.seal("ABCD-EFGH", state)
        val opened = TogetherWire.open("abcd efgh", sealed) as? TogetherMessage.State
        assertNotNull(opened)
        assertEquals(true, opened?.hostLocked)
        assertEquals("Alan", opened?.hostName)

        // Legacy State JSON without hostLocked / hostName
        val legacyJson = Gson().fromJson(
            """{"type":"State","from":"old-host","sentAt":1000,"positionMs":1200,"isPlaying":true,"automix":false,"queueVersion":1}""",
            JsonObject::class.java
        )
        val legacy = TogetherMessage.fromJson(legacyJson) as? TogetherMessage.State
        assertNotNull(legacy)
        assertEquals(false, legacy?.hostLocked)
        assertNull(legacy?.hostName)
    }

    @Test
    fun testStartLiveBroadcastAndHostLockEnforcement() = runBlocking {
        var controlTriggered = false
        var playNextTriggered = false

        val fakePlayer = object : TogetherPlayer {
            override fun sharedCurrent() = sampleTrack
            override fun sharedPositionMs() = 15_000L
            override fun sharedIsPlaying() = true
            override fun sharedUpcoming() = emptyList<SharedTrack>()
            override fun sharedAutomix() = false
            override fun followHost(track: SharedTrack, positionMs: Long, isPlaying: Boolean, upcoming: List<SharedTrack>?, automix: Boolean) {}
            override fun hostControl(action: String, positionMs: Long) { controlTriggered = true }
            override fun hostPlayNext(track: SharedTrack) { playNextTriggered = true }
            override fun hostPlay(tracks: List<SharedTrack>, index: Int) {}
        }

        Together.myName = "Alan"
        Together.start(fakePlayer)

        val code = Together.startLiveBroadcast(seedTracks = listOf(sampleTrack), lockControls = true)
        assertEquals(8, code.length)
        assertEquals(code, Together.active.value)
        assertTrue(Together.isHostOf(code))
        assertTrue(Together.isHostLocked.value)

        // Remote listener sends Control while hostLocked = true -> ignored
        Together.handle(code, TogetherMessage.Control("guest-1", System.currentTimeMillis(), TogetherMessage.CONTROL_PAUSE, 15_000L))
        assertFalse("Locked host must ignore guest Control", controlTriggered)

        // Remote listener sends PlayNext while hostLocked = true -> converted into Suggestion
        Together.handle(code, TogetherMessage.PlayNext("guest-1", System.currentTimeMillis(), sampleTrack, "Foxy"))
        assertFalse("Locked host must not insert directly into queue", playNextTriggered)
        assertEquals(1, Together.rooms.value[code]?.suggestions?.size)
        assertEquals("Foxy", Together.rooms.value[code]?.suggestions?.first()?.byName)

        // Unlocking allows Control
        Together.setHostLocked(false)
        Together.handle(code, TogetherMessage.Control("guest-1", System.currentTimeMillis(), TogetherMessage.CONTROL_PAUSE, 15_000L))
        assertTrue("Unlocked host should apply guest Control", controlTriggered)

        val oldPersonal = Together.personalLiveCode
        val regenerated = Together.regeneratePersonalLiveCode()
        assertNotEquals(oldPersonal, regenerated)
        assertEquals(regenerated, Together.personalLiveCode)
    }

    @Test
    fun testTogetherUriRequestsDispatchesDeepLinkFromCliArgs() = runBlocking {
        TogetherUriRequests.dispatchIncomingArgs(
            listOf("\"kittytune://together/K9M2-P4X7?join=1\"")
        )
        val received = withTimeout(2_000L) {
            TogetherUriRequests.requests.first()
        }
        assertEquals("K9M2P4X7", received.code)
        assertTrue(received.autoJoin)
    }

    @Test
    fun testPlatformProtocolRegistrationFormats() {
        val winCmd = ProtocolHandlerInstaller.buildWindowsOpenCommand("C:\\Users\\alan\\AppData\\Local\\KittyTune\\KittyTune.exe")
        assertEquals("\"C:\\Users\\alan\\AppData\\Local\\KittyTune\\KittyTune.exe\" \"%1\"", winCmd)

        val desktopContent = LinuxDesktopIdentity.updateEntry(
            text = "[Desktop Entry]\nName=KittyTune\nType=Application\n",
            fallbackExec = "/opt/kitty-tune/bin/KittyTune",
            variant = "og",
            hidden = false,
        )
        assertTrue(desktopContent.contains("Exec=/opt/kitty-tune/bin/KittyTune %U"))
        assertTrue(desktopContent.contains("x-scheme-handler/kittytune;"))
    }

    @Test
    fun testTogetherUiFilesContainZeroGradients() {
        val togetherDir = File("src/main/kotlin/com/alananasss/kittytune/ui/together")
        val uiFiles = togetherDir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue("Together UI directory should contain Kotlin files", uiFiles.isNotEmpty())
        for (file in uiFiles) {
            val content = file.readText()
            assertFalse("${file.name} must not use Brush.linearGradient", content.contains("linearGradient"))
            assertFalse("${file.name} must not use Brush.verticalGradient", content.contains("verticalGradient"))
            assertFalse("${file.name} must not use Brush.radialGradient", content.contains("radialGradient"))
        }
    }

    @Test
    fun testTogetherHomeScreenRendersInCompactAndWideExpressiveModes() {
        val prevLang = com.alananasss.kittytune.core.Strings.appLanguage
        com.alananasss.kittytune.core.Strings.appLanguage = "fr"
        Together.create("Alan · Live")
        try {
            for ((w, h) in listOf(780 to 920, 1280 to 860)) {
                val scene = androidx.compose.ui.ImageComposeScene(
                    width = w,
                    height = h,
                    density = androidx.compose.ui.unit.Density(1f),
                ) {
                    com.alananasss.kittytune.ui.theme.SoundTuneTheme(
                        themeMode = com.alananasss.kittytune.data.local.AppThemeMode.DARK,
                        dynamicColor = false,
                        pureBlack = false,
                        keyColor = 0xFFFF7A1A.toInt(),
                        colorStyle = "Expressive",
                        typography = com.alananasss.kittytune.ui.theme.getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
                    ) {
                        com.alananasss.kittytune.ui.together.TogetherHomeScreen(
                            onBack = {},
                            onOpen = {},
                        )
                    }
                }
                try {
                    scene.render(0L).close()
                    scene.render(500_000_000L).use { image ->
                        val bytes = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes
                        assertNotNull("Rendered PNG for ${w}x${h} should not be null", bytes)
                        assertTrue("Rendered PNG for ${w}x${h} should be non-trivial", (bytes?.size ?: 0) > 1000)
                    }
                } finally {
                    scene.close()
                }
            }
        } finally {
            com.alananasss.kittytune.core.Strings.appLanguage = prevLang
        }
    }
}
