package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Desktop counterpart of the Android haptic helpers, so screens ported from Android keep their call sites.
 * A desktop has no vibration motor: every kind is accepted and does nothing.
 */
enum class HapticKind { MICRO, LIGHT, UI, MEDIUM, HEAVY, STRONG, CONFIRM, REJECT, SLIDER, THRESHOLD, DOUBLE }

@Stable
class AppHaptics internal constructor() {
    @Suppress("UNUSED_PARAMETER")
    fun perform(kind: HapticKind) = Unit
}

@Composable
fun rememberAppHaptics(): AppHaptics = remember { AppHaptics() }

/** A plain click on desktop; the [kind] documents the feel the Android build gives it. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.hapticClickable(kind: HapticKind = HapticKind.UI, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, onClick = onClick)

/** Wraps [action] the way the Android build adds a haptic to it; on desktop it is the action itself. */
@Suppress("UNUSED_PARAMETER")
fun hapticAction(kind: HapticKind = HapticKind.UI, action: () -> Unit): () -> Unit = action
