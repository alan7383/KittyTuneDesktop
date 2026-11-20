package com.alananasss.kittytune

import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.togetherDiscordParty
import com.my.kizzy.gateway.entities.presence.Activity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

        val payload = Json.encodeToString(Activity.serializer(), Activity("KittyTune", party = party))
        val partyJson = Json.parseToJsonElement(payload).jsonObject.getValue("party")
        assertEquals(Json.parseToJsonElement("""{"id":"${party.id}","size":[3,16]}"""), partyJson)
        val inactive = Json.encodeToString(Activity.serializer(), Activity("KittyTune"))
        assertFalse(Json.parseToJsonElement(inactive).jsonObject.containsKey("party"))
    }
}
