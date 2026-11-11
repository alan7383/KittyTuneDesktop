@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.login.LoginScreen
import kotlinx.coroutines.launch

/** Opens [AccountScreen] over the app to switch account, without signing anyone out. */
object AccountLauncher {
    var requested by mutableStateOf(false)
}

private enum class AccountStage { CHOICE, LOGIN, FINISHING }

/**
 * Asks who is listening, and nothing else.
 *
 * The setup is shown once. Someone who has been through it and then signs out, a guest who now wants to sign in,
 * or anyone switching account lands here instead: the accounts already on this computer, one click each, and
 * the two ways of adding to them, then straight back into the app.
 *
 * [onCancel] is given when the screen was opened from inside the app, where leaving without choosing is fine.
 */
@Composable
fun AccountScreen(
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,
    soundCloudLoginContent: @Composable (onSuccess: () -> Unit, onCancel: () -> Unit) -> Unit = { success, cancel ->
        LoginScreen(onLoginSuccess = success, onBackClick = cancel, embedded = true)
    },
    loadSoundCloudProfile: suspend () -> com.alananasss.kittytune.domain.User = {
        com.alananasss.kittytune.data.network.RetrofitClient.create().getMe()
    },
) {
    var stage by remember { mutableStateOf(AccountStage.CHOICE) }
    val accounts by TokenManager.accounts.collectAsState()
    val scope = rememberCoroutineScope()
    com.alananasss.kittytune.core.BackHandler(enabled = stage == AccountStage.LOGIN || onCancel != null) {
        if (stage == AccountStage.LOGIN) stage = AccountStage.CHOICE else onCancel?.invoke()
    }
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background)) {
        val wide = maxWidth >= 900.dp
        val roomy = wide && maxHeight >= 780.dp
        Row(Modifier.widthIn(max = 1120.dp).heightIn(max = 820.dp).fillMaxSize().align(Alignment.Center)
            .padding(if (wide) 20.dp else 12.dp).testTag("accountFrame"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (wide) HeroPane(
                if (stage == AccountStage.CHOICE) MaterialShapes.Clover4Leaf else MaterialShapes.Cookie12Sided,
                if (stage == AccountStage.CHOICE) 0 else 1,
                Modifier.width(340.dp).fillMaxHeight(),
                icon = { size -> StepIcon(SetupStep.ACCOUNT, size, cs.onPrimary) },
            ) {
                Text(str("onboard_account_label"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                // The space is kept either way, so the label does not jump when the wait begins.
                Box(Modifier.fillMaxWidth().padding(top = 18.dp).height(10.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(stage != AccountStage.CHOICE, enter = fadeIn(), exit = fadeOut()) {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary,
                            trackColor = cs.onPrimaryContainer.copy(alpha = 0.18f))
                    }
                }
            }
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(if (wide) 40.dp else 28.dp), color = cs.surfaceContainerLow) {
                Box {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 28.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        if (!wide) Reveal(0, 1) {
                            MorphingShape(SetupStep.ACCOUNT.polygon, cs.primaryContainer, Modifier.size(64.dp)) {
                                StepIcon(SetupStep.ACCOUNT, 28.dp, cs.onPrimaryContainer)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Reveal(0, 1) {
                                Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                                    Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StepIcon(SetupStep.ACCOUNT, 16.dp, cs.onSecondaryContainer)
                                        Text(str("onboard_account_label"), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            Reveal(1, 1) {
                                // Nobody is coming back when the screen was opened to switch: it says what it is for instead.
                                Text(str(if (onCancel != null) "onboard_account_switch" else "account_return_title"), fontWeight = FontWeight.Bold,
                                    style = if (roomy) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge)
                            }
                            if (onCancel == null) Reveal(2, 1) {
                                Text(str("account_return_body"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                            }
                        }
                        Reveal(3, 1) {
                            AnimatedContent(stage, transitionSpec = { swapInPlace() }, label = "accountStage") { shown ->
                                when (shown) {
                                    AccountStage.LOGIN -> soundCloudLoginContent({
                                        // The login only left tokens behind; the profile says whose they are.
                                        stage = AccountStage.FINISHING
                                        scope.launch {
                                            try {
                                                val me = loadSoundCloudProfile()
                                                TokenManager.rememberActiveAccount(me.id, me.username.orEmpty(), me.avatarUrl)
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                // The app reads the profile again once it opens, and files the account then.
                                            }
                                            onDone()
                                        }
                                    }, { stage = AccountStage.CHOICE })
                                    AccountStage.FINISHING -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                        LoadingIndicator(Modifier.size(64.dp))
                                    }
                                    AccountStage.CHOICE -> Column(Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        if (accounts.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            val active = TokenManager.activeAccountId()
                                            accounts.forEachIndexed { i, account ->
                                                key(account.id) {
                                                    SavedAccountRow(account, active = account.id == active, shape = getSettingsShape(accounts.size, i),
                                                        onClick = {
                                                            // Picking the account already in use changes nothing, so nothing is reloaded.
                                                            if (account.id == active && onCancel != null) onCancel()
                                                            else if (TokenManager.switchToAccount(account.id)) onDone()
                                                        },
                                                        onForget = {
                                                            if (account.id == active) TokenManager.logout() else TokenManager.forgetAccount(account.id)
                                                        })
                                                }
                                            }
                                        }
                                        AccountChoices(
                                            onSoundCloud = { stage = AccountStage.LOGIN },
                                            onGuest = {
                                                // Someone signed in is leaving an account behind; after a sign-out there is nothing to leave.
                                                if (TokenManager.hasAccessToken()) TokenManager.continueAsGuest() else TokenManager.setGuestMode(true)
                                                onDone()
                                            },
                                            soundCloudTitle = str(if (accounts.isEmpty()) "login_soundcloud" else "onboard_account_change"))
                                    }
                                }
                            }
                        }
                    }
                    if (onCancel != null) FilledTonalIconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.TopEnd).padding(if (wide) 28.dp else 20.dp).testTag("accountClose")) {
                        Icon(Icons.Rounded.Close, str("btn_cancel"))
                    }
                }
            }
        }
    }
}

/** One saved account: who it is, whether it is the one in use, and a way to remove it from this computer. */
@Composable
private fun SavedAccountRow(account: TokenManager.SavedAccount, active: Boolean, shape: Shape, onClick: () -> Unit, onForget: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(if (active) cs.secondaryContainer else cs.surfaceContainerHigh, label = "accountRow")
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("account-${account.id}"), shape = shape, color = container,
        contentColor = if (active) cs.onSecondaryContainer else cs.onSurface) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(56.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(if (active) cs.primary else cs.secondaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, Modifier.size(28.dp), tint = if (active) cs.onPrimary else cs.onSecondaryContainer)
                if (!account.avatarUrl.isNullOrBlank()) coil3.compose.AsyncImage(
                    model = account.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.username.ifBlank { "SoundCloud" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (active) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = cs.primary)
                    else Icon(painterResource("icons/soundcloud.svg"), null, Modifier.size(20.dp, 12.dp), tint = cs.onSurfaceVariant)
                    Text(if (active) str("setup_discord_connected") else "SoundCloud", style = MaterialTheme.typography.bodyMedium,
                        color = if (active) cs.primary else cs.onSurfaceVariant)
                }
            }
            Tip(str("setup_discord_disconnect")) {
                IconButton(onClick = onForget, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, str("setup_discord_disconnect"), tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}
