package com.alananasss.kittytune.data.together

import com.my.kizzy.gateway.entities.presence.ActivityParty

private const val DISCORD_ADVERTISED_PARTY_SIZE = 16

internal fun togetherDiscordParty(code: String, room: Together.Room?, localMemberId: String): ActivityParty {
    val participants = mutableSetOf(localMemberId)
    room?.listeners?.mapTo(participants) { it.id }
    if (room?.hasLiveHost == true) room.hostId?.let(participants::add)
    val size = participants.size
    return ActivityParty(
        id = TogetherWire.topicFor(code).substringAfterLast('/'),
        size = listOf(size, maxOf(size, DISCORD_ADVERTISED_PARTY_SIZE)),
    )
}
