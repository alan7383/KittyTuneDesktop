package com.alananasss.kittytunewebsite.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytunewebsite.ui.theme.KittyTuneWebTheme
import com.materialkolor.PaletteStyle

enum class Page {
    HOME, FEATURES, DOWNLOAD
}

// Predefined color presets like in the app
data class ColorPreset(val name: String, val color: Color)

val colorPresets = listOf(
    ColorPreset("Blue", Color(0xFF1976D2)),
    ColorPreset("Purple", Color(0xFF7B1FA2)),
    ColorPreset("Teal", Color(0xFF00897B)),
    ColorPreset("Red", Color(0xFFC62828)),
    ColorPreset("Orange", Color(0xFFE65100)),
    ColorPreset("Pink", Color(0xFFAD1457)),
    ColorPreset("Indigo", Color(0xFF283593)),
    ColorPreset("Cyan", Color(0xFF00838F)),
    ColorPreset("Green", Color(0xFF2E7D32)),
    ColorPreset("Gold", Color(0xFFF9A825)),
)

val paletteStyles = listOf(
    "Vibrant" to PaletteStyle.Vibrant,
    "Tonal Spot" to PaletteStyle.TonalSpot,
    "Expressive" to PaletteStyle.Expressive,
    "Rainbow" to PaletteStyle.Rainbow,
    "Fidelity" to PaletteStyle.Fidelity,
    "Fruit Salad" to PaletteStyle.FruitSalad,
    "Monochrome" to PaletteStyle.Monochrome,
    "Neutral" to PaletteStyle.Neutral,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
    var currentPage by remember { mutableStateOf(Page.HOME) }
    var selectedColor by remember { mutableStateOf(colorPresets[0]) }
    var selectedStyle by remember { mutableStateOf(paletteStyles[0]) }
    var showThemePanel by remember { mutableStateOf(false) }

    KittyTuneWebTheme(
        seedColor = selectedColor.color,
        paletteStyle = selectedStyle.second
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "KittyTune",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavBarItem("Home", currentPage == Page.HOME) { currentPage = Page.HOME }
                                NavBarItem("Features", currentPage == Page.FEATURES) { currentPage = Page.FEATURES }
                                NavBarItem("Download", currentPage == Page.DOWNLOAD) { currentPage = Page.DOWNLOAD }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Theme customization button
                                IconButton(onClick = { showThemePanel = !showThemePanel }) {
                                    Icon(
                                        Icons.Filled.Palette,
                                        contentDescription = "Customize theme",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "page_transition"
                    ) { page ->
                        when (page) {
                            Page.HOME -> HomePage { currentPage = Page.DOWNLOAD }
                            Page.FEATURES -> FeaturesPage()
                            Page.DOWNLOAD -> DownloadPage()
                        }
                    }
                }
            }

            // Floating Theme Panel
            if (showThemePanel) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showThemePanel = false }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 72.dp, end = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { /* block click passthrough */ }
                        .padding(24.dp)
                        .width(320.dp)
                ) {
                    ThemePanel(
                        selectedColor = selectedColor,
                        onColorSelect = { selectedColor = it },
                        selectedStyle = selectedStyle,
                        onStyleSelect = { selectedStyle = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemePanel(
    selectedColor: ColorPreset,
    onColorSelect: (ColorPreset) -> Unit,
    selectedStyle: Pair<String, PaletteStyle>,
    onStyleSelect: (Pair<String, PaletteStyle>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                "Customize Theme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            "Seed Color",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Color grid
        val chunked = colorPresets.chunked(5)
        chunked.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(preset.color)
                            .clickable { onColorSelect(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor.name == preset.name) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Color Palette Style",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 250.dp)
        ) {
            paletteStyles.forEach { style ->
                val isSelected = selectedStyle.first == style.first
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onStyleSelect(style) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        style.first,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavBarItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}
