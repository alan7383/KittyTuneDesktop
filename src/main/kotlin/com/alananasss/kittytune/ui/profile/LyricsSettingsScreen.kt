    package com.alananasss.kittytune.ui.profile

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults
    
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.Article // Import ajouté
    import androidx.compose.material.icons.rounded.Description
    import androidx.compose.material.icons.rounded.FormatAlignLeft
    import androidx.compose.material.icons.rounded.FormatSize
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.SdStorage
    import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import com.alananasss.kittytune.ui.common.Slider
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Shape
        import com.alananasss.kittytune.core.EscapableAlertDialog
        import com.alananasss.kittytune.core.BackHandler
        import com.alananasss.kittytune.core.str
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.window.Dialog
        import com.alananasss.kittytune.data.local.LyricsAlignment
    import com.alananasss.kittytune.data.local.LyricsDisplayStyle
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.common.SettingsGroupTitle
    import kotlin.math.roundToInt
    
    @Composable
    fun LyricsSettingsScreen(
        onBackClick: (() -> Unit)? = null,
        playerViewModel: PlayerViewModel
    ) {
            val prefs = remember { PlayerPreferences() }
    
        val fontSize = playerViewModel.lyricsFontSize
        val fullScreenFontSize = playerViewModel.lyricsFullScreenFontSize
        val alignment = playerViewModel.lyricsAlignment
        var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }
        var showLyricsButton by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
        var inlineLyrics by remember { mutableStateOf(prefs.getInlineLyricsEnabled()) }
    
        var showAlignmentDialog by remember { mutableStateOf(false) }
        var showDisplayStyleDialog by remember { mutableStateOf(false) }
        var showFontSizeDialog by remember { mutableStateOf(false) }
        var showFullScreenFontSizeDialog by remember { mutableStateOf(false) }
        var showAutoScrollSpeedDialog by remember { mutableStateOf(false) }
        var showWheelStepDialog by remember { mutableStateOf(false) }
        var provider by remember { mutableStateOf(playerViewModel.lyricsProvider) }
        var showProviderDialog by remember { mutableStateOf(false) }

        var enableTranslation by remember { mutableStateOf(prefs.getLyricsTranslationEnabled()) }
        var targetLang by remember { mutableStateOf(prefs.getLyricsTranslationLang()) }
        var showLangDialog by remember { mutableStateOf(false) }

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
                confirmButton = { TextButton(onClick = { showProviderDialog = false }) { Text(str("btn_cancel")) } }
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
                confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text(str("btn_cancel")) } }
            )
        }
    
        // --- DIALOGS ---
    
        if (showFontSizeDialog) {
            BackHandler(onBack = { showFontSizeDialog = false })
            Dialog(onDismissRequest = { showFontSizeDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_size"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${fontSize.roundToInt()} sp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsFontSize((fontSize - 2f).coerceAtLeast(12f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = fontSize,
                                onValueChange = { playerViewModel.updateLyricsFontSize(it) },
                                valueRange = 12f..100f,
                                steps = 43,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsFontSize((fontSize + 2f).coerceAtMost(100f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsFontSize(42f) }) { Text(str("pref_lyrics_reset")) }
                            TextButton(onClick = { showFontSizeDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showFullScreenFontSizeDialog) {
            BackHandler(onBack = { showFullScreenFontSizeDialog = false })
            Dialog(onDismissRequest = { showFullScreenFontSizeDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_fullscreen_size"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${fullScreenFontSize.roundToInt()} sp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsFullScreenFontSize((fullScreenFontSize - 2f).coerceAtLeast(12f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = fullScreenFontSize,
                                onValueChange = { playerViewModel.updateLyricsFullScreenFontSize(it) },
                                valueRange = 12f..100f,
                                steps = 43,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsFullScreenFontSize((fullScreenFontSize + 2f).coerceAtMost(100f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsFullScreenFontSize(34f) }) { Text(str("pref_lyrics_reset")) }
                            TextButton(onClick = { showFullScreenFontSizeDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
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
                            IconButton(onClick = {
                                playerViewModel.updatePlainAutoScrollSpeed(playerViewModel.plainAutoScrollSpeed - 0.25f)
                            }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.plainAutoScrollSpeed,
                                onValueChange = { playerViewModel.updatePlainAutoScrollSpeed(it) },
                                valueRange = 0.25f..4f,
                                steps = 14,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = {
                                playerViewModel.updatePlainAutoScrollSpeed(playerViewModel.plainAutoScrollSpeed + 0.25f)
                            }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updatePlainAutoScrollSpeed(1.5f) }) {
                                Text(str("pref_lyrics_reset"))
                            }
                            TextButton(onClick = { showAutoScrollSpeedDialog = false }) { Text(str("btn_close")) }
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
                            IconButton(onClick = {
                                playerViewModel.updateLyricsWheelLines(playerViewModel.lyricsWheelLines - 0.5f)
                            }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsWheelLines,
                                onValueChange = { playerViewModel.updateLyricsWheelLines(it) },
                                valueRange = PlayerPreferences.LYRICS_WHEEL_LINES_MIN..PlayerPreferences.LYRICS_WHEEL_LINES_MAX,
                                steps = 21,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = {
                                playerViewModel.updateLyricsWheelLines(playerViewModel.lyricsWheelLines + 0.5f)
                            }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsWheelLines(3f) }) {
                                Text(str("pref_lyrics_reset"))
                            }
                            TextButton(onClick = { showWheelStepDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showAlignmentDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showAlignmentDialog = false },
                title = { Text(str("pref_lyrics_align")) },
                text = {
                    Column {
                        AlignRadioButton(str("align_left"), LyricsAlignment.LEFT, alignment) { playerViewModel.updateLyricsAlignment(it); showAlignmentDialog = false }
                        AlignRadioButton(str("align_center"), LyricsAlignment.CENTER, alignment) { playerViewModel.updateLyricsAlignment(it); showAlignmentDialog = false }
                        AlignRadioButton(str("align_right"), LyricsAlignment.RIGHT, alignment) { playerViewModel.updateLyricsAlignment(it); showAlignmentDialog = false }
                    }
                },
                confirmButton = { TextButton(onClick = { showAlignmentDialog = false }) { Text(str("btn_cancel")) } }
            )
        }
    
        if (showDisplayStyleDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showDisplayStyleDialog = false },
                title = { Text(str("pref_lyrics_display_style")) },
                text = {
                    Column {
                        LyricsDisplayStyle.entries.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerViewModel.updateLyricsDisplayStyle(option)
                                        showDisplayStyleDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = option == playerViewModel.lyricsDisplayStyle,
                                    onClick = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(str(displayStyleLabel(option)))
                                    Text(
                                        str(displayStyleDescription(option)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDisplayStyleDialog = false }) { Text(str("btn_cancel")) }
                }
            )
        }

        // --- MAIN SCREEN ---
    
        Column(modifier = Modifier.fillMaxWidth()) {
                // SOURCE
                Box {
                    SettingsGroup(
                        title = str("settings_cat_source"),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("pref_lyrics_local"),
                                    subtitle = str("pref_lyrics_local_sub"),
                                    hasSwitch = true,
                                    switchState = preferLocal,
                                    onSwitchChange = {
                                        preferLocal = it
                                        prefs.setLyricsPreferLocal(it)
                                    }
                                )
                            },

                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("pref_lyrics_word_sync"),
                                    subtitle = str("pref_lyrics_word_sync_sub"),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isWordSyncEnabled,
                                    onSwitchChange = { playerViewModel.toggleWordSync(it) }
                                )
                            },
                            { shape ->
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = playerViewModel.isWordSyncEnabled,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    SettingsItem(
                                        shape = shape,
                                        title = str("pref_lyrics_apple_effect"),
                                        subtitle = str("pref_lyrics_apple_effect_sub"),
                                        hasSwitch = true,
                                        switchState = playerViewModel.isAppleMusicEffectEnabled,
                                        onSwitchChange = { playerViewModel.toggleAppleMusicEffect(it) }
                                    )
                                }
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("pref_lyrics_romanization"),
                                    subtitle = str("pref_lyrics_romanization_sub"),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isRomanizationEnabled,
                                    onSwitchChange = { playerViewModel.toggleRomanization(it) }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("pref_lyrics_translation_title"),
                                    subtitle = str("pref_lyrics_translation_sub"),
                                    hasSwitch = true,
                                    switchState = enableTranslation,
                                    onSwitchChange = {
                                        enableTranslation = it
                                        playerViewModel.toggleLyricsTranslation(it)
                                    }
                                )
                            },
                            { shape ->
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = enableTranslation,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    SettingsItem(
                                        shape = shape,
                                        title = str("pref_lyrics_translation_lang"),
                                        subtitle = targetLang.uppercase(),
                                        onClick = { showLangDialog = true }
                                    )
                                }
                            }
                        )
                    )
                }
    
                // APPEARANCE REWORKED
                Box {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(str("settings_cat_appearance"))
    
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    
                            // The auto-scroll speed row only exists while auto-scroll is on, and
                            // the inline row only while the lyrics button is shown, so the count
                            // the shapes are derived from has to follow both.
                            val autoScrollOn = playerViewModel.isPlainAutoScrollEnabled
                            val totalVisibleItems =
                                (if (showLyricsButton) 9 else 8) + (if (autoScrollOn) 1 else 0)
    
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, 0),
                                title = str("pref_lyrics_provider_title"),
                                subtitle = if (provider == com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY) str("pref_lyrics_provider_max_quality") else str("pref_lyrics_provider_open_source"),
                                onClick = { showProviderDialog = true }
                            )

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, 1),
                                title = str("pref_lyrics_show_button"),
                                subtitle = str("pref_lyrics_show_button_sub"),
                                hasSwitch = true,
                                switchState = showLyricsButton,
                                onSwitchChange = {
                                    showLyricsButton = it
                                    prefs.setShowLyricsButtonEnabled(it)
                                }
                            )
    
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showLyricsButton,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                SettingsItem(
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, 2),
                                    title = str("pref_lyrics_inline"),
                                    subtitle = str("pref_lyrics_inline_sub"),
                                    hasSwitch = true,
                                    switchState = inlineLyrics,
                                    onSwitchChange = {
                                        inlineLyrics = it
                                        prefs.setInlineLyricsEnabled(it)
                                    }
                                )
                            }
    
                            val alignIndex = if (showLyricsButton) 3 else 2
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, alignIndex),
                                title = str("pref_lyrics_align"),
                                subtitle = when(alignment) {
                                    LyricsAlignment.LEFT -> str("align_left")
                                    LyricsAlignment.CENTER -> str("align_center_simple")
                                    LyricsAlignment.RIGHT -> str("align_right")
                                },
                                onClick = { showAlignmentDialog = true }
                            )
    
                            val styleIndex = alignIndex + 1
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, styleIndex),
                                title = str("pref_lyrics_display_style"),
                                subtitle = str(displayStyleLabel(playerViewModel.lyricsDisplayStyle)),
                                onClick = { showDisplayStyleDialog = true }
                            )

                            val sizeIndex = styleIndex + 1
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, sizeIndex),
                                title = str("pref_lyrics_size"),
                                subtitle = "${fontSize.roundToInt()} sp",
                                onClick = { showFontSizeDialog = true }
                            )

                            val fullScreenSizeIndex = sizeIndex + 1
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, fullScreenSizeIndex),
                                title = str("pref_lyrics_fullscreen_size"),
                                subtitle = "${fullScreenFontSize.roundToInt()} sp",
                                onClick = { showFullScreenFontSizeDialog = true }
                            )

                            // Only unsynced lyrics scroll on their own — synced ones already
                            // follow the track (issue #33).
                            val autoScrollIndex = fullScreenSizeIndex + 1
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, autoScrollIndex),
                                title = str("pref_lyrics_autoscroll"),
                                subtitle = str("pref_lyrics_autoscroll_sub"),
                                hasSwitch = true,
                                switchState = autoScrollOn,
                                onSwitchChange = { playerViewModel.togglePlainAutoScroll(it) }
                            )

                            androidx.compose.animation.AnimatedVisibility(
                                visible = autoScrollOn,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                SettingsItem(
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, autoScrollIndex + 1),
                                    title = str("pref_lyrics_autoscroll_speed"),
                                    subtitle = autoScrollSpeedLabel(playerViewModel.plainAutoScrollSpeed),
                                    onClick = { showAutoScrollSpeedDialog = true }
                                )
                            }

                            // Applies to every lyrics view, synced or not, which is why it sits
                            // outside the auto-scroll block (issue #33).
                            val wheelIndex = autoScrollIndex + (if (autoScrollOn) 2 else 1)
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, wheelIndex),
                                title = str("pref_lyrics_wheel_step"),
                                subtitle = str("pref_lyrics_wheel_step_value", wheelLinesLabel(playerViewModel.lyricsWheelLines)),
                                onClick = { showWheelStepDialog = true }
                            )
                        }
                    }
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

/** Settings label for one lyrics display style. */
private fun displayStyleLabel(style: LyricsDisplayStyle): String = when (style) {
    LyricsDisplayStyle.STANDARD -> "lyrics_style_standard"
    LyricsDisplayStyle.SCALE -> "lyrics_style_scale"
    LyricsDisplayStyle.FOCUS -> "lyrics_style_focus"
}

/** One line on what each style actually does to the lines. */
private fun displayStyleDescription(style: LyricsDisplayStyle): String = when (style) {
    LyricsDisplayStyle.STANDARD -> "lyrics_style_standard_sub"
    LyricsDisplayStyle.SCALE -> "lyrics_style_scale_sub"
    LyricsDisplayStyle.FOCUS -> "lyrics_style_focus_sub"
}
