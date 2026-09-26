package com.alananasss.kittytune.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

/**
 * Audio files the OS asked the app to open — "Open with KittyTune", a double-click on an associated
 * file, or a second launch forwarding its arguments to the running instance.
 *
 * A buffered channel rather than a shared flow, because the first batch arrives from `main()` before
 * anything on screen is listening, and it must still be played once something is.
 */
object OpenFileRequests {
    private val channel = Channel<List<File>>(Channel.UNLIMITED)

    val requests: Flow<List<File>> = channel.receiveAsFlow()

    fun submit(paths: List<String>) {
        val files = paths.map(::File).filter { it.isFile }
        if (files.isNotEmpty()) channel.trySend(files)
    }
}
