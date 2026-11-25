@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay

// ─── Catalogue ──────────────────────────────────────────────────────────────────────────

/** Every game of the arcade. The names are stored (play time, records): never rename an entry. */
internal enum class GameId(val titleKey: String, val subtitleKey: String, val icon: ImageVector) {
    SNAKE("arcade_snake", "arcade_snake_sub", Icons.Rounded.Timeline),
    BLOCKS("arcade_blocks", "arcade_blocks_sub", Icons.Rounded.Widgets),
    STACK("arcade_stack", "arcade_stack_sub", Icons.Rounded.ViewModule),
    MINES("arcade_mines", "arcade_mines_sub", Icons.Rounded.Flag),
    BRICKS("arcade_bricks", "arcade_bricks_sub", Icons.Rounded.SportsBaseball),
    MERGE("arcade_merge", "arcade_merge_sub", Icons.Rounded.Tag),
}

/** Best scores of the arcade games (the snake keeps its own, older store). */
internal object GameRecords {
    private val prefs by lazy { NamedPrefs("arcade_records") }
    fun best(key: String): Int = prefs.getInt(key, 0)
    /** Saves [value] if it beats the record; returns the record afterwards. */
    fun offer(key: String, value: Int, lowerIsBetter: Boolean = false): Int {
        val old = prefs.getInt(key, 0)
        val better = if (lowerIsBetter) old == 0 || value in 1 until old else value > old
        if (better) prefs.putInt(key, value)
        return if (better) value else old
    }
}

// ─── Keys ───────────────────────────────────────────────────────────────────────────────

/** One handler for whichever game is on screen, fed by its dialog and by the main window alike. */
internal object GameInput {
    @Volatile
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    fun handle(event: KeyEvent): Boolean {
        GamePlaytime.touch()
        return keyHandler?.invoke(event) ?: false
    }
}

/** What the main window asks while any game is shown. */
internal object GameKeys {
    val active: Boolean get() = SnakeGameLauncher.pending != null || ArcadeLauncher.isOpen

    fun handle(event: KeyEvent): Boolean = GameInput.handle(event)

    /** Escape: out of the game to the arcade, then out of the arcade. */
    fun escape() {
        when {
            SnakeGameLauncher.pending != null -> SnakeGameLauncher.dismiss()
            ArcadeLauncher.current != null -> ArcadeLauncher.backToHub()
            else -> ArcadeLauncher.dismiss()
        }
    }
}

/** Registers [onKey] as the game's key handler for as long as the caller is composed. */
@Composable
internal fun GameKeyHandler(onKey: (KeyEvent) -> Boolean) {
    val current by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = { current(it) }
        GameInput.keyHandler = handler
        onDispose { if (GameInput.keyHandler === handler) GameInput.keyHandler = null }
    }
}

// ─── Launcher & host ────────────────────────────────────────────────────────────────────

internal object ArcadeLauncher {
    var isOpen by mutableStateOf(false)
        private set
    var current by mutableStateOf<GameId?>(null)
        private set

    fun open(game: GameId? = null) {
        current = game
        isOpen = true
    }

    fun select(game: GameId) {
        current = game
    }

    fun backToHub() {
        current = null
    }

    fun dismiss() {
        isOpen = false
        current = null
        GameInput.keyHandler = null
    }
}

@Composable
internal fun ArcadeHost(playerViewModel: PlayerViewModel) {
    if (!ArcadeLauncher.isOpen) return
    BackHandler(onBack = { GameKeys.escape() })
    Dialog(
        onDismissRequest = { ArcadeLauncher.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        ArcadeScreen(
            current = ArcadeLauncher.current,
            onSelect = { game ->
                if (game == GameId.SNAKE) {
                    // The snake eats songs: it plays from the wave, in its own window.
                    ArcadeLauncher.dismiss()
                    val title = str("wave_title")
                    // Music off (the default): the classic snake, nothing is loaded or played.
                    SnakeGameLauncher.launch(
                        if (SnakeGameLauncher.playMusic) {
                            SnakeGameRequest(title, SnakeGameMode.WAVE, playbackContext = PlaybackContext(title, PlayerViewModel.MY_WAVE_NAV_ID))
                        } else {
                            SnakeGameRequest(str("arcade_snake"), SnakeGameMode.CLASSIC)
                        }
                    )
                } else {
                    ArcadeLauncher.select(game)
                }
            },
            onBack = ArcadeLauncher::backToHub,
            onClose = ArcadeLauncher::dismiss,
        )
    }
}

/** The arcade window: the hub, or one game with the back arrow to the hub. */
@Composable
internal fun ArcadeScreen(
    current: GameId?,
    onSelect: (GameId) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(0.94f).heightIn(max = 900.dp).fillMaxHeight(0.94f),
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(current) { runCatching { focus.requestFocus() } }
    Surface(
        modifier = modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyDown) GameKeys.escape()
                    true
                } else {
                    GameInput.handle(event)
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        GamePlaytime.touch()
                    }
                }
            },
        shape = RoundedCornerShape(32.dp),
        color = scheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = targetState != null
                (fadeIn(tween(220)) + slideInHorizontally(spring(stiffness = 500f)) { if (forward) it / 6 else -it / 6 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally { if (forward) -it / 8 else it / 8 })
            },
            label = "arcade",
        ) { game ->
            when (game) {
                null -> ArcadeHub(onSelect = onSelect, onClose = onClose)
                else -> GameFrame(game, onBack = onBack, onClose = onClose) {
                    when (game) {
                        GameId.BLOCKS -> BlocksGame()
                        GameId.MINES -> MinesGame()
                        GameId.BRICKS -> BricksGame()
                        GameId.MERGE -> MergeGame()
                        GameId.STACK -> StackGame()
                        GameId.SNAKE -> Unit
                    }
                }
            }
        }
    }
}

// ─── Hub ────────────────────────────────────────────────────────────────────────────────

private data class GameLook(val container: Color, val content: Color, val accent: Color, val shape: Shape)

@Composable
private fun lookOf(game: GameId): GameLook {
    val s = MaterialTheme.colorScheme
    return when (game) {
        GameId.SNAKE -> GameLook(s.secondaryContainer, s.onSecondaryContainer, s.secondary, MaterialShapes.Cookie9Sided.toShape())
        GameId.BLOCKS -> GameLook(s.primaryContainer, s.onPrimaryContainer, s.primary, MaterialShapes.Clover4Leaf.toShape())
        GameId.MINES -> GameLook(s.errorContainer, s.onErrorContainer, s.error, MaterialShapes.SoftBurst.toShape())
        GameId.STACK -> GameLook(s.tertiaryContainer, s.onTertiaryContainer, s.tertiary, MaterialShapes.Cookie6Sided.toShape())
        GameId.BRICKS -> GameLook(s.surfaceContainerHighest, s.onSurface, s.primary, MaterialShapes.Pill.toShape())
        GameId.MERGE -> GameLook(s.secondaryContainer, s.onSecondaryContainer, s.tertiary, MaterialShapes.Cookie4Sided.toShape())
    }
}

internal fun bestSummary(game: GameId): String? = when (game) {
    GameId.SNAKE -> SnakeGameLauncher.bestScore().takeIf { it > 0 }?.let { str("game_best", it) }
    GameId.BLOCKS -> GameRecords.best(BLOCKS_BEST).takeIf { it > 0 }?.let { str("game_best", it) }
    GameId.BRICKS -> GameRecords.best(BRICKS_BEST).takeIf { it > 0 }?.let { str("game_best", it) }
    GameId.MERGE -> GameRecords.best(MERGE_BEST).takeIf { it > 0 }?.let { str("game_best", it) }
    GameId.STACK -> GameRecords.best(STACK_BEST).takeIf { it > 0 }?.let { str("game_best", it) }
    GameId.MINES -> GameRecords.best(minesBestKey(MinesLevel.EASY)).takeIf { it > 0 }?.let { str("arcade_best_time", formatClock(it)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArcadeHub(onSelect: (GameId) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Read so the card shows the time just added when coming back from a game.
    @Suppress("UNUSED_VARIABLE") val tick = GamePlaytime.version
    val totals = remember(GamePlaytime.version) { GamePlaytime.totals() }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ShapeBadge(Icons.Rounded.SportsEsports, MaterialShapes.Cookie9Sided.toShape(), scheme.primaryContainer, scheme.onPrimaryContainer, size = 56)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(str("arcade_title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(str("arcade_subtitle"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            val total = totals.values.sum()
            if (total > 0) {
                Surface(shape = RoundedCornerShape(50), color = scheme.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatPlaytime(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.height(20.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }
            val gap = 16.dp
            val cardWidth = (maxWidth - gap * (columns - 1)) / columns - 1.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    maxItemsInEachRow = columns,
                ) {
                    // The newest games first; the snake, which needs songs, closes the list.
                    listOf(GameId.BLOCKS, GameId.STACK, GameId.BRICKS, GameId.MINES, GameId.MERGE, GameId.SNAKE).forEachIndexed { index, game ->
                        GameCard(game, totals[game] ?: 0L, index, Modifier.width(cardWidth)) { onSelect(game) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GameCard(game: GameId, playedMs: Long, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val look = lookOf(game)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1.03f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "lift")
    val spin by animateFloatAsState(if (hovered) 30f else 0f, spring(dampingRatio = 0.4f, stiffness = 200f), label = "spin")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(420, delayMillis = 55 * index, easing = androidx.compose.animation.core.EaseOutBack))
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        color = look.container,
        contentColor = look.content,
        interactionSource = interaction,
        modifier = modifier
            .height(220.dp)
            .hoverable(interaction)
            .graphicsLayer {
                scaleX = lift * (0.9f + 0.1f * appear.value)
                scaleY = lift * (0.9f + 0.1f * appear.value)
                alpha = appear.value
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            // A big soft shape in the corner, turning a little under the pointer.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp, end = 0.dp)
                    .size(150.dp)
                    .graphicsLayer {
                        translationX = 40.dp.toPx()
                        translationY = -30.dp.toPx()
                        rotationZ = spin
                    }
                    .clip(look.shape)
                    .background(look.accent.copy(alpha = 0.16f)),
            )
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Box(
                    Modifier.size(64.dp).graphicsLayer { rotationZ = spin / 2 }.clip(look.shape).background(look.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(game.icon, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = -spin / 2 })
                }
                Spacer(Modifier.weight(1f))
                Text(str(game.titleKey), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    str(game.subtitleKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = look.content.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bestSummary(game)?.let { MiniChip(Icons.Rounded.EmojiEvents, it, look) }
                    if (playedMs >= 60_000L) MiniChip(Icons.Rounded.Schedule, formatPlaytime(playedMs), look)
                }
            }
        }
    }
}

@Composable
private fun MiniChip(icon: ImageVector, text: String, look: GameLook) {
    Surface(shape = RoundedCornerShape(50), color = look.content.copy(alpha = 0.1f), contentColor = look.content) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ─── Game chrome ────────────────────────────────────────────────────────────────────────

/** A game's page: back to the arcade, its name, and the board below. */
@Composable
private fun GameFrame(game: GameId, onBack: () -> Unit, onClose: () -> Unit, content: @Composable () -> Unit) {
    val look = lookOf(game)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Tip(str("arcade_back")) {
                FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("arcade_back"))
                }
            }
            Spacer(Modifier.width(12.dp))
            ShapeBadge(game.icon, look.shape, look.accent, MaterialTheme.colorScheme.surface, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str(game.titleKey), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(str(game.subtitleKey), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Tip(str("btn_close")) {
        FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
        }
    }
}

@Composable
internal fun ShapeBadge(icon: ImageVector, shape: Shape, container: Color, content: Color, size: Int = 48) {
    Box(Modifier.size(size.dp).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size((size * 0.52f).dp))
    }
}

/** A score tile: label over a big number that bounces when it changes. */
@Composable
internal fun GameStat(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        if (first) { first = false; return@LaunchedEffect }
        bounce.snapTo(1.18f)
        bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (emphasized) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (emphasized) scheme.onPrimaryContainer else scheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Text(
                value,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
            )
        }
    }
}

/** The card shown over a board when a run ends. */
@Composable
internal fun GameEndCard(
    title: String,
    body: String?,
    icon: ImageVector,
    onRestart: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 340.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f) },
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeBadge(icon, MaterialShapes.SoftBurst.toShape(), scheme.tertiaryContainer, scheme.onTertiaryContainer, size = 68)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            extra?.invoke(this)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRestart, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(str("game_restart"), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Dims the board and centres [content] on it. */
@Composable
internal fun BoardScrim(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "scrim")
    if (alpha < 0.01f) return
    Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { if (visible) content() }
}

/** The round "new game" button games put next to their score. */
@Composable
internal fun NewGameButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tip(str("arcade_new_game")) {
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier.size(52.dp)) {
            Icon(Icons.Rounded.Refresh, contentDescription = str("arcade_new_game"))
        }
    }
}

// ─── Time ───────────────────────────────────────────────────────────────────────────────

/** Awaits frames for as long as [onFrame] returns true, about 60 a second even on a fast screen (see AmbientClock). */
internal suspend fun runGameFrames(onFrame: (dtSeconds: Float) -> Boolean) {
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
        last = now
        if (!onFrame(dt)) return
        val spent = (System.nanoTime() - now) / 1_000_000L
        val wait = 14L - spent
        if (wait > 1) delay(wait)
    }
}

/** "1 h 05", "12 min", "40 s" for the playtime chips. */
internal fun formatPlaytime(ms: Long): String {
    val minutes = ms / 60_000L
    return when {
        minutes >= 60 -> str("listening_stats_duration_hr_min", minutes / 60, minutes % 60)
        minutes > 0 -> str("listening_stats_duration_min", minutes)
        else -> str("listening_stats_duration_sec", ms / 1000)
    }
}

/** "1:07" from seconds. */
internal fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
