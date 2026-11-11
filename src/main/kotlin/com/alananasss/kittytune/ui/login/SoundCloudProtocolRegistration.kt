package com.alananasss.kittytune.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/** Capture the port from this composition, rather than reading mutable state inside the effect.
 * A null-key effect can otherwise register the newly created server before recomposition, then
 * immediately unregister it when Compose restarts the effect with the actual port as its key.
 */
@Composable
internal fun SoundCloudProtocolRegistration(
    port: Int?,
    prepare: suspend (Int) -> AutoCloseable,
    onReady: (AutoCloseable) -> Unit,
    onError: (Exception) -> Unit
) {
    LaunchedEffect(port) {
        if (port == null) return@LaunchedEffect
        var registration: AutoCloseable? = null
        try {
            registration = prepare(port)
            onReady(registration)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        } finally {
            registration?.close()
        }
    }
}
