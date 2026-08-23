package com.alananasss.kittytune.ui.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.theme.End4ThemeManager
import com.alananasss.kittytune.ui.profile.DiscordLoginScreen
import kotlinx.coroutines.launch

private data class SetupColor(val color: Color, val nameRes: String)

private val ExtendedColors = listOf(
    SetupColor(Color(0xFFF44336), "Red"),
    SetupColor(Color(0xFFE91E63), "Pink"),
    SetupColor(Color(0xFF9C27B0), "Purple"),
    SetupColor(Color(0xFF673AB7), "Deep Purple"),
    SetupColor(Color(0xFF3F51B5), "Indigo"),
    SetupColor(Color(0xFF2196F3), "Blue"),
    SetupColor(Color(0xFF03A9F4), "Light Blue"),
    SetupColor(Color(0xFF00BCD4), "Cyan"),
    SetupColor(Color(0xFF009688), "Teal"),
    SetupColor(Color(0xFF4CAF50), "Green"),
    SetupColor(Color(0xFF8BC34A), "Light Green"),
    SetupColor(Color(0xFFCDDC39), "Lime"),
    SetupColor(Color(0xFFFFEB3B), "Yellow"),
    SetupColor(Color(0xFFFFC107), "Amber"),
    SetupColor(Color(0xFFFF9800), "Orange"),
    SetupColor(Color(0xFFFF5722), "Deep Orange"),
    SetupColor(Color(0xFF795548), "Brown")
)

@Composable
fun <T> ExpressiveButtonGroup(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.first == selectedOption
            
            val cornerRadius by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 4.dp,
                animationSpec = tween(durationMillis = 300)
            )
            
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 200)
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(durationMillis = 200)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(containerColor)
                    .clickable { onOptionSelected(option.first) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.second,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    val coroutineScope = rememberCoroutineScope()
    
    var showDiscordLogin by remember { mutableStateOf(false) }
    
    if (showDiscordLogin) {
        DiscordLoginScreen(
            onBackClick = { showDiscordLogin = false },
            onLoginSuccess = { showDiscordLogin = false }
        )
        return
    }

    var selectedColor by remember { mutableStateOf(Color(0xFFFF7A1A)) }
    var useEnd4 by remember { mutableStateOf(false) }
    var useWindowsAccent by remember { mutableStateOf(false) }
    
    val end4Installed = remember { End4ThemeManager.isInstalled() }
    val isWindowsOS = remember { System.getProperty("os.name").lowercase().contains("win") }

    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var language by remember { mutableStateOf(prefs.getAppLanguage()) }
    var discordRpc by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }
    var discordToken by remember { mutableStateOf(prefs.getDiscordToken()) }

    // Enforce default variant on first load
    LaunchedEffect(Unit) {
        prefs.setColorStyle("Vibrant")
        prefs.setKeyColor(selectedColor.toArgb())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth(0.9f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = str("setup_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // LEFT COLUMN: Appearance
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = str("setup_appearance"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Color Picker
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = str("setup_choose_color"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                val listState = rememberLazyListState()
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                listState.animateScrollBy(-200f)
                                            }
                                        },
                                        enabled = listState.canScrollBackward
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronLeft, 
                                            contentDescription = null, 
                                            tint = if (listState.canScrollBackward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }

                                    Box(modifier = Modifier.weight(1f)) {
                                        LazyRow(
                                            state = listState,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            items(ExtendedColors) { setupColor ->
                                                val isSelected = selectedColor == setupColor.color && !useEnd4 && !useWindowsAccent
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(setupColor.color)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 0.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            selectedColor = setupColor.color
                                                            useEnd4 = false
                                                            useWindowsAccent = false
                                                            prefs.setKeyColor(selectedColor.toArgb())
                                                            prefs.setColorStyle("Vibrant")
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        if (listState.canScrollBackward) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .matchParentSize()
                                                    .width(20.dp)
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(MaterialTheme.colorScheme.surfaceContainerHigh, Color.Transparent)
                                                        )
                                                    )
                                            )
                                        }
                                        
                                        if (listState.canScrollForward) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .matchParentSize()
                                                    .width(20.dp)
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerHigh)
                                                        )
                                                    )
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                listState.animateScrollBy(200f)
                                            }
                                        },
                                        enabled = listState.canScrollForward
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight, 
                                            contentDescription = null, 
                                            tint = if (listState.canScrollForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                        }

                        // End4 or Windows Accent
                        if (end4Installed || isWindowsOS) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (end4Installed) str("setup_end4_detected") else str("setup_windows_color"),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (end4Installed) str("setup_use_end4") else str("setup_use_windows_color"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = if (end4Installed) useEnd4 else useWindowsAccent,
                                        onCheckedChange = { checked ->
                                            if (end4Installed) {
                                                useEnd4 = checked
                                                if (checked) {
                                                    useWindowsAccent = false
                                                    prefs.setColorStyle("end4 (Material You)")
                                                } else {
                                                    prefs.setColorStyle("Vibrant")
                                                    prefs.setKeyColor(selectedColor.toArgb())
                                                }
                                            } else if (isWindowsOS) {
                                                useWindowsAccent = checked
                                                if (checked) {
                                                    useEnd4 = false
                                                    prefs.setColorStyle("Windows Accent")
                                                } else {
                                                    prefs.setColorStyle("Vibrant")
                                                    prefs.setKeyColor(selectedColor.toArgb())
                                                }
                                            }
                                        },
                                        thumbContent = { 
                                            val isChecked = if (end4Installed) useEnd4 else useWindowsAccent
                                            Icon(
                                                imageVector = if (isChecked) Icons.Filled.Check else Icons.Rounded.Close, 
                                                contentDescription = null, 
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            ) 
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedIconColor = MaterialTheme.colorScheme.primary,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                            uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    )
                                }
                            }
                        }

                        // Theme Mode
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = str("setup_theme_mode"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                val themeOptions = listOf(
                                    AppThemeMode.SYSTEM to str("setup_theme_system"),
                                    AppThemeMode.LIGHT to str("setup_theme_light"),
                                    AppThemeMode.DARK to str("setup_theme_dark")
                                )
                                
                                ExpressiveButtonGroup(
                                    options = themeOptions,
                                    selectedOption = themeMode,
                                    onOptionSelected = {
                                        themeMode = it
                                        prefs.setThemeMode(it)
                                    }
                                )
                            }
                        }
                    }

                    // RIGHT COLUMN: General & Continue Action
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = str("setup_general"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Language Card
                        var expanded by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = str("setup_language"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        val langText = when (language) {
                                            AppLanguage.SYSTEM -> str("setup_theme_system")
                                            AppLanguage.ENGLISH -> "English"
                                            AppLanguage.FRENCH -> "Français"
                                            AppLanguage.HUNGARIAN -> "Magyar"
                                            AppLanguage.RUSSIAN -> "Русский"
                                        }
                                        Text(langText)
                                    }
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        AppLanguage.values().forEach { lang ->
                                            DropdownMenuItem(
                                                text = {
                                                    val text = when (lang) {
                                                        AppLanguage.SYSTEM -> str("setup_theme_system")
                                                        AppLanguage.ENGLISH -> "English"
                                                        AppLanguage.FRENCH -> "Français"
                                                        AppLanguage.HUNGARIAN -> "Magyar"
                                                        AppLanguage.RUSSIAN -> "Русский"
                                                    }
                                                    Text(text)
                                                },
                                                onClick = {
                                                    language = lang
                                                    prefs.setAppLanguage(lang)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Discord RPC Configuration Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = str("setup_discord_rpc"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = str("setup_discord_rpc_desc"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Surface(
                                        shape = CircleShape,
                                        color = if (discordToken != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = if (discordToken == null) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (discordToken != null) Icons.Default.Check else Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (discordToken != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = if (discordToken != null) str("setup_discord_connected") else str("setup_discord_not_connected"),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (discordToken != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                if (discordToken != null) {
                                    // Account Connected Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { 
                                                discordToken = null
                                                discordRpc = false
                                                prefs.setDiscordToken(null)
                                                prefs.setDiscordRpcEnabled(false)
                                                showDiscordLogin = true 
                                            },
                                            shapes = ButtonDefaults.shapes(),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text(str("setup_discord_change"), style = MaterialTheme.typography.labelMedium)
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                discordToken = null
                                                discordRpc = false
                                                prefs.setDiscordToken(null)
                                                prefs.setDiscordRpcEnabled(false)
                                            },
                                            shapes = ButtonDefaults.shapes(),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 8.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        ) {
                                            Text(str("setup_discord_disconnect"), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                } else {
                                    // Not Connected State
                                    Button(
                                        onClick = { showDiscordLogin = true },
                                        shapes = ButtonDefaults.shapes(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(str("setup_discord_connect"))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Continue Button
                        Button(
                            onClick = {
                                prefs.setHasCompletedSetup(true)
                                onSetupComplete()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = str("setup_continue"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
