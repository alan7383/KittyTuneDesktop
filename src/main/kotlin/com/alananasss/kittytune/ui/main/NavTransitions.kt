package com.alananasss.kittytune.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Screen-to-screen motion for the main panel, following Material 3's transition patterns.
 *
 * The NavHost used navigation-compose's default — a 700 ms cross-fade — for everything, which reads as
 * the app hesitating rather than moving. Two patterns replace it:
 *
 *  - **Fade through** between the sidebar's own destinations. They are siblings with no spatial
 *    relationship, so nothing slides: the old screen fades out quickly and the new one fades in with a
 *    slight zoom.
 *  - **Shared axis X** everywhere else. Going deeper (a playlist, a profile, a settings page) moves
 *    forward from the right; going back moves the other way, so the direction tells you which it was.
 */
private val TOP_LEVEL_ROUTES = setOf("home", "feed", "genres", "recognition", "sync_settings")

private const val DURATION_MS = 300
private const val OUTGOING_MS = 90
/** How far a shared-axis screen travels; callers convert it with the current density. */
internal val NavSlideDistance = androidx.compose.ui.unit.Dp(30f)

/** Material's "emphasized decelerate": arrives quickly and settles. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isBetweenTopLevel(): Boolean =
    initialState.destination.route in TOP_LEVEL_ROUTES && targetState.destination.route in TOP_LEVEL_ROUTES

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(DURATION_MS - OUTGOING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(DURATION_MS - OUTGOING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing), initialScale = 0.96f)

private fun fadeThroughOut(): ExitTransition =
    fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(slidePx: Int): EnterTransition =
    if (isBetweenTopLevel()) fadeThroughIn()
    else sharedAxisIn(forward = true, slidePx)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(slidePx: Int): ExitTransition =
    if (isBetweenTopLevel()) fadeThroughOut()
    else sharedAxisOut(forward = true, slidePx)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnter(slidePx: Int): EnterTransition =
    if (isBetweenTopLevel()) fadeThroughIn()
    else sharedAxisIn(forward = false, slidePx)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExit(slidePx: Int): ExitTransition =
    if (isBetweenTopLevel()) fadeThroughOut()
    else sharedAxisOut(forward = false, slidePx)

private fun sharedAxisIn(forward: Boolean, distance: Int): EnterTransition {
    return slideInHorizontally(tween(DURATION_MS, easing = EmphasizedDecelerate)) { if (forward) distance else -distance } +
        fadeIn(tween(DURATION_MS - OUTGOING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing))
}

private fun sharedAxisOut(forward: Boolean, distance: Int): ExitTransition {
    // Gone as soon as it is invisible. The slide used to run the full length after the fade had finished,
    // which kept the old screen composed and drawn for 300 ms — two whole screens per frame, which on an
    // integrated GPU is what made every transition stutter.
    return slideOutHorizontally(tween(OUTGOING_MS, easing = FastOutLinearInEasing)) { if (forward) -distance / 3 else distance / 3 } +
        fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))
}
