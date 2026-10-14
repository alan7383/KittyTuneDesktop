package com.alananasss.kittytune.data.sync

import kotlin.test.*

class ConnectNetworkStateTest {
    private val wifi = ConnectNetworkState.Capabilities(2, lan = true, internet = true)
    private val cell = ConnectNetworkState.Capabilities(1, lan = false, internet = true)
    private fun ready(state: ConnectNetworkState<String>, network: String, caps: ConnectNetworkState.Capabilities) {
        state.available(network)
        state.capabilities(network, caps)
        assertTrue(state.properties(network, "interface-$network"))
    }

    @Test fun wifiToCellularInvalidatesLanBeforeNewRouteIsReady() {
        val state = ConnectNetworkState<String>()
        ready(state, "wifi", wifi)
        assertTrue(state.available("cell"))
        assertNull(state.route, "Old LAN sockets must close immediately, without waiting for a TCP timeout")
        assertFalse(state.capabilities("cell", cell))
        assertTrue(state.properties("cell", "rmnet"))
        assertEquals("cell", state.route?.network)
        assertFalse(state.route!!.capabilities.lan)
        assertFalse(state.lost("wifi"), "A late Wi-Fi loss must not tear down the new cellular session")
        assertFalse(state.capabilities("wifi", wifi))
    }

    @Test fun vpnTransportChangeIsObservedEvenWhenNetworkIdentityDoesNotChange() {
        val state = ConnectNetworkState<String>()
        ready(state, "vpn", wifi.copy(transports = 18))
        assertTrue(state.capabilities("vpn", cell.copy(transports = 17)))
        assertFalse(state.route!!.capabilities.lan)
        assertFalse(state.capabilities("vpn", cell.copy(transports = 17)), "Repeated equivalent events do not reconnect")
        assertTrue(state.properties("vpn", "new-dns-and-route"))
    }

    @Test fun cellWithoutInternetWaitsForValidationAndBlockedNetworkCloses() {
        val state = ConnectNetworkState<String>()
        state.available("cell")
        state.capabilities("cell", cell.copy(internet = false))
        assertFalse(state.properties("cell", "rmnet"))
        assertNull(state.route)
        assertTrue(state.capabilities("cell", cell))
        assertTrue(state.blocked("cell", true))
        assertNull(state.route)
        assertTrue(state.blocked("cell", false))
        assertTrue(state.lost("cell"))
        assertNull(state.route)
    }

    @Test fun localWifiWorksWithoutValidatedInternet() {
        val state = ConnectNetworkState<String>()
        ready(state, "wifi", wifi.copy(internet = false))
        assertTrue(state.route!!.capabilities.lan)
        assertFalse(state.available("wifi"), "Initial callback must not invalidate an unchanged seeded route")
    }

    @Test fun desktopKeepsRelayStandbyButOnlyAuthenticatedPeerArrivalPromotesIt() {
        assertTrue(ConnectTransportPolicy.relayStandby(false, "LAN"))
        assertFalse(ConnectTransportPolicy.relayStandby(true, "LAN"))
        assertFalse(ConnectTransportPolicy.relayStandby(false, "Internet"))
        val hello = ConnectMessage(kind = "hello", sender = "phone", session = "session", sequence = 1, challenge = "a".repeat(32))
        assertTrue(ConnectTransportPolicy.authenticatedArrival(hello, "phone"))
        assertFalse(ConnectTransportPolicy.authenticatedArrival(null, "phone"))
        assertFalse(ConnectTransportPolicy.authenticatedArrival(hello.copy(kind = "state"), "phone"))
        assertFalse(ConnectTransportPolicy.authenticatedArrival(hello.copy(sender = "other"), "phone"))
        assertFalse(ConnectTransportPolicy.authenticatedArrival(hello.copy(challenge = ""), "phone"))
    }
}
