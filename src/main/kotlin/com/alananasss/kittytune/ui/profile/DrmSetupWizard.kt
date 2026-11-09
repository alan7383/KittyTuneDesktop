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
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.drm.DrmSelfTest
import com.alananasss.kittytune.data.drm.NativeCdmSetup
import com.alananasss.kittytune.data.drm.ProtectedBackend
import com.alananasss.kittytune.data.drm.WslSidecarSetup
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Bolt

/** Where the assistant opens: the whole introduction, or straight at one method's setup. */
enum class DrmWizardStart { INTRO, NATIVE, WSL }

private enum class WizardStep { INTRO, HOW, CHOOSE, NATIVE_SETUP, WSL_CHECK, WSL_SETUP, DONE }

internal enum class StepState { PENDING, RUNNING, DONE, FAILED }

/**
 * The protected tracks assistant.
 *
 * It opens by itself the first time the page is visited, and from the page afterwards. It explains
 * what SoundCloud protects and how KittyTune plays it, then sets the chosen method up:
 * the **native** one needs nothing but a download (no administrator, no restart, no virtual machine),
 * and the WSL one — kept for machines where the loader cannot run — does its own heavy setup in the
 * same place.
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
    val windows = NativeCdmSetup.windows
    var choice by remember {
        mutableStateOf(
            when (start) {
                DrmWizardStart.WSL -> ProtectedBackend.WSL
                DrmWizardStart.NATIVE -> ProtectedBackend.NATIVE
                else -> ProtectedBackend.current
            }
        )
    }
    val steps = when {
        start == DrmWizardStart.WSL -> listOf(WizardStep.WSL_CHECK, WizardStep.WSL_SETUP, WizardStep.DONE)
        start == DrmWizardStart.NATIVE -> listOf(WizardStep.NATIVE_SETUP, WizardStep.DONE)
        !windows -> listOf(WizardStep.INTRO, WizardStep.HOW, WizardStep.DONE)
        choice == ProtectedBackend.WSL -> listOf(
            WizardStep.INTRO, WizardStep.HOW, WizardStep.CHOOSE,
            WizardStep.WSL_CHECK, WizardStep.WSL_SETUP, WizardStep.DONE,
        )
        else -> listOf(WizardStep.INTRO, WizardStep.HOW, WizardStep.CHOOSE, WizardStep.NATIVE_SETUP, WizardStep.DONE)
    }
    // Resuming after the reboot the feature needed. The check step comes first; it is skipped for the setup
    // step only once WSL is really ready for the pending version — jumping on "pending" alone started an
    // import that could only fail when part of WSL (its engine) was still missing.
    val resuming = remember { start == DrmWizardStart.WSL && WslSidecarSetup.pendingVersion() != null }
    var step by remember { mutableStateOf(steps.first()) }
    LaunchedEffect(Unit) {
        if (resuming && WizardStep.WSL_SETUP in steps && WslSidecarSetup.pendingReady()) step = WizardStep.WSL_SETUP
    }
    val index = steps.indexOf(step).coerceAtLeast(0)

    // Each method keeps its state here, so going back and forth does not restart the setup.
    val wsl = remember {
        WslWizardState().apply {
            if (resuming) {
                version = WslSidecarSetup.pendingVersion() ?: 1
                versionChosen = true
            }
        }
    }
    val native = remember { NativeWizardState() }

    val close = {
        prefs.setDrmOnboardingDone(true)
        // Whoever went through the assistant needs no announcement of what it explains.
        prefs.setProtectedTracksAnnounced(true)
        onClose()
    }
    val canGoOn = when (step) {
        WizardStep.WSL_CHECK -> !wsl.checking && wsl.detection?.featureOn(wsl.version) == true
        WizardStep.WSL_SETUP -> wsl.succeeded
        WizardStep.NATIVE_SETUP -> native.succeeded
        else -> true
    }
    // Leaving the setup while it runs would kill it halfway; going back is offered once it is over.
    val canGoBack = index > 0 && !wsl.running && !native.running

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
                                version = wsl.version,
                                onVersion = { wsl.version = it; wsl.versionChosen = true },
                                onSkip = {
                                    prefs.setProtectedTracksEnabled(false)
                                    StreamResolver.forgetStreams()
                                    close()
                                },
                            )
                            WizardStep.NATIVE_SETUP -> NativeSetupStep(native)
                            WizardStep.WSL_CHECK -> WslCheckStep(wsl, showVersionChoice = WizardStep.CHOOSE !in steps)
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
                            if (step == WizardStep.CHOOSE) {
                                // Choosing a method is choosing to play protected tracks: off by default on
                                // Windows, the switch is turned on here rather than left for the user to find.
                                prefs.setProtectedTracksEnabled(true)
                                StreamResolver.forgetStreams()
                                // The backend is only switched once its setup succeeds, never half-done:
                                // both installers do it themselves.
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
internal fun ColumnScope.ChooseStep(
    choice: ProtectedBackend,
    onChoose: (ProtectedBackend) -> Unit,
    version: Int,
    onVersion: (Int) -> Unit,
    onSkip: () -> Unit,
) {
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
    // Only WSL is the heavy, experimental one: the native module needs no warning.
    AnimatedVisibility(
        visible = choice == ProtectedBackend.WSL,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DrmExperimentalCard(windows = true)
            WslVersionChoice(version = version, onVersion = onVersion)
        }
    }
    TextButton(
        shapes = ButtonDefaults.shapes(),
        onClick = onSkip,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(str("drm_wiz_skip"))
    }
}

/**
 * The WSL1-vs-WSL2 sub-choice, hidden behind an "advanced" button so most people never see it.
 *
 * WSL1 is the default: no virtual machine, almost no memory. WSL2 is heavier but works on the rare
 * machine where the WSL1 Windows feature cannot be turned on. Both play protected tracks the same way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WslVersionChoice(version: Int, onVersion: (Int) -> Unit) {
    var open by remember { mutableStateOf(version != 1) }
    TextButton(shapes = ButtonDefaults.shapes(), onClick = { open = !open }) {
        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(str("drm_wiz_advanced"))
    }
    AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                VersionRow(
                    selected = version == 1,
                    title = str("drm_wiz_version_wsl1"),
                    body = str("drm_wiz_version_wsl1_desc"),
                    onClick = { onVersion(1) },
                )
                VersionRow(
                    selected = version == 2,
                    title = str("drm_wiz_version_wsl2"),
                    body = str("drm_wiz_version_wsl2_desc"),
                    onClick = { onVersion(2) },
                )
            }
        }
    }
}

@Composable
private fun VersionRow(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Where the code lives, for anyone with a better way to play these tracks. */
internal const val KITTYTUNE_REPO_URL = "https://github.com/alan7383/KittyTuneDesktop"

/**
 * Says plainly that this is still a beta: on Windows, that the WSL method is a tiny, light Linux made
 * only for the module; everywhere, that the code is open to a better idea.
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
        title = { Text(str(if (wsl) "drm_info_wsl_title" else "drm_info_native_title"), textAlign = TextAlign.Center) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoSection(Icons.Rounded.Tune, str("drm_info_how"), str(if (wsl) "drm_info_wsl_how" else "drm_info_native_how"))
                InfoSection(Icons.Rounded.Search, str("drm_info_found"), str(if (wsl) "drm_info_wsl_found" else "drm_info_native_found"))
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

/** The method's icon in its own shape: a terminal for the native loader, a cookie for WSL. */
@Composable
internal fun MethodBadge(method: ProtectedBackend, size: Int = 44) {
    val shape = if (method == ProtectedBackend.NATIVE) MaterialShapes.Cookie4Sided else MaterialShapes.Pill
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
    if (method == ProtectedBackend.NATIVE) Icons.Rounded.Terminal else Icons.Rounded.Memory

internal fun methodName(method: ProtectedBackend): String =
    str(if (method == ProtectedBackend.NATIVE) "drm_method_native" else "drm_method_wsl")

internal fun methodTagline(method: ProtectedBackend): String =
    str(if (method == ProtectedBackend.NATIVE) "drm_method_native_tagline" else "drm_method_wsl_tagline")

/**
 * What a method can and cannot do. The same abilities on both sides, so the two lists read as a
 * comparison: what WSL costs is the one thing the native module does not.
 */
@Composable
internal fun MethodPoints(method: ProtectedBackend) {
    val abilities = listOf(
        "pref_drm_platforms_effects",
        "pref_drm_platforms_visualizer",
        "pref_drm_platforms_download",
        "pref_drm_platforms_start",
    )
    val points = if (method == ProtectedBackend.NATIVE) {
        abilities.map { it to true } + listOf(
            "drm_method_pro_light" to true,
            "drm_method_pro_noreboot" to true,
        )
    } else {
        abilities.map { it to true } + listOf("drm_method_pro_light" to true, "drm_method_con_setup" to false)
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

    /** Which WSL version to set up: 1 (WSL1, light, no VM, the default) or 2 (a lightweight VM). */
    var version by mutableStateOf(1)
    /** True once the user picked a version themselves, so detection does not override it. */
    var versionChosen by mutableStateOf(false)
    var enabling by mutableStateOf(false)
    var enableRefused by mutableStateOf(false)
    /** Why turning the component on failed (not a refusal), or null. */
    var enableError by mutableStateOf<String?>(null)
    /** How far the WSL engine's download is, in percent, while it runs; null otherwise. */
    var enableProgress by mutableStateOf<Int?>(null)
    /** True once the Windows feature was turned on and the machine must restart for the setup to go on. */
    var awaitingReboot by mutableStateOf(false)

    var running by mutableStateOf(false)
    var succeeded by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    val states = mutableStateListOf(*Array(WslSidecarSetup.Step.entries.size) { StepState.PENDING })
    var log by mutableStateOf("")
    var showLog by mutableStateOf(false)
}

/**
 * Checks that the Windows component the chosen WSL version runs on is on, and turns it on when it is not.
 *
 * Readiness is judged for [WslWizardState.version] only: WSL2's component being on does not make a WSL1
 * import possible, and vice versa. Turning a component on goes through Windows' own UAC prompt, then a
 * restart; the assistant resumes by itself afterwards.
 *
 * @param showVersionChoice offer the WSL1/WSL2 choice here, for when the assistant opened straight at
 *   this step (from the settings) and the method step, which normally holds it, is not part of the flow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WslCheckStep(wsl: WslWizardState, showVersionChoice: Boolean) {
    val scope = rememberCoroutineScope()
    val recheck: () -> Unit = {
        scope.launch {
            wsl.checking = true
            try {
                val found = WslSidecarSetup.detect()
                // Redoing an existing setup keeps its version, unless the user picked one.
                if (!wsl.versionChosen) found.installedVersion?.let { wsl.version = it }
                wsl.detection = found
            } finally {
                wsl.checking = false
            }
        }
    }
    LaunchedEffect(Unit) { if (wsl.detection == null) recheck() }
    // After the restart (or straight away when Windows needed none), the component reads as on: move on.
    LaunchedEffect(wsl.awaitingReboot) {
        while (wsl.awaitingReboot) {
            delay(5_000)
            val found = WslSidecarSetup.detect()
            wsl.detection = found
            if (found.featureOn(wsl.version)) wsl.awaitingReboot = false
        }
    }

    val enable: (Int) -> Unit = { version ->
        scope.launch {
            wsl.enabling = true
            wsl.enableRefused = false
            wsl.enableError = null
            wsl.enableProgress = null
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    WslSidecarSetup.enable(version) { percent -> wsl.enableProgress = percent }
                }
                wsl.version = version
                wsl.enableRefused = result.refused
                if (!result.ok && !result.refused) {
                    wsl.enableError = result.log.lineSequence().lastOrNull { it.isNotBlank() } ?: ""
                }
                if (result.ok) {
                    // Nothing left for a restart to do (the component was already on, only the engine was
                    // missing): carry straight on instead of asking for one.
                    val found = WslSidecarSetup.detect()
                    wsl.detection = found
                    wsl.awaitingReboot = !found.featureOn(version)
                }
            } finally {
                wsl.enabling = false
                wsl.enableProgress = null
            }
        }
    }

    Hero(MaterialShapes.Pill, Icons.Rounded.Terminal)
    StepTitle(str("drm_wiz_wsl_title"), str("drm_wiz_wsl_what"))

    val detection = wsl.detection
    val version = wsl.version
    when {
        detection == null || wsl.checking -> WaitingCard(str("drm_wiz_wsl_checking"))
        detection.featureOn(version) -> ResultCard(ok = true, text = str("drm_wiz_wsl_ready", version))
        wsl.awaitingReboot -> {
            ResultCard(ok = true, text = str("drm_wiz_wsl_enabled"))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("drm_wiz_reboot_step1", "drm_wiz_reboot_step2", "drm_wiz_reboot_step3").forEachIndexed { i, key ->
                        NumberedLine(i + 1, str(key))
                    }
                }
            }
            Button(shapes = ButtonDefaults.shapes(), onClick = { restartWindows() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(str("drm_wiz_reboot_now"))
            }
        }
        else -> {
            // The component this version runs on is off: one click turns it on through Windows' UAC prompt.
            // A normal step to take, not a failure: a neutral card, never the red error one.
            InfoCard(str("drm_wiz_wsl_needs_enable", version))
            ExplainRow(
                Icons.Rounded.Memory,
                str(if (version == 1) "drm_wiz_version_wsl1" else "drm_wiz_version_wsl2"),
                str(if (version == 1) "drm_wiz_version_wsl1_desc" else "drm_wiz_version_wsl2_desc"),
            )
            Text(
                str("drm_wiz_enable_how") + if (!detection.wslPackage) "\n" + str("drm_wiz_engine_needed") else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val problem = when {
                wsl.enableRefused -> str("drm_wiz_enable_refused")
                wsl.enableError != null -> str("drm_wiz_enable_failed") +
                    wsl.enableError.orEmpty().let { if (it.isBlank()) "" else "\n$it" }
                else -> null
            }
            if (problem != null) {
                Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (wsl.enabling) {
                val progress = wsl.enableProgress
                WaitingCard(if (progress != null && progress < 100) str("drm_wiz_engine_downloading", progress) else str("drm_wiz_enabling"))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = { enable(version) }) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str("drm_wiz_enable", version))
                    }
                    // The other version, once the chosen one was declined or could not be turned on.
                    if (problem != null) {
                        val other = if (version == 1) 2 else 1
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { enable(other) }) {
                            Text(str("drm_wiz_enable_other", other))
                        }
                    }
                }
            }
        }
    }
    if (showVersionChoice && !wsl.enabling && !wsl.awaitingReboot) {
        WslVersionChoice(version = wsl.version, onVersion = { wsl.version = it; wsl.versionChosen = true })
    }
}

/** Asks Windows to restart so a just-enabled component takes effect; KittyTune resumes on next launch. */
private fun restartWindows() {
    // A few seconds' grace, as the uninstall assistant gives: the click was deliberate, but not a crash.
    runCatching { ProcessBuilder("shutdown.exe", "/r", "/t", "10").start() }
}

/** The native install's state, kept above the sliding content like the WSL one. */
internal class NativeWizardState {
    var running by mutableStateOf(false)
    var succeeded by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    var log by mutableStateOf("")
    var showLog by mutableStateOf(false)
    var started by mutableStateOf(false)
    val states = MutableList(NativeCdmSetup.Step.entries.size) { StepState.PENDING }
}

private fun nativeStepLabel(step: NativeCdmSetup.Step): String = str(
    when (step) {
        NativeCdmSetup.Step.CHECK -> "drm_wiz_native_step_check"
        NativeCdmSetup.Step.HOST -> "drm_wiz_native_step_host"
        NativeCdmSetup.Step.DOWNLOAD -> "drm_wiz_native_step_download"
        NativeCdmSetup.Step.EXTRACT -> "drm_wiz_native_step_extract"
        NativeCdmSetup.Step.CONFIG -> "drm_wiz_native_step_config"
        NativeCdmSetup.Step.PROBE -> "drm_wiz_native_step_probe"
        NativeCdmSetup.Step.CONNECT -> "drm_wiz_native_step_connect"
    }
)

/**
 * The native install: a download, an extraction, a configuration file and a probe. It starts by
 * itself, because there is nothing to decide and nothing to accept — no administrator, no restart.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NativeSetupStep(state: NativeWizardState) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val start: () -> Unit = start@{
        if (state.running) return@start
        scope.launch {
            state.running = true
            state.succeeded = false
            state.failure = null
            state.log = ""
            for (i in state.states.indices) state.states[i] = StepState.PENDING
            val log = StringBuilder()
            var current: NativeCdmSetup.Step? = null
            try {
                val ok = NativeCdmSetup.provision(
                    onStep = { next ->
                        current?.let { state.states[it.ordinal] = StepState.DONE }
                        current = next
                        state.states[next.ordinal] = StepState.RUNNING
                    },
                    onLine = { line ->
                        synchronized(log) { log.append(line).append('\n') }
                        state.log = synchronized(log) { log.toString() }
                    },
                )
                if (ok) {
                    current?.let { state.states[it.ordinal] = StepState.DONE }
                    state.succeeded = true
                    // Setting the module up is asking for protected tracks.
                    PlayerPreferences().setProtectedTracksEnabled(true)
                } else {
                    current?.let { state.states[it.ordinal] = StepState.FAILED }
                    state.failure = state.log.lineSequence().lastOrNull { it.isNotBlank() }
                        ?: str("drm_wiz_setup_failed", nativeStepLabel(current ?: NativeCdmSetup.Step.CHECK))
                    state.showLog = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                current?.let { state.states[it.ordinal] = StepState.FAILED }
                state.failure = e.message ?: e.javaClass.simpleName
                state.showLog = true
            } finally {
                state.running = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!state.started) {
            state.started = true
            start()
        }
    }

    Hero(MaterialShapes.Cookie4Sided, Icons.Rounded.Terminal)
    StepTitle(str("drm_wiz_native_title"), str("drm_wiz_native_body"))
    ExplainRow(Icons.Rounded.Bolt, str("drm_wiz_native_fast_title"), str("drm_wiz_native_fast"))

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeCdmSetup.Step.entries.forEach { step ->
                SetupStepRow(nativeStepLabel(step), state.states[step.ordinal])
            }
            if (state.running || state.succeeded) {
                val done = state.states.count { it == StepState.DONE }
                val progress by animateFloatAsState(done.toFloat() / state.states.size, label = "native-progress")
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }

    when {
        state.succeeded -> ResultCard(ok = true, text = str("drm_wiz_native_done"))
        state.failure != null -> ResultCard(ok = false, text = state.failure.orEmpty())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!state.running && !state.succeeded) {
            Button(shapes = ButtonDefaults.shapes(), onClick = start) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(str(if (state.failure != null) "btn_retry" else "drm_wiz_setup_start"))
            }
        }
        if (state.log.isNotEmpty()) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { state.showLog = !state.showLog }) {
                Text(str(if (state.showLog) "drm_wiz_details_hide" else "drm_wiz_details_show"))
            }
        }
    }
    AnimatedVisibility(state.showLog && state.log.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        LogBox(state.log, onCopy = { clipboard.setText(AnnotatedString(state.log)) })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WslSetupStep(wsl: WslWizardState) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val start: () -> Unit = start@{
        if (wsl.running) return@start
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
                    version = wsl.version,
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
                // Setting WSL up is asking for protected tracks, including from the settings' own button.
                PlayerPreferences().setProtectedTracksEnabled(true)
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

    // Resuming after the reboot the feature needed: the setup picks up by itself, no button to find.
    LaunchedEffect(Unit) {
        val pending = WslSidecarSetup.pendingVersion()
        if (pending != null && WslSidecarSetup.pendingReady() && !wsl.running && !wsl.succeeded && wsl.failure == null) {
            wsl.version = pending
            start()
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
                else -> "drm_wiz_done_native"
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
                                passed = DrmSelfTest.run { line ->
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
        WslSidecarSetup.Step.DOWNLOAD -> "drm_wiz_step_download"
        WslSidecarSetup.Step.IMPORT -> "drm_wiz_step_import"
        WslSidecarSetup.Step.CDM -> "drm_wiz_step_cdm"
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

/** Something the user has to do next, said neutrally: neither a success nor an error. */
@Composable
internal fun InfoCard(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
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
