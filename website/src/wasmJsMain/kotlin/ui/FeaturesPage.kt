package com.alananasss.kittytunewebsite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class Feature(
    val icon: ImageVector,
    val title: String,
    val description: String
)

val featuresList = listOf(
    Feature(
        Icons.Filled.Palette, "Material 3 Expressive",
        "Built strictly on the Material Design 3 Spec 2025. Utilizes dynamic color engines, expressive shapes, and fluid motion schemes natively."
    ),
    Feature(
        Icons.Filled.FormatPaint, "Live Theme Customization",
        "Change seed colors and palette styles without application restarts. The UI layer completely repaints in real-time."
    ),
    Feature(
        Icons.Filled.OfflinePin, "Offline Audio & Meta Caching",
        "Playlists, tracks, covers, and lyrics are securely cached locally. Full navigation and playback support without network."
    ),
    Feature(
        Icons.Filled.Search, "Unified Query Engine",
        "Aggregates search results from SoundCloud and YouTube. Manage cross-platform tracks in a single local queue."
    ),
    Feature(
        Icons.Filled.Speed, "Native Desktop Performance",
        "Compiled via Kotlin/Wasm and Compose Multiplatform for Desktop. Delivers stable 60fps animations across all OS environments."
    ),
    Feature(
        Icons.Filled.TextFields, "Variable Font Control",
        "Integrates Google Sans Flex. Granular control over font weight, width, slant, and roundness directly from settings."
    )
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeaturesPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp)
            .padding(top = 64.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        // Header
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                "Features.",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Technical overview of KittyTune capabilities.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Hero Feature
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Word-by-word Lyrics Synchronization",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Parses LRC and JSON lyrics data from LrcLib and Musixmatch APIs. Provides an optimized rendering engine for exact word-level highlighting matching audio playback time.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        lineHeight = 28.sp
                    )
                }
            }
        }

        // Grid Features
        val rows = featuresList.chunked(2)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                row.forEach { feature ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = feature.icon,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                feature.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                feature.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
