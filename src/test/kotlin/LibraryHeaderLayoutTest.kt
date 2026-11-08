@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.library.AdaptiveLibraryHeader
import org.junit.Test
import kotlin.test.assertTrue

class LibraryHeaderLayoutTest {
    @Test
    fun translatedTitlesAndFolderActionsNeverOverlap() {
        val previous = Strings.appLanguage
        try {
            for (language in listOf("en", "fr", "de", "hu", "ru", "tr", "vi")) {
                Strings.appLanguage = language
                for (width in listOf(240, 280, 340, 420, 800)) {
                    for (folder in listOf(false, true)) {
                        var titleBounds: Rect? = null
                        var actionBounds: Rect? = null
                        val scene = ImageComposeScene(width = width, height = 160) {
                            MaterialTheme {
                                AdaptiveLibraryHeader(
                                    Modifier.fillMaxWidth(),
                                    title = {
                                        Row(Modifier.onGloballyPositioned { titleBounds = it.boundsInRoot() }) {
                                            Icon(if (folder) Icons.Rounded.ArrowBack else Icons.Rounded.LibraryMusic, null)
                                            Spacer(Modifier.width(10.dp))
                                            Text(if (folder) "A very long folder name" else str("nav_library"), maxLines = 1)
                                        }
                                    },
                                    actions = { extended, _ ->
                                        Row(Modifier.onGloballyPositioned { actionBounds = it.boundsInRoot() }) {
                                            if (folder) {
                                                SplitButtonLayout(
                                                    leadingButton = { SplitButtonDefaults.LeadingButton(onClick = {}, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.PlayArrow, null) } },
                                                    trailingButton = { SplitButtonDefaults.TrailingButton(checked = false, onCheckedChange = {}, modifier = Modifier.width(32.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.ArrowDropDown, null) } },
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            if (extended) {
                                                OutlinedButton(onClick = {}, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                                                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(str("lib_create"), maxLines = 1, softWrap = false)
                                                }
                                            } else {
                                                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, null) }
                                            }
                                            repeat(3) {
                                                Spacer(Modifier.width(4.dp))
                                                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Schedule, null) }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        try {
                            scene.render().close()
                            val title = requireNotNull(titleBounds)
                            val actions = requireNotNull(actionBounds)
                            val context = "$language width=$width folder=$folder: $title / $actions"
                            assertTrue(title.right <= actions.left + 0.5f || title.bottom <= actions.top + 0.5f, context)
                            assertTrue(actions.left >= 0 && actions.right <= width + 0.5f, context)
                            assertTrue(actions.width >= if (folder) 220f else 140f, context)
                        } finally { scene.close() }
                    }
                }
            }
        } finally { Strings.appLanguage = previous }
    }
}
