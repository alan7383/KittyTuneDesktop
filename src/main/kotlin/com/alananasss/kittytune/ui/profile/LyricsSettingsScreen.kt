    package com.alananasss.kittytune.ui.profile

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults
    
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material.icons.rounded.DragIndicator
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.CropFree
    import androidx.compose.material.icons.rounded.Fullscreen
    import androidx.compose.material.icons.rounded.ViewSidebar
    import androidx.compose.material.icons.rounded.Article // Import ajouté
    import androidx.compose.material.icons.rounded.Description
    import androidx.compose.material.icons.rounded.FormatAlignLeft
    import androidx.compose.material.icons.rounded.FormatSize
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.SdStorage
    import androidx.compose.material.icons.rounded.DarkMode
    import androidx.compose.material.icons.rounded.SwapVert
    import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import com.alananasss.kittytune.ui.common.Slider
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.alpha
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Shape
        import com.alananasss.kittytune.core.EscapableAlertDialog
        import com.alananasss.kittytune.core.BackHandler
        import com.alananasss.kittytune.core.str
        import com.alananasss.kittytune.core.trackTextInput
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.window.Dialog
        import com.alananasss.kittytune.data.local.InstrumentalIndicatorStyle
        import com.alananasss.kittytune.data.local.LyricsAlignment
    import com.alananasss.kittytune.data.local.LyricsFont
    import com.alananasss.kittytune.data.local.LyricsSurfaceMode
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.common.SettingsSwitch
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.common.SettingsGroupTitle
    import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
    import com.alananasss.kittytune.data.lyrics.providers.DefaultLyricsProviderOrder
    import com.alananasss.kittytune.data.lyrics.clients.PaxsenixClient
    import kotlin.math.roundToInt
    
    @Composable
    fun LyricsSettingsScreen(
        onBackClick: (() -> Unit)? = null,
        playerViewModel: PlayerViewModel,
        page: LyricsSettingsPage,
        onOpenPage: ((LyricsSettingsPage) -> Unit)? = null,
    ) {
            val prefs = remember { PlayerPreferences() }
    
        var dialogMode by remember(page) {
            mutableStateOf(
                when (page) {
                    LyricsSettingsPage.FULLSCREEN -> LyricsSurfaceMode.FULLSCREEN
                    LyricsSettingsPage.SIDEBAR -> LyricsSurfaceMode.SIDEBAR
                    else -> LyricsSurfaceMode.CENTRAL
                }
            )
        }
        var showAlignmentDialog by remember { mutableStateOf(false) }
        var showCentralPositionDialog by remember { mutableStateOf(false) }
        var showFontSizeDialog by remember { mutableStateOf(false) }
        var showAutoScrollSpeedDialog by remember { mutableStateOf(false) }
        var showWheelStepDialog by remember { mutableStateOf(false) }
        var provider by remember { mutableStateOf(playerViewModel.lyricsProvider) }
        var showProviderDialog by remember { mutableStateOf(false) }

        var targetLang by remember { mutableStateOf(prefs.getLyricsTranslationLang()) }
        var showLangDialog by remember { mutableStateOf(false) }
        var showPaxsenixKeyDialog by remember { mutableStateOf(false) }
        var paxsenixKeyInput by remember { mutableStateOf(prefs.getPaxsenixApiKey()) }
        var showProviderOrderDialog by remember { mutableStateOf(false) }

        var showLyricsFontDialog by remember { mutableStateOf(false) }
        var showBounceFactorDialog by remember { mutableStateOf(false) }
        var showGlowFactorDialog by remember { mutableStateOf(false) }
        var showFillTransitionDialog by remember { mutableStateOf(false) }
        var showLineSpacingDialog by remember { mutableStateOf(false) }
        var showActiveScaleDialog by remember { mutableStateOf(false) }
        var showHorizontalMarginDialog by remember { mutableStateOf(false) }
        var showVerticalOffsetDialog by remember { mutableStateOf(false) }
        var showInstrumentalDialog by remember { mutableStateOf(false) }
        var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }
        var oneLineLyrics by remember { mutableStateOf(prefs.getOneLineLyricsEnabled()) }
        val isFsMode = dialogMode == LyricsSurfaceMode.FULLSCREEN
        val isSbMode = dialogMode == LyricsSurfaceMode.SIDEBAR

        if (showProviderDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showProviderDialog = false },
                title = { Text(str("pref_lyrics_provider_title")) },
                text = {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { 
                            provider = com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY
                            playerViewModel.updateLyricsProvider(provider)
                            showProviderDialog = false 
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (provider == com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("pref_lyrics_provider_max_quality"))
                        }
                        Row(Modifier.fillMaxWidth().clickable { 
                            provider = com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE
                            playerViewModel.updateLyricsProvider(provider)
                            showProviderDialog = false 
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (provider == com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("pref_lyrics_provider_open_source"))
                        }
                    }
                },
                confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { showProviderDialog = false }) { Text(str("btn_cancel")) } }
            )
        }

        if (showLangDialog) {
            val systemLangCode = java.util.Locale.getDefault().language
            val allLanguages = remember {
                val locales = java.util.Locale.getISOLanguages()
                    .map { code ->
                        val loc = java.util.Locale(code)
                        code to loc.getDisplayLanguage(loc).replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
                    }
                    .filter { it.second.isNotBlank() && it.first.length == 2 }
                    .distinctBy { it.first }
                    .sortedBy { it.second }

                val list = mutableListOf<Pair<String, String>>()
                val systemLoc = locales.find { it.first == systemLangCode }
                if (systemLoc != null) {
                    list.add(systemLoc.first to "${systemLoc.second} (${str("theme_system")})")
                }
                list.addAll(locales.filter { it.first != systemLangCode })
                list
            }

            EscapableAlertDialog(
                onDismissRequest = { showLangDialog = false },
                title = { Text(str("pref_lyrics_translation_lang")) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(allLanguages) { (code, name) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { 
                                    targetLang = code
                                    showLangDialog = false 
                                    playerViewModel.setLyricsTranslationLanguage(code)
                                }.padding(vertical = 12.dp), 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (targetLang == code), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { showLangDialog = false }) { Text(str("btn_cancel")) } }
            )
        }

        if (showPaxsenixKeyDialog) {
            var tempKey by remember { mutableStateOf(paxsenixKeyInput) }
            EscapableAlertDialog(
                onDismissRequest = { showPaxsenixKeyDialog = false },
                title = { Text(str("pref_lyrics_paxsenix_key", "Paxsenix API Key")) },
                text = {
                    Column {
                        Text(str("pref_lyrics_paxsenix_key_sub", "Required for Apple Music, Spotify and Paxsenix Musixmatch"), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            modifier = Modifier.fillMaxWidth().trackTextInput(),
                            singleLine = true,
                            placeholder = { Text("Bearer token...") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        paxsenixKeyInput = tempKey
                        prefs.setPaxsenixApiKey(tempKey)
                        PaxsenixClient.setApiKey(tempKey)
                        showPaxsenixKeyDialog = false
                    }) {
                        Text(str("btn_save", "Save"))
                    }
                },
                dismissButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { showPaxsenixKeyDialog = false }) {
                        Text(str("btn_cancel", "Cancel"))
                    }
                }
            )
        }

        if (showProviderOrderDialog) {
            LyricsProviderOrderDialog(prefs = prefs, onDismiss = { showProviderOrderDialog = false })
        }
    
        // --- DIALOGS ---

        if (showLyricsFontDialog) {
            val currentFont = playerViewModel.lyricsFontFor(isFsMode, isSbMode)
            EscapableAlertDialog(
                onDismissRequest = { showLyricsFontDialog = false },
                title = { Text(str("pref_lyrics_font_title")) },
                text = {
                    Column {
                        LyricsFont.entries.forEach { font ->
                            val label = when (font) {
                                LyricsFont.APPLE -> str("pref_lyrics_font_apple")
                                LyricsFont.APP_DEFAULT -> str("pref_lyrics_font_app_default")
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    when (dialogMode) {
                                        LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenFont(font)
                                        LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarFont(font)
                                        LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsFont(font)
                                    }
                                    showLyricsFontDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (currentFont == font), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { showLyricsFontDialog = false }) {
                        Text(str("btn_cancel"))
                    }
                }
            )
        }

        if (showInstrumentalDialog) {
            val currentStyle = playerViewModel.instrumentalIndicatorStyleFor(isFsMode, isSbMode)
            EscapableAlertDialog(
                onDismissRequest = { showInstrumentalDialog = false },
                title = { Text(str("pref_instrumental_indicator")) },
                text = {
                    Column {
                        InstrumentalIndicatorStyle.entries.forEach { style ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    when (dialogMode) {
                                        LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenInstrumentalIndicatorStyle(style)
                                        LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarInstrumentalIndicatorStyle(style)
                                        LyricsSurfaceMode.CENTRAL -> playerViewModel.updateInstrumentalIndicatorStyle(style)
                                    }
                                    showInstrumentalDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (currentStyle == style), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(instrumentalStyleLabel(style))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { showInstrumentalDialog = false }) {
                        Text(str("btn_cancel"))
                    }
                }
            )
        }

        if (showBounceFactorDialog) {
            val currentValue = playerViewModel.bounceFactorFor(isFsMode, isSbMode)
            val updateBounce: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenBounceFactor(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarBounceFactor(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsBounceFactor(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_bounce_factor_title"),
                valueText = "${(currentValue * 100).toInt()}%",
                value = currentValue,
                valueRange = 0f..2f,
                steps = 19,
                stepDelta = 0.1f,
                defaultValue = 1f,
                onValueChange = updateBounce,
                onDismiss = { showBounceFactorDialog = false },
            )
        }

        if (showGlowFactorDialog) {
            val currentValue = playerViewModel.glowFactorFor(isFsMode, isSbMode)
            val updateGlow: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenGlowFactor(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarGlowFactor(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsGlowFactor(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_glow_factor_title"),
                valueText = "${(currentValue * 100).toInt()}%",
                value = currentValue,
                valueRange = 0f..2f,
                steps = 19,
                stepDelta = 0.1f,
                defaultValue = 1f,
                onValueChange = updateGlow,
                onDismiss = { showGlowFactorDialog = false },
            )
        }

        if (showFillTransitionDialog) {
            val currentValue = playerViewModel.fillTransitionWidthFor(isFsMode, isSbMode)
            val updateFill: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenFillTransitionWidth(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarFillTransitionWidth(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsFillTransitionWidth(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_fill_transition_title"),
                valueText = "${currentValue.toInt()} dp",
                value = currentValue,
                valueRange = 2f..24f,
                steps = 10,
                stepDelta = 2f,
                defaultValue = 8f,
                onValueChange = updateFill,
                onDismiss = { showFillTransitionDialog = false },
            )
        }

        if (showLineSpacingDialog) {
            val currentValue = playerViewModel.lineSpacingFor(isFsMode, isSbMode)
            val maxSpacing = if (isFsMode) 64f else 48f
            val spacingSteps = if (isFsMode) 31 else 23
            val updateSpacing: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenLineSpacing(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarLineSpacing(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsLineSpacing(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_line_spacing_title"),
                valueText = "${currentValue.toInt()} dp",
                value = currentValue,
                valueRange = 0f..maxSpacing,
                steps = spacingSteps,
                stepDelta = 2f,
                defaultValue = 0f,
                onValueChange = updateSpacing,
                onDismiss = { showLineSpacingDialog = false },
            )
        }

        if (showActiveScaleDialog) {
            val currentValue = playerViewModel.activeScaleFor(isFsMode, isSbMode)
            val updateScale: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenActiveScale(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarActiveScale(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsActiveScale(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_active_scale_title"),
                subtitle = str("pref_lyrics_active_scale_desc"),
                valueText = "${(currentValue * 100).toInt()}%",
                value = currentValue,
                valueRange = 1.00f..1.30f,
                steps = 5,
                stepDelta = 0.05f,
                defaultValue = 1.00f,
                onValueChange = updateScale,
                onDismiss = { showActiveScaleDialog = false },
            )
        }

        if (showHorizontalMarginDialog) {
            val currentValue = playerViewModel.horizontalMarginFor(isFsMode, isSbMode)
            val maxMargin = if (isFsMode) 160f else 64f
            val stepDelta = if (isFsMode) 8f else 4f
            val steps = if (isFsMode) 19 else 7
            val updateMargin: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenHorizontalMargin(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarHorizontalMargin(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsHorizontalMargin(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_horizontal_margin_title"),
                valueText = "${currentValue.toInt()} dp",
                value = currentValue,
                valueRange = 0f..maxMargin,
                steps = steps,
                stepDelta = stepDelta,
                defaultValue = 0f,
                onValueChange = updateMargin,
                onDismiss = { showHorizontalMarginDialog = false },
            )
        }

        if (showVerticalOffsetDialog) {
            val currentValue = playerViewModel.verticalOffsetFor(isFsMode, isSbMode)
            val defaultOffset = if (isSbMode) 0.36f else 0.38f
            val updateOffset: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenVerticalOffset(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarVerticalOffset(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsVerticalOffset(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_vertical_offset_title"),
                valueText = "${(currentValue * 100).toInt()}%",
                value = currentValue,
                valueRange = 0.20f..0.60f,
                steps = 19,
                stepDelta = 0.02f,
                defaultValue = defaultOffset,
                onValueChange = updateOffset,
                onDismiss = { showVerticalOffsetDialog = false },
            )
        }

        if (showFontSizeDialog) {
            val currentValue = playerViewModel.fontSizeFor(isFsMode, isSbMode)
            val defaultSize = if (isSbMode) 22f else 42f
            val updateFontSize: (Float) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenFontSize(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarFontSize(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsFontSize(v)
                }
            }
            LyricsModeSliderDialog(
                title = str("pref_lyrics_size"),
                valueText = "${currentValue.roundToInt()} sp",
                value = currentValue,
                valueRange = 12f..100f,
                steps = 43,
                stepDelta = 2f,
                defaultValue = defaultSize,
                onValueChange = updateFontSize,
                onDismiss = { showFontSizeDialog = false },
            )
        }
    
        if (showAutoScrollSpeedDialog) {
            BackHandler(onBack = { showAutoScrollSpeedDialog = false })
            Dialog(onDismissRequest = { showAutoScrollSpeedDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_autoscroll_speed"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            str("pref_lyrics_autoscroll_speed_sub"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                autoScrollSpeedLabel(playerViewModel.plainAutoScrollSpeed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(60.dp)
                            )
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                playerViewModel.updatePlainAutoScrollSpeed(playerViewModel.plainAutoScrollSpeed - 0.25f)
                            }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.plainAutoScrollSpeed,
                                onValueChange = { playerViewModel.updatePlainAutoScrollSpeed(it) },
                                valueRange = 0.25f..4f,
                                steps = 14,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                playerViewModel.updatePlainAutoScrollSpeed(playerViewModel.plainAutoScrollSpeed + 0.25f)
                            }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { playerViewModel.updatePlainAutoScrollSpeed(1.5f) }) {
                                Text(str("pref_lyrics_reset"))
                            }
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showAutoScrollSpeedDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showWheelStepDialog) {
            BackHandler(onBack = { showWheelStepDialog = false })
            Dialog(onDismissRequest = { showWheelStepDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_wheel_step"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            str("pref_lyrics_wheel_step_sub"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                str("pref_lyrics_wheel_step_value", wheelLinesLabel(playerViewModel.lyricsWheelLines)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(70.dp)
                            )
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                playerViewModel.updateLyricsWheelLines(playerViewModel.lyricsWheelLines - 0.5f)
                            }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsWheelLines,
                                onValueChange = { playerViewModel.updateLyricsWheelLines(it) },
                                valueRange = PlayerPreferences.LYRICS_WHEEL_LINES_MIN..PlayerPreferences.LYRICS_WHEEL_LINES_MAX,
                                steps = 21,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                playerViewModel.updateLyricsWheelLines(playerViewModel.lyricsWheelLines + 0.5f)
                            }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { playerViewModel.updateLyricsWheelLines(3f) }) {
                                Text(str("pref_lyrics_reset"))
                            }
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showWheelStepDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showCentralPositionDialog) {
            val current = playerViewModel.lyricsCentralPosition
            val setVal: (LyricsAlignment) -> Unit = { v ->
                playerViewModel.updateLyricsCentralPosition(v)
                showCentralPositionDialog = false
            }
            EscapableAlertDialog(
                onDismissRequest = { showCentralPositionDialog = false },
                title = { Text(str("pref_lyrics_position_title")) },
                text = {
                    Column {
                        AlignRadioButton(str("align_left"), LyricsAlignment.LEFT, current, setVal)
                        AlignRadioButton(str("align_center_simple"), LyricsAlignment.CENTER, current, setVal)
                        AlignRadioButton(str("align_right"), LyricsAlignment.RIGHT, current, setVal)
                    }
                },
                confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { showCentralPositionDialog = false }) { Text(str("btn_cancel")) } }
            )
        }

        if (showAlignmentDialog) {
            val current = playerViewModel.alignmentFor(isFsMode, isSbMode)
            val setVal: (LyricsAlignment) -> Unit = { v ->
                when (dialogMode) {
                    LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenAlignment(v)
                    LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarAlignment(v)
                    LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsAlignment(v)
                }
                showAlignmentDialog = false
            }
            EscapableAlertDialog(
                onDismissRequest = { showAlignmentDialog = false },
                title = { Text(str("pref_lyrics_align")) },
                text = {
                    Column {
                        AlignRadioButton(str("align_left"), LyricsAlignment.LEFT, current, setVal)
                        AlignRadioButton(str("align_center"), LyricsAlignment.CENTER, current, setVal)
                        AlignRadioButton(str("align_right"), LyricsAlignment.RIGHT, current, setVal)
                    }
                },
                confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { showAlignmentDialog = false }) { Text(str("btn_cancel")) } }
            )
        }

        // --- MAIN SCREEN ---
        fun shows(part: LyricsSettingsPage) = page == part
        val autoScrollOn = playerViewModel.isPlainAutoScrollEnabled

        Column(modifier = Modifier.fillMaxWidth()) {
            if (shows(LyricsSettingsPage.OVERVIEW)) {
                if (onOpenPage != null) {
                    SettingsGroup(
                        title = str("lyrics_modes_title"),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("lyrics_mode_fullscreen"),
                                    subtitle = str("lyrics_mode_fullscreen_sub"),
                                    icon = Icons.Rounded.Fullscreen,
                                    onClick = { onOpenPage(LyricsSettingsPage.FULLSCREEN) },
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("lyrics_mode_central"),
                                    subtitle = str("lyrics_mode_central_sub"),
                                    icon = Icons.Rounded.CropFree,
                                    onClick = { onOpenPage(LyricsSettingsPage.CENTRAL) },
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("lyrics_mode_sidebar"),
                                    subtitle = str("lyrics_mode_sidebar_sub"),
                                    icon = Icons.Rounded.ViewSidebar,
                                    onClick = { onOpenPage(LyricsSettingsPage.SIDEBAR) },
                                )
                            },
                        ),
                    )
                }

                SettingsGroup(
                    title = str("settings_cat_behavior", "Behavior"),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_autoscroll"),
                                highlightKey = "pref_lyrics_autoscroll",
                                icon = Icons.Rounded.KeyboardDoubleArrowDown,
                                subtitle = str("pref_lyrics_autoscroll_sub"),
                                hasSwitch = true,
                                switchState = autoScrollOn,
                                onSwitchChange = { playerViewModel.togglePlainAutoScroll(it) }
                            )
                        }
                        if (autoScrollOn) add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_autoscroll_speed"),
                                highlightKey = "pref_lyrics_autoscroll_speed",
                                icon = Icons.Rounded.Speed,
                                subtitle = autoScrollSpeedLabel(playerViewModel.plainAutoScrollSpeed),
                                onClick = { showAutoScrollSpeedDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_wheel_step"),
                                highlightKey = "pref_lyrics_wheel_step",
                                icon = Icons.Rounded.Mouse,
                                subtitle = str("pref_lyrics_wheel_step_value", wheelLinesLabel(playerViewModel.lyricsWheelLines)),
                                onClick = { showWheelStepDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_one_line_title"),
                                icon = Icons.Rounded.Subtitles,
                                subtitle = str("pref_lyrics_one_line_sub"),
                                hasSwitch = true,
                                switchState = oneLineLyrics,
                                onSwitchChange = {
                                    oneLineLyrics = it
                                    prefs.setOneLineLyricsEnabled(it)
                                },
                                highlightKey = "pref_lyrics_one_line",
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_translation_lang"),
                                highlightKey = "pref_lyrics_translation_lang",
                                icon = Icons.Rounded.Language,
                                subtitle = targetLang.uppercase(),
                                onClick = { showLangDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_reset_typography"),
                                highlightKey = "pref_lyrics_reset_typography",
                                icon = Icons.Rounded.RestartAlt,
                                subtitle = str("pref_lyrics_reset_typography_desc"),
                                onClick = { playerViewModel.resetAllLyricsTypography() }
                            )
                        }
                    },
                )
            }

            if (shows(LyricsSettingsPage.FULLSCREEN)) {
                LyricsModeGroup(
                    mode = LyricsSurfaceMode.FULLSCREEN,
                    playerViewModel = playerViewModel,
                    screensaverEnabled = playerViewModel.fullPlayerScreensaverEnabled,
                    onScreensaverChange = { playerViewModel.updateFullPlayerScreensaverEnabled(it) },
                    onOpenFontDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showLyricsFontDialog = true },
                    onOpenAlignmentDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showAlignmentDialog = true },
                    onOpenFontSizeDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showFontSizeDialog = true },
                    onOpenLineSpacingDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showLineSpacingDialog = true },
                    onOpenHorizontalMarginDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showHorizontalMarginDialog = true },
                    onOpenVerticalOffsetDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showVerticalOffsetDialog = true },
                    onOpenActiveScaleDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showActiveScaleDialog = true },
                    onOpenBounceFactorDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showBounceFactorDialog = true },
                    onOpenGlowFactorDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showGlowFactorDialog = true },
                    onOpenFillTransitionDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showFillTransitionDialog = true },
                    onOpenInstrumentalDialog = { dialogMode = LyricsSurfaceMode.FULLSCREEN; showInstrumentalDialog = true },
                    onOpenTranslationLangDialog = { showLangDialog = true },
                )
            }

            if (shows(LyricsSettingsPage.CENTRAL)) {
                LyricsModeGroup(
                    mode = LyricsSurfaceMode.CENTRAL,
                    playerViewModel = playerViewModel,
                    screensaverEnabled = null,
                    onScreensaverChange = {},
                    onOpenFontDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showLyricsFontDialog = true },
                    onOpenCentralPositionDialog = { showCentralPositionDialog = true },
                    onOpenAlignmentDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showAlignmentDialog = true },
                    onOpenFontSizeDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showFontSizeDialog = true },
                    onOpenLineSpacingDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showLineSpacingDialog = true },
                    onOpenHorizontalMarginDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showHorizontalMarginDialog = true },
                    onOpenVerticalOffsetDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showVerticalOffsetDialog = true },
                    onOpenActiveScaleDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showActiveScaleDialog = true },
                    onOpenBounceFactorDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showBounceFactorDialog = true },
                    onOpenGlowFactorDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showGlowFactorDialog = true },
                    onOpenFillTransitionDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showFillTransitionDialog = true },
                    onOpenInstrumentalDialog = { dialogMode = LyricsSurfaceMode.CENTRAL; showInstrumentalDialog = true },
                    onOpenTranslationLangDialog = { showLangDialog = true },
                )
            }

            if (shows(LyricsSettingsPage.SIDEBAR)) {
                LyricsModeGroup(
                    mode = LyricsSurfaceMode.SIDEBAR,
                    playerViewModel = playerViewModel,
                    screensaverEnabled = null,
                    onScreensaverChange = {},
                    onOpenFontDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showLyricsFontDialog = true },
                    onOpenAlignmentDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showAlignmentDialog = true },
                    onOpenFontSizeDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showFontSizeDialog = true },
                    onOpenLineSpacingDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showLineSpacingDialog = true },
                    onOpenHorizontalMarginDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showHorizontalMarginDialog = true },
                    onOpenVerticalOffsetDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showVerticalOffsetDialog = true },
                    onOpenActiveScaleDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showActiveScaleDialog = true },
                    onOpenBounceFactorDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showBounceFactorDialog = true },
                    onOpenGlowFactorDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showGlowFactorDialog = true },
                    onOpenFillTransitionDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showFillTransitionDialog = true },
                    onOpenInstrumentalDialog = { dialogMode = LyricsSurfaceMode.SIDEBAR; showInstrumentalDialog = true },
                    onOpenTranslationLangDialog = { showLangDialog = true },
                )
            }

            if (shows(LyricsSettingsPage.SOURCES)) {
                SettingsGroup(
                    title = str("pref_lyrics_providers_category"),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_local"),
                                highlightKey = "pref_lyrics_local",
                                icon = Icons.Rounded.FolderOpen,
                                subtitle = str("pref_lyrics_local_sub"),
                                hasSwitch = true,
                                switchState = preferLocal,
                                onSwitchChange = {
                                    preferLocal = it
                                    prefs.setLyricsPreferLocal(it)
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_provider_title"),
                                highlightKey = "pref_lyrics_provider_title",
                                icon = Icons.Rounded.CloudQueue,
                                subtitle = if (provider == com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY) str("pref_lyrics_provider_max_quality") else str("pref_lyrics_provider_open_source"),
                                onClick = { showProviderDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_order", "Provider Priority Order"),
                                highlightKey = "pref_lyrics_order",
                                icon = Icons.Rounded.SwapVert,
                                subtitle = str("pref_lyrics_order_sub", "Order in which providers are searched"),
                                onClick = { showProviderOrderDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_paxsenix_key", "Paxsenix API Key"),
                                highlightKey = "pref_lyrics_paxsenix_key",
                                icon = Icons.Rounded.Key,
                                subtitle = if (paxsenixKeyInput.isNotBlank()) "••••••••" else str("pref_lyrics_paxsenix_key_sub", "Required for Apple Music, Spotify and Paxsenix Musixmatch"),
                                onClick = { showPaxsenixKeyDialog = true }
                            )
                        }
                    },
                )
            }
        }
    }
    

    /** "3" rather than "3.0", and "2.5" when it is not whole. */
    private fun wheelLinesLabel(lines: Float): String {
        val rounded = kotlin.math.round(lines * 2f) / 2f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    @Composable
    fun AlignRadioButton(text: String, mode: LyricsAlignment, selected: LyricsAlignment, onSelect: (LyricsAlignment) -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = (mode == selected), onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(text)
        }
    }



/** "1.25×" — one decimal only when there is one, so the common speeds read as whole numbers. */
private fun autoScrollSpeedLabel(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100f) / 100f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return "$text×"
}

/** Which part of the lyrics settings a page shows. */
enum class LyricsSettingsPage { OVERVIEW, FULLSCREEN, CENTRAL, SIDEBAR, SOURCES }

/**
 * The settings of one lyrics view — full screen, central or side panel. The three used to be interleaved in
 * one list as "Alignment (side panel)", "Alignment (central)", "Alignment (full screen)" and so on; each view
 * now has its own page with plain titles.
 */
@Composable
private fun LyricsModeGroup(
    mode: LyricsSurfaceMode,
    playerViewModel: PlayerViewModel,
    screensaverEnabled: Boolean?,
    onScreensaverChange: (Boolean) -> Unit,
    onOpenFontDialog: () -> Unit,
    onOpenCentralPositionDialog: (() -> Unit)? = null,
    onOpenAlignmentDialog: () -> Unit,
    onOpenFontSizeDialog: () -> Unit,
    onOpenLineSpacingDialog: () -> Unit,
    onOpenHorizontalMarginDialog: () -> Unit,
    onOpenVerticalOffsetDialog: () -> Unit,
    onOpenActiveScaleDialog: () -> Unit,
    onOpenBounceFactorDialog: () -> Unit,
    onOpenGlowFactorDialog: () -> Unit,
    onOpenFillTransitionDialog: () -> Unit,
    onOpenInstrumentalDialog: () -> Unit,
    onOpenTranslationLangDialog: () -> Unit,
) {
    val isFS = mode == LyricsSurfaceMode.FULLSCREEN
    val isSB = mode == LyricsSurfaceMode.SIDEBAR
    val font = playerViewModel.lyricsFontFor(isFS, isSB)
    val lineBlur = playerViewModel.lineBlurFor(isFS, isSB)
    val alignment = playerViewModel.alignmentFor(isFS, isSB)
    val fontSize = playerViewModel.fontSizeFor(isFS, isSB)
    val lineSpacing = playerViewModel.lineSpacingFor(isFS, isSB)
    val horizontalMargin = playerViewModel.horizontalMarginFor(isFS, isSB)
    val verticalOffset = playerViewModel.verticalOffsetFor(isFS, isSB)
    val activeScale = playerViewModel.activeScaleFor(isFS, isSB)
    val wordSync = playerViewModel.wordSyncEnabledFor(isFS, isSB)
    val appleEffect = playerViewModel.appleMusicEffectEnabledFor(isFS, isSB)
    val duetView = playerViewModel.duetViewEnabledFor(isFS, isSB)
    val splitBacking = playerViewModel.splitBackingVocalsFor(isFS, isSB)
    val revealWords = playerViewModel.revealWordsFor(isFS, isSB)
    val sweepEnabled = playerViewModel.sweepEnabledFor(isFS, isSB)
    val instrumentalStyle = playerViewModel.instrumentalIndicatorStyleFor(isFS, isSB)
    val romanization = playerViewModel.romanizationEnabledFor(isFS, isSB)
    val translation = playerViewModel.translationEnabledFor(isFS, isSB)
    val lrcBounce = playerViewModel.lrcBounceEnabledFor(isFS, isSB)
    val bounceFactor = playerViewModel.bounceFactorFor(isFS, isSB)
    val glowFactor = playerViewModel.glowFactorFor(isFS, isSB)
    val fillTransition = playerViewModel.fillTransitionWidthFor(isFS, isSB)

    SettingsGroup(
        title = str("settings_cat_appearance"),
        items = buildList {
            if (screensaverEnabled != null) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_screensaver_title"),
                    icon = Icons.Rounded.DarkMode,
                    subtitle = str("pref_screensaver_desc"),
                    hasSwitch = true,
                    switchState = screensaverEnabled,
                    onSwitchChange = onScreensaverChange,
                    highlightKey = "pref_screensaver",
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_font_title"),
                    highlightKey = "pref_lyrics_font_title",
                    icon = Icons.Rounded.TextFields,
                    subtitle = when (font) {
                        LyricsFont.APPLE -> str("pref_lyrics_font_apple")
                        LyricsFont.APP_DEFAULT -> str("pref_lyrics_font_app_default")
                    },
                    onClick = onOpenFontDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_line_blur_title"),
                    highlightKey = "pref_lyrics_line_blur_title",
                    icon = Icons.Rounded.BlurOn,
                    subtitle = str("pref_lyrics_line_blur_desc"),
                    hasSwitch = true,
                    switchState = lineBlur,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenLineBlurEnabled(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarLineBlurEnabled(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsLineBlurEnabled(enabled)
                        }
                    },
                )
            }
            if (onOpenCentralPositionDialog != null) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_position_title"),
                    highlightKey = "pref_lyrics_position_title",
                    icon = Icons.Rounded.ViewWeek,
                    subtitle = when (playerViewModel.lyricsCentralPosition) {
                        LyricsAlignment.LEFT -> str("align_left")
                        LyricsAlignment.CENTER -> str("align_center_simple")
                        LyricsAlignment.RIGHT -> str("align_right")
                    },
                    onClick = onOpenCentralPositionDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_align"),
                    highlightKey = "pref_lyrics_align",
                    icon = Icons.Rounded.FormatAlignCenter,
                    subtitle = when (alignment) {
                        LyricsAlignment.LEFT -> str("align_left")
                        LyricsAlignment.CENTER -> str("align_center_simple")
                        LyricsAlignment.RIGHT -> str("align_right")
                    },
                    onClick = onOpenAlignmentDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_size"),
                    highlightKey = "pref_lyrics_size",
                    icon = Icons.Rounded.FormatSize,
                    subtitle = "${fontSize.roundToInt()} sp",
                    onClick = onOpenFontSizeDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_line_spacing_title"),
                    highlightKey = "pref_lyrics_line_spacing_title",
                    icon = Icons.Rounded.FormatLineSpacing,
                    subtitle = "${lineSpacing.toInt()} dp",
                    onClick = onOpenLineSpacingDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_horizontal_margin_title"),
                    highlightKey = "pref_lyrics_horizontal_margin_title",
                    icon = Icons.Rounded.SwapHoriz,
                    subtitle = "${horizontalMargin.toInt()} dp",
                    onClick = onOpenHorizontalMarginDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_vertical_offset_title"),
                    highlightKey = "pref_lyrics_vertical_offset_title",
                    icon = Icons.Rounded.VerticalAlignCenter,
                    subtitle = "${(verticalOffset * 100).toInt()}%",
                    onClick = onOpenVerticalOffsetDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_active_scale_title"),
                    highlightKey = "pref_lyrics_active_scale_title",
                    icon = Icons.Rounded.ZoomOutMap,
                    subtitle = "${(activeScale * 100).toInt()}%",
                    onClick = onOpenActiveScaleDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_reset_typography"),
                    highlightKey = "pref_lyrics_reset_typography",
                    icon = Icons.Rounded.RestartAlt,
                    subtitle = str("pref_lyrics_reset_typography_desc"),
                    onClick = { playerViewModel.resetLyricsTypography(isFullScreen = isFS, isSidebar = isSB) },
                )
            }
        },
    )

    SettingsGroup(
        title = str("lyrics_content_title"),
        items = buildList {
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_word_sync"),
                    highlightKey = "pref_lyrics_word_sync",
                    icon = Icons.Rounded.Mic,
                    subtitle = str("pref_lyrics_word_sync_sub"),
                    hasSwitch = true,
                    switchState = wordSync,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.toggleFullScreenWordSync(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.toggleSidebarWordSync(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.toggleWordSync(enabled)
                        }
                    },
                )
            }
            if (wordSync) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_apple_effect"),
                    highlightKey = "pref_lyrics_apple_effect",
                    icon = Icons.Rounded.AutoAwesome,
                    subtitle = str("pref_lyrics_apple_effect_sub"),
                    hasSwitch = true,
                    switchState = appleEffect,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.toggleFullScreenAppleMusicEffect(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.toggleSidebarAppleMusicEffect(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.toggleAppleMusicEffect(enabled)
                        }
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_duet_title"),
                    highlightKey = "pref_lyrics_duet_title",
                    icon = Icons.Rounded.Groups,
                    subtitle = str("pref_lyrics_duet_desc"),
                    hasSwitch = true,
                    switchState = duetView,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.toggleFullScreenDuetView(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.toggleSidebarDuetView(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.toggleDuetView(enabled)
                        }
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_backing_title"),
                    highlightKey = "pref_lyrics_backing",
                    icon = Icons.Rounded.RecordVoiceOver,
                    subtitle = str("pref_lyrics_backing_sub"),
                    hasSwitch = true,
                    switchState = splitBacking,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenSplitBackingVocals(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarSplitBackingVocals(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsSplitBackingVocals(enabled)
                        }
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_reveal_words_title"),
                    highlightKey = "pref_lyrics_reveal_words",
                    icon = Icons.Rounded.Animation,
                    subtitle = str("pref_lyrics_reveal_words_sub"),
                    hasSwitch = true,
                    switchState = revealWords,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenRevealWords(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarRevealWords(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsRevealWords(enabled)
                        }
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_line_animation"),
                    highlightKey = "pref_lyrics_line_animation",
                    icon = Icons.Rounded.Lyrics,
                    subtitle = str("pref_lyrics_line_animation_sub"),
                    hasSwitch = true,
                    switchState = sweepEnabled,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenSweepEnabled(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarSweepEnabled(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsSweepEnabled(enabled)
                        }
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_instrumental_indicator"),
                    highlightKey = "pref_instrumental_indicator",
                    icon = Icons.Rounded.GraphicEq,
                    subtitle = instrumentalStyleLabel(instrumentalStyle),
                    onClick = onOpenInstrumentalDialog,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_romanization"),
                    highlightKey = "pref_lyrics_romanization",
                    icon = Icons.Rounded.Abc,
                    subtitle = str("pref_lyrics_romanization_sub"),
                    hasSwitch = true,
                    switchState = romanization,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.toggleFullScreenRomanization(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.toggleSidebarRomanization(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.toggleRomanization(enabled)
                        }
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_translation_title"),
                    highlightKey = "pref_lyrics_translation_title",
                    icon = Icons.Rounded.Translate,
                    subtitle = str("pref_lyrics_translation_sub"),
                    hasSwitch = true,
                    switchState = translation,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.toggleFullScreenLyricsTranslation(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.toggleSidebarLyricsTranslation(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.toggleLyricsTranslation(enabled)
                        }
                    },
                )
            }
            if (translation) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_translation_lang"),
                    highlightKey = "pref_lyrics_translation_lang",
                    icon = Icons.Rounded.Language,
                    subtitle = playerViewModel.lyricsTranslationLang.uppercase(),
                    onClick = onOpenTranslationLangDialog,
                )
            }
        },
    )

    SettingsGroup(
        title = str("pref_lyrics_effects_category"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_lrc_bounce_title"),
                    highlightKey = "pref_lyrics_lrc_bounce_title",
                    icon = Icons.Rounded.Animation,
                    subtitle = str("pref_lyrics_lrc_bounce_desc"),
                    hasSwitch = true,
                    switchState = lrcBounce,
                    onSwitchChange = { enabled ->
                        when (mode) {
                            LyricsSurfaceMode.FULLSCREEN -> playerViewModel.updateLyricsFullScreenLrcBounceEnabled(enabled)
                            LyricsSurfaceMode.SIDEBAR -> playerViewModel.updateLyricsSidebarLrcBounceEnabled(enabled)
                            LyricsSurfaceMode.CENTRAL -> playerViewModel.updateLyricsLrcBounceEnabled(enabled)
                        }
                    },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_bounce_factor_title"),
                    highlightKey = "pref_lyrics_bounce_factor_title",
                    icon = Icons.Rounded.Height,
                    subtitle = "${(bounceFactor * 100).toInt()}%",
                    onClick = onOpenBounceFactorDialog,
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_glow_factor_title"),
                    highlightKey = "pref_lyrics_glow_factor_title",
                    icon = Icons.Rounded.LightMode,
                    subtitle = "${(glowFactor * 100).toInt()}%",
                    onClick = onOpenGlowFactorDialog,
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_lyrics_fill_transition_title"),
                    highlightKey = "pref_lyrics_fill_transition_title",
                    icon = Icons.Rounded.Gradient,
                    subtitle = "${fillTransition.toInt()} dp",
                    onClick = onOpenFillTransitionDialog,
                )
            },
        ),
    )
}

@Composable
private fun instrumentalStyleLabel(style: InstrumentalIndicatorStyle): String = when (style) {
    InstrumentalIndicatorStyle.DOTS -> str("pref_instrumental_dots")
    InstrumentalIndicatorStyle.CIRCLE -> str("pref_instrumental_circle")
    InstrumentalIndicatorStyle.NOTE_WAVE -> str("pref_instrumental_note_wave")
    InstrumentalIndicatorStyle.OFF -> str("pref_instrumental_off")
}

@Composable
private fun LyricsModeSliderDialog(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    stepDelta: Float,
    defaultValue: Float,
    subtitle: String? = null,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (subtitle != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(valueText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { onValueChange((value - stepDelta).coerceAtLeast(valueRange.start)) },
                    ) { Icon(Icons.Rounded.Remove, null) }
                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        valueRange = valueRange,
                        steps = steps,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { onValueChange((value + stepDelta).coerceAtMost(valueRange.endInclusive)) },
                    ) { Icon(Icons.Rounded.Add, null) }
                }
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { onValueChange(defaultValue) }) {
                        Text(str("pref_lyrics_reset"))
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                        Text(str("btn_close"))
                    }
                }
            }
        }
    }
}
