package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.BuildConfig
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.data.UpdateStatus
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import kotlinx.coroutines.launch

data class Contributor(
    val name: String,
    val role: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val appVersion = BuildConfig.VERSION_NAME
    val scope = rememberCoroutineScope()
    var tapCount by remember { mutableStateOf(0) }
    val updateStatus by UpdateManager.status.collectAsState()
    var showCreditsSheet by remember { mutableStateOf(false) }

    val contributors = listOf(
        Contributor("alananasss", "Developer", "https://github.com/alan7383"),
        Contributor("mattdotcat", "Translation", "https://t.me/b37246")
    )

    if (showCreditsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreditsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = str("about_credits"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                )
                LazyColumn {
                    items(contributors) { person ->
                        ListItem(
                            headlineContent = { Text(person.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(person.role) },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = person.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { uriHandler.openUri(person.url) }
                        )
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(860.dp)
                .wrapContentHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(32.dp).height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                tapCount++
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource("icons/kittytune.png"),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().scale(0.8f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = str("app_name"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Version $appVersion",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Update Button
                    Button(
                        onClick = {
                            scope.launch {
                                UpdateManager.checkForUpdate(isManual = true)
                            }
                        },
                        enabled = updateStatus != UpdateStatus.CHECKING && updateStatus != UpdateStatus.DOWNLOADING,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    ) {
                        if (updateStatus == UpdateStatus.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            str("about_check_updates"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Ko-fi Button
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = str("about_kofi_title"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource("drawable/ic_kofi_symbol.xml"),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Unspecified
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = str("about_kofi_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { uriHandler.openUri("https://ko-fi.com/alan7383") },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource("drawable/ic_kofi_logo.xml"),
                                    contentDescription = str("about_kofi_btn"),
                                    modifier = Modifier.width(52.dp).height(14.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                }

                // Vertical Divider
                VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxHeight())

                // Right Column: Settings Groups
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    SettingsGroup(
                        title = str("about_website_group"),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    icon = Icons.Rounded.Language,
                                    title = str("about_website_title"),
                                    onClick = { uriHandler.openUri("https://alan7383.github.io/kittytune-website/") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    icon = Icons.Rounded.Download,
                                    title = str("about_downloads_title"),
                                    subtitle = str("about_downloads_desc"),
                                    onClick = { uriHandler.openUri("https://alan7383.github.io/kittytune-website/download") }
                                )
                            }
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsGroup(
                        title = str("about_help_group"),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    icon = Icons.Rounded.Groups,
                                    title = str("about_credits"),
                                    onClick = { showCreditsSheet = true }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    icon = Icons.Rounded.Code,
                                    title = str("about_github"),
                                    onClick = { uriHandler.openUri("https://github.com/alan7383/kittytune") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    icon = Icons.Filled.BugReport,
                                    title = str("about_bug_report"),
                                    onClick = { uriHandler.openUri("https://github.com/alan7383/kittytune/issues") }
                                )
                            }
                        )
                    )
                }
            }
        }
    }
}
