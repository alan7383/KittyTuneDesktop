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
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Memory
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
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Bolt

/** Where the assistant opens: the whole introduction, or straight at one method's setup. */
enum class DrmWizardStart { INTRO, NATIVE }

private enum class WizardStep { INTRO, HOW, NATIVE_SETUP, DONE }

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
    val steps = when {
        start == DrmWizardStart.NATIVE -> listOf(WizardStep.NATIVE_SETUP, WizardStep.DONE)
        !windows -> listOf(WizardStep.INTRO, WizardStep.HOW, WizardStep.DONE)
        else -> listOf(WizardStep.INTRO, WizardStep.HOW, WizardStep.NATIVE_SETUP, WizardStep.DONE)
    }
    var step by remember { mutableStateOf(steps.first()) }
    val index = steps.indexOf(step).coerceAtLeast(0)

    // The installer keeps its state here, so going back and forth does not restart it.
    val native = remember { NativeWizardState() }

    val close = {
        prefs.setDrmOnboardingDone(true)
        // Whoever went through the assistant needs no announcement of what it explains.
        prefs.setProtectedTracksAnnounced(true)
        onClose()
    }
    val canGoOn = when (step) {
        WizardStep.NATIVE_SETUP -> native.succeeded
        else -> true
    }
    // Leaving the setup while it runs would kill it halfway; going back is offered once it is over.
    val canGoBack = index > 0 && !native.running

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
                            WizardStep.NATIVE_SETUP -> NativeSetupStep(native)
                            WizardStep.DONE -> DoneStep(windows)
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
        str("drm_wiz_how_nobrowser"),
    )
    Text(
        text = str(if (windows) "drm_wiz_how_windows" else "drm_wiz_how_desktop"),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A titled point with a small icon, for the explanation dialog. */
@Composable
private fun InfoSection(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * What this method gives, in one list.
 *
 * It opens with the thing that matters most and is easiest to misread: the only piece of Chrome used
 * is its Widevine module, and Chrome itself never runs — no window, no background process.
 */
@Composable
internal fun MethodPoints() {
    val points = listOf(
        "drm_method_pro_chrome_module",
        "drm_method_pro_light",
        "drm_method_pro_noreboot",
        "drm_method_pro_effects",
        "drm_method_pro_downloads",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { key ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    str(key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** The method's icon in its own shape. */
@Composable
internal fun MethodBadge(size: Int = 44) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie4Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size((size * 0.45f).dp),
        )
    }
}

/** Opens the explanation of how the native module works. */
@Composable
internal fun MethodInfoButton() {
    var open by remember { mutableStateOf(false) }
    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { open = true }) {
        Icon(Icons.Rounded.Description, contentDescription = str("drm_info_open"), modifier = Modifier.size(20.dp))
    }
    if (open) MethodInfoDialog(onDismiss = { open = false })
}

@Composable
internal fun MethodInfoDialog(onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { MethodBadge(size = 48) },
        title = { Text(str("drm_info_native_title"), textAlign = TextAlign.Center) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoSection(Icons.Rounded.Tune, str("drm_info_how"), str("drm_info_native_how"))
                InfoSection(Icons.Rounded.Search, str("drm_info_found"), str("drm_info_native_found"))
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(str("btn_close")) }
        },
    )
}

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
                    // Everything that is drawn is done: a step the installer had no reason to run
                    // (a module already on disk) must not leave an empty circle and a short bar.
                    for (i in state.states.indices) state.states[i] = StepState.DONE
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
internal fun DoneStep(windows: Boolean) {
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
            if (windows) "drm_wiz_done_native" else "drm_wiz_done_desktop"
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
