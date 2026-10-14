package com.alananasss.kittytune.data.sync

/** The PC stays reachable on its relay while the phone uses LAN; the phone needs just one socket. */
internal object ConnectTransportPolicy {
    fun relayStandby(mobile: Boolean, currentTransport: String?) = !mobile && currentTransport == "LAN"
    fun authenticatedArrival(message: ConnectMessage?, peerId: String): Boolean =
        message?.kind == "hello" && message.sender == peerId && message.challenge.length in 16..64
}
