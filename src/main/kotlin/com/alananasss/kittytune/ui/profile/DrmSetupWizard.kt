@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Science
import com.alananasss.kittytune.core.openUrl
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.audio.WindowsPlayReadyRuntime
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.drm.DrmSelfTest
import com.alananasss.kittytune.data.drm.ProtectedBackend
import com.alananasss.kittytune.data.drm.WslSidecarSetup
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the assistant opens: the whole introduction, or straight at the WSL setup. */
enum class DrmWizardStart { INTRO, WSL }

private enum class WizardStep { INTRO, HOW, CHOOSE, WSL_CHECK, WSL_SETUP, DONE }

internal enum class StepState { PENDING, RUNNING, DONE, FAILED }

/**
 * The protected tracks assistant.
 *
 * It opens by itself the first time the page is visited, and from the page afterwards. It explains
 * what SoundCloud protects and how KittyTune plays it, lets a Windows user choose between PlayReady
 * and WSL, and, for WSL, does the whole setup: it checks for WSL, opens Microsoft's installer when it
 * is missing, and installs everything inside Ubuntu without a single command typed.
 *
 * Steps slide like the onboarding of essentials; the hero of each step is a Material shape.
 *
 * @param onClose called when the user leaves, finished or not. [changed] is true when a setting the
 *   page shows may have moved (the method, or protected tracks being turned off).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrmSetupWizard(start: DrmWizardStart, onClose: () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    val windows = WindowsPlayReadyRuntime.windows
    var choice by remember {
        mutableStateOf(if (start == DrmWizardStart.WSL) ProtectedBackend.WSL else ProtectedBackend.current)
    }
    val steps = when {
        start == DrmWizardStart.WSL -> listOf(WizardStep.WSL_CHECK, WizardStep.WSL_SETUP, WizardStep.DONE)
        !windows -> listOf(WizardStep.INTRO, WizardStep.HOW, WizardStep.DONE)
        choice == ProtectedBackend.WSL -> listOf(
            WizardStep.INTRO, WizardStep.HOW, WizardStep.CHOOSE,
            WizardStep.WSL_CHECK, WizardStep.WSL_SETUP, WizardStep.DONE,
        )
        else -> listOf(WizardStep.INTRO, WizardStep.HOW, WizardStep.CHOOSE, WizardStep.DONE)
    }
    var step by remember { mutableStateOf(steps.first()) }
    val index = steps.indexOf(step).coerceAtLeast(0)

    // The WSL part keeps its state here, so going back and forth does not restart the setup.
    val wsl = remember { WslWizardState() }

    val close = {
        prefs.setDrmOnboardingDone(true)
        onClose()
    }
    val canGoOn = when (step) {
        WizardStep.WSL_CHECK -> wsl.detection?.state.let {
            it == WslSidecarSetup.State.NOT_SET_UP || it == WslSidecarSetup.State.SET_UP
        }
        WizardStep.WSL_SETUP -> wsl.succeeded
        else -> true
    }
    // Leaving the setup while it runs would kill it halfway; going back is offered once it is over.
    val canGoBack = index > 0 && !wsl.running

    BackHandler(onBack = close)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(640.dp).fillMaxHeight(0.9f).heightIn(max = 780.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepDots(count = steps.size, current = index, modifier = Modifier.weight(1f))
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = close) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                    }
                }

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                        (slideInHorizontally(tween(380)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(380)))
                            .togetherWith(slideOutHorizontally(tween(380)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(250)))
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "drm-wizard",
                ) { shown ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (shown) {
                            WizardStep.INTRO -> IntroStep()
                            WizardStep.HOW -> HowStep(windows)
                            WizardStep.CHOOSE -> ChooseStep(
                                choice = choice,
                                onChoose = { choice = it },
                                onSkip = {
                                    prefs.setProtectedTracksEnabled(false)
                                    StreamResolver.forgetStreams()
                                    close()
                                },
                            )
                            WizardStep.WSL_CHECK -> WslCheckStep(wsl)
                            WizardStep.WSL_SETUP -> WslSetupStep(wsl)
                            WizardStep.DONE -> DoneStep(windows, choice)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            enabled = canGoBack,
                            onClick = { step = steps[index - 1] },
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("btn_back"))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val last = index == steps.lastIndex
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        enabled = canGoOn,
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.height(48.dp),
                        onClick = {
                            if (step == WizardStep.CHOOSE && choice == ProtectedBackend.PLAYREADY) {
                                // WSL is only selected once its setup succeeds, never half-done.
                                ProtectedBackend.select(ProtectedBackend.PLAYREADY)
                            }
                            if (last) close() else step = steps[index + 1]
                        },
                    ) {
                        Text(
                            text = str(
                                when {
                                    last -> "drm_wiz_finish"
                                    index == 0 -> "drm_wiz_start"
                                    else -> "drm_wiz_next"
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                        if (!last) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Progress at the top: the current step is a long pill, the others dots. */
@Composable
internal fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateFloatAsState(if (i == current) 28f else 8f, label = "dot")
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot-color",
            )
            Box(Modifier.size(width = width.dp, height = 8.dp).background(color, CircleShape))
        }
    }
}

/** The big shape at the top of a step, slowly turning behind a still icon. */
@Composable
internal fun Hero(shape: androidx.graphics.shapes.RoundedPolygon, icon: ImageVector) {
    val turn = rememberInfiniteTransition(label = "hero")
    val rotation by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "hero-rotation",
    )
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { rotationZ = rotation }
                    .background(MaterialTheme.colorScheme.primaryContainer, shape.toShape())
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
internal fun StepTitle(title: String, body: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (body != null) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A titled point with a small shaped icon, for explanations. */
@Composable
internal fun ExplainRow(icon: ImageVector, title: String, body: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie6Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun IntroStep() {
    Hero(MaterialShapes.Cookie9Sided, Icons.Rounded.Lock)
    StepTitle(str("drm_wiz_intro_title"), str("drm_wiz_intro_body"))
    ExplainRow(Icons.Rounded.SwapHoriz, str("drm_wiz_intro_without_title"), str("drm_wiz_intro_without"))
}

@Composable
internal fun HowStep(windows: Boolean) {
    Hero(MaterialShapes.Clover4Leaf, Icons.Rounded.Key)
    StepTitle(str("drm_wiz_how_title"))
    ExplainRow(Icons.Rounded.Key, str("drm_wiz_how_license_title"), str("drm_wiz_how_license"))
    ExplainRow(Icons.Rounded.Shield, str("drm_wiz_how_cdm_title"), str("drm_wiz_how_cdm"))
    ExplainRow(Icons.Rounded.SwapHoriz, str("drm_wiz_how_fallback_title"), str("drm_wiz_how_fallback"))
    ExplainRow(
        Icons.Rounded.Memory,
        str("drm_wiz_how_nobrowser_title"),
        str("drm_wiz_how_nobrowser") + if (windows) " " + str("drm_wiz_how_nobrowser_wsl") else "",
    )
    if (!windows) DrmExperimentalCard(windows = false)
    Text(
        text = str(if (windows) "drm_wiz_how_windows" else "drm_wiz_how_desktop"),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ColumnScope.ChooseStep(choice: ProtectedBackend, onChoose: (ProtectedBackend) -> Unit, onSkip: () -> Unit) {
    Hero(MaterialShapes.Sunny, Icons.Rounded.Tune)
    StepTitle(str("drm_wiz_choose_title"), str("drm_wiz_choose_body"))
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProtectedBackend.entries.forEach { method ->
            MethodChoiceCard(
                method = method,
                selected = method == choice,
                onClick = { onChoose(method) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
    // Only WSL is the heavy, experimental one: PlayReady needs no warning.
    AnimatedVisibility(
        visible = choice == ProtectedBackend.WSL,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        DrmExperimentalCard(windows = true)
    }
    TextButton(
        shapes = ButtonDefaults.shapes(),
        onClick = onSkip,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(str("drm_wiz_skip"))
    }
}

/** Where the code lives, for anyone with a better way to play these tracks. */
internal const val KITTYTUNE_REPO_URL = "https://github.com/alan7383/KittyTuneDesktop"

/**
 * Says plainly that this is experimental: on Windows, that WSL is heavy but the only stable way for
 * now and that a real Linux stays lighter; everywhere, that the code is open to a better idea.
 */
@Composable
internal fun DrmExperimentalCard(windows: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(MaterialTheme.colorScheme.tertiaryContainer, MaterialShapes.Cookie7Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(str("drm_exp_title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                str(if (windows) "drm_exp_windows" else "drm_exp_desktop"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        str("drm_exp_open"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    )
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { openUrl(KITTYTUNE_REPO_URL) }) {
                        Text(str("drm_exp_github"), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** One method as a big selectable card: what it is, then what it can and cannot do. */
@Composable
private fun MethodChoiceCard(method: ProtectedBackend, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "method-container",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MethodBadge(method, size = 40)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(methodName(method), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        MethodInfoButton(method)
                    }
                    Text(
                        methodTagline(method),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = onClick)
            }
            MethodPoints(method)
        }
    }
}

/** The little "i" next to a method's name: how it works, and how it was found. */
@Composable
internal fun MethodInfoButton(method: ProtectedBackend) {
    var open by remember { mutableStateOf(false) }
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        onClick = { open = true },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = str("drm_info_open"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    if (open) MethodInfoDialog(method, onDismiss = { open = false })
}

@Composable
internal fun MethodInfoDialog(method: ProtectedBackend, onDismiss: () -> Unit) {
    val wsl = method == ProtectedBackend.WSL
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { MethodBadge(method, size = 48) },
        title = { Text(str(if (wsl) "drm_info_wsl_title" else "drm_info_playready_title"), textAlign = TextAlign.Center) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoSection(Icons.Rounded.Tune, str("drm_info_how"), str(if (wsl) "drm_info_wsl_how" else "drm_info_playready_how"))
                InfoSection(Icons.Rounded.Search, str("drm_info_found"), str(if (wsl) "drm_info_wsl_found" else "drm_info_playready_found"))
                if (wsl) InfoSection(Icons.Rounded.Memory, str("drm_info_linux_title"), str("drm_info_wsl_linux"))
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(str("btn_close")) }
        },
    )
}

@Composable
private fun InfoSection(icon: ImageVector, title: String, body: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The method's icon in its own shape: a shield for Windows' renderer, a terminal for Linux. */
@Composable
internal fun MethodBadge(method: ProtectedBackend, size: Int = 44) {
    val shape = if (method == ProtectedBackend.PLAYREADY) MaterialShapes.Cookie4Sided else MaterialShapes.Pill
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.tertiaryContainer, shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = methodIcon(method),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size((size / 2).dp),
        )
    }
}

internal fun methodIcon(method: ProtectedBackend): ImageVector =
    if (method == ProtectedBackend.PLAYREADY) Icons.Rounded.Shield else Icons.Rounded.Terminal

internal fun methodName(method: ProtectedBackend): String =
    str(if (method == ProtectedBackend.PLAYREADY) "drm_method_playready" else "drm_method_wsl")

internal fun methodTagline(method: ProtectedBackend): String =
    str(if (method == ProtectedBackend.PLAYREADY) "drm_method_playready_tagline" else "drm_method_wsl_tagline")

/**
 * What a method can and cannot do. The same four abilities on both sides, so the two lists read as a
 * comparison: PlayReady's missing ones are WSL's gains.
 */
@Composable
internal fun MethodPoints(method: ProtectedBackend) {
    val abilities = listOf(
        "pref_drm_platforms_effects",
        "pref_drm_platforms_visualizer",
        "pref_drm_platforms_download",
        "pref_drm_platforms_start",
    )
    val points = if (method == ProtectedBackend.PLAYREADY) {
        listOf("drm_method_pro_ready" to true) + abilities.map { it to false }
    } else {
        abilities.map { it to true } + listOf("drm_method_con_setup" to false, "drm_method_con_space" to false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        points.forEach { (key, good) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (good) Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircleOutline,
                    contentDescription = null,
                    tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).padding(top = 1.dp),
                )
                Text(
                    text = str(key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (good) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The WSL steps' state, kept above the sliding content so it survives going back and forth. */
internal class WslWizardState {
    var detection by mutableStateOf<WslSidecarSetup.Detection?>(null)
    var checking by mutableStateOf(false)
    var installerOpened by mutableStateOf(false)
    var installerFailed by mutableStateOf(false)

    var running by mutableStateOf(false)
    var succeeded by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    val states = mutableStateListOf(*Array(WslSidecarSetup.Step.entries.size) { StepState.PENDING })
    var log by mutableStateOf("")
    var showLog by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WslCheckStep(wsl: WslWizardState) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val recheck: () -> Unit = {
        scope.launch {
            wsl.checking = true
            try {
                wsl.detection = WslSidecarSetup.detect()
            } finally {
                wsl.checking = false
            }
        }
    }
    LaunchedEffect(Unit) { if (wsl.detection == null) recheck() }
    // While Microsoft's installer runs, look again now and then: no button to remember to press.
    LaunchedEffect(wsl.installerOpened) {
        while (wsl.installerOpened) {
            delay(5_000)
            val found = WslSidecarSetup.detect()
            wsl.detection = found
            if (found.state != WslSidecarSetup.State.NO_WSL && found.state != WslSidecarSetup.State.NO_DISTRO) {
                wsl.installerOpened = false
            }
        }
    }

    Hero(MaterialShapes.Pill, Icons.Rounded.Terminal)
    StepTitle(str("drm_wiz_wsl_title"), str("drm_wiz_wsl_what"))

    val state = wsl.detection?.state
    when {
        state == null || (wsl.checking && !wsl.installerOpened) -> WaitingCard(str("drm_wiz_wsl_checking"))
        state == WslSidecarSetup.State.NOT_SET_UP || state == WslSidecarSetup.State.SET_UP -> ResultCard(
            ok = true,
            text = str("drm_wiz_wsl_found", wsl.detection?.distro ?: "Linux"),
        )
        else -> {
            ResultCard(
                ok = false,
                text = str(if (state == WslSidecarSetup.State.NO_WSL) "drm_wiz_wsl_missing" else "drm_wiz_wsl_no_distro"),
            )
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("drm_wiz_wsl_step1", "drm_wiz_wsl_step2", "drm_wiz_wsl_step3").forEachIndexed { i, key ->
                        NumberedLine(i + 1, str(key))
                    }
                }
            }
            if (wsl.installerOpened) {
                WaitingCard(str("drm_wiz_wsl_waiting"))
            }
            if (wsl.installerFailed) {
                Text(
                    str("drm_wiz_wsl_install_failed", WslSidecarSetup.INSTALL_COMMAND),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        val opened = WslSidecarSetup.openInstaller(state)
                        wsl.installerFailed = !opened
                        wsl.installerOpened = opened
                    },
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str(if (state == WslSidecarSetup.State.NO_WSL) "drm_wiz_wsl_install" else "drm_wiz_wsl_install_ubuntu"))
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = recheck, enabled = !wsl.checking) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("drm_wiz_wsl_recheck"))
                }
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { clipboard.setText(AnnotatedString(WslSidecarSetup.INSTALL_COMMAND)) },
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("drm_wiz_wsl_copy"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WslSetupStep(wsl: WslWizardState) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val start: () -> Unit = start@{
        val distro = wsl.detection?.distro ?: return@start
        scope.launch {
            wsl.running = true
            wsl.succeeded = false
            wsl.failure = null
            wsl.log = ""
            for (i in wsl.states.indices) wsl.states[i] = StepState.PENDING
            val log = StringBuilder()
            var current: WslSidecarSetup.Step? = null
            try {
                WslSidecarSetup.provision(
                    distro = distro,
                    onStep = { next ->
                        current?.let { wsl.states[it.ordinal] = StepState.DONE }
                        current = next
                        wsl.states[next.ordinal] = StepState.RUNNING
                    },
                    onLine = { line ->
                        synchronized(log) { log.append(line).append('\n') }
                        wsl.log = synchronized(log) { log.toString() }
                    },
                )
                current?.let { wsl.states[it.ordinal] = StepState.DONE }
                wsl.succeeded = true
            } catch (e: WslSidecarSetup.SetupException) {
                wsl.states[e.step.ordinal] = StepState.FAILED
                wsl.failure = str("drm_wiz_setup_failed", stepLabel(e.step)) + "\n" + e.message
                wsl.showLog = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                current?.let { wsl.states[it.ordinal] = StepState.FAILED }
                wsl.failure = e.message ?: e.javaClass.simpleName
                wsl.showLog = true
            } finally {
                wsl.running = false
            }
        }
    }

    Hero(MaterialShapes.Cookie12Sided, Icons.Rounded.Build)
    StepTitle(str("drm_wiz_setup_title"), str("drm_wiz_setup_body"))
    ExplainRow(Icons.Rounded.Memory, str("drm_wiz_how_nobrowser_title"), str("drm_wiz_setup_light"))

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WslSidecarSetup.Step.entries.forEach { s ->
                SetupStepRow(stepLabel(s), wsl.states[s.ordinal])
            }
            if (wsl.running || wsl.succeeded) {
                val done = wsl.states.count { it == StepState.DONE }
                val progress by animateFloatAsState(done.toFloat() / wsl.states.size, label = "setup-progress")
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }

    when {
        wsl.succeeded -> ResultCard(ok = true, text = str("drm_wiz_setup_done"))
        wsl.failure != null -> ResultCard(ok = false, text = wsl.failure.orEmpty())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!wsl.running && !wsl.succeeded) {
            Button(shapes = ButtonDefaults.shapes(), onClick = start) {
                Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(str(if (wsl.failure != null) "btn_retry" else "drm_wiz_setup_start"))
            }
        }
        if (wsl.log.isNotEmpty()) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { wsl.showLog = !wsl.showLog }) {
                Text(str(if (wsl.showLog) "drm_wiz_details_hide" else "drm_wiz_details_show"))
            }
        }
    }
    AnimatedVisibility(wsl.showLog && wsl.log.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        LogBox(wsl.log, onCopy = { clipboard.setText(AnnotatedString(wsl.log)) })
    }
}

@Composable
internal fun DoneStep(windows: Boolean, choice: ProtectedBackend) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var testing by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf<Boolean?>(null) }
    var log by remember { mutableStateOf("") }
    var showLog by remember { mutableStateOf(false) }

    Hero(MaterialShapes.SoftBurst, Icons.Rounded.Celebration)
    StepTitle(
        str("drm_wiz_done_title"),
        str(
            when {
                !windows -> "drm_wiz_done_desktop"
                choice == ProtectedBackend.WSL -> "drm_wiz_done_wsl"
                else -> "drm_wiz_done_playready"
            }
        ),
    )
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    enabled = !testing,
                    onClick = {
                        scope.launch {
                            testing = true
                            passed = null
                            val lines = StringBuilder()
                            try {
                                passed = DrmSelfTest.run(widevine = !windows || choice == ProtectedBackend.WSL) { line ->
                                    synchronized(lines) { lines.append(line).append('\n') }
                                    log = synchronized(lines) { lines.toString() }
                                }
                            } finally {
                                testing = false
                            }
                            if (passed == false) showLog = true
                        }
                    },
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("drm_wiz_test"))
                }
                Text(
                    str("pref_drm_test_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                testing -> WaitingRow(str("pref_drm_testing"))
                passed == true -> StatusText(ok = true, text = str("pref_drm_windows_test_ok"))
                passed == false -> StatusText(ok = false, text = str("drm_wiz_test_failed"))
            }
            if (log.isNotEmpty()) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showLog = !showLog }) {
                    Text(str(if (showLog) "drm_wiz_details_hide" else "drm_wiz_details_show"))
                }
            }
            AnimatedVisibility(showLog && log.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LogBox(log, onCopy = { clipboard.setText(AnnotatedString(log)) })
            }
        }
    }
}

private fun stepLabel(step: WslSidecarSetup.Step): String = str(
    when (step) {
        WslSidecarSetup.Step.TOOLS -> "drm_wiz_step_tools"
        WslSidecarSetup.Step.SOURCES -> "drm_wiz_step_sources"
        WslSidecarSetup.Step.CDM -> "drm_wiz_step_cdm"
        WslSidecarSetup.Step.BUILD -> "drm_wiz_step_build"
        WslSidecarSetup.Step.PROBE -> "drm_wiz_step_probe"
        WslSidecarSetup.Step.CONNECT -> "drm_wiz_step_connect"
    }
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetupStepRow(label: String, state: StepState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.PENDING -> Icon(
                    Icons.Rounded.RadioButtonUnchecked, contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp),
                )
                StepState.RUNNING -> LoadingIndicator(Modifier.size(28.dp))
                StepState.DONE -> Icon(
                    Icons.Rounded.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
                )
                StepState.FAILED -> Icon(
                    Icons.Rounded.ErrorOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (state == StepState.RUNNING) FontWeight.Bold else FontWeight.Normal,
            color = when (state) {
                StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                StepState.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun NumberedLine(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WaitingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingIndicator(Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun WaitingCard(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) { WaitingRow(text) }
    }
}

@Composable
internal fun StatusText(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun ResultCard(ok: Boolean, text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (ok) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) { StatusText(ok, text) }
    }
}

@Composable
internal fun LogBox(log: String, onCopy: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(log) { scroll.animateScrollTo(scroll.maxValue) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            SelectionContainer {
                Text(
                    text = log.trimEnd(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(scroll),
                )
            }
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onCopy, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(str("pref_drm_log_copy"))
            }
        }
    }
}
