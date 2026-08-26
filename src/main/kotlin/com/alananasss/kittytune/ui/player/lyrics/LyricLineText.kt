package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import com.alananasss.kittytune.data.local.LyricsDisplayStyle
import kotlinx.coroutines.isActive

/**
 * How one line of lyrics looks, in one place (issue #33).
 *
 * The full screen and the right-hand panel drew their lines independently, and they had drifted into
 * disagreeing about what the *same named setting* does. Under "scale" the full screen shrank every other
 * line by a flat 14% while the panel grew the current one by 18%; under "focus" the full screen blurred and
 * the panel did not; and word-by-word karaoke existed only on the full screen, so anyone who preferred the
 * panel never saw the feature they had switched on.
 *
 * Two things live here as a result: the *decision* about how a line at a given distance from the current one
 * should look ([LyricLineStyling]), and the *drawing* of the line itself ([LyricLineText]). The two views
 * still own their own typography and layout — a headline on a full screen is not a title in a side panel —
 * but they can no longer disagree about the things a user chose.
 */
internal data class LyricLineTreatment(
    val scale: Float,
    val alpha: Float,
    val blur: Dp,
)

/**
 * The visual treatment of a line, by how far it is from the one being sung.
 *
 * Graduated rather than on/off. The request that prompted this arrived as a sketch of five stacked lines
 * labelled "more lower / lower / standard / lower / more lower": size falls away with distance in both
 * directions, so the current line reads as the near one rather than merely the bold one. A single step —
 * which is what was implemented — gives none of that; it just makes the other lines smaller.
 */
internal object LyricLineStyling {

    /** One line either side of the current one. */
    const val SCALE_NEAR = 0.90f

    /** Two lines away. */
    const val SCALE_MID = 0.82f

    /** Three or more. It stops falling here: past this, lines are decoration and shrinking them further
     *  only costs legibility. */
    const val SCALE_FAR = 0.78f

    /** How far back "focus" pushes everything that is not the current line. */
    const val FOCUS_ALPHA = 0.18f

    /** Already sung, in the two styles that do not override it. Dimmer than what is coming. */
    const val PAST_ALPHA = 0.45f

    /** Still to come — the half worth reading, so it stays brighter than the half already gone. */
    const val UPCOMING_ALPHA = 0.70f

    /**
     * @param distance `index - activeIndex`; zero is the line being sung, negative is already past.
     * @param focusBlur how much to blur the rest under [LyricsDisplayStyle.FOCUS]. A parameter because it
     *   is the one part of the treatment that cannot be shared blindly: the radius that reads as soft focus
     *   behind a 42 sp headline erases a 16 sp line in a panel.
     */
    fun treatmentFor(
        style: LyricsDisplayStyle,
        distance: Int,
        focusBlur: Dp = 2.dp,
    ): LyricLineTreatment {
        val isActive = distance == 0
        val scale = when {
            isActive || style != LyricsDisplayStyle.SCALE -> 1f
            else -> when (kotlin.math.abs(distance)) {
                1 -> SCALE_NEAR
                2 -> SCALE_MID
                else -> SCALE_FAR
            }
        }
        val alpha = when {
            isActive -> 1f
            style == LyricsDisplayStyle.FOCUS -> FOCUS_ALPHA
            distance < 0 -> PAST_ALPHA
            else -> UPCOMING_ALPHA
        }
        val blur = if (!isActive && style == LyricsDisplayStyle.FOCUS) focusBlur else 0.dp
        return LyricLineTreatment(scale = scale, alpha = alpha, blur = blur)
    }
}

/**
 * The words of one line, drawn the way the reader asked for.
 *
 * Three renderings, in order of how much the source gives us:
 *
 * 1. **Word timings and the fill effect** — the line is drawn twice, dim underneath and bright on top, and
 *    the bright copy is clipped to a path that grows through the characters. That is what makes a word fill
 *    letter by letter instead of snapping on.
 * 2. **Word timings alone** — one pass, each word coloured by whether it has been reached.
 * 3. **Neither** — the plain line.
 *
 * The panel used to have only the third, whatever the setting said. It now shares all three with the full
 * screen, which is the only way "highlight the lyrics word by word" can mean the same thing in both.
 *
 * @param positionMs the playback position the drawing should reflect, already adjusted by the user's offset.
 *   A float because the position is interpolated between progress ticks to keep the fill smooth.
 * @param inactiveColor a line that is not the current one.
 * @param unsungColor the part of the *current* line that has not been reached yet. A separate colour because
 *   it is a separate thing: text waiting its turn inside the line being sung, not a line out of focus.
 * @param textModifier applied to the text itself, for decoration that has to draw over the glyphs — the
 *   full screen's hover rule. Kept separate from [modifier] because that one lays the line out.
 */
@Composable
internal fun LyricLineText(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Float,
    wordSync: Boolean,
    fillEffect: Boolean,
    activeStyle: TextStyle,
    inactiveStyle: TextStyle,
    activeColor: Color,
    inactiveColor: Color,
    unsungColor: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val words = if (wordSync && isActive) line.words else emptyList()

    if (words.isEmpty()) {
        Text(
            text = line.text,
            style = if (isActive) activeStyle else inactiveStyle,
            color = if (isActive) activeColor else inactiveColor,
            textAlign = textAlign,
            modifier = modifier.fillMaxWidth().then(textModifier),
            onTextLayout = onTextLayout,
        )
        return
    }

    // Rebuilt from the words rather than using line.text: the timings index into this string, and the two
    // can differ by a space.
    val text = remember(words) { words.joinToString("") { it.text } }

    if (!fillEffect) {
        val coloured = buildAnnotatedString {
            for (word in words) {
                val reached = positionMs >= word.startTime
                withStyle(SpanStyle(color = if (reached) activeColor else unsungColor)) {
                    append(word.text)
                }
            }
        }
        Text(
            text = coloured,
            style = activeStyle,
            textAlign = textAlign,
            modifier = modifier.fillMaxWidth().then(textModifier),
            onTextLayout = onTextLayout,
        )
        return
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val ranges = remember(words) {
        var start = 0
        words.map { word ->
            val range = start to start + word.text.length
            start += word.text.length
            range
        }
    }

    Box(modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = activeStyle,
            color = unsungColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().then(textModifier),
            onTextLayout = {
                layout = it
                onTextLayout(it)
            },
        )
        Text(
            text = text,
            style = activeStyle,
            color = activeColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().drawWithContent {
                val result = layout ?: return@drawWithContent
                // Clamped to this line's own end so the fill can only ever complete, never overrun and
                // then snap back when the next line takes over.
                val position =
                    if (line.endTime > line.startTime) positionMs.coerceAtMost(line.endTime.toFloat())
                    else positionMs
                clipPath(sungPath(result, words, ranges, text.length, position)) {
                    this@drawWithContent.drawContent()
                }
            },
        )
    }
}

/**
 * The region of the line that has been sung by [positionMs].
 *
 * Whole characters for words already finished, and a fraction of one character for the word in progress —
 * which is what separates a smooth fill from a word-by-word jump.
 */
private fun sungPath(
    layout: TextLayoutResult,
    words: List<LyricWord>,
    ranges: List<Pair<Int, Int>>,
    textLength: Int,
    positionMs: Float,
): Path {
    val path = Path()
    val lastIndex = (textLength - 1).coerceAtLeast(0)

    for (i in words.indices) {
        val word = words[i]
        val (from, to) = ranges[i]
        if (from >= to) continue

        if (positionMs >= word.endTime) {
            for (c in from until to) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))
            continue
        }
        if (positionMs < word.startTime) continue

        val span = (word.endTime - word.startTime).coerceAtLeast(1L)
        val progress = ((positionMs - word.startTime) / span).coerceIn(0f, 1f)
        val exact = progress * (to - from)
        val whole = exact.toInt()
        for (c in from until from + whole) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))

        val partial = from + whole
        if (partial < to) {
            val box = layout.getBoundingBox(partial.coerceIn(0, lastIndex))
            val edge = box.left + (box.right - box.left) * (exact - whole)
            path.addRect(Rect(box.left, box.top, edge, box.bottom))
        }
    }
    return path
}

/**
 * The playback position, interpolated between the player's reports (issue #33).
 *
 * The player reports roughly four times a second. A fill driven straight off that advances in visible steps,
 * so this runs a per-frame estimate from the last report — scaled by the playback speed, because at 2× the
 * words arrive twice as fast.
 *
 * Bounded by [MAX_EXTRAPOLATION_MS] on purpose. The player stops reporting while it buffers, seeks or is
 * scrubbed, and an unbounded estimate then ran the highlight off to the end of the line and snapped it back
 * when the real position finally arrived — reported as "one line goes straight to the end and jumps around".
 *
 * Shared so the panel's fill is as smooth as the full screen's rather than stepping four times a second.
 */
@Composable
internal fun rememberSmoothPosition(
    positionMs: Long,
    isPlaying: Boolean,
    speed: Float,
): Float {
    var smooth by remember { mutableFloatStateOf(positionMs.toFloat()) }
    LaunchedEffect(positionMs, isPlaying, speed) {
        if (!isPlaying) {
            smooth = positionMs.toFloat()
            return@LaunchedEffect
        }
        val startedAtMs = System.currentTimeMillis()
        val startedAt = positionMs.toFloat()
        while (isActive) {
            withFrameMillis {
                val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtMost(MAX_EXTRAPOLATION_MS)
                smooth = startedAt + (elapsed * speed)
            }
        }
    }
    return smooth
}

/** One report interval plus slack. Past this the estimate is guessing, not interpolating. */
private const val MAX_EXTRAPOLATION_MS = 400L
