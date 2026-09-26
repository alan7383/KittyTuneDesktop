@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package com.alananasss.kittytune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.SystemFont
import com.alananasss.kittytune.core.AppDirs
import java.awt.GraphicsEnvironment
import java.io.File

/**
 * The typeface the whole app is set in.
 *
 * Stored as a short id: `default` (Compose's own sans), `flex` (the bundled variable Google Sans Flex, the one
 * the old "custom font" switch turned on, whose axes stay adjustable), `system:<family>` for an installed font,
 * or `file:<name>` for one the user added, copied into the app's data folder.
 */
sealed interface AppFont {
    val id: String

    data object Default : AppFont { override val id = "default" }
    data object Flex : AppFont { override val id = "flex" }
    data class System(val family: String) : AppFont { override val id = "system:$family" }
    data class UserFile(val fileName: String) : AppFont { override val id = "file:$fileName" }

    companion object {
        fun parse(id: String?): AppFont = when {
            id == null || id == Default.id -> Default
            id == Flex.id -> Flex
            id.startsWith("system:") -> System(id.removePrefix("system:"))
            id.startsWith("file:") -> UserFile(id.removePrefix("file:"))
            else -> Default
        }
    }
}

object AppFonts {
    /** Where fonts the user added are kept. */
    val userDir: File get() = File(AppDirs.dataDir, "fonts").apply { mkdirs() }

    /**
     * Installed fonts worth offering, in order; only those present on this machine are listed. A mix of
     * Windows and Linux faces so both get a real choice.
     */
    private val CURATED = listOf(
        "Segoe UI Variable Display", "Bahnschrift", "Inter", "Roboto", "Nunito", "Montserrat", "Ubuntu",
        "Cantarell", "Noto Sans", "Fira Sans", "Trebuchet MS", "Candara", "Georgia", "Cambria",
        "Cascadia Code", "JetBrains Mono", "Consolas", "Comic Sans MS",
    )

    fun availableSystemFonts(): List<String> {
        val installed = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }.getOrDefault(emptySet())
        return CURATED.filter { it in installed }
    }

    fun userFonts(): List<String> =
        userDir.listFiles { f -> f.extension.lowercase() in setOf("ttf", "otf") }?.map { it.name }?.sorted().orEmpty()

    /** Copies [source] into the fonts folder; returns its stored name, or null when it is not a usable font. */
    fun import(source: File): String? {
        if (source.extension.lowercase() !in setOf("ttf", "otf")) return null
        val isFont = runCatching { java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, source) }.isSuccess
        if (!isFont) return null
        val target = File(userDir, source.name)
        source.copyTo(target, overwrite = true)
        return target.name
    }

    /** The family to draw [font] with, or null for the default and the variable face, which are built elsewhere. */
    fun familyFor(font: AppFont): FontFamily? = when (font) {
        AppFont.Default, AppFont.Flex -> null
        is AppFont.System -> FontFamily(
            SystemFont(font.family, FontWeight.Normal),
            SystemFont(font.family, FontWeight.Medium),
            SystemFont(font.family, FontWeight.SemiBold),
            SystemFont(font.family, FontWeight.Bold),
        )
        is AppFont.UserFile -> File(userDir, font.fileName).takeIf { it.isFile }?.let { FontFamily(Font(it)) }
    }

    /** A display name for the picker and the settings row. */
    fun nameOf(font: AppFont): String = when (font) {
        AppFont.Default -> ""
        AppFont.Flex -> "Google Sans Flex"
        is AppFont.System -> font.family
        is AppFont.UserFile -> font.fileName.substringBeforeLast('.')
    }
}

/** [this] with every style set in [family]. */
fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
