package com.alananasss.kittytune

import com.alananasss.kittytune.core.LinuxDesktopIdentity
import com.alananasss.kittytune.core.ProtocolHandlerInstaller
import com.alananasss.kittytune.core.TogetherUriRequests
import com.alananasss.kittytune.data.discord.DiscordRemoteAuth
import com.alananasss.kittytune.data.together.SharedListener
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherRelay
import com.alananasss.kittytune.data.together.TogetherWire
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

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
        val appLink = TogetherLinks.appDeepLink(code)
        assertEquals("kittytune://together/ABCD-EFGH", appLink)
        assertEquals(code, TogetherLinks.extractCodeFromUri(appLink))
        assertEquals(code, TogetherLinks.extractCodeFromUri("$appLink?join=1"))
        assertTrue(TogetherLinks.isCustomSchemeUri(appLink))
        assertTrue(TogetherLinks.isTogetherUri(appLink))

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
            hostAvatarUrl = "https://cdn.discordapp.com/avatars/42/hash123.png?size=128",
        )
        val sealed = TogetherWire.seal("ABCD-EFGH", state)
        val opened = TogetherWire.open("abcd efgh", sealed) as? TogetherMessage.State
        assertNotNull(opened)
        assertEquals(true, opened?.hostLocked)
        assertEquals("Alan", opened?.hostName)
        assertEquals("https://cdn.discordapp.com/avatars/42/hash123.png?size=128", opened?.hostAvatarUrl)

        val hello = TogetherMessage.Hello(
            from = "member-1",
            sentAt = 5100L,
            name = "Foxy",
            isListening = true,
            avatarUrl = "https://i1.sndcdn.com/avatars-000123-t200x200.jpg",
        )
        val openedHello = TogetherWire.open("ABCD-EFGH", TogetherWire.seal("ABCD-EFGH", hello)) as? TogetherMessage.Hello
        assertNotNull(openedHello)
        assertEquals("Foxy", openedHello?.name)
        assertEquals("https://i1.sndcdn.com/avatars-000123-t200x200.jpg", openedHello?.avatarUrl)

        // Legacy State JSON without hostLocked / hostName / hostAvatarUrl
        val legacyJson = Gson().fromJson(
            """{"type":"State","from":"old-host","sentAt":1000,"positionMs":1200,"isPlaying":true,"automix":false,"queueVersion":1}""",
            JsonObject::class.java
        )
        val legacy = TogetherMessage.fromJson(legacyJson) as? TogetherMessage.State
        assertNotNull(legacy)
        assertEquals(false, legacy?.hostLocked)
        assertNull(legacy?.hostName)
        assertNull(legacy?.hostAvatarUrl)

        // Legacy Hello JSON without avatarUrl
        val legacyHelloJson = Gson().fromJson(
            """{"type":"Hello","from":"old-member","sentAt":1100,"name":"Classic","isListening":true}""",
            JsonObject::class.java
        )
        val legacyHello = TogetherMessage.fromJson(legacyHelloJson) as? TogetherMessage.Hello
        assertNotNull(legacyHello)
        assertEquals("Classic", legacyHello?.name)
        assertNull(legacyHello?.avatarUrl)
    }

    @Test
    fun testSoundCloudDiscordLetterAvatarPriorityResolution() {
        assertNull(Together.myAvatarUrl)

        val scLarge = "https://i1.sndcdn.com/avatars-abc123-large.jpg"
        val discordCdn = "https://cdn.discordapp.com/avatars/987654/fedcba.png?size=128"

        // 1. Both SoundCloud (real photo) and Discord present -> SoundCloud wins, upgraded to -t200x200
        Together.soundCloudAvatarUrl = scLarge
        Together.discordAvatarUrl = discordCdn
        assertEquals("https://i1.sndcdn.com/avatars-abc123-t200x200.jpg", Together.myAvatarUrl)

        // 2. SoundCloud has default placeholder -> falls back to Discord avatar
        Together.soundCloudAvatarUrl = "https://a1.sndcdn.com/images/default_avatar_large.png"
        assertEquals(discordCdn, Together.myAvatarUrl)

        // 3. Guest mode / logged out of SoundCloud (null) -> falls back to Discord avatar
        Together.soundCloudAvatarUrl = null
        assertEquals(discordCdn, Together.myAvatarUrl)

        // 4. Neither valid (e.g. insecure http or blank) -> null so UI falls back to initial letter
        Together.soundCloudAvatarUrl = "http://i1.sndcdn.com/avatars-abc123-large.jpg"
        Together.discordAvatarUrl = "   "
        assertNull(Together.myAvatarUrl)

        Together.discordAvatarUrl = "http://cdn.discordapp.com/avatars/987654/fedcba.png"
        assertNull(Together.myAvatarUrl)

        // 5. resetForTesting clears both avatar fields
        Together.soundCloudAvatarUrl = scLarge
        Together.discordAvatarUrl = discordCdn
        assertNotNull(Together.myAvatarUrl)
        Together.resetForTesting()
        assertNull(Together.soundCloudAvatarUrl)
        assertNull(Together.discordAvatarUrl)
        assertNull(Together.myAvatarUrl)
    }

    @Test
    fun testIncomingHelloAndStatePopulateMemberAvatarsAndFilterPlaceholders() = runBlocking {
        val saved = Together.create("Avatar Sync Room")
        val code = saved.code
        val now = System.currentTimeMillis()

        // Valid Hello avatar stored on Member
        Together.handle(
            code,
            TogetherMessage.Hello(
                from = "listener-sc",
                sentAt = now,
                name = "M83Fan",
                isListening = true,
                avatarUrl = "https://i1.sndcdn.com/avatars-xyz-t200x200.jpg",
            ),
        )
        assertEquals(
            "https://i1.sndcdn.com/avatars-xyz-t200x200.jpg",
            Together.rooms.value[code]?.members?.get("listener-sc")?.avatarUrl,
        )

        // Placeholder default_avatar in Hello is filtered to null (initial-letter fallback)
        Together.handle(
            code,
            TogetherMessage.Hello(
                from = "listener-placeholder",
                sentAt = now,
                name = "NoPic",
                isListening = true,
                avatarUrl = "https://a1.sndcdn.com/images/default_avatar_large.png",
            ),
        )
        assertNull(Together.rooms.value[code]?.members?.get("listener-placeholder")?.avatarUrl)

        // State.hostAvatarUrl populates host Member entry
        Together.handle(
            code,
            TogetherMessage.State(
                from = "host-dc",
                sentAt = now,
                track = sampleTrack,
                positionMs = 5_000L,
                isPlaying = true,
                automix = false,
                queueVersion = 1,
                hostLocked = false,
                hostName = "HostAlan",
                hostAvatarUrl = "https://cdn.discordapp.com/avatars/777/avhash.png?size=128",
            ),
        )
        val hostMember = Together.rooms.value[code]?.members?.get("host-dc")
        assertNotNull(hostMember)
        assertEquals("HostAlan", hostMember?.name)
        assertEquals("https://cdn.discordapp.com/avatars/777/avhash.png?size=128", hostMember?.avatarUrl)

        // Subsequent State omitting hostAvatarUrl preserves known avatarUrl
        Together.handle(
            code,
            TogetherMessage.State(
                from = "host-dc",
                sentAt = now + 1_000L,
                track = sampleTrack,
                positionMs = 6_000L,
                isPlaying = true,
                automix = false,
                queueVersion = 1,
                hostLocked = false,
                hostName = "HostAlan",
                hostAvatarUrl = null,
            ),
        )
        assertEquals(
            "https://cdn.discordapp.com/avatars/777/avhash.png?size=128",
            Together.rooms.value[code]?.members?.get("host-dc")?.avatarUrl,
        )
    }

    @Test
    fun testDiscordUsersMeAvatarParsing() {
        assertEquals(
            "https://cdn.discordapp.com/avatars/123456789012345678/a_1b2c3d4e.png?size=128",
            DiscordRemoteAuth.parseDiscordUserAvatarUrl(
                """{"id":"123456789012345678","username":"alan","avatar":"a_1b2c3d4e"}"""
            ),
        )
        assertNull(
            DiscordRemoteAuth.parseDiscordUserAvatarUrl(
                """{"id":"123456789012345678","username":"alan","avatar":null}"""
            )
        )
        assertNull(
            DiscordRemoteAuth.parseDiscordUserAvatarUrl(
                """{"id":"123456789012345678","username":"alan","avatar":""}"""
            )
        )
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
    fun testTogetherUriRequestsDeliversLegacyLinksForConfirmation() = runBlocking {
        TogetherUriRequests.dispatchIncomingArgs(
            listOf("\"kittytune://together/K9M2-P4X7?join=1\"")
        )
        val received = withTimeout(2_000L) {
            TogetherUriRequests.requests.first()
        }
        assertEquals("K9M2P4X7", received)
        assertNull(Together.active.value)
        assertFalse(Together.saved.value.any { it.code == received })
    }

    @Test
    fun testConfirmedNicknameIsUsedWhenJoining() {
        val code = "K9M2P4X7"
        val previousName = Together.myName
        try {
            assertNull(Together.joinAndListen(code, "   "))
            assertNull(Together.active.value)
            assertEquals(previousName, Together.myName)

            assertEquals(code, Together.joinAndListen(code, "  DJ Nébuleuse  ")?.code)
            assertEquals(code, Together.active.value)
            assertEquals("DJ Nébuleuse", Together.myName)
            assertFalse(Together.isHostOf(code))
        } finally {
            Together.forget(code)
            Together.myName = previousName
        }
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
    fun testWindowsUpdateRepairsPartialAndStaleProtocolRegistrations() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val registry = WinReg.HKEY_CURRENT_USER
        val key = "Software\\KittyTune-Protocol-Test-${UUID.randomUUID()}"
        val commandKey = "$key\\shell\\open\\command"
        val iconKey = "$key\\DefaultIcon"
        val oldLauncher = "C:\\Program Files\\Old KittyTune\\KittyTune.exe"
        val newLauncher = "C:\\Program Files\\KittyTune 2\\KittyTune.exe"
        try {
            Advapi32Util.registryCreateKey(registry, key)
            Advapi32Util.registrySetStringValue(registry, key, "URL Protocol", "")
            Advapi32Util.registryCreateKey(registry, iconKey)
            Advapi32Util.registrySetStringValue(registry, iconKey, "", "\"$oldLauncher\",0")
            assertFalse(Advapi32Util.registryKeyExists(registry, commandKey))

            assertTrue(ProtocolHandlerInstaller.repairWindowsProtocolRegistration(key, oldLauncher))
            assertEquals("\"$oldLauncher\" \"%1\"", Advapi32Util.registryGetStringValue(registry, commandKey, ""))

            assertTrue(ProtocolHandlerInstaller.repairWindowsProtocolRegistration(key, newLauncher))
            assertEquals("\"$newLauncher\" \"%1\"", Advapi32Util.registryGetStringValue(registry, commandKey, ""))
            assertEquals("\"$newLauncher\",0", Advapi32Util.registryGetStringValue(registry, iconKey, ""))
            assertEquals("", Advapi32Util.registryGetStringValue(registry, key, "URL Protocol"))
            assertFalse(ProtocolHandlerInstaller.repairWindowsProtocolRegistration(key, newLauncher))
        } finally {
            for (path in listOf(commandKey, "$key\\shell\\open", "$key\\shell", iconKey, key)) {
                if (Advapi32Util.registryKeyExists(registry, path)) Advapi32Util.registryDeleteKey(registry, path)
            }
        }
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

    @Test
    fun testTogetherMemberPillRendersAvatarAndInitialFallback() {
        val scene = androidx.compose.ui.ImageComposeScene(
            width = 480,
            height = 120,
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
                androidx.compose.foundation.layout.Row {
                    com.alananasss.kittytune.ui.together.TogetherMemberPill(
                        name = "Alan",
                        isHost = true,
                        isMe = true,
                        avatarUrl = "https://cdn.discordapp.com/avatars/123456/abcdef.png?size=128",
                    )
                    com.alananasss.kittytune.ui.together.TogetherMemberPill(
                        name = "Guest",
                        isHost = false,
                        isMe = false,
                        avatarUrl = null,
                    )
                }
            }
        }
        try {
            scene.render(0L).use { image ->
                val bytes = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes
                assertNotNull(bytes)
                assertTrue((bytes?.size ?: 0) > 200)
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun testSnapshotTopicDerivationParityAndRelayResolution() {
        val code = "ABCDEFGH"
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest("snap:ABCDEFGH".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)

        assertEquals("kittytune/together/snap/$expectedHash/state", TogetherWire.stateSnapshotTopicFor("abcd-efgh"))
        assertEquals("kittytune/together/snap/$expectedHash/queue", TogetherWire.queueSnapshotTopicFor("abcd efgh"))
        assertEquals("kittytune/together/snap/$expectedHash/#", TogetherWire.snapshotWildcardTopicFor(code))
        assertFalse(TogetherWire.stateSnapshotTopicFor(code).contains(code, ignoreCase = true))
        assertNotEquals(TogetherWire.topicFor(code), TogetherWire.stateSnapshotTopicFor(code))
        assertNotEquals(TogetherWire.stateSnapshotTopicFor(code), TogetherWire.stateSnapshotTopicFor("ABCDEFGJ"))

        val relay = TogetherRelay("test-relay-snap")
        try {
            relay.follow("abcd-efgh")
            assertTrue(relay.isFollowing(code))
            assertEquals(code, relay.resolveCodeForTopic(TogetherWire.topicFor(code)))
            assertEquals(code, relay.resolveCodeForTopic(TogetherWire.stateSnapshotTopicFor(code)))
            assertEquals(code, relay.resolveCodeForTopic(TogetherWire.queueSnapshotTopicFor(code)))
            assertEquals(code, relay.resolveCodeForTopic("kittytune/together/snap/$expectedHash/extra"))

            relay.unfollow(code)
            assertFalse(relay.isFollowing(code))
            assertNull(relay.resolveCodeForTopic(TogetherWire.topicFor(code)))
            assertNull(relay.resolveCodeForTopic(TogetherWire.stateSnapshotTopicFor(code)))
            assertNull(relay.resolveCodeForTopic("kittytune/together/snap/$expectedHash/extra"))
        } finally {
            relay.close()
        }
    }

    @Test
    fun testStateListenersWireRoundTripAndImmediateMemberMerge() = runBlocking {
        val sharedListeners = listOf(
            SharedListener("host-1", "Alan", "https://cdn.discordapp.com/avatars/42/hash123.png?size=128"),
            SharedListener("peer-sc", "Foxy", "https://i1.sndcdn.com/avatars-abc-t200x200.jpg"),
            SharedListener("peer-placeholder", "NoPic", "https://a1.sndcdn.com/images/default_avatar_large.png"),
            SharedListener(Together.memberId, "SelfEcho", "https://cdn.discordapp.com/avatars/99/self.png?size=128"),
        )
        val state = TogetherMessage.State(
            from = "host-1",
            sentAt = System.currentTimeMillis() - 1_200L,
            track = sampleTrack,
            positionMs = 42_000L,
            isPlaying = true,
            automix = false,
            queueVersion = 2,
            hostLocked = true,
            hostName = "Alan",
            hostAvatarUrl = "https://cdn.discordapp.com/avatars/42/hash123.png?size=128",
            listeners = sharedListeners,
        )

        val jsonObj = state.toJson()
        val listenersJson = jsonObj.getAsJsonArray("listeners")
        assertNotNull(listenersJson)
        assertEquals(4, listenersJson.size())
        assertEquals("peer-sc", listenersJson[1].asJsonObject.get("id").asString)
        assertEquals("Foxy", listenersJson[1].asJsonObject.get("name").asString)

        val opened = TogetherWire.open("ABCD-EFGH", TogetherWire.seal("abcd-efgh", state)) as? TogetherMessage.State
        assertNotNull(opened)
        assertEquals(sharedListeners, opened?.listeners)

        // Legacy State without listeners field remains null and backward compatible
        val legacyJson = Gson().fromJson(
            """{"type":"State","from":"old-host","sentAt":1000,"positionMs":500,"isPlaying":false,"automix":false,"queueVersion":1}""",
            JsonObject::class.java
        )
        val legacy = TogetherMessage.fromJson(legacyJson) as? TogetherMessage.State
        assertNotNull(legacy)
        assertNull(legacy?.listeners)

        // Applying State (even as initial retained snapshot) populates all active listeners + sanitized avatars immediately
        val saved = Together.create("Instant Snapshot Room")
        Together.handle(saved.code, opened!!, isSnapshot = true)

        val room = Together.rooms.value[saved.code]
        assertNotNull(room)
        assertEquals("host-1", room?.hostId)
        assertTrue(room?.hasLiveHost == true)
        assertEquals(sampleTrack.id, room?.nowPlaying?.id)

        // Host + 2 remote peers merged immediately; self (memberId) is excluded from remote members map
        assertNull(room?.members?.get(Together.memberId))
        assertEquals("https://cdn.discordapp.com/avatars/42/hash123.png?size=128", room?.members?.get("host-1")?.avatarUrl)
        assertEquals("Foxy", room?.members?.get("peer-sc")?.name)
        assertEquals("https://i1.sndcdn.com/avatars-abc-t200x200.jpg", room?.members?.get("peer-sc")?.avatarUrl)
        assertEquals("NoPic", room?.members?.get("peer-placeholder")?.name)
        assertNull(room?.members?.get("peer-placeholder")?.avatarUrl)
        assertEquals(3, room?.listeners?.size)
    }

    @Test
    fun testStaleSnapshotRejectionAndFreshSnapshotAcceptance() = runBlocking {
        val saved = Together.create("Stale Guard Room")
        val code = saved.code
        val now = System.currentTimeMillis()

        val staleSnapshot = TogetherMessage.State(
            from = "crashed-host",
            sentAt = now - 120_000L,
            track = sampleTrack,
            positionMs = 10_000L,
            isPlaying = true,
            automix = false,
            queueVersion = 1,
            hostName = "GhostHost",
            listeners = listOf(SharedListener("crashed-host", "GhostHost")),
        )
        Together.handle(code, staleSnapshot, isSnapshot = true)

        val roomAfterStale = Together.rooms.value[code]
        assertNull("Stale retained snapshot (>60s) must be ignored", roomAfterStale?.hostId)
        assertNull(roomAfterStale?.nowPlaying)
        assertTrue(roomAfterStale?.listeners.orEmpty().isEmpty())
        assertFalse(roomAfterStale?.hasLiveHost == true)

        val freshSnapshot = TogetherMessage.State(
            from = "live-host",
            sentAt = now - 4_000L,
            track = sampleTrack,
            positionMs = 25_000L,
            isPlaying = true,
            automix = true,
            queueVersion = 3,
            hostName = "LiveHost",
            listeners = listOf(
                SharedListener("live-host", "LiveHost", "https://cdn.discordapp.com/avatars/1/live.png?size=128"),
                SharedListener("guest-2", "Luna", "https://i1.sndcdn.com/avatars-luna-t200x200.jpg"),
            ),
        )
        Together.handle(code, freshSnapshot, isSnapshot = true)

        val roomAfterFresh = Together.rooms.value[code]
        assertEquals("live-host", roomAfterFresh?.hostId)
        assertTrue(roomAfterFresh?.hasLiveHost == true)
        assertEquals(sampleTrack.id, roomAfterFresh?.nowPlaying?.id)
        assertEquals(2, roomAfterFresh?.listeners?.size)
    }
}
