package com.alananasss.kittytune.ui.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.domain.Track

/**
 * "Like all songs" on an album asks first: a record often ends with a minute of silence, a thirty-second voice note or
 * a twenty-minute drone, and none of those are songs anybody means to like. The prompt is shared, so both places that
 * offer the action get the same question from a single host at the top of the window.
 */
object LikeAllPrompt {
    /** Shortest and longest a track can be and still be kept when the reader skips the odd ones. */
    private const val MIN_MS = 60_000L
    private const val MAX_MS = 20 * 60_000L

    var pending by mutableStateOf<List<Track>?>(null)
        private set

    fun ask(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        pending = tracks
    }

    internal fun dismiss() {
        pending = null
    }

    /** The tracks a reader who skips the odd ones would like: anything with a known length between the two bounds. */
    internal fun sensible(tracks: List<Track>): List<Track> = tracks.filter { track ->
        val ms = track.durationMs ?: return@filter true
        ms in MIN_MS..MAX_MS
    }

    /** Likes [tracks] and says how many went in. */
    internal fun like(tracks: List<Track>): Int {
        val liked = LikeRepository.addLikesBulk(tracks)
        com.alananasss.kittytune.core.Toaster.show(
            if (liked > 0) str("toast_like_all_done", liked) else str("toast_like_all_nothing")
        )
        return liked
    }
}

/** Put once at the top of the window; shows the prompt whenever [LikeAllPrompt.ask] has been called. */
@Composable
fun LikeAllPromptHost() {
    val tracks = LikeAllPrompt.pending ?: return
    val sensible = LikeAllPrompt.sensible(tracks)
    AlertDialog(
        onDismissRequest = { LikeAllPrompt.dismiss() },
        title = { Text(str("like_all_prompt_title")) },
        text = { Text(str("like_all_prompt_text", tracks.size, tracks.size - sensible.size)) },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    LikeAllPrompt.dismiss()
                    LikeAllPrompt.like(sensible)
                },
                shapes = ButtonDefaults.shapes(),
                enabled = sensible.isNotEmpty() && sensible.size < tracks.size,
            ) { Text(str("like_all_prompt_skip_odd")) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    LikeAllPrompt.dismiss()
                    LikeAllPrompt.like(tracks)
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(str("like_all_prompt_all")) }
        },
    )
}
