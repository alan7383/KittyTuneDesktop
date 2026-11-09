package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.drm.WslSidecarSetup
import com.alananasss.kittytune.data.drm.WslSidecarSetup.UninstallStep
import kotlinx.coroutines.launch

private enum class UninstallPage { WHAT, REMOVE, DONE }

/** State kept above the sliding pages, so going back does not lose a check that took seconds. */
internal class UninstallState {
    /** Also turn the Windows component back off (admin + restart); off by default — it is harmless to leave. */
    var removeFeature by mutableStateOf(false)
    /**
     * The WSL version (1 or 2) whose component that choice turns off — the one KittyTune's distribution
     * ran on — or null when it is not offered (off already, or another distribution still needs it).
     */
    var featureVersion by mutableStateOf<Int?>(null)

    /** What the Windows side allows (remove WSL entirely, or one component); null while checking. */
    var windowsSide by mutableStateOf<WslSidecarSetup.WindowsSide?>(null)

    /** Remove WSL itself too, for a system as it was before. Takes every other distribution with it. */
    var removeWsl by mutableStateOf(false)

    /** The user's other distributions ticked for deletion — never by default; always shown, in red. */
    var deleteDistros by mutableStateOf<Set<String>>(emptySet())

    /** Also turn the virtual machine platform off when removing WSL, when it was on before KittyTune. */
    var disablePlatform by mutableStateOf(false)

    /** Whether the removal that ran included WSL itself, for the messages after it. */
    var wslRemoved by mutableStateOf(false)

    /** WSL goes only when every other distribution is ticked for deletion too: nothing is lost unseen. */
    val removingWsl: Boolean
        get() = removeWsl && windowsSide?.wslPresent == true &&
            windowsSide?.otherDistros.orEmpty().all { it in deleteDistros }

    /**
     * The single-component choice makes sense only when WSL stays because other distributions are kept:
     * when nothing else is there, removing WSL covers it, and two overlapping boxes would only confuse.
     */
    val featureChoiceShown: Boolean
        get() = !removeWsl && windowsSide?.otherDistros.orEmpty().isNotEmpty()

    /** What [WslSidecarSetup.uninstall] is asked to turn off on its own: only that choice, when shown and ticked. */
    val featureToRemove: Int? get() = featureVersion?.takeIf { removeFeature && featureChoiceShown }

    /** Whether KittyTune's own distribution is there; null while checking. */
    var ourDistroPresent by mutableStateOf<Boolean?>(null)

    /** KittyTune files found in other distributions; null while the scan runs. */
    var leftovers by mutableStateOf<List<WslSidecarSetup.Leftover>?>(null)

    /** The distributions whose leftovers are ticked for removal (all of them by default: they are ours). */
    var cleanDistros by mutableStateOf<Set<String>>(emptySet())

    val chosenLeftovers: List<WslSidecarSetup.Leftover>
        get() = leftovers.orEmpty().filter { it.distro in cleanDistros }

    var steps by mutableStateOf<List<UninstallStep>>(emptyList())
    val states = mutableStateListOf<StepState>()
    var running by mutableStateOf(false)
    var succeeded by mutableStateOf(false)
    var restartNeeded by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
    var log by mutableStateOf("")
    var showLog by mutableStateOf(false)
}

/**
 * Removes the WSL method from the PC, for whoever chose it and wants their machine back as it was.
 *
 * It says first, in plain words, what goes: KittyTune's own Linux image (the `kittytune-cdm`
 * distribution) and its link in KittyTune. Turning back off the Windows component it ran on (WSL1's, or
 * WSL2's virtual machine platform) is offered as a choice, unticked, and only when no other distribution
 * runs on it. Then it does all of it, and protected tracks go back to PlayReady.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WslUninstallWizard(onClose: () -> Unit) {
    val un = remember { UninstallState() }
    var page by remember { mutableStateOf(UninstallPage.WHAT) }
    val pages = UninstallPage.entries
    val index = pages.indexOf(page)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        un.ourDistroPresent = WslSidecarSetup.imported()
        val side = WslSidecarSetup.planWindowsSide()
        un.windowsSide = side
        un.featureVersion = side.featureVersion
        if (un.featureVersion == null) un.removeFeature = false
        // Files an older version, a script or a manual install left in other distributions.
        val found = WslSidecarSetup.findLeftovers()
        un.cleanDistros = found.map { it.distro }.toSet()
        un.leftovers = found
    }

    val start: () -> Unit = start@{
        if (un.leftovers == null || un.windowsSide == null) return@start
        val deleteDistros = un.windowsSide?.otherDistros.orEmpty().filter { it in un.deleteDistros }
        val leftovers = un.chosenLeftovers.filterNot { it.distro in deleteDistros }
        val removeWsl = un.removingWsl
        un.wslRemoved = removeWsl
        un.steps = WslSidecarSetup.uninstallSteps(
            un.featureToRemove != null, leftovers.isNotEmpty(), removeWsl, deleteDistros.isNotEmpty(),
        )
        un.states.clear()
        un.states.addAll(List(un.steps.size) { StepState.PENDING })
        un.failure = null
        un.log = ""
        un.running = true
        scope.launch {
            val log = StringBuilder()
            var current: UninstallStep? = null
            try {
                un.restartNeeded = WslSidecarSetup.uninstall(
                    featureVersion = un.featureToRemove,
                    leftovers = leftovers,
                    removeWsl = removeWsl,
                    deleteDistros = deleteDistros,
                    disablePlatform = removeWsl && un.disablePlatform,
                    onStep = { next ->
                        current?.let { un.states[un.steps.indexOf(it)] = StepState.DONE }
                        current = next
                        un.states[un.steps.indexOf(next)] = StepState.RUNNING
                    },
                    onLine = { line ->
                        synchronized(log) { log.append(line).append('\n') }
                        un.log = synchronized(log) { log.toString() }
                    },
                )
                current?.let { un.states[un.steps.indexOf(it)] = StepState.DONE }
                un.succeeded = true
            } catch (e: WslSidecarSetup.UninstallException) {
                un.states[un.steps.indexOf(e.step)] = StepState.FAILED
                un.failure = if (e.refused) {
                    if (e.step == UninstallStep.WSL) str("drm_unwiz_wsl_refused") else str("drm_unwiz_refused", un.featureVersion ?: 1)
                }
                else str("drm_unwiz_failed", uninstallStepLabel(e.step, un.featureVersion)) + "\n" + e.message
                un.showLog = !e.refused
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                current?.let { un.states[un.steps.indexOf(it)] = StepState.FAILED }
                un.failure = e.message ?: e.javaClass.simpleName
                un.showLog = true
            } finally {
                un.running = false
            }
        }
    }

    // Nothing may interrupt a removal halfway: no closing, no going back while it runs.
    val close = { if (!un.running) onClose() }
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
                    StepDots(count = pages.size, current = index, modifier = Modifier.weight(1f))
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = close, enabled = !un.running) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                    }
                }

                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(tween(380)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(380)))
                            .togetherWith(slideOutHorizontally(tween(380)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(250)))
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "wsl-uninstall",
                ) { shown ->
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (shown) {
                            UninstallPage.WHAT -> WhatPage(un)
                            UninstallPage.REMOVE -> RemovePage(un, onRetry = start)
                            UninstallPage.DONE -> DonePage(un)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back only from a removal that stopped: once something is removed there is no going back
                    // to choose, and while it runs nothing may interrupt it.
                    if (page == UninstallPage.REMOVE && !un.running && !un.succeeded) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { page = UninstallPage.WHAT }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("btn_back"))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    when (page) {
                        UninstallPage.WHAT -> Button(
                            shapes = ButtonDefaults.shapes(),
                            // Not before the scan has said what there is to remove.
                            enabled = un.leftovers != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.height(48.dp),
                            onClick = {
                                page = UninstallPage.REMOVE
                                start()
                            },
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("drm_unwiz_start"), fontWeight = FontWeight.Bold)
                        }
                        UninstallPage.REMOVE -> Button(
                            shapes = ButtonDefaults.shapes(),
                            enabled = un.succeeded,
                            modifier = Modifier.height(48.dp),
                            onClick = { page = UninstallPage.DONE },
                        ) {
                            Text(str("drm_wiz_next"), fontWeight = FontWeight.Bold)
                        }
                        UninstallPage.DONE -> Button(
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.height(48.dp),
                            onClick = onClose,
                        ) {
                            Text(str("drm_wiz_finish"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WhatPage(un: UninstallState) {
    Hero(MaterialShapes.Cookie9Sided, Icons.Rounded.DeleteSweep)
    StepTitle(str("drm_unwiz_title"), str("drm_unwiz_body"))

    if (un.ourDistroPresent != false) {
        ExplainRow(Icons.Rounded.Folder, str("drm_unwiz_files_title", WslSidecarSetup.DISTRO), str("drm_unwiz_files_body"))
    }
    ExplainRow(Icons.Rounded.Settings, str("drm_unwiz_settings_title"), str("drm_unwiz_settings_body"))

    // KittyTune files outside the official setup, in the user's other distributions: theirs to tick off.
    // Not for a distribution ticked for deletion below: deleting it takes them anyway.
    val leftovers = un.leftovers
    if (leftovers == null) {
        WaitingCard(str("drm_unwiz_scanning"))
    } else {
        leftovers.filterNot { it.distro in un.deleteDistros }.forEach { leftover ->
            ChoiceCard(
                checked = leftover.distro in un.cleanDistros,
                title = str("drm_unwiz_leftover_title", leftover.distro),
                body = str("drm_unwiz_leftover_body") + "\n" + leftover.paths.joinToString("\n"),
                warning = false,
                onToggle = { on ->
                    un.cleanDistros = if (on) un.cleanDistros + leftover.distro else un.cleanDistros - leftover.distro
                },
            )
        }
    }

    // The user's other distributions — an Ubuntu an older KittyTune installed, or their own. Deleting one
    // erases everything in it, so never ticked by default, always in red. Removing WSL needs them all gone.
    val side = un.windowsSide
    side?.otherDistros?.forEach { distro ->
        ChoiceCard(
            checked = distro in un.deleteDistros,
            title = str("drm_unwiz_distro_delete_title", distro),
            body = str("drm_unwiz_distro_delete_body"),
            warning = true,
            onToggle = { on ->
                un.deleteDistros = if (on) un.deleteDistros + distro else un.deleteDistros - distro
                // WSL cannot go while a distribution stays.
                if (!on) un.removeWsl = false
            },
        )
    }

    // WSL itself, for a system as it was before KittyTune. Ticking it ticks the distributions it would take
    // with it, visibly, so nothing is lost unseen.
    if (side != null && side.wslPresent) {
        ChoiceCard(
            checked = un.removingWsl,
            title = str("drm_unwiz_wsl_title"),
            body = str("drm_unwiz_wsl_body") +
                if (side.otherDistros.isNotEmpty()) "\n" + str("drm_unwiz_wsl_needs", side.otherDistros.joinToString(", ")) else "",
            warning = true,
            onToggle = { on ->
                un.removeWsl = on
                if (on) un.deleteDistros = un.deleteDistros + side.otherDistros
            },
        )
        // The VM platform, when it was on before KittyTune: may serve something else, so the user's choice.
        if (un.removingWsl && side.platformOn && !side.platformByApp) {
            ChoiceCard(
                checked = un.disablePlatform,
                title = str("drm_unwiz_platform_title"),
                body = str("drm_unwiz_platform_body"),
                warning = true,
                onToggle = { un.disablePlatform = it },
            )
        }
    }

    // A single component, when WSL stays for the distributions kept. Left on by default (harmless; turning it
    // off needs admin + a restart), and only offered when nothing else runs on it.
    if (un.featureChoiceShown) un.featureVersion?.let { version ->
        ChoiceCard(
            checked = un.removeFeature,
            title = str("drm_unwiz_feature_title", version),
            body = str("drm_unwiz_feature_body"),
            warning = true,
            onToggle = { un.removeFeature = it },
        )
    }
}

/** A titled choice with a checkbox; [warning] colours the text when ticking it deletes something of the user's. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceCard(
    checked: Boolean,
    title: String,
    body: String,
    warning: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (checked && warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = enabled) { onToggle(!checked) }
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = onToggle, enabled = enabled)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (checked && warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RemovePage(un: UninstallState, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Hero(MaterialShapes.Cookie12Sided, Icons.Rounded.DeleteSweep)
    StepTitle(str("drm_unwiz_running_title"), str("drm_unwiz_running_body"))

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            un.steps.forEachIndexed { i, s ->
                SetupStepRow(uninstallStepLabel(s, un.featureVersion), un.states.getOrElse(i) { StepState.PENDING })
            }
            if (un.running || un.succeeded) {
                val done = un.states.count { it == StepState.DONE }
                val progress by animateFloatAsState(
                    if (un.states.isEmpty()) 0f else done.toFloat() / un.states.size,
                    label = "uninstall-progress",
                )
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }

    when {
        un.succeeded -> ResultCard(ok = true, text = str("drm_unwiz_removed"))
        un.failure != null -> ResultCard(ok = false, text = un.failure.orEmpty())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!un.running && !un.succeeded && un.failure != null) {
            Button(shapes = ButtonDefaults.shapes(), onClick = onRetry) {
                Text(str("btn_retry"))
            }
        }
        if (un.log.isNotEmpty()) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { un.showLog = !un.showLog }) {
                Text(str(if (un.showLog) "drm_wiz_details_hide" else "drm_wiz_details_show"))
            }
        }
    }
    AnimatedVisibility(un.showLog && un.log.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        LogBox(un.log, onCopy = { clipboard.setText(AnnotatedString(un.log)) })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DonePage(un: UninstallState) {
    var restarting by remember { mutableStateOf(false) }
    Hero(MaterialShapes.SoftBurst, Icons.Rounded.Verified)
    StepTitle(str("drm_unwiz_done_title"), str("drm_unwiz_done_body"))
    if (un.restartNeeded) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (un.wslRemoved) str("drm_unwiz_wsl_restart") else str("drm_unwiz_restart", un.featureVersion ?: 1),
                    style = MaterialTheme.typography.bodyLarge,
                )
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    enabled = !restarting,
                    onClick = {
                        // The user's own click, after the dialog said why; Windows still gives a few seconds.
                        restarting = true
                        // Nothing of WSL may be open as Windows restarts: it would be relaunched at sign-in,
                        // as the "install WSL" stub.
                        if (un.wslRemoved) WslSidecarSetup.closeWslProcesses()
                        runCatching { ProcessBuilder("shutdown.exe", "/r", "/t", "10").start() }
                            .onFailure { restarting = false }
                    },
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("drm_unwiz_restart_now"))
                }
            }
        }
    }
}

private fun uninstallStepLabel(step: UninstallStep, featureVersion: Int?): String = when (step) {
    UninstallStep.STOP -> str("drm_unwiz_step_stop")
    UninstallStep.DISTRO -> str("drm_unwiz_step_distro")
    UninstallStep.SETTINGS -> str("drm_unwiz_step_settings")
    UninstallStep.LEFTOVERS -> str("drm_unwiz_step_leftovers")
    UninstallStep.DISTROS -> str("drm_unwiz_step_distros")
    UninstallStep.FEATURE -> str("drm_unwiz_step_feature", featureVersion ?: 1)
    UninstallStep.WSL -> str("drm_unwiz_step_wsl")
}
