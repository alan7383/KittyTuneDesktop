package com.alananasss.kittytune.data.sync

/** Event-driven route state. Losing a route invalidates sockets before the new route is ready. */
internal class ConnectNetworkState<N : Any> {
    data class Capabilities(val transports: Int, val lan: Boolean, val internet: Boolean)
    data class Route<N>(val network: N, val capabilities: Capabilities, val properties: String)

    private var current: N? = null
    private var capabilities: Capabilities? = null
    private var properties: String? = null
    private var blocked = false
    @Volatile var route: Route<N>? = null
        private set

    @Synchronized fun available(network: N): Boolean {
        if (network == current) return false
        current = network
        capabilities = null
        properties = null
        blocked = false
        return update()
    }

    @Synchronized fun capabilities(network: N, value: Capabilities): Boolean {
        if (network != current) return false
        capabilities = value
        return update()
    }

    @Synchronized fun properties(network: N, value: String): Boolean {
        if (network != current) return false
        properties = value
        return update()
    }

    @Synchronized fun blocked(network: N, value: Boolean): Boolean {
        if (network != current) return false
        blocked = value
        return update()
    }

    @Synchronized fun lost(network: N): Boolean {
        if (network != current) return false
        return clear()
    }

    @Synchronized fun clear(): Boolean {
        current = null
        capabilities = null
        properties = null
        return update()
    }

    private fun update(): Boolean {
        val network = current
        val caps = capabilities
        val links = properties
        val next = if (network != null && caps != null && links != null && !blocked && (caps.lan || caps.internet))
            Route(network, caps, links) else null
        if (next == route) return false
        route = next
        return true
    }
}
