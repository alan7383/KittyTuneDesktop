package com.alananasss.kittytune.core

import com.alananasss.kittytune.data.together.TogetherLinks
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class TogetherUriRequest(
    val code: String,
    val autoJoin: Boolean,
)

/**
 * Buffered channel of `kittytune://together/...` (or `?together=`) deep-link requests arriving
 * either as startup CLI arguments, via [SingleInstance] loopback forwarding from a second launch
 * triggered by a browser's "Open KittyTune?" prompt, or via macOS AppleEvents (`setOpenURIHandler`).
 */
object TogetherUriRequests {
    private val channel = Channel<TogetherUriRequest>(Channel.UNLIMITED)

    val requests: Flow<TogetherUriRequest> = channel.receiveAsFlow()

    fun submitUri(rawUri: String?): Boolean {
        val code = TogetherLinks.extractCodeFromUri(rawUri) ?: return false
        val autoJoin = TogetherLinks.isAutoJoinUri(rawUri)
        channel.trySend(TogetherUriRequest(code = code, autoJoin = autoJoin))
        return true
    }

    fun dispatchIncomingArgs(args: List<String>) {
        if (args.isEmpty()) return
        val filePaths = ArrayList<String>(args.size)
        for (arg in args) {
            val trimmed = arg.trim().removeSurrounding("\"")
            if (trimmed.isEmpty()) continue
            if (submitUri(trimmed) || TogetherLinks.isCustomSchemeUri(trimmed)) {
                continue
            }
            filePaths.add(trimmed)
        }
        if (filePaths.isNotEmpty()) {
            OpenFileRequests.submit(filePaths)
        }
    }
}
