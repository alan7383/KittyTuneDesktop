@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
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
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
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
    }
}

private class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor?): Any {
        if (flavor == DataFlavor.imageFlavor) return image
        throw UnsupportedFlavorException(flavor)
    }
}

private fun loadImageFromUrl(urlStr: String): BufferedImage? {
    return try {
        if (urlStr.startsWith("http://", ignoreCase = true) || urlStr.startsWith("https://", ignoreCase = true)) {
            val connection = URI(urlStr).toURL().openConnection()
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.getInputStream().use { input ->
                ImageIO.read(input)
            }
        } else if (urlStr.startsWith("file://", ignoreCase = true)) {
            ImageIO.read(File(URI(urlStr)))
        } else {
            val file = File(urlStr)
            if (file.exists()) {
                ImageIO.read(file)
            } else {
                ImageIO.read(URI(urlStr).toURL())
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
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
    var isCopyMenuExpanded by remember { mutableStateOf(false) }

    if (visible) {
        BackHandler {
            CoverViewerState.hide()
        }
    }

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
                    // S'adapte à la hauteur dispo (weight) et calcule la largeur en fonction
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

                    // Boutons Expressive en bas
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SplitButton Expressive pour copier l'image / copier le lien
                        Box {
                            val splitColors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            SplitButtonLayout(
                                leadingButton = {
                                    SplitButtonDefaults.LeadingButton(
                                        onClick = {
                                            url?.let { targetUrl ->
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val image = loadImageFromUrl(targetUrl)
                                                        if (image != null) {
                                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                                                ImageTransferable(image),
                                                                null
                                                            )
                                                            withContext(Dispatchers.Main) {
                                                                Toaster.show(str("cover_image_copied"))
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                Toaster.show(str("cover_save_error"))
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        withContext(Dispatchers.Main) {
                                                            Toaster.show(str("cover_save_error"))
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        colors = splitColors
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(str("cover_copy_image"))
                                    }
                                },
                                trailingButton = {
                                    SplitButtonDefaults.TrailingButton(
                                        checked = isCopyMenuExpanded,
                                        onCheckedChange = { isCopyMenuExpanded = it },
                                        colors = splitColors
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDropDown,
                                            contentDescription = str("cover_copy_link"),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )

                            DropdownMenu(
                                expanded = isCopyMenuExpanded,
                                onDismissRequest = { isCopyMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(str("cover_copy_link")) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        isCopyMenuExpanded = false
                                        url?.let {
                                            val selection = StringSelection(it)
                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                            Toaster.show(str("cover_copy_link"))
                                        }
                                    }
                                )
                            }
                        }

                        // Bouton Télécharger
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
                                                val image = loadImageFromUrl(targetUrl)
                                                if (image != null) {
                                                    val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) image else {
                                                        BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).apply {
                                                            createGraphics().apply {
                                                                drawImage(image, 0, 0, java.awt.Color.WHITE, null)
                                                                dispose()
                                                            }
                                                        }
                                                    }
                                                    ImageIO.write(rgb, "jpg", file)
                                                    withContext(Dispatchers.Main) { Toaster.show(str("cover_saved")) }
                                                } else {
                                                    withContext(Dispatchers.Main) { Toaster.show(str("cover_save_error")) }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) { Toaster.show(str("cover_save_error")) }
                                            }
                                        }
                                    }
                                }
                            },
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