package com.alananasss.kittytune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.alananasss.kittytune.data.MusicManager
import kotlinx.coroutines.delay

/** Refreshes while these controls are visible, including after an engine swap or crossfade. */
@Composable
internal fun protectedPlaybackInUse(): Boolean {
    val active by produceState(MusicManager.player.activeEngine.usesProtectedPlayback) {
        while (true) {
            value = MusicManager.player.activeEngine.usesProtectedPlayback
            delay(250)
        }
    }
    return active
}
