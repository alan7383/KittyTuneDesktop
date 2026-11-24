package com.alananasss.kittytune

import com.alananasss.kittytune.data.discord.discordActivity
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.togetherDiscordParty
import com.alananasss.kittytune.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TogetherDiscordPartyTest {
    @Test
    fun stablePartyCountsActiveListenersAndEncodesDiscordSize() {
        val code = "K7M9P3Q4"
        val now = System.currentTimeMillis()
        val room = Together.Room(
            code = code,
            name = "Live",
            hostId = "host",
            hostSeenAt = now,
            members = listOf(
                Together.Member("self", "Self", true, now),
                Together.Member("friend", "Friend", true, now),
                Together.Member("left", "Left", false, now),
                Together.Member("expired", "Expired", true, now - 60_000),
            ).associateBy { it.id },
        )
        val party = togetherDiscordParty(code, room, "self")
        assertEquals(listOf(3, 16), party.size)
        assertEquals(party.id, togetherDiscordParty("K7M9-P3Q4", room, "host").id)
        assertNotEquals(party.id, togetherDiscordParty("W4X8Y2Z6", null, "self").id)
        assertEquals(listOf(1, 16), togetherDiscordParty(code, null, "self").size)

        val track = Track(1, "Song", null, 185_000, null)
        val payload = discordActivity(
            track, null, true, 0, 185_000, DiscordStatusDisplay.ACTIVITY, TogetherLinks.inviteUrl(code), party,
        )
        val partyJson = payload.getJSONObject("party")
        assertEquals(party.id, partyJson.getString("id"))
        assertEquals(listOf(3, 16), partyJson.getJSONArray("size").toList())
        val inactive = discordActivity(track, null, true, 0, 185_000, DiscordStatusDisplay.ACTIVITY)
        assertFalse(inactive.has("party"))
    }
}
