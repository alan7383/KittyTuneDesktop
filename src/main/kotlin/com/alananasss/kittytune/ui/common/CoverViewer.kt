package com.alananasss.kittytune.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URL
import javax.imageio.ImageIO

object CoverViewerState {
    var visible by mutableStateOf(false)
    var currentUrl by mutableStateOf<String?>(null)

    fun show(url: String?) {
        if (!url.isNullOrBlank()) {
            currentUrl = url
            visible = true
        }
    }

    fun hide() {
        visible = false
        currentUrl = null
    }
}

fun Modifier.viewableCover(url: String?): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null
) {
    if (!url.isNullOrBlank()) CoverViewerState.show(url)
}

@Composable
fun CoverViewerOverlay() {
    val visible = CoverViewerState.visible
    val url = CoverViewerState.currentUrl
    val scope = rememberCoroutineScope()

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { CoverViewerState.hide() }
                )
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        CoverViewerState.hide()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(visible = visible, enter = scaleIn(initialScale = 0.9f), exit = scaleOut(targetScale = 0.9f)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 64.dp, horizontal = 32.dp)
                ) {
                    
                    // La magie est ici : ça s'adapte à la hauteur dispo (weight) et ça calcule la largeur en fonction
                    Box(
                        modifier = Modifier
                            .weight(1f) 
                            .aspectRatio(1f, matchHeightConstraintsFirst = true) 
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    ) {
                        AsyncImage(
                            model = url, 
                            contentDescription = null, 
                            contentScale = ContentScale.Crop, 
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Boutons correctement placés en bas
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledTonalButton(
                            onClick = {
                                url?.let {
                                    val selection = StringSelection(it)
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    Toaster.show(str("cover_copy_link"))
                                }
                            },
                            // L'effet de déformation Material 3 Expressive est ici :
                            shapes = ButtonDefaults.shapes() 
                        ) {
                            Icon(Icons.Rounded.Link, null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("cover_copy_link"))
                        }
                        
                        Button(
                            onClick = {
                                url?.let { targetUrl ->
                                    val dialog = FileDialog(null as Frame?, str("btn_download"), FileDialog.SAVE)
                                    dialog.file = "cover.jpg"
                                    dialog.isVisible = true
                                    val file = dialog.files.firstOrNull()
                                    if (file != null) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val image = ImageIO.read(URL(targetUrl))
                                                ImageIO.write(image, "jpg", file)
                                                withContext(Dispatchers.Main) { Toaster.show(str("cover_saved")) }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) { Toaster.show(str("cover_save_error")) }
                                            }
                                        }
                                    }
                                }
                            },
                            // L'effet de déformation Material 3 Expressive est ici :
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("btn_download"))
                        }
                    }
                }
            }
            
            // Bouton fermer toujours accessible en haut à droite
            IconButton(
                onClick = { CoverViewerState.hide() },
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).size(48.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }
    }
}