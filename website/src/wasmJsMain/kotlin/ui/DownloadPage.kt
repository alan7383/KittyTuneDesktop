package com.alananasss.kittytunewebsite.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ReleaseNote(val version: String, val date: String, val highlights: List<String>)

val releases = listOf(
    ReleaseNote(
        "v1.0.27", "August 2026",
        listOf(
            "Google Sans Flex variable font integration",
            "Material 3 Expressive Spec 2025 implementation",
            "Discord Rich Presence state sync",
            "Word-by-word lyrics via LrcLib"
        )
    ),
    ReleaseNote(
        "v1.0.20", "July 2026",
        listOf(
            "Local audio persistence & DB caching",
            "Unified search resolution update",
            "JVM garbage collection optimizations"
        )
    )
)

data class PlatformInfo(
    val name: String,
    val icon: ImageVector,
    val extension: String,
    val description: String
)

val platforms = listOf(
    PlatformInfo("Windows", Icons.Filled.DesktopWindows, ".exe", "x64 architecture"),
    PlatformInfo("macOS", Icons.Filled.DesktopMac, ".dmg", "Universal (Intel/Apple Silicon)"),
    PlatformInfo("Linux", Icons.Filled.Terminal, ".AppImage", "x86_64 / aarch64"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadPage() {
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
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Download.",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Available for all major desktop platforms.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Platforms
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            platforms.forEach { platform ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Icon(
                            platform.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                platform.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                platform.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(50))
                        ) {
                            Text(
                                "Download ${platform.extension}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Changelog
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Text(
                    "Recent Updates",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                releases.forEachIndexed { index, release ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Column(modifier = Modifier.width(100.dp)) {
                            Text(
                                release.version,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                release.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            release.highlights.forEach { highlight ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        highlight,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    if (index < releases.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
