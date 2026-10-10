package com.alananasss.kittytune

import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TogetherEngineTest {

    private val sampleTrack = SharedTrack(
        id = 101L,
        title = "Starfall",
        artist = "Illenium",
        artworkUrl = "https://example.com/art.jpg",
        durationMs = 210_000L,
        permalinkUrl = "https://example.com/starfall",
        source = "soundcloud",
        userId = 42L,
        username = "illenium",
    )

    private val sampleTrack2 = SharedTrack(
        id = 102L,
        title = "Night Drive",
        artist = "Kavinsky",
        artworkUrl = "https://example.com/art2.jpg",
        durationMs = 180_000L,
        permalinkUrl = "https://example.com/nightdrive",
        source = "soundcloud",
        userId = 43L,
        username = "kavinsky",
    )

    @Before
    fun setUp() {
        Together.resetForTesting()
    }

    @After
    fun tearDown() {
        Together.resetForTesting()
    }

    // ─── 1. Message Serialization / Deserialization ───────────────────────────────

    @Test
    fun testAllTogetherMessagesRoundTripJson() {
        val messages = listOf(
            TogetherMessage.Hello("user1", 1000L, "Alice", isListening = true),
            TogetherMessage.Bye("user1", 1005L),
            TogetherMessage.State("host1", 1010L, sampleTrack, 45_000L, isPlaying = true, automix = false, queueVersion = 3),
            TogetherMessage.Queue("host1", 1015L, 3, listOf(sampleTrack2), listOf(sampleTrack, sampleTrack2), "Chill Party"),
            TogetherMessage.SyncRequest("user2", 1020L),
            TogetherMessage.PlayNext("user2", 1025L, sampleTrack2, "Bob"),
            TogetherMessage.Suggest("user2", 1030L, sampleTrack2, "Bob"),
            TogetherMessage.SuggestionDone("host1", 1035L, 102L),
            TogetherMessage.Control("user2", 1040L, TogetherMessage.CONTROL_PAUSE, 45_000L),
            TogetherMessage.ClaimHost("user2", 1045L),
        )

        for (msg in messages) {
            val json = msg.toJson()
            assertTrue("JSON must contain type property", json.has("type"))
            assertEquals(msg::class.simpleName, json.get("type").asString)

            val restored = TogetherMessage.fromJson(json)
            assertNotNull("Message should deserialize correctly: ${msg::class.simpleName}", restored)
            assertEquals(msg, restored)
        }

        // Invalid / unknown message type
        val unknownObj = JsonObject().apply { addProperty("type", "UnknownType") }
        assertNull(TogetherMessage.fromJson(unknownObj))
    }

    // ─── 2. SharedTrack Data Mapping ─────────────────────────────────────────────

    @Test
    fun testSharedTrackConversion() {
        val domainTrack = Track(
            id = 200L,
            title = "Midnight City",
            artworkUrl = "https://example.com/m83.jpg",
            durationMs = 240_000L,
            user = User(55L, "M83", null),
            source = "soundcloud",
            permalinkUrl = "https://example.com/m83",
        )

        val shared = SharedTrack.of(domainTrack)
        assertEquals(200L, shared.id)
        assertEquals("Midnight City", shared.title)
        assertEquals("M83", shared.artist)
        assertEquals("https://example.com/m83.jpg", shared.artworkUrl)
        assertEquals(240_000L, shared.durationMs)

        val convertedBack = shared.toTrack()
        assertEquals(domainTrack.id, convertedBack.id)
        assertEquals(domainTrack.title, convertedBack.title)
        assertEquals(domainTrack.artworkUrl, convertedBack.artworkUrl)
        assertEquals(domainTrack.durationMs, convertedBack.durationMs)
        assertEquals(domainTrack.user?.id, convertedBack.user?.id)
    }

    // ─── 3. Room Timeouts and Active Listeners ─────────────────────────────────────

    @Test
    fun testRoomActiveListenersAndLiveHostTimeouts() {
        val now = System.currentTimeMillis()

        val activeMember = Together.Member("m1", "Alice", isListening = true, lastSeen = now - 5_000L)
        val notListeningMember = Together.Member("m2", "Bob", isListening = false, lastSeen = now - 5_000L)
        val timedOutMember = Together.Member("m3", "Charlie", isListening = true, lastSeen = now - 30_000L)

        val roomWithHost = Together.Room(
            code = "TEST1234",
            name = "Test Room",
            members = mapOf("m1" to activeMember, "m2" to notListeningMember, "m3" to timedOutMember),
            hostId = "m1",
            hostSeenAt = now - 2_000L,
        )

        // Only m1 is actively listening and not timed out (< 25s)
        assertEquals(listOf(activeMember), roomWithHost.listeners)
        assertTrue(roomWithHost.hasLiveHost)

        // Host times out after 20s
        val roomHostTimedOut = roomWithHost.copy(hostSeenAt = now - 22_000L)
        assertFalse(roomHostTimedOut.hasLiveHost)

        // No host
        val roomNoHost = roomWithHost.copy(hostId = null)
        assertFalse(roomNoHost.hasLiveHost)
    }

    // ─── 4. Room Creation, Joining, Normalization & Forgetting ───────────────────

    @Test
    fun testCreateJoinForgetLifecycle() {
        val created = Together.create("  My Summer Playlist  ")
        assertEquals("My Summer Playlist", created.name)
        assertEquals(8, created.code.length)
        assertTrue(Together.saved.value.any { it.code == created.code })
        assertNotNull(Together.rooms.value[created.code])

        // Join with lowercase and dash formatting: "abcd-efgh"
        val codeToJoin = "23456789"
        val joined = Together.join(" 2345-6789 ")
        assertNotNull(joined)
        assertEquals(codeToJoin, joined?.code)
        assertTrue(Together.saved.value.any { it.code == codeToJoin })

        // Joining an invalid code returns null
        assertNull(Together.join("SHORT"))
        assertNull(Together.join("TOOLONGCODE12345"))

        // Re-joining existing room returns existing without duplicates
        val reJoined = Together.join("2345-6789")
        assertEquals(joined, reJoined)
        assertEquals(1, Together.saved.value.count { it.code == codeToJoin })

        // Forgetting room cleans up saved and active state
        Together.startListening(codeToJoin)
        assertEquals(codeToJoin, Together.active.value)

        Together.forget(codeToJoin)
        assertFalse(Together.saved.value.any { it.code == codeToJoin })
        assertNull(Together.rooms.value[codeToJoin])
        assertNull(Together.active.value)
    }

    // ─── 5. Host Conflict Resolution ──────────────────────────────────────────────

    @Test
    fun testHostConflictResolutionLogic() = runBlocking {
        val code = "HOSTTEST"
        Together.create("Host Battle")
        val room = Together.rooms.value.values.first()

        // Initial claim by host A at t = 1000
        Together.handle(room.code, TogetherMessage.ClaimHost("host-A", 1000L))
        assertEquals("host-A", Together.rooms.value[room.code]?.hostId)
        assertEquals(1000L, Together.rooms.value[room.code]?.hostClaimedAt)

        // Host B claims at t = 1500 (newer -> wins)
        Together.handle(room.code, TogetherMessage.ClaimHost("host-B", 1500L))
        assertEquals("host-B", Together.rooms.value[room.code]?.hostId)
        assertEquals(1500L, Together.rooms.value[room.code]?.hostClaimedAt)

        // Host C claims at t = 1200 (older than current 1500 -> ignored)
        Together.handle(room.code, TogetherMessage.ClaimHost("host-C", 1200L))
        assertEquals("host-B", Together.rooms.value[room.code]?.hostId)

        // Host D claims at exact same millisecond 1500 with smaller id -> ignored
        Together.handle(room.code, TogetherMessage.ClaimHost("host-A", 1500L))
        assertEquals("host-B", Together.rooms.value[room.code]?.hostId)

        // Host Z claims at exact same millisecond 1500 with larger id ("host-Z" > "host-B") -> wins tie-breaker
        Together.handle(room.code, TogetherMessage.ClaimHost("host-Z", 1500L))
        assertEquals("host-Z", Together.rooms.value[room.code]?.hostId)
    }

    // ─── 6. Member Presence & Heartbeats (Hello / Bye) ─────────────────────────────

    @Test
    fun testMemberHelloAndBye() = runBlocking {
        val saved = Together.create("Presence Test")
        val code = saved.code

        // Member joins via Hello
        val t1 = System.currentTimeMillis()
        Together.handle(code, TogetherMessage.Hello("member-1", t1, "Dave", isListening = true))

        val roomAfterHello = Together.rooms.value[code]
        assertNotNull(roomAfterHello)
        val member = roomAfterHello?.members?.get("member-1")
        assertNotNull(member)
        assertEquals("Dave", member?.name)
        assertTrue(member?.isListening == true)

        // Member leaves via Bye
        Together.handle(code, TogetherMessage.Bye("member-1", t1 + 1000))
        val roomAfterBye = Together.rooms.value[code]
        assertFalse(roomAfterBye?.members?.get("member-1")?.isListening == true)

        // If host sends Bye, hostId is cleared
        Together.handle(code, TogetherMessage.ClaimHost("member-2", t1 + 2000))
        assertEquals("member-2", Together.rooms.value[code]?.hostId)
        Together.handle(code, TogetherMessage.Bye("member-2", t1 + 3000))
        assertNull(Together.rooms.value[code]?.hostId)
    }

    // ─── 7. Suggestion Workflow (Suggest / Accept / Decline) ───────────────────────

    @Test
    fun testSuggestionsWorkflow() = runBlocking {
        val saved = Together.create("Suggestion Room")
        val code = saved.code

        // Simulate host existing (host-1)
        Together.handle(code, TogetherMessage.ClaimHost("host-1", 1000L))

        // Non-host suggests track 1
        Together.handle(code, TogetherMessage.Suggest("user-9", 1010L, sampleTrack, "Gemma"))
        val room = Together.rooms.value[code]!!
        assertEquals(1, room.suggestions.size)
        assertEquals(sampleTrack.id, room.suggestions.first().track.id)
        assertEquals("Gemma", room.suggestions.first().byName)

        // Duplicate suggestion is ignored
        Together.handle(code, TogetherMessage.Suggest("user-10", 1020L, sampleTrack, "Dan"))
        assertEquals(1, Together.rooms.value[code]?.suggestions?.size)

        // Host accepts suggestion: track added to playlist, suggestion resolved
        val suggestion = room.suggestions.first()
        Together.acceptSuggestion(code, suggestion)

        val roomAfterAccept = Together.rooms.value[code]!!
        assertEquals(0, roomAfterAccept.suggestions.size)
        assertEquals(1, roomAfterAccept.playlist.size)
        assertEquals(sampleTrack.id, roomAfterAccept.playlist.first().id)

        // Suggest second track and decline it
        Together.handle(code, TogetherMessage.Suggest("user-9", 1030L, sampleTrack2, "Gemma"))
        assertEquals(1, Together.rooms.value[code]?.suggestions?.size)

        val suggestion2 = Together.rooms.value[code]!!.suggestions.first()
        Together.declineSuggestion(code, suggestion2)

        val roomAfterDecline = Together.rooms.value[code]!!
        assertEquals(0, roomAfterDecline.suggestions.size)
        // Playlist still has only sampleTrack
        assertEquals(1, roomAfterDecline.playlist.size)
        assertEquals(sampleTrack.id, roomAfterDecline.playlist.first().id)
    }

    // ─── 8. Clock Drift & Flight Time Compensation ────────────────────────────────

    @Test
    fun testFlightTimeDriftCompensation() {
        val host = "host-alpha"

        // First packet arrives with 200ms gap
        val flight1 = Together.flightTime(host, arrivedMinusSent = 200L)
        // Base delay should be TYPICAL_TRIP_MS (120ms)
        assertEquals(120L, flight1)

        // Faster packet arrives (150ms gap) -> becomes new floor
        val flight2 = Together.flightTime(host, arrivedMinusSent = 150L)
        assertEquals(120L, flight2)

        // Slower packet (350ms gap): 350 - 150 + 120 = 320ms
        val flight3 = Together.flightTime(host, arrivedMinusSent = 350L)
        assertEquals(320L, flight3)

        // Massive delay clamped to MAX_FLIGHT_MS (3000ms)
        val flight4 = Together.flightTime(host, arrivedMinusSent = 10_000L)
        assertEquals(3_000L, flight4)
    }

    // ─── 9. Player Integration & Remote Controls ──────────────────────────────────

    @Test
    fun testPlayerIntegrationAndControls() = runBlocking {
        val mockPlayer = MockTogetherPlayer()
        Together.start(mockPlayer)

        val saved = Together.create("Playback Sync Room")
        val code = saved.code

        // Host claims room
        Together.claimHost(code)
        assertTrue(Together.isHostOf(code))
        assertTrue(Together.mayChangeAutomix())

        // Non-host sends control pause
        Together.handle(code, TogetherMessage.Control("remote-user", 1000L, TogetherMessage.CONTROL_PAUSE, 30_000L))
        assertEquals(TogetherMessage.CONTROL_PAUSE, mockPlayer.lastControlAction)

        // Non-host sends play next
        Together.handle(code, TogetherMessage.PlayNext("remote-user", 1010L, sampleTrack2, "Friend"))
        assertEquals(sampleTrack2, mockPlayer.lastPlayNextTrack)

        // Switch to follower mode
        Together.resetForTesting()
        val mockPlayerFollower = MockTogetherPlayer()
        Together.start(mockPlayerFollower)
        Together.join(code)

        // Existing host claims room before this member starts listening
        val hostClaimTime = System.currentTimeMillis()
        Together.handle(code, TogetherMessage.ClaimHost("real-host", hostClaimTime))

        Together.startListening(code)
        assertFalse(Together.isHostOf(code))
        assertFalse(Together.mayChangeAutomix())

        // Host sends State
        val stateMsg = TogetherMessage.State(
            from = "real-host",
            sentAt = System.currentTimeMillis() - 50L,
            track = sampleTrack,
            positionMs = 60_000L,
            isPlaying = true,
            automix = false,
            queueVersion = 1,
        )
        Together.handle(code, stateMsg)

        // Follower player should follow host
        assertNotNull(mockPlayerFollower.lastFollowHostTrack)
        assertEquals(sampleTrack.id, mockPlayerFollower.lastFollowHostTrack?.id)
        assertTrue(mockPlayerFollower.lastFollowHostIsPlaying)
        assertEquals(true, mockPlayerFollower.lastFollowHostPositionMs != null && mockPlayerFollower.lastFollowHostPositionMs!! >= 60_000L)
    }

    // ─── 10. Complete Collaborative Session Simulation ────────────────────────────

    @Test
    fun testFullCollaborativeSessionBetweenTwoUsers() = runBlocking {
        val hostPlayer = MockTogetherPlayer()
        Together.start(hostPlayer)

        // 1. Host creates playlist "Roadtrip Vibes"
        val roomInfo = Together.create("Roadtrip Vibes")
        val code = roomInfo.code
        Together.claimHost(code)
        assertTrue(Together.isHostOf(code))

        // 2. Follower joins room and announces presence
        val tJoin = System.currentTimeMillis()
        Together.handle(code, TogetherMessage.Hello("follower-bob", tJoin, "Bob", isListening = true))

        val room = Together.rooms.value[code]!!
        assertEquals(1, room.listeners.size)
        assertEquals("Bob", room.listeners.first().name)

        // 3. Follower suggests Track 1
        val tSuggest = System.currentTimeMillis()
        Together.handle(code, TogetherMessage.Suggest("follower-bob", tSuggest, sampleTrack, "Bob"))
        assertEquals(1, Together.rooms.value[code]!!.suggestions.size)

        // 4. Host accepts suggestion
        val suggestion = Together.rooms.value[code]!!.suggestions.first()
        Together.acceptSuggestion(code, suggestion)
        assertEquals(0, Together.rooms.value[code]!!.suggestions.size)
        assertEquals(1, Together.rooms.value[code]!!.playlist.size)
        assertEquals(sampleTrack.id, Together.rooms.value[code]!!.playlist.first().id)

        // 5. Follower requests PlayNext
        Together.handle(code, TogetherMessage.PlayNext("follower-bob", System.currentTimeMillis(), sampleTrack2, "Bob"))
        assertEquals(sampleTrack2, hostPlayer.lastPlayNextTrack)

        // 6. Follower pauses playback
        Together.handle(code, TogetherMessage.Control("follower-bob", System.currentTimeMillis(), TogetherMessage.CONTROL_PAUSE, 45_000L))
        assertEquals(TogetherMessage.CONTROL_PAUSE, hostPlayer.lastControlAction)
        assertEquals(45_000L, hostPlayer.lastControlPositionMs)

        // 7. Follower seeks playback
        Together.handle(code, TogetherMessage.Control("follower-bob", System.currentTimeMillis(), TogetherMessage.CONTROL_SEEK, 90_000L))
        assertEquals(TogetherMessage.CONTROL_SEEK, hostPlayer.lastControlAction)
        assertEquals(90_000L, hostPlayer.lastControlPositionMs)

        // 8. Follower requests Next track
        Together.handle(code, TogetherMessage.Control("follower-bob", System.currentTimeMillis(), TogetherMessage.CONTROL_NEXT, 0L))
        assertEquals(TogetherMessage.CONTROL_NEXT, hostPlayer.lastControlAction)

        // 9. Host leaves, Follower takes over as new host
        val tClaim = System.currentTimeMillis() + 1000L
        Together.handle(code, TogetherMessage.ClaimHost("follower-bob", tClaim))
        assertEquals("follower-bob", Together.rooms.value[code]!!.hostId)
    }

    private class MockTogetherPlayer : TogetherPlayer {
        var lastControlAction: String? = null
        var lastControlPositionMs: Long? = null
        var lastPlayNextTrack: SharedTrack? = null
        var lastFollowHostTrack: SharedTrack? = null
        var lastFollowHostPositionMs: Long? = null
        var lastFollowHostIsPlaying: Boolean = false

        override fun sharedCurrent(): SharedTrack? = null
        override fun sharedPositionMs(): Long = 0L
        override fun sharedIsPlaying(): Boolean = false
        override fun sharedUpcoming(): List<SharedTrack> = emptyList()
        override fun sharedAutomix(): Boolean = false

        override fun followHost(track: SharedTrack, positionMs: Long, isPlaying: Boolean, upcoming: List<SharedTrack>?, automix: Boolean) {
            lastFollowHostTrack = track
            lastFollowHostPositionMs = positionMs
            lastFollowHostIsPlaying = isPlaying
        }

        override fun hostControl(action: String, positionMs: Long) {
            lastControlAction = action
            lastControlPositionMs = positionMs
        }

        override fun hostPlayNext(track: SharedTrack) {
            lastPlayNextTrack = track
        }

        override fun hostPlay(tracks: List<SharedTrack>, index: Int) {
            // no-op
        }
    }
}
