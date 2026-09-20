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
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Shape
        import com.alananasss.kittytune.core.EscapableAlertDialog
        import com.alananasss.kittytune.core.BackHandler
        import com.alananasss.kittytune.core.str
        import com.alananasss.kittytune.core.trackTextInput
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.window.Dialog
        import com.alananasss.kittytune.data.local.LyricsAlignment
    import com.alananasss.kittytune.data.local.LyricsDisplayStyle
    import com.alananasss.kittytune.data.local.LyricsUnderCoverPlacement
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.common.SettingsGroupTitle
    import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
    import com.alananasss.kittytune.data.lyrics.providers.DefaultLyricsProviderOrder
    import com.alananasss.kittytune.data.lyrics.clients.PaxsenixClient
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
        val fullScreenAlignment = playerViewModel.lyricsFullScreenAlignment
        var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }
        var showLyricsButton by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
        var inlineLyrics by remember { mutableStateOf(prefs.getInlineLyricsEnabled()) }
        var lyricsUnderCover by remember { mutableStateOf(prefs.getLyricsUnderCoverEnabled()) }
        var lyricsMultiState by remember { mutableStateOf(prefs.getLyricsMultiStateToggle()) }
        var lyricsUnderCoverPlacement by remember { mutableStateOf(prefs.getLyricsUnderCoverPlacement()) }
        var lyricsUnderCoverAlways by remember { mutableStateOf(prefs.getLyricsUnderCoverAlwaysVisible()) }
        var showPlacementDialog by remember { mutableStateOf(false) }
    
        var showAlignmentDialog by remember { mutableStateOf(false) }
        var showFullScreenAlignmentDialog by remember { mutableStateOf(false) }
        var showDisplayStyleDialog by remember { mutableStateOf(false) }
        var showFullScreenDisplayStyleDialog by remember { mutableStateOf(false) }
        var showFontSizeDialog by remember { mutableStateOf(false) }
        var showFullScreenFontSizeDialog by remember { mutableStateOf(false) }
        var showAutoScrollSpeedDialog by remember { mutableStateOf(false) }
        var showWheelStepDialog by remember { mutableStateOf(false) }
        var provider by remember { mutableStateOf(playerViewModel.lyricsProvider) }
        var showProviderDialog by remember { mutableStateOf(false) }

        var enableTranslation by remember { mutableStateOf(prefs.getLyricsTranslationEnabled()) }
        var targetLang by remember { mutableStateOf(prefs.getLyricsTranslationLang()) }
        var showLangDialog by remember { mutableStateOf(false) }
        var showPaxsenixKeyDialog by remember { mutableStateOf(false) }
        var paxsenixKeyInput by remember { mutableStateOf(prefs.getPaxsenixApiKey()) }
        var showProviderOrderDialog by remember { mutableStateOf(false) }
        var providerOrder by remember { mutableStateOf(prefs.getLyricsProviderOrder()) }

        var showUiStyleDialog by remember { mutableStateOf(false) }
        var showLyricsFontDialog by remember { mutableStateOf(false) }
        var showBounceFactorDialog by remember { mutableStateOf(false) }
        var showGlowFactorDialog by remember { mutableStateOf(false) }
        var showFillTransitionDialog by remember { mutableStateOf(false) }
        var showLineSpacingDialog by remember { mutableStateOf(false) }

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

        if (showPlacementDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showPlacementDialog = false },
                title = { Text(str("pref_lyrics_under_cover_placement")) },
                text = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                lyricsUnderCoverPlacement = LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST
                                prefs.setLyricsUnderCoverPlacement(lyricsUnderCoverPlacement)
                                showPlacementDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("pref_lyrics_under_cover_replace"))
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                lyricsUnderCoverPlacement = LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST
                                prefs.setLyricsUnderCoverPlacement(lyricsUnderCoverPlacement)
                                showPlacementDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("pref_lyrics_under_cover_above"))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showPlacementDialog = false }) { Text(str("btn_cancel")) } }
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
                    TextButton(onClick = {
                        paxsenixKeyInput = tempKey
                        prefs.setPaxsenixApiKey(tempKey)
                        PaxsenixClient.setApiKey(tempKey)
                        showPaxsenixKeyDialog = false
                    }) {
                        Text(str("btn_save", "Save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPaxsenixKeyDialog = false }) {
                        Text(str("btn_cancel", "Cancel"))
                    }
                }
            )
        }

        if (showProviderOrderDialog) {
            var currentOrder by remember { mutableStateOf(prefs.getLyricsProviderOrder().toMutableList()) }
            EscapableAlertDialog(
                onDismissRequest = { showProviderOrderDialog = false },
                title = { Text(str("pref_lyrics_order", "Provider Priority Order")) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(currentOrder.size) { index ->
                            val p = currentOrder[index]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${index + 1}. ${p.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    if (index > 0) {
                                        IconButton(onClick = {
                                            val list = currentOrder.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index - 1, item)
                                            currentOrder = list
                                        }) {
                                            Text("▲")
                                        }
                                    }
                                    if (index < currentOrder.size - 1) {
                                        IconButton(onClick = {
                                            val list = currentOrder.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index + 1, item)
                                            currentOrder = list
                                        }) {
                                            Text("▼")
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        providerOrder = currentOrder
                        prefs.setLyricsProviderOrder(currentOrder)
                        showProviderOrderDialog = false
                    }) {
                        Text(str("btn_save", "Save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showProviderOrderDialog = false }) {
                        Text(str("btn_cancel", "Cancel"))
                    }
                }
            )
        }
    
        // --- DIALOGS ---

        if (showUiStyleDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showUiStyleDialog = false },
                title = { Text(str("pref_lyrics_ui_style_title")) },
                text = {
                    Column {
                        com.alananasss.kittytune.data.local.LyricsUiStyle.entries.forEach { style ->
                            val label = when (style) {
                                com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> str("pref_lyrics_ui_style_enhanced", "Apple Music")
                                com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> str("pref_lyrics_ui_style_classic", "Classique")
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    playerViewModel.updateLyricsUiStyle(style)
                                    showUiStyleDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (playerViewModel.lyricsUiStyle == style), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUiStyleDialog = false }) {
                        Text(str("btn_cancel"))
                    }
                }
            )
        }

        if (showLyricsFontDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showLyricsFontDialog = false },
                title = { Text(str("pref_lyrics_font_title")) },
                text = {
                    Column {
                        com.alananasss.kittytune.data.local.LyricsFont.entries.forEach { font ->
                            val label = when (font) {
                                com.alananasss.kittytune.data.local.LyricsFont.APPLE -> str("pref_lyrics_font_apple")
                                com.alananasss.kittytune.data.local.LyricsFont.APP_DEFAULT -> str("pref_lyrics_font_app_default")
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    playerViewModel.updateLyricsFont(font)
                                    showLyricsFontDialog = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (playerViewModel.lyricsFont == font), onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLyricsFontDialog = false }) {
                        Text(str("btn_cancel"))
                    }
                }
            )
        }


        if (showBounceFactorDialog) {
            BackHandler(onBack = { showBounceFactorDialog = false })
            Dialog(onDismissRequest = { showBounceFactorDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_bounce_factor_title"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${(playerViewModel.lyricsBounceFactor * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsBounceFactor((playerViewModel.lyricsBounceFactor - 0.1f).coerceAtLeast(0f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsBounceFactor,
                                onValueChange = { playerViewModel.updateLyricsBounceFactor(it) },
                                valueRange = 0f..2f,
                                steps = 19,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsBounceFactor((playerViewModel.lyricsBounceFactor + 0.1f).coerceAtMost(2f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsBounceFactor(1f) }) { Text(str("pref_lyrics_reset")) }
                            TextButton(onClick = { showBounceFactorDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showGlowFactorDialog) {
            BackHandler(onBack = { showGlowFactorDialog = false })
            Dialog(onDismissRequest = { showGlowFactorDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_glow_factor_title"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${(playerViewModel.lyricsGlowFactor * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsGlowFactor((playerViewModel.lyricsGlowFactor - 0.1f).coerceAtLeast(0f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsGlowFactor,
                                onValueChange = { playerViewModel.updateLyricsGlowFactor(it) },
                                valueRange = 0f..2f,
                                steps = 19,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsGlowFactor((playerViewModel.lyricsGlowFactor + 0.1f).coerceAtMost(2f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsGlowFactor(1f) }) { Text(str("pref_lyrics_reset")) }
                            TextButton(onClick = { showGlowFactorDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showFillTransitionDialog) {
            BackHandler(onBack = { showFillTransitionDialog = false })
            Dialog(onDismissRequest = { showFillTransitionDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_fill_transition_title"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${playerViewModel.lyricsFillTransitionWidth.toInt()} dp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsFillTransitionWidth((playerViewModel.lyricsFillTransitionWidth - 2f).coerceAtLeast(2f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsFillTransitionWidth,
                                onValueChange = { playerViewModel.updateLyricsFillTransitionWidth(it) },
                                valueRange = 2f..24f,
                                steps = 10,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsFillTransitionWidth((playerViewModel.lyricsFillTransitionWidth + 2f).coerceAtMost(24f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsFillTransitionWidth(8f) }) { Text(str("pref_lyrics_reset")) }
                            TextButton(onClick = { showFillTransitionDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

        if (showLineSpacingDialog) {
            BackHandler(onBack = { showLineSpacingDialog = false })
            Dialog(onDismissRequest = { showLineSpacingDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(str("pref_lyrics_line_spacing_title"), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${playerViewModel.lyricsLineSpacing.toInt()} dp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            IconButton(onClick = { playerViewModel.updateLyricsLineSpacing((playerViewModel.lyricsLineSpacing - 2f).coerceAtLeast(12f)) }) { Icon(Icons.Rounded.Remove, null) }
                            Slider(
                                value = playerViewModel.lyricsLineSpacing,
                                onValueChange = { playerViewModel.updateLyricsLineSpacing(it) },
                                valueRange = 12f..48f,
                                steps = 17,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { playerViewModel.updateLyricsLineSpacing((playerViewModel.lyricsLineSpacing + 2f).coerceAtMost(48f)) }) { Icon(Icons.Rounded.Add, null) }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { playerViewModel.updateLyricsLineSpacing(24f) }) { Text(str("pref_lyrics_reset")) }
                            TextButton(onClick = { showLineSpacingDialog = false }) { Text(str("btn_close")) }
                        }
                    }
                }
            }
        }

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

        if (showFullScreenAlignmentDialog) {
            EscapableAlertDialog(
                onDismissRequest = { showFullScreenAlignmentDialog = false },
                title = { Text(str("pref_lyrics_fullscreen_align")) },
                text = {
                    Column {
                        AlignRadioButton(str("align_left"), LyricsAlignment.LEFT, fullScreenAlignment) { playerViewModel.updateLyricsFullScreenAlignment(it); showFullScreenAlignmentDialog = false }
                        AlignRadioButton(str("align_center"), LyricsAlignment.CENTER, fullScreenAlignment) { playerViewModel.updateLyricsFullScreenAlignment(it); showFullScreenAlignmentDialog = false }
                        AlignRadioButton(str("align_right"), LyricsAlignment.RIGHT, fullScreenAlignment) { playerViewModel.updateLyricsFullScreenAlignment(it); showFullScreenAlignmentDialog = false }
                    }
                },
                confirmButton = { TextButton(onClick = { showFullScreenAlignmentDialog = false }) { Text(str("btn_cancel")) } }
            )
        }
    
        if (showDisplayStyleDialog) {
            val currentStyle = playerViewModel.lyricsDisplayStyle
            val hasScale = currentStyle == LyricsDisplayStyle.SCALE || currentStyle == LyricsDisplayStyle.SCALE_FOCUS
            val hasFocus = currentStyle == LyricsDisplayStyle.FOCUS || currentStyle == LyricsDisplayStyle.SCALE_FOCUS

            EscapableAlertDialog(
                onDismissRequest = { showDisplayStyleDialog = false },
                title = { Text(str("pref_lyrics_display_style")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = when {
                                        !hasScale && hasFocus -> LyricsDisplayStyle.SCALE_FOCUS
                                        !hasScale -> LyricsDisplayStyle.SCALE
                                        hasFocus -> LyricsDisplayStyle.FOCUS
                                        else -> LyricsDisplayStyle.STANDARD
                                    }
                                    playerViewModel.updateLyricsDisplayStyle(next)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasScale,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(str("lyrics_style_scale"), fontWeight = FontWeight.Bold)
                                Text(
                                    str("lyrics_style_scale_sub"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = when {
                                        hasScale && !hasFocus -> LyricsDisplayStyle.SCALE_FOCUS
                                        hasScale -> LyricsDisplayStyle.SCALE
                                        !hasFocus -> LyricsDisplayStyle.FOCUS
                                        else -> LyricsDisplayStyle.STANDARD
                                    }
                                    playerViewModel.updateLyricsDisplayStyle(next)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasFocus,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(str("lyrics_style_focus"), fontWeight = FontWeight.Bold)
                                Text(
                                    str("lyrics_style_focus_sub"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDisplayStyleDialog = false }) { Text(str("btn_close")) }
                }
            )
        }

        if (showFullScreenDisplayStyleDialog) {
            val currentStyle = playerViewModel.lyricsFullScreenDisplayStyle
            val hasScale = currentStyle == LyricsDisplayStyle.SCALE || currentStyle == LyricsDisplayStyle.SCALE_FOCUS
            val hasFocus = currentStyle == LyricsDisplayStyle.FOCUS || currentStyle == LyricsDisplayStyle.SCALE_FOCUS

            EscapableAlertDialog(
                onDismissRequest = { showFullScreenDisplayStyleDialog = false },
                title = { Text(str("pref_lyrics_fullscreen_display_style")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = when {
                                        !hasScale && hasFocus -> LyricsDisplayStyle.SCALE_FOCUS
                                        !hasScale -> LyricsDisplayStyle.SCALE
                                        hasFocus -> LyricsDisplayStyle.FOCUS
                                        else -> LyricsDisplayStyle.STANDARD
                                    }
                                    playerViewModel.updateLyricsFullScreenDisplayStyle(next)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasScale,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(str("lyrics_style_scale"), fontWeight = FontWeight.Bold)
                                Text(
                                    str("lyrics_style_scale_sub"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = when {
                                        hasScale && !hasFocus -> LyricsDisplayStyle.SCALE_FOCUS
                                        hasScale -> LyricsDisplayStyle.SCALE
                                        !hasFocus -> LyricsDisplayStyle.FOCUS
                                        else -> LyricsDisplayStyle.STANDARD
                                    }
                                    playerViewModel.updateLyricsFullScreenDisplayStyle(next)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasFocus,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(str("lyrics_style_focus"), fontWeight = FontWeight.Bold)
                                Text(
                                    str("lyrics_style_focus_sub"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFullScreenDisplayStyleDialog = false }) { Text(str("btn_close")) }
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
                                    title = str("pref_lyrics_duet_title"),
                                    subtitle = str("pref_lyrics_duet_desc"),
                                    hasSwitch = true,
                                    switchState = playerViewModel.isDuetViewEnabled,
                                    onSwitchChange = { playerViewModel.toggleDuetView(it) }
                                )
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

                Box {
                    val providers = remember { PreferredLyricsProvider.entries }
                    val itemsList = remember(providerOrder) {
                        val list = mutableListOf<@Composable (Shape) -> Unit>()
                        list.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_order", "Provider Priority Order"),
                                subtitle = str("pref_lyrics_order_sub", "Order in which providers are searched"),
                                onClick = { showProviderOrderDialog = true }
                            )
                        }
                        list.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_paxsenix_key", "Paxsenix API Key"),
                                subtitle = if (paxsenixKeyInput.isNotBlank()) "••••••••" else str("pref_lyrics_paxsenix_key_sub", "Required for Apple Music, Spotify and Paxsenix Musixmatch"),
                                onClick = { showPaxsenixKeyDialog = true }
                            )
                        }
                        providers.forEach { p ->
                            list.add { shape ->
                                var enabled by remember { mutableStateOf(prefs.getLyricsProviderEnabled(p)) }
                                SettingsItem(
                                    shape = shape,
                                    title = p.displayName,
                                    subtitle = "Enable ${p.displayName}",
                                    hasSwitch = true,
                                    switchState = enabled,
                                    onSwitchChange = {
                                        enabled = it
                                        prefs.setLyricsProviderEnabled(p, it)
                                    }
                                )
                            }
                        }
                        list
                    }

                    SettingsGroup(
                        title = str("pref_lyrics_providers_category", "Lyrics Providers"),
                        items = itemsList
                    )
                }

                // LYRICS UI STYLE & ANIMATIONS
                Box {
                    val isEnhanced = playerViewModel.lyricsUiStyle != com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC
                    val styleItems = mutableListOf<@Composable (Shape) -> Unit>()
                    styleItems.add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = str("pref_lyrics_ui_style_title"),
                            subtitle = when (playerViewModel.lyricsUiStyle) {
                                com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> str("pref_lyrics_ui_style_enhanced", "Apple Music")
                                com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> str("pref_lyrics_ui_style_classic", "Classique")
                            },
                            onClick = { showUiStyleDialog = true }
                        )
                    }
                    if (isEnhanced) {
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_line_blur_title"),
                                subtitle = str("pref_lyrics_line_blur_desc"),
                                hasSwitch = true,
                                switchState = playerViewModel.lyricsLineBlurEnabled,
                                onSwitchChange = { playerViewModel.updateLyricsLineBlurEnabled(it) }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_lrc_bounce_title"),
                                subtitle = str("pref_lyrics_lrc_bounce_desc"),
                                hasSwitch = true,
                                switchState = playerViewModel.lyricsLrcBounceEnabled,
                                onSwitchChange = { playerViewModel.updateLyricsLrcBounceEnabled(it) }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_bounce_factor_title"),
                                subtitle = "${(playerViewModel.lyricsBounceFactor * 100).toInt()}%",
                                onClick = { showBounceFactorDialog = true }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_glow_factor_title"),
                                subtitle = "${(playerViewModel.lyricsGlowFactor * 100).toInt()}%",
                                onClick = { showGlowFactorDialog = true }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_fill_transition_title"),
                                subtitle = "${playerViewModel.lyricsFillTransitionWidth.toInt()} dp",
                                onClick = { showFillTransitionDialog = true }
                            )
                        }
                        styleItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_lyrics_line_spacing_title"),
                                subtitle = "${playerViewModel.lyricsLineSpacing.toInt()} dp",
                                onClick = { showLineSpacingDialog = true }
                            )
                        }
                    }

                    SettingsGroup(
                        title = str("pref_lyrics_effects_category"),
                        items = styleItems
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
                            val isClassic = playerViewModel.lyricsUiStyle == com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC
                            val autoScrollOn = playerViewModel.isPlainAutoScrollEnabled
                            val totalVisibleItems =
                                (if (showLyricsButton) 1 else 0) +
                                1 + // lyricsUnderCover switch
                                (if (lyricsUnderCover) 3 else 0) + // multiState, placement, always
                                1 + // lyrics font item
                                8 + // provider, show_button, align, fsAlign, size, fsSize, autoscroll, wheelStep
                                (if (isClassic) 2 else 0) + // style, fsStyle (only in classic mode)
                                (if (autoScrollOn) 1 else 0)
                            var itemIndex = 0

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_font_title"),
                                subtitle = when (playerViewModel.lyricsFont) {
                                    com.alananasss.kittytune.data.local.LyricsFont.APPLE -> str("pref_lyrics_font_apple")
                                    com.alananasss.kittytune.data.local.LyricsFont.APP_DEFAULT -> str("pref_lyrics_font_app_default")
                                },
                                onClick = { showLyricsFontDialog = true }
                            )

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_provider_title"),
                                subtitle = if (provider == com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY) str("pref_lyrics_provider_max_quality") else str("pref_lyrics_provider_open_source"),
                                onClick = { showProviderDialog = true }
                            )

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
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
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
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

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_under_cover"),
                                subtitle = str("pref_lyrics_under_cover_sub"),
                                hasSwitch = true,
                                switchState = lyricsUnderCover,
                                onSwitchChange = {
                                    lyricsUnderCover = it
                                    prefs.setLyricsUnderCoverEnabled(it)
                                }
                            )

                            androidx.compose.animation.AnimatedVisibility(
                                visible = lyricsUnderCover,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    SettingsItem(
                                        shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                        title = str("pref_lyrics_multi_state"),
                                        subtitle = str("pref_lyrics_multi_state_sub"),
                                        hasSwitch = true,
                                        switchState = lyricsMultiState,
                                        onSwitchChange = {
                                            lyricsMultiState = it
                                            prefs.setLyricsMultiStateToggle(it)
                                        }
                                    )

                                    SettingsItem(
                                        shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                        title = str("pref_lyrics_under_cover_placement"),
                                        subtitle = when (lyricsUnderCoverPlacement) {
                                            LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST -> str("pref_lyrics_under_cover_replace")
                                            LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST -> str("pref_lyrics_under_cover_above")
                                        },
                                        onClick = { showPlacementDialog = true }
                                    )

                                    SettingsItem(
                                        shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                        title = str("pref_lyrics_under_cover_always"),
                                        subtitle = str("pref_lyrics_under_cover_always_sub"),
                                        hasSwitch = true,
                                        switchState = lyricsUnderCoverAlways,
                                        onSwitchChange = {
                                            lyricsUnderCoverAlways = it
                                            prefs.setLyricsUnderCoverAlwaysVisible(it)
                                        }
                                    )
                                }
                            }
    
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_align"),
                                subtitle = when(alignment) {
                                    LyricsAlignment.LEFT -> str("align_left")
                                    LyricsAlignment.CENTER -> str("align_center_simple")
                                    LyricsAlignment.RIGHT -> str("align_right")
                                },
                                onClick = { showAlignmentDialog = true }
                            )

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_fullscreen_align"),
                                subtitle = when(fullScreenAlignment) {
                                    LyricsAlignment.LEFT -> str("align_left")
                                    LyricsAlignment.CENTER -> str("align_center_simple")
                                    LyricsAlignment.RIGHT -> str("align_right")
                                },
                                onClick = { showFullScreenAlignmentDialog = true }
                            )
    
                            if (isClassic) {
                                SettingsItem(
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                    title = str("pref_lyrics_display_style"),
                                    subtitle = displayStyleLabel(playerViewModel.lyricsDisplayStyle),
                                    onClick = { showDisplayStyleDialog = true }
                                )

                                SettingsItem(
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                    title = str("pref_lyrics_fullscreen_display_style"),
                                    subtitle = displayStyleLabel(playerViewModel.lyricsFullScreenDisplayStyle),
                                    onClick = { showFullScreenDisplayStyleDialog = true }
                                )
                            }

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_size"),
                                subtitle = "${fontSize.roundToInt()} sp",
                                onClick = { showFontSizeDialog = true }
                            )

                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                title = str("pref_lyrics_fullscreen_size"),
                                subtitle = "${fullScreenFontSize.roundToInt()} sp",
                                onClick = { showFullScreenFontSizeDialog = true }
                            )

                            // Only unsynced lyrics scroll on their own — synced ones already
                            // follow the track (issue #33).
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
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
                                    shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
                                    title = str("pref_lyrics_autoscroll_speed"),
                                    subtitle = autoScrollSpeedLabel(playerViewModel.plainAutoScrollSpeed),
                                    onClick = { showAutoScrollSpeedDialog = true }
                                )
                            }

                            // Applies to every lyrics view, synced or not, which is why it sits
                            // outside the auto-scroll block (issue #33).
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(totalVisibleItems, itemIndex++),
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

/** Settings label for lyrics display style. */
private fun displayStyleLabel(style: LyricsDisplayStyle): String = when (style) {
    LyricsDisplayStyle.STANDARD -> str("lyrics_style_standard")
    LyricsDisplayStyle.SCALE -> str("lyrics_style_scale")
    LyricsDisplayStyle.FOCUS -> str("lyrics_style_focus")
    LyricsDisplayStyle.SCALE_FOCUS -> "${str("lyrics_style_scale")} + ${str("lyrics_style_focus")}"
}

/** One line on what each style actually does to the lines. */
private fun displayStyleDescription(style: LyricsDisplayStyle): String = when (style) {
    LyricsDisplayStyle.STANDARD -> "lyrics_style_standard_sub"
    LyricsDisplayStyle.SCALE -> "lyrics_style_scale_sub"
    LyricsDisplayStyle.FOCUS -> "lyrics_style_focus_sub"
    LyricsDisplayStyle.SCALE_FOCUS -> "lyrics_style_scale_focus_sub"
}
