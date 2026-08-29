package com.alananasss.kittytune.ui.main

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.library.SIDEBAR_COLLAPSED_WIDTH
import kotlin.math.roundToInt

/**
 * The geometry the sidebar keeps on both sides of a collapse, and the one gesture its labels leave by
 * (issue #33).
 *
 * ## What was wrong with collapsing before this
 *
 * "I think we need to improve the minimisation, because right now it looks as cheap as possible. […]
 * Right now, there's no animation — the text doesn't shrink, it's just removed, and the icons are moved
 * to the centre. […] I also noticed that the 'Favorites' folder and so on are positioned lower when
 * collapsed than when expanded."
 *
 * Three separate things, all of them true, and none of them fixed by a fade:
 *
 *  1. **The labels were deleted, not animated.** Every row was written twice, `if (compact) … else …`,
 *     so a label existed at full size in one frame and not at all in the next. A cross-dissolve between
 *     the two layouts hides the cut but does not remove it: what the eye follows is still one thing
 *     appearing where another disappeared.
 *  2. **The icons jumped.** The expanded row inset its icon by 16 dp; the collapsed row centred it in an
 *     80 dp rail, at 40 dp. Twelve dp sideways, in a single frame, at the same moment the label vanished.
 *  3. **The lists started at different heights.** The rail stacked three 48 dp icon buttons above its
 *     entries where the full panel had a header, a chip row and a search row — 168 dp against 138 dp — so
 *     everything below shifted down by about thirty as it closed.
 *
 * ## What replaces it
 *
 * One vertical line, at [ICON_INSET] + half an icon, which is the centre of the rail. Every icon that
 * survives a collapse — navigation, the library's own header, the artwork of every entry in list mode —
 * sits on that line in *both* states, so none of them has anywhere to travel. What changes is only what
 * is beside them, and that leaves by [pushedBack] rather than by being removed.
 *
 * The progress driving all of it is read from the panel's real width ([progressFor]) rather than from a
 * timer of its own. The width is on a spring in `MainScreen`; anything animated to its own schedule is
 * either finished while the edge is still moving or still going after it has stopped, which is the
 * "abrupt" half of the same report. Measured against the width instead, the two cannot disagree at any
 * point along the way — including the one place a timer could never have followed, which is the handover
 * from the full layout to the rail ([RAIL_SWAP_AT]).
 */
internal object SidebarMorph {

    /** The width the panel collapses to. */
    val RAIL_WIDTH: Dp = SIDEBAR_COLLAPSED_WIDTH.dp

    /** Material's icon size, which is what the inset below is centred around. */
    val ICON_SIZE: Dp = 24.dp

    /**
     * Leading inset for a row's icon, chosen so the icon's centre falls exactly on the middle of
     * [RAIL_WIDTH].
     *
     * This is the whole of "make sure that all icons are in the same place when collapsed and expanded":
     * a rail centres its contents, so the only inset an expanded row can use without moving the icon is
     * the one that agrees with that centre.
     */
    val ICON_INSET: Dp = (RAIL_WIDTH - ICON_SIZE) / 2

    /** Gap between an icon and the label that is going to leave without it. */
    val LABEL_GAP: Dp = 12.dp

    /**
     * Stand-in for the height of the library's header, chip row and search row together, used only until
     * the real one has been measured.
     *
     * The rail matches the block it replaces by measuring it, which is the only way to be right at every
     * font size and density. That measurement needs the full layout to have existed at least once, and it
     * has not when the app is launched with the panel already collapsed — so these two are what the first
     * frame after such a launch uses, and nothing else. Deliberately a little generous: too tall leaves a
     * few dp of space above the entries for one frame, too short would clip the actions inside.
     */
    val FALLBACK_LEADING_BLOCK: Dp = 146.dp

    /** Stand-in for the header row alone. See [FALLBACK_LEADING_BLOCK]. */
    val FALLBACK_HEADER: Dp = 54.dp

    /**
     * How far into a collapse the panel is, from its width alone.
     *
     * @param expanded the width the panel returns to, i.e. the stored sidebar width.
     * @param actual the width it is being laid out at this frame, part-way along the spring.
     * @return 0 while fully open, 1 once it is a rail.
     */
    fun progressFor(expanded: Dp, actual: Dp): Float {
        val travel = expanded - RAIL_WIDTH
        if (travel <= 0.dp) return if (actual <= RAIL_WIDTH) 1f else 0f
        return ((expanded - actual) / travel).coerceIn(0f, 1f)
    }

    /**
     * How far into the travel the library card hands over from the full layout to the rail.
     *
     * There is one thing the rail holds that no amount of receding can produce from the full layout:
     * the create and history buttons, which live in a header row when the panel is open and in a column
     * of their own once it is not. So one swap survives — but it happens at the *end* of the travel
     * rather than at the start of it, which is the whole difference.
     *
     * It used to key off [LibraryViewModel.isSidebarCollapsed], and that flag flips the instant the
     * button is pressed: the rail began fading in over a panel still at full width with all of its text
     * still on screen, and the two pictures were nothing like each other. Keyed off the width instead,
     * the handover happens when the panel is nine tenths shut — by which point the labels have finished
     * receding and both layouts are the same column of icons in the same places, so there is nothing
     * left for a fade to give away.
     */
    const val RAIL_SWAP_AT = 0.9f

    /** Long enough to carry the rail's own buttons in, short enough to finish with the width. */
    const val RAIL_SWAP_MS = 180

    /**
     * Where the fade of a receding element finishes, as a fraction of the travel.
     *
     * Before the handover at [RAIL_SWAP_AT], with room to spare. Anything still visible when the
     * layouts change hands is something the eye can catch being cut.
     */
    const val FADE_DONE_AT = 0.6f
}

/**
 * Takes a label away by pushing it back behind its icon, instead of deleting it.
 *
 * "I think that when minimising, the text should be minimised in a way similar to how it works in search
 * when you scale down the window — so that the text seems to be pushed back." (issue #33)
 *
 * Three things at once, all keyed to the same [progress]:
 *
 *  - it **gives up its width**, linearly, so the row closes up around it rather than the row's contents
 *    being re-laid-out in one step;
 *  - it **shrinks towards its leading edge** and slides a little that way, which is the receding part —
 *    the origin is the icon's side, so the text reads as going behind the icon rather than shrinking
 *    towards its own middle;
 *  - it **fades**, and finishes fading at about six tenths of the way, so the last stretch of the panel's
 *    travel is not spent dragging a barely-visible label along.
 *
 * The width it gives up is reported without changing what is drawn, so nothing inside is ever measured
 * narrower than it was laid out for. That matters more than it sounds: measured against the shrinking
 * width instead, a label re-wraps on every frame of the animation, and "Explorer" ends up one letter per
 * line on the way out. Anything using this should also be a single non-wrapping line, so that a *steady*
 * narrow panel cannot wrap it either.
 *
 * @param progress 0 leaves the label exactly as it was; 1 is fully away.
 * @param unbounded whether to ignore the width the parent offers while measuring. True for a label whose
 *   parent is itself narrowing — a navigation row follows the panel's edge, and measured against that
 *   edge the label truncates a letter at a time on the way out. False where the parent's width is fixed
 *   for the whole travel, as a library row's is: there the offered width is already the right one, and
 *   asking for an unbounded measurement instead would let a long title report a width wider than the row
 *   it sits in.
 */
fun Modifier.pushedBack(progress: Float, unbounded: Boolean = true): Modifier {
    if (progress <= 0f) return this
    return this
        .layout { measurable, constraints ->
            // Measured with no width limit at all, so the label keeps every letter it had while it
            // recedes. Measured against the shrinking width instead, it would ellipsise its way out —
            // "Explorer", "Explore…", "Exp…", "E…" — which is a label being truncated, not one being
            // pushed back, and it is the same re-measuring that used to stack it one letter per line.
            // Only while it is actually leaving: at rest the row is laid out normally, so a label too
            // long for a narrow sidebar still ellipsises the way it always did.
            val placeable = measurable.measure(
                if (unbounded) constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
                else constraints.copy(minWidth = 0)
            )
            val width = (placeable.width * (1f - progress)).roundToInt().coerceAtLeast(0)
            layout(width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        .graphicsLayer {
            alpha = fadeOut(progress)
            val shrink = 1f - 0.4f * progress
            scaleX = shrink
            scaleY = shrink
            translationX = -size.width * 0.15f * progress
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
}

/**
 * Sends a whole block back without letting it give up any of its height.
 *
 * For the two rows the rail has no counterpart for — the filter chips and the search field. A label can
 * hand its width back because the row closes up around it; these two are the full width of the panel and
 * there is nothing beside them to close up, so all that is left is to recede. Which is the point: they
 * are what the rail's create and history buttons stand in the place of, and if they gave up their height
 * on the way out, the block would shrink, the height the rail is pinned to would change underneath it,
 * and every entry below would move — the exact complaint this is all in service of.
 *
 * Drawing only, therefore: [graphicsLayer] scales and fades without touching the layout, so the block
 * measures the same on the last frame of the collapse as on the first.
 *
 * The pointer block is not decoration. A search field at zero alpha is still a search field: without it,
 * a click anywhere in the top of a half-shut panel lands in a text field nobody can see, and the caret
 * starts blinking in an empty rectangle. Applied a little after the fade begins, so a click during the
 * first few frames — when the field is still legible and may well have been aimed at — still arrives.
 *
 * @param progress 0 leaves the block alone; 1 is fully away.
 */
fun Modifier.receded(progress: Float): Modifier {
    if (progress <= 0f) return this
    return this
        .graphicsLayer {
            alpha = fadeOut(progress)
            val shrink = 1f - 0.12f * progress
            scaleX = shrink
            scaleY = shrink
            // The same origin the icons stand on, so this recedes towards the column that stays rather
            // than towards the middle of a panel that is on its way out.
            val iconLinePx = SidebarMorph.RAIL_WIDTH.toPx() / 2f
            transformOrigin = TransformOrigin(
                if (size.width > 0f) (iconLinePx / size.width).coerceIn(0f, 1f) else 0f,
                0.5f,
            )
        }
        .then(
            if (progress > POINTERS_OFF_AT) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            } else Modifier
        )
}

/** One fade curve for everything that leaves, so nothing is still visible when the layouts swap. */
private fun fadeOut(progress: Float): Float =
    (1f - progress / SidebarMorph.FADE_DONE_AT).coerceIn(0f, 1f)

/** Far enough in that the block is nearly invisible, early enough that it cannot be clicked blind. */
private const val POINTERS_OFF_AT = 0.35f
