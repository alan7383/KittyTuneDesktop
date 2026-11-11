@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.profile.ColorPaletteContent
import com.alananasss.kittytune.ui.profile.LocalMediaSettingsScreen
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.PaletteStyle
import com.alananasss.kittytune.ui.theme.parseMaterialKolorColorSpec
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.UserInfo
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.ui.login.LoginScreen
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.ui.profile.DiscordLoginScreen
import com.alananasss.kittytune.ui.profile.DrmSetupWizard
import com.alananasss.kittytune.ui.profile.DrmWizardStart

/** Replay is explicit and never clears the completed flag or the user's preferences. */
object SetupLauncher {
    var requested by mutableStateOf(false)
}

internal enum class SetupStep(val key: String, val icon: ImageVector) {
    WELCOME("welcome", Icons.Rounded.WavingHand),
    APPEARANCE("appearance", Icons.Rounded.Palette),
    ACCOUNT("account", Icons.Rounded.AccountCircle),
    DESKTOP("desktop", Icons.Rounded.DesktopWindows),
    LIBRARY("library", Icons.Rounded.FolderOpen),
    DISCORD("discord", Icons.Rounded.Forum),
    PROTECTED("protected", Icons.Rounded.LockOpen),
    READY("ready", Icons.Rounded.Celebration)
}

/** What the account step is showing; switching between them is animated, so each needs a name. */
private enum class AccountView { LOGIN, CONNECTED, GUEST, CHOICE }

/**
 * Subscribes the calling composable to every preference change.
 *
 * [PlayerPreferences] reads are plain function calls that Compose cannot observe, so a page that shows a
 * preference reads this first and is recomposed when one is written.
 */
@Composable
private fun observePreferences(): Any = Prefs.flow.collectAsState().value

@Composable
fun SetupScreen(onSetupComplete: () -> Unit, onCancel: (() -> Unit)? = null, initialStep: Int = 0,
    soundCloudLoginContent: @Composable (onSuccess: () -> Unit, onCancel: () -> Unit) -> Unit = { success, cancel ->
        LoginScreen(onLoginSuccess = success, onBackClick = cancel, embedded = true)
    },
    loadSoundCloudProfile: suspend () -> com.alananasss.kittytune.domain.User = {
        com.alananasss.kittytune.data.network.RetrofitClient.create().getMe()
    }
) {
    val prefs = remember { PlayerPreferences() }
    var index by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(0, SetupStep.entries.lastIndex)) }
    // Which way the last move went, so a page enters from the side the setup is travelling towards.
    var direction by remember { mutableIntStateOf(1) }
    var soundCloudLogin by remember { mutableStateOf(false) }
    var guestConfirmation by remember { mutableStateOf(false) }
    var accountConnected by remember { mutableStateOf(TokenManager.hasAccessToken()) }
    var profile by remember { mutableStateOf<com.alananasss.kittytune.domain.User?>(null) }
    var profileError by remember { mutableStateOf(false) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var accountChosen by remember { mutableStateOf(TokenManager.hasAccessToken() || TokenManager.isGuestMode()) }
    var discordLogin by remember { mutableStateOf(false) }
    var discordProfile by remember { mutableStateOf<Pair<String, UserInfo>?>(null) }
    var drmWizard by remember { mutableStateOf(false) }
    var customTheme by remember { mutableStateOf(false) }
    // Keep all choices above the external flows: returning never resets the step or its selections.
    observePreferences()
    val step = SetupStep.entries[index]
    val discordToken = prefs.getDiscordToken()?.takeIf { it.isNotBlank() }
    val visibleDiscordProfile = discordProfile?.takeIf { it.first == discordToken }?.second
    LaunchedEffect(step, discordToken, discordLogin) {
        if (step == SetupStep.DISCORD && discordToken != null && !discordLogin) {
            val info = KizzyRPC.getUserInfo(discordToken).getOrNull()
            if (info != null && prefs.getDiscordToken() == discordToken) {
                discordProfile = discordToken to info
            }
        }
    }
    LaunchedEffect(step, accountConnected, soundCloudLogin, profileRevision) {
        if (step == SetupStep.ACCOUNT && accountConnected && !soundCloudLogin) {
            profileError = false
            try {
                profile = loadSoundCloudProfile().also { TokenManager.rememberActiveAccount(it.id, it.username.orEmpty(), it.avatarUrl) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                profileError = true
            }
        }
    }
    if (customTheme) SetupThemeDialog { customTheme = false }
    if (drmWizard) DrmSetupWizard(DrmWizardStart.INTRO) { drmWizard = false }
    val go = { target: Int ->
        val next = target.coerceIn(0, SetupStep.entries.lastIndex)
        if (next != index) {
            direction = if (next > index) 1 else -1
            soundCloudLogin = false
            discordLogin = false
            index = next
        }
    }
    val finish = {
        if (!TokenManager.hasAccessToken() && !TokenManager.isGuestMode()) {
            go(SetupStep.ACCOUNT.ordinal)
        } else {
            prefs.setHasCompletedSetup(true)
            Prefs.flush()
            onSetupComplete()
        }
    }
    val back: () -> Unit = {
        if (soundCloudLogin) soundCloudLogin = false else if (discordLogin) discordLogin = false else if (index > 0) go(index - 1) else onCancel?.invoke()
    }
    com.alananasss.kittytune.core.BackHandler(enabled = !customTheme && !drmWizard && (index > 0 || onCancel != null)) { back() }
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background)) {
        val wide = maxWidth >= 900.dp
        val roomy = wide && maxHeight >= 780.dp
        Row(Modifier.widthIn(max = 1120.dp).heightIn(max = 820.dp).fillMaxSize().align(Alignment.Center)
            .padding(if (wide) 20.dp else 12.dp).testTag("setupFrame"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (wide) SetupHero(step, Modifier.width(340.dp).fillMaxHeight())
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(if (wide) 40.dp else 28.dp), color = cs.surfaceContainerLow) {
                Column(Modifier.padding(if (wide) 28.dp else 20.dp)) {
                    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        StepIndicator(index, onSelect = go)
                        Spacer(Modifier.weight(1f))
                        if (onCancel != null) FilledTonalIconButton(modifier = Modifier.testTag("setupClose"), onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, str("btn_cancel"))
                        }
                    }
                    Spacer(Modifier.height(if (roomy) 20.dp else 12.dp))
                    // The outgoing page only steps aside; the incoming one arrives element by element (see Reveal).
                    AnimatedContent(index, modifier = Modifier.weight(1f), transitionSpec = {
                        val way = if (targetState > initialState) 1 else -1
                        fadeIn(tween(1)) togetherWith
                            (fadeOut(tween(110)) + slideOutHorizontally(tween(160)) { -it / 14 * way }) using SizeTransform(clip = false)
                    }, label = "setupPage") { shown ->
                        val page = SetupStep.entries[shown]
                        val scroll = rememberScrollState()
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().padding(end = 14.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (!wide) Reveal(0, direction) {
                                    MorphingShape(page.polygon, cs.primaryContainer, Modifier.size(64.dp)) { StepIcon(page, 28.dp, cs.onPrimaryContainer) }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Reveal(0, direction) {
                                        Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                                            Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StepIcon(page, 16.dp, cs.onSecondaryContainer)
                                                Text(if (wide) str("onboard_" + page.key + "_label")
                                                    else "${shown + 1} / ${SetupStep.entries.size}  ·  " + str("onboard_" + page.key + "_label"),
                                                    style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                    Reveal(1, direction) {
                                        Text(str("onboard_" + page.key + "_title"), fontWeight = FontWeight.Bold,
                                            style = if (roomy) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge)
                                    }
                                    Reveal(2, direction) {
                                        Text(str("onboard_" + page.key + "_body"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                                    }
                                }
                                when (page) {
                                    SetupStep.WELCOME -> WelcomePage(prefs, direction)
                                    SetupStep.APPEARANCE -> AppearancePage(prefs, direction, onCustom = { prefs.setTrackDynamicTheme(false); customTheme = true })
                                    SetupStep.ACCOUNT -> Reveal(3, direction) {
                                        val view = when {
                                            soundCloudLogin -> AccountView.LOGIN
                                            accountConnected -> AccountView.CONNECTED
                                            guestConfirmation -> AccountView.GUEST
                                            else -> AccountView.CHOICE
                                        }
                                        AnimatedContent(view, transitionSpec = { swapInPlace() }, label = "setupAccount") { shownView ->
                                            when (shownView) {
                                                AccountView.LOGIN -> soundCloudLoginContent({
                                                    soundCloudLogin = false
                                                    accountChosen = true
                                                    accountConnected = true
                                                    guestConfirmation = false
                                                    profile = null
                                                    profileRevision++
                                                }, { soundCloudLogin = false })
                                                AccountView.CONNECTED -> SoundCloudAccountConfirmation(profile, profileError,
                                                    onRetry = { profileRevision++ }, onChangeAccount = { soundCloudLogin = true },
                                                    onGuest = {
                                                        TokenManager.continueAsGuest()
                                                        accountConnected = false
                                                        accountChosen = true
                                                        profile = null
                                                        profileError = false
                                                        guestConfirmation = true
                                                    })
                                                AccountView.GUEST -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    SetupNote(Icons.Rounded.Person, str("onboard_guest_mode"), str("onboard_guest_selected"))
                                                    FilledTonalButton(onClick = { soundCloudLogin = true }, shapes = ButtonDefaults.shapes(),
                                                        modifier = Modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                                                        Icon(painterResource("icons/soundcloud.svg"), null, Modifier.size(28.dp, 18.dp))
                                                        Spacer(Modifier.width(12.dp)); Text(str("login_soundcloud"), style = MaterialTheme.typography.titleMedium)
                                                    }
                                                }
                                                AccountView.CHOICE -> AccountChoices(
                                                    onSoundCloud = { soundCloudLogin = true },
                                                    onGuest = {
                                                        TokenManager.setGuestMode(true)
                                                        accountChosen = true
                                                        go(index + 1)
                                                    })
                                            }
                                        }
                                    }
                                    SetupStep.DESKTOP -> DesktopPage(prefs, direction)
                                    SetupStep.LIBRARY -> Reveal(3, direction) { LocalMediaSettingsScreen(horizontalPadding = 0.dp) }
                                    SetupStep.DISCORD -> Reveal(3, direction) {
                                        AnimatedContent(discordLogin, transitionSpec = { swapInPlace() }, label = "setupDiscord") { loggingIn ->
                                            if (loggingIn) {
                                                DiscordLoginScreen(
                                                    onBackClick = { discordLogin = false },
                                                    onLoginSuccess = { discordLogin = false },
                                                    embedded = true,
                                                    onAccountConnected = { name, avatar ->
                                                        val token = prefs.getDiscordToken()
                                                        if (token != null && name != null) {
                                                            discordProfile = token to UserInfo(name, name, avatar)
                                                        }
                                                    }
                                                )
                                            } else {
                                                DiscordAccount(prefs, discordToken != null, visibleDiscordProfile, onConnect = { discordLogin = true })
                                            }
                                        }
                                    }
                                    SetupStep.PROTECTED -> ProtectedPage(direction, onConfigure = { drmWizard = true })
                                    SetupStep.READY -> ReadyPage(direction)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimatedVisibility(index > 0,
                            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
                            exit = scaleOut(tween(120)) + fadeOut(tween(120))) {
                            FilledTonalIconButton(onClick = back, modifier = Modifier.size(56.dp).testTag("setupBack"), shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("onboard_back"))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(step == SetupStep.DISCORD || step == SetupStep.PROTECTED || step == SetupStep.LIBRARY,
                            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.8f),
                            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f)) {
                            TextButton(modifier = Modifier.heightIn(min = 56.dp).testTag("setupSkip"), onClick = { go(index + 1) },
                                shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 20.dp)) {
                                Text(str("onboard_skip"), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        val last = step == SetupStep.READY
                        Button(enabled = !soundCloudLogin && !discordLogin && (step != SetupStep.ACCOUNT || accountChosen),
                            onClick = { if (last) finish() else go(index + 1) }, shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(start = 28.dp, end = 22.dp),
                            modifier = Modifier.testTag("setupNext").heightIn(min = 56.dp).animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))) {
                            AnimatedContent(last, transitionSpec = { swapInPlace() }, label = "setupNextLabel") { finishing ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(str(if (finishing) "onboard_finish" else "onboard_next"), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.width(10.dp))
                                    Icon(if (finishing) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A quick fade with a small spring pop, for content replaced where it stands rather than moved across the page. */
internal fun <S> AnimatedContentTransitionScope<S>.swapInPlace(): ContentTransform =
    (fadeIn(tween(180, 60)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.94f)) togetherWith
        fadeOut(tween(90)) using SizeTransform(clip = false)

@Composable
private fun WelcomePage(prefs: PlayerPreferences, direction: Int) {
    observePreferences()
    val cs = MaterialTheme.colorScheme
    val names = listOf("System", "Français", "English", "Deutsch", "Magyar", "Русский", "Tiếng Việt", "Türkçe")
    val current = prefs.getAppLanguage()
    Reveal(3, direction) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShapeBadge(MaterialShapes.Cookie9Sided, cs.secondaryContainer, size = 44.dp) {
                        Icon(Icons.Rounded.Language, null, Modifier.size(24.dp), tint = cs.onSecondaryContainer)
                    }
                    Text(str("pref_language"), style = MaterialTheme.typography.titleMedium)
                }
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEachIndexed { i, language ->
                        ToggleButton(checked = language == current,
                            shapes = ToggleButtonShapes(shape = CircleShape, pressedShape = RoundedCornerShape(10.dp), checkedShape = RoundedCornerShape(16.dp)),
                            onCheckedChange = { prefs.setAppLanguage(language); Strings.appLanguage = language.code },
                            colors = ToggleButtonDefaults.colors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurfaceVariant,
                                checkedContainerColor = cs.primary, checkedContentColor = cs.onPrimary)) {
                            Text(if (i == 0) str("theme_system") else names[i])
                        }
                    }
                }
            }
        }
    }
    Reveal(4, direction) { SetupNote(Icons.Rounded.AutoAwesome, str("onboard_choices_title"), str("onboard_choices_body")) }
}

@Composable
private fun AppearancePage(prefs: PlayerPreferences, direction: Int, onCustom: () -> Unit) {
    observePreferences()
    val cs = MaterialTheme.colorScheme
    val seed = prefs.getKeyColor()
    val style = prefs.getColorStyle()
    val dark = when (prefs.getThemeMode()) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palettes = listOf("ocean" to 0xFF1565C0, "forest" to 0xFF2E7D32, "sunset" to 0xFFE64A19, "rose" to 0xFFD81B60, "lavender" to 0xFF7E57C2, "mint" to 0xFF00A884)
    Reveal(3, direction) {
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            palettes.forEach { (name, color) ->
                val selected = seed == color.toInt() && style == "TonalSpot"
                val scheme = rememberDynamicColorScheme(seedColor = Color(color), isDark = dark,
                    isAmoled = prefs.getPureBlack(), style = PaletteStyle.TonalSpot,
                    specVersion = parseMaterialKolorColorSpec(prefs.getColorSpec()))
                PaletteSwatch(selected, str("theme_preset_$name"),
                    onClick = { prefs.setKeyColor(color.toInt()); prefs.setColorStyle("TonalSpot"); prefs.setTrackDynamicTheme(false) },
                    primary = scheme.primary, secondary = scheme.secondary, tertiary = scheme.tertiary) {
                    AnimatedVisibility(selected,
                        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(tween(90)),
                        exit = scaleOut(tween(100)) + fadeOut(tween(100))) {
                        Box(Modifier.size(24.dp).background(scheme.onPrimary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = scheme.primary)
                        }
                    }
                }
            }
            val custom = palettes.none { it.second.toInt() == seed } || style != "TonalSpot"
            PaletteSwatch(custom, str("theme_custom_short"), onClick = onCustom, modifier = Modifier.testTag("setupCustomTheme"),
                primary = if (custom) cs.primary else cs.surfaceContainerHighest) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp), tint = if (custom) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
    Reveal(4, direction) {
        val labels = mapOf(AppThemeMode.SYSTEM to "theme_system", AppThemeMode.LIGHT to "theme_light", AppThemeMode.DARK to "theme_dark")
        ExpressiveConnectedButtonGroup(
            options = labels.keys.toList(), selectedOption = prefs.getThemeMode(), onOptionSelected = { prefs.setThemeMode(it) },
            fillMaxWidth = true, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            uncheckedContainerColor = cs.surfaceContainerHigh, iconSpacing = 8.dp,
            labelProvider = { Text(str(labels.getValue(it)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            iconProvider = {
                Icon(when (it) {
                    AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                    AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                    AppThemeMode.DARK -> Icons.Rounded.DarkMode
                }, null, Modifier.size(20.dp))
            })
    }
    Reveal(5, direction) {
        SettingsGroup(horizontalPadding = 0.dp, items = buildList {
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) add { shape ->
                SettingsItem(shape = shape, icon = Icons.Rounded.Palette,
                    title = str("setup_windows_color"), subtitle = str("setup_use_windows_color"),
                    hasSwitch = true, switchState = style.contains("windows", ignoreCase = true),
                    onSwitchChange = { prefs.setColorStyle(if (it) "Windows Accent" else "TonalSpot") })
            }
            add { shape -> SettingsItem(shape = shape, icon = Icons.Rounded.DarkMode,
                title = str("onboard_amoled"), subtitle = str("onboard_amoled_body"),
                hasSwitch = true, switchState = prefs.getPureBlack(), onSwitchChange = { prefs.setPureBlack(it) }) }
        })
    }
}

@Composable
private fun DesktopPage(prefs: PlayerPreferences, direction: Int) {
    observePreferences()
    Reveal(3, direction) {
        SettingsGroup(horizontalPadding = 0.dp, items = buildList {
            add { shape -> SettingsItem(shape = shape, icon = Icons.Rounded.Headphones,
                title = str("onboard_background"), subtitle = str("onboard_background_body"),
                hasSwitch = true, switchState = !prefs.getStopOnTaskClear(), onSwitchChange = { prefs.setStopOnTaskClear(!it) }) }
            add { shape -> SettingsItem(shape = shape, icon = Icons.Rounded.QueueMusic,
                title = str("pref_persist_queue"), subtitle = str("pref_persist_queue_sub"),
                hasSwitch = true, switchState = prefs.getPersistentQueueEnabled(), onSwitchChange = { prefs.setPersistentQueueEnabled(it) }) }
            if (prefs.getPersistentQueueEnabled()) add { shape -> SettingsItem(shape = shape, icon = Icons.Rounded.History,
                title = str("pref_save_position"), subtitle = str("pref_save_position_sub"),
                hasSwitch = true, switchState = prefs.getSavePositionEnabled(), onSwitchChange = { prefs.setSavePositionEnabled(it) }) }
            add { shape -> SettingsItem(shape = shape, icon = Icons.Rounded.SystemUpdate,
                title = str("pref_auto_update"), subtitle = str("onboard_update_body"),
                hasSwitch = true, switchState = prefs.getAutoUpdateEnabled(), onSwitchChange = { prefs.setAutoUpdateEnabled(it) }) }
        })
    }
}

@Composable
private fun ProtectedPage(direction: Int, onConfigure: () -> Unit) {
    Reveal(3, direction) { SetupNote(Icons.Rounded.Lock, str("onboard_drm_what"), str("onboard_drm_explanation")) }
    Reveal(4, direction) {
        FilledTonalButton(onClick = onConfigure, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp)) {
            Icon(Icons.Rounded.Build, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
            Text(str("onboard_drm_action"), style = MaterialTheme.typography.titleMedium)
        }
    }
    Reveal(5, direction) {
        Text(str("onboard_drm_later"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyPage(direction: Int) {
    Reveal(3, direction) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SetupNote(Icons.Rounded.Bookmark, str("onboard_saved_title"), str("onboard_saved_body"),
                shape = getSettingsShape(2, 0), polygon = MaterialShapes.Cookie9Sided)
            SetupNote(Icons.Rounded.Replay, str("onboard_replay"), str("onboard_replay_body"),
                shape = getSettingsShape(2, 1), polygon = MaterialShapes.Clover4Leaf)
        }
    }
}

/** The two ways in, as tiles side by side where there is room for them. */
@Composable
internal fun AccountChoices(onSoundCloud: () -> Unit, onGuest: () -> Unit, soundCloudTitle: String = str("login_soundcloud")) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val soundCloud: @Composable (Modifier) -> Unit = { modifier ->
            ChoiceTile(soundCloudTitle, onSoundCloud, modifier.testTag("setupSoundCloud"),
                container = cs.primaryContainer, contentColor = cs.onPrimaryContainer,
                polygon = MaterialShapes.Cookie9Sided, badge = cs.primary) {
                Icon(painterResource("icons/soundcloud.svg"), null, Modifier.size(32.dp, 20.dp), tint = cs.onPrimary)
            }
        }
        val guest: @Composable (Modifier) -> Unit = { modifier ->
            ChoiceTile(str("login_guest"), onGuest, modifier.testTag("setupGuest"),
                container = cs.surfaceContainerHigh, contentColor = cs.onSurface,
                polygon = MaterialShapes.Clover4Leaf, badge = cs.secondaryContainer) {
                Icon(Icons.Rounded.Person, null, Modifier.size(28.dp), tint = cs.onSecondaryContainer)
            }
        }
        if (maxWidth >= 520.dp) Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            soundCloud(Modifier.weight(1f).fillMaxHeight())
            guest(Modifier.weight(1f).fillMaxHeight())
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            soundCloud(Modifier.fillMaxWidth())
            guest(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChoiceTile(
    title: String, onClick: () -> Unit, modifier: Modifier, container: Color, contentColor: Color,
    polygon: RoundedPolygon, badge: Color, icon: @Composable BoxScope.() -> Unit
) {
    ExpressiveTile(onClick, modifier, container, contentColor) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            ShapeBadge(polygon, badge, size = 60.dp, content = icon)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun SoundCloudAccountConfirmation(
    profile: com.alananasss.kittytune.domain.User?, error: Boolean,
    onRetry: () -> Unit, onChangeAccount: () -> Unit, onGuest: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(40.dp), tint = cs.onSecondaryContainer)
                    if (!profile?.avatarUrl.isNullOrBlank()) coil3.compose.AsyncImage(
                        model = profile?.avatarUrl, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource("icons/soundcloud.svg"), null, Modifier.size(24.dp, 14.dp), tint = cs.onSurfaceVariant)
                        Text("SoundCloud", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                    if (profile != null) Text(profile.username.orEmpty(), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else if (error) Text(str("error_generic"))
                    else LoadingIndicator(Modifier.size(36.dp))
                    Surface(shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                            Text(str("onboard_account_success"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (error) TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(str("btn_retry")) }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onChangeAccount, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(str("onboard_account_switch"))
                }
                TextButton(onClick = onGuest, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(str("onboard_guest_mode"))
                }
            }
        }
    }
}

@Composable
private fun DiscordAccount(prefs: PlayerPreferences, connected: Boolean, profile: UserInfo?, onConnect: () -> Unit) {
    observePreferences()
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(painterResource("icons/discord.svg"), null, Modifier.size(34.dp), tint = cs.onSecondaryContainer)
                        if (profile?.avatarUrl != null) coil3.compose.AsyncImage(
                            model = profile.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile?.username ?: "Discord", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (connected) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = cs.primary)
                            Text(str(if (connected) "setup_discord_connected" else "setup_discord_not_connected"),
                                style = MaterialTheme.typography.bodyMedium, color = if (connected) cs.primary else cs.onSurfaceVariant)
                        }
                    }
                }
                val label: @Composable RowScope.() -> Unit = {
                    Icon(painterResource("icons/discord.svg"), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(str(if (connected) "setup_discord_change" else "setup_discord_connect"))
                }
                if (connected) FilledTonalButton(onClick = onConnect, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp), content = label)
                else Button(onClick = onConnect, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp), content = label)
            }
        }
        if (connected) SettingsItem(shape = RoundedCornerShape(28.dp), title = str("setup_discord_rpc"), subtitle = str("setup_discord_rpc_desc"),
            icon = Icons.Rounded.MusicNote, hasSwitch = true, switchState = prefs.getDiscordRpcEnabled(), onSwitchChange = { prefs.setDiscordRpcEnabled(it) })
    }
}

@Composable
private fun SetupNote(
    icon: ImageVector, title: String, body: String,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.dp),
    polygon: RoundedPolygon = MaterialShapes.Cookie6Sided,
) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = shape, color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShapeBadge(polygon, cs.tertiaryContainer) { Icon(icon, null, Modifier.size(24.dp), tint = cs.onTertiaryContainer) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** The existing custom palette screen, hosted here without leaving the onboarding. Changes apply live. */
@Composable
private fun SetupThemeDialog(onClose: () -> Unit) {
    com.alananasss.kittytune.core.BackHandler(onBack = onClose)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SetupThemePopupContent(onClose)
    }
}

/** The desktop dialog fills the window, so its backdrop needs an explicit dismiss target. */
@Composable
internal fun SetupThemePopupContent(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose
        ))
        // Consume blank-space clicks inside the editor while its controls handle their own input.
        Box(Modifier.padding(24.dp).widthIn(max = 780.dp).heightIn(max = 740.dp).fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}
        )) {
            SetupThemeEditor(onClose)
        }
    }
}

@Composable
internal fun SetupThemeEditor(onClose: () -> Unit) {
    DisposableEffect(Unit) {
        onDispose { com.alananasss.kittytune.ui.theme.ThemeState.previewKeyColor = null }
    }
    Surface(
        Modifier.widthIn(max = 780.dp).heightIn(max = 740.dp).fillMaxSize().testTag("setupThemeDialog"),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str("color_palette_screen_title"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, str("btn_cancel"))
                }
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().padding(end = 14.dp).verticalScroll(scroll)) { ColorPaletteContent() }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            Button(onClick = onClose, shapes = ButtonDefaults.shapes(), modifier = Modifier.align(Alignment.End)) {
                Text(str("onboard_custom_done"))
            }
        }
    }
}
