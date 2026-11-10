package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.drm.ProtectedTracksNotice
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.alananasss.kittytune.data.drm.NativeCdmSetup

/**
 * Whether the "protected tracks are here" card is still owed to this user.
 *
 * Versioned, and deliberately blind to the old choice: someone who turned protected tracks off, or
 * dismissed the card during the 1.5.0 update, has to hear that the method changed — it no longer
 * needs WSL, a browser or a restart.
 */
internal fun protectedTracksAnnouncementDue(prefs: PlayerPreferences = PlayerPreferences()): Boolean =
    prefs.getProtectedAnnouncementVersion() < ProtectedTracksNotice.CURRENT

/**
 * Turns protected tracks on from a card or a notice. On Windows the module has to be downloaded once, so
 * this returns false until it is: the caller then opens the assistant, which installs it.
 */
private suspend fun enableProtectedTracks(): Boolean {
    if (NativeCdmSetup.windows && !withContext(Dispatchers.IO) { NativeCdmSetup.isInstalled() }) return false
    val prefs = PlayerPreferences()
    prefs.setProtectedTracksEnabled(true)
    prefs.setProtectedAnnouncementVersion(ProtectedTracksNotice.CURRENT)
    StreamResolver.forgetStreams()
    return true
}

/**
 * The card on the home screen announcing protected tracks to people updating from a version without them.
 *
 * Windows: off by default there, so the card offers them ("Turn on") alongside the assistant. Linux and macOS:
 * already on, so the card says so and points at the explanation. A beta everywhere, and said so. Once dealt with —
 * turned on, explained, or put off — it does not come back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtectedTracksAnnouncementCard(modifier: Modifier = Modifier, bottomSpacing: androidx.compose.ui.unit.Dp = 0.dp) {
    val windows = NativeCdmSetup.windows
    var visible by remember { mutableStateOf(protectedTracksAnnouncementDue()) }
    var enabled by remember {
        mutableStateOf(PlayerPreferences().getProtectedTracksEnabled() && (!windows || NativeCdmSetup.isInstalled()))
    }
    // On Windows the module has to be downloaded before the switch can mean anything: the card then
    // offers to install it rather than to flip a switch that would do nothing.
    var needsModule by remember { mutableStateOf(windows && !NativeCdmSetup.isInstalled()) }
    var busy by remember { mutableStateOf(false) }
    var openWizard by remember { mutableStateOf(false) }
    var enableRequest by remember { mutableStateOf(0) }

    fun done() {
        PlayerPreferences().setProtectedAnnouncementVersion(ProtectedTracksNotice.CURRENT)
        visible = false
    }

    LaunchedEffect(enableRequest) {
        if (enableRequest == 0) return@LaunchedEffect
        busy = true
        if (enableProtectedTracks()) {
            enabled = true
            delay(4_500)
            visible = false
        } else {
            openWizard = true
        }
        busy = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically(tween(320)) + fadeOut(tween(220)),
        modifier = modifier,
    ) {
        // The spacing below goes with the card, so nothing is left behind when it collapses.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomSpacing),
        ) {
            Box {
                AnimatedContent(
                    targetState = enabled,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "protected-announcement",
                ) { isEnabled ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 56.dp, top = 20.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ShapeBadge(
                            icon = if (isEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                            size = 64,
                            spinning = !isEnabled,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(str("protected_ann_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = str(
                                    when {
                                        isEnabled -> "protected_ann_enabled"
                                        windows -> "protected_ann_body_windows"
                                        else -> "protected_ann_body_desktop"
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!isEnabled) {
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (needsModule) {
                                        Button(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = { openWizard = true }) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(str("protected_ann_install"), fontWeight = FontWeight.Bold)
                                        }
                                    } else if (windows) {
                                        Button(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = { enableRequest++ }) {
                                            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(str("protected_ann_enable"), fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(shapes = ButtonDefaults.shapes(), onClick = { done() }) {
                                            Text(str("protected_ann_got_it"), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = { openWizard = true }) {
                                        Text(str("protected_ann_learn"))
                                    }
                                    if (windows) {
                                        TextButton(shapes = ButtonDefaults.shapes(), enabled = !busy, onClick = { done() }) {
                                            Text(str("protected_ann_later"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { done() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                }
            }
        }
    }

    if (openWizard) {
        DrmSetupWizard(start = DrmWizardStart.INTRO, onClose = {
            openWizard = false
            // The assistant marks the announcement as dealt with; the switch is whatever it was left at.
            visible = protectedTracksAnnouncementDue()
        })
    }
}

/**
 * The floating notice the player raises about a protected track, above the player bar: an offer to turn protected
 * tracks on (once, Windows), or why one could not be played. It goes away by itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtectedTracksNoticeHost(modifier: Modifier = Modifier) {
    val notice by ProtectedTracksNotice.current.collectAsState()
    var enabled by remember { mutableStateOf(false) }
    var openWizard by remember { mutableStateOf(false) }
    var enableRequest by remember { mutableStateOf(0) }

    LaunchedEffect(notice?.id, enabled) {
        val current = notice ?: return@LaunchedEffect
        delay(
            when {
                enabled -> 3_500L
                current.kind == ProtectedTracksNotice.Kind.OFFER_ENABLE -> 14_000L
                else -> 9_000L
            }
        )
        ProtectedTracksNotice.dismiss()
    }
    LaunchedEffect(notice?.id) { enabled = false }
    LaunchedEffect(enableRequest) {
        if (enableRequest == 0) return@LaunchedEffect
        if (enableProtectedTracks()) enabled = true else openWizard = true
    }

    AnimatedVisibility(
        visible = notice != null,
        enter = slideInVertically(tween(360)) { it / 2 } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val kind = notice?.kind ?: ProtectedTracksNotice.Kind.OFFER_ENABLE
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeBadge(icon = if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, size = 44, spinning = false)
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(str("protected_hint_title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            enabled -> str("protected_ann_enabled")
                            kind == ProtectedTracksNotice.Kind.OFFER_ENABLE -> str("protected_hint_offer")
                            NativeCdmSetup.windows -> str("drm_unavailable_toast_windows").removePrefix("🔒").trim()
                            else -> str("drm_unavailable_toast").removePrefix("🔒").trim()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!enabled) {
                    if (kind == ProtectedTracksNotice.Kind.OFFER_ENABLE) {
                        Button(shapes = ButtonDefaults.shapes(), onClick = { enableRequest++ }) {
                            Text(str("protected_ann_enable"), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { openWizard = true }) {
                            Text(str("protected_ann_learn"))
                        }
                    }
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { ProtectedTracksNotice.dismiss() }) {
                    Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                }
            }
        }
    }

    if (openWizard) {
        DrmSetupWizard(start = DrmWizardStart.INTRO, onClose = {
            openWizard = false
            ProtectedTracksNotice.dismiss()
        })
    }
}

/**
 * An icon on a Material shape, slowly turning on the big card the way the assistant's heroes do. Turned on the
 * shared ambient pulse: the card can sit on the home screen for a while, and an infinite transition there would
 * have the window redrawn at the display's rate for as long as it does (see AmbientClock).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBadge(icon: ImageVector, size: Int, spinning: Boolean) {
    val rotation = remember { mutableFloatStateOf(0f) }
    val ambientClock = com.alananasss.kittytune.ui.common.LocalAmbientClock.current
    val seen = com.alananasss.kittytune.core.LocalWindowSeen.current
    LaunchedEffect(spinning, seen, ambientClock) {
        if (!spinning || !seen) return@LaunchedEffect
        val start = com.alananasss.kittytune.ui.common.withAmbientFrameNanos(ambientClock) { it }
        while (true) {
            com.alananasss.kittytune.ui.common.withAmbientFrameNanos(ambientClock) { now ->
                rotation.floatValue = (((now - start) / 1_000_000.0 / BADGE_TURN_MS) % 1.0 * 360.0).toFloat()
            }
        }
    }
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size.dp)
                .graphicsLayer { rotationZ = rotation.floatValue }
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape())
        )
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size((size * 0.45f).dp),
        )
    }
}

private const val BADGE_TURN_MS = 24_000.0
