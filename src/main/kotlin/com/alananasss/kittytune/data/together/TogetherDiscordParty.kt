package com.alananasss.kittytune.data.together

private const val DISCORD_ADVERTISED_PARTY_SIZE = 16

internal data class DiscordActivityParty(val id: String, val size: List<Int>)

internal fun togetherDiscordParty(code: String, room: Together.Room?, localMemberId: String): DiscordActivityParty {
    val participants = mutableSetOf(localMemberId)
    room?.listeners?.mapTo(participants) { it.id }
    if (room?.hasLiveHost == true) room.hostId?.let(participants::add)
    val size = participants.size
    return DiscordActivityParty(
        id = TogetherWire.topicFor(code).substringAfterLast('/'),
        size = listOf(size, maxOf(size, DISCORD_ADVERTISED_PARTY_SIZE)),
    )
}
