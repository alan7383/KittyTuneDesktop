import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.ui.setup.SetupScreen
import com.alananasss.kittytune.ui.setup.SetupStep
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import org.jetbrains.skia.EncodedImageFormat
import kotlinx.coroutines.launch
import org.junit.Test
import java.io.File
import kotlin.test.*

class SetupOnboardingTest {
    @Test fun `first launch opens setup and later sign outs only ask for an account`() {
        assertEquals(com.alananasss.kittytune.AppState.SETUP, com.alananasss.kittytune.initialAppState(false, false))
        assertEquals(com.alananasss.kittytune.AppState.SETUP, com.alananasss.kittytune.initialAppState(false, true))
        assertEquals(com.alananasss.kittytune.AppState.ACCOUNT, com.alananasss.kittytune.initialAppState(true, false))
        assertEquals(com.alananasss.kittytune.AppState.MAIN, com.alananasss.kittytune.initialAppState(true, true))
    }

    @Test fun `all pages render in desktop and compact layouts without changing preferences`() {
        val saved = Prefs.flow.value
        val language = Strings.appLanguage
        try {
            val prefs = PlayerPreferences()
            prefs.setAppLanguage(AppLanguage.FRENCH)
            prefs.setThemeMode(AppThemeMode.DARK)
            prefs.setKeyColor(0xFF1565C0.toInt())
            prefs.setColorStyle("TonalSpot")
            val before = Prefs.flow.value
            for ((width, height) in listOf(1920 to 1080, 1280 to 900, 1280 to 720, 760 to 600)) {
                for (step in SetupStep.entries.indices) {
                    val scene = ImageComposeScene(width = width, height = height) {
                        KittyTuneTheme { SetupScreen(onSetupComplete = {}, initialStep = step) }
                    }
                    try {
                        repeat(24) { scene.render(it * 16_000_000L).close() }
                        scene.render(400_000_000L).use { image ->
                            image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                                File("build/previews/setup-$step-$width-$height.png").apply { parentFile.mkdirs() }.writeBytes(data.bytes)
                            }
                        }
                    } finally { scene.close() }
                }
            }
            assertEquals(before, Prefs.flow.value, "Visiting any step must not write defaults or install extras")
        } finally { Prefs.restore(saved); Strings.appLanguage = language }
    }

    @Test fun `custom theme editor preserves choices and clears transient preview when closed`() {
        val saved = Prefs.flow.value
        val language = Strings.appLanguage
        try {
            val prefs = PlayerPreferences()
            prefs.setAppLanguage(AppLanguage.FRENCH)
            prefs.setThemeMode(AppThemeMode.DARK)
            prefs.setKeyColor(0xFF7E57C2.toInt())
            prefs.setColorStyle("TonalSpot")
            val before = Prefs.flow.value
            var closed = 0
            val scene = ImageComposeScene(width = 780, height = 740) {
                KittyTuneTheme { com.alananasss.kittytune.ui.setup.SetupThemeEditor { closed++ } }
            }
            try {
                repeat(24) { scene.render(it * 16_000_000L).close() }
                scene.render(400_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File("build/previews/setup-custom-theme.png").writeBytes(data.bytes)
                    }
                }
                assertEquals(before, Prefs.flow.value, "Opening the editor must preserve the current theme")
                scene.sendPointerEvent(PointerEventType.Press, Offset(700f, 696f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(700f, 696f))
                repeat(24) { scene.render((it + 24) * 16_000_000L).close() }
                assertEquals(1, closed)
                com.alananasss.kittytune.ui.theme.ThemeState.previewKeyColor = 0xFF1565C0.toInt()
            } finally { scene.close() }
            assertNull(com.alananasss.kittytune.ui.theme.ThemeState.previewKeyColor)
            assertEquals(before, Prefs.flow.value)
        } finally { Prefs.restore(saved); Strings.appLanguage = language }
    }

    @Test fun `guest selection stays in setup and preserves previous choices`() {
        org.junit.Assume.assumeFalse(com.alananasss.kittytune.data.TokenManager.hasAccessToken())
        val saved = Prefs.flow.value
        val wasGuest = com.alananasss.kittytune.data.TokenManager.isGuestMode()
        try {
            com.alananasss.kittytune.data.TokenManager.setGuestMode(false)
            PlayerPreferences().setHasCompletedSetup(true)
            val before = Prefs.flow.value
            var completed = 0
            val scene = ImageComposeScene(width = 1280, height = 900) {
                KittyTuneTheme { SetupScreen(onSetupComplete = { completed++ }) }
            }
            try {
                repeat(24) { scene.render(it * 16_000_000L).close() }
                // Account follows Welcome and Appearance even when reconnecting.
                scene.sendPointerEvent(PointerEventType.Press, Offset(1090f, 790f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(1090f, 790f))
                repeat(24) { scene.render((it + 24) * 16_000_000L).close() }
                assertEquals(0, completed, "Welcome must lead to Appearance, not enter the app")
                scene.sendPointerEvent(PointerEventType.Press, Offset(1090f, 790f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(1090f, 790f))
                repeat(24) { scene.render((it + 48) * 16_000_000L).close() }
                assertEquals(0, completed, "Appearance must lead to Account")
                scene.render(1_200_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File("build/previews/setup-reconnect-account.png").writeBytes(data.bytes)
                    }
                }
                scene.sendPointerEvent(PointerEventType.Press, Offset(975f, 408f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(975f, 408f))
                repeat(24) { scene.render((it + 80) * 16_000_000L).close() }
                assertEquals(0, completed, "Selecting an account must not skip the rest of setup")
                assertTrue(com.alananasss.kittytune.data.TokenManager.isGuestMode())
                assertEquals(before, Prefs.flow.value)
            } finally { scene.close() }
        } finally {
            Prefs.restore(saved)
            com.alananasss.kittytune.data.TokenManager.setGuestMode(wasGuest)
        }
    }

    @Test fun `changing language updates the visible setup without reopening it`() {
        val saved = Prefs.flow.value
        val previousLanguage = Strings.appLanguage
        val prefs = PlayerPreferences()
        val scene = ImageComposeScene(width = 1280, height = 900) {
            KittyTuneTheme { SetupScreen(onSetupComplete = {}) }
        }
        try {
            var frame = 0L
            var englishImage: ByteArray? = null
            for (language in listOf(AppLanguage.ENGLISH, AppLanguage.VIETNAMESE, AppLanguage.GERMAN,
                AppLanguage.HUNGARIAN, AppLanguage.RUSSIAN, AppLanguage.TURKISH, AppLanguage.FRENCH)) {
                prefs.setAppLanguage(language)
                repeat(30) { scene.render(frame++ * 16_000_000L).close() }
                assertEquals(language.code, Strings.resolvedLanguage)
                scene.render(frame++ * 16_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File("build/previews/setup-language-${language.code}.png").apply { parentFile.mkdirs() }.writeBytes(data.bytes)
                        if (language == AppLanguage.ENGLISH) englishImage = data.bytes
                        else assertFalse(englishImage!!.contentEquals(data.bytes), "Setup must update to ${language.code}")
                    }
                }
            }
        } finally { scene.close(); Prefs.restore(saved); Strings.appLanguage = previousLanguage }
    }

    @Test fun `color engine choices fit narrow and wide editors`() {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "fr"
        try {
            for (width in listOf(320, 720)) {
                val selected = androidx.compose.runtime.mutableStateOf("System")
                val scene = ImageComposeScene(width = width, height = 1400) {
                    KittyTuneTheme {
                        androidx.compose.foundation.layout.Column {
                            com.alananasss.kittytune.ui.profile.ColorEngineCard(
                                colorStyle = selected.value, colorSpec = "SPEC_2025",
                                onStyleSelected = { selected.value = it }, onSpecSelected = {})
                        }
                    }
                }
                try {
                    repeat(24) { scene.render(it * 16_000_000L).close() }
                    scene.render(400_000_000L).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File("build/previews/color-engine-$width.png").apply { parentFile.mkdirs() }.writeBytes(data.bytes)
                        }
                    }
                    val count = com.materialkolor.PaletteStyle.entries.size + 1 + if (System.getProperty("os.name").startsWith("Windows")) 1 else 0
                    val columns = if (width == 720) 3 else 1
                    val lastRowCount = (count - 1) % columns + 1
                    val x = 34f + (width - 68f) * (lastRowCount - .5f) / lastRowCount
                    val y = 112f + ((count - 1) / columns) * 60f
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                    repeat(24) { scene.render((it + 30) * 16_000_000L).close() }
                    assertEquals(com.materialkolor.PaletteStyle.entries.last().name, selected.value, "Final style must be clickable at width $width")
                } finally { scene.close() }
            }
        } finally { Strings.appLanguage = previousLanguage }
    }

    @Test fun `theme popup dismisses outside clicks and keeps inside clicks in the editor`() {
        var closed = 0
        val scene = ImageComposeScene(width = 1280, height = 900) {
            KittyTuneTheme { com.alananasss.kittytune.ui.setup.SetupThemePopupContent { closed++ } }
        }
        try {
            var frame = 0L
            fun settle() { repeat(24) { scene.render(frame++ * 16_000_000L).close() } }
            fun click(position: Offset) {
                scene.sendPointerEvent(PointerEventType.Press, position)
                scene.sendPointerEvent(PointerEventType.Release, position)
                settle()
            }
            settle()
            click(Offset(400f, 110f))
            assertEquals(0, closed, "Clicking blank space inside the popup must not dismiss it")
            click(Offset(10f, 10f))
            assertEquals(1, closed, "Clicking beside the popup must dismiss it")
        } finally { scene.close() }
    }

    @Test fun `SoundCloud browser wait stays inside setup and cancellation returns to account choices`() {
        org.junit.Assume.assumeFalse(com.alananasss.kittytune.data.TokenManager.hasAccessToken())
        val saved = Prefs.flow.value
        val language = Strings.appLanguage
        PlayerPreferences().setAppLanguage(AppLanguage.FRENCH)
        var active = false
        var opened = 0
        val scene = ImageComposeScene(width = 1280, height = 900) {
            KittyTuneTheme {
                SetupScreen(onSetupComplete = { error("Must remain in setup") }, initialStep = SetupStep.ACCOUNT.ordinal,
                    soundCloudLoginContent = { _, cancel ->
                        androidx.compose.runtime.DisposableEffect(Unit) {
                            active = true
                            opened++
                            onDispose { active = false }
                        }
                        com.alananasss.kittytune.ui.login.SoundCloudLoginStatus(null, false, true, {}, {}, cancel)
                    })
            }
        }
        try {
            var frame = 0L
            fun settle() { repeat(24) { scene.render(frame++ * 16_000_000L).close() } }
            fun click(x: Float, y: Float) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                settle()
            }
            settle()
            click(640f, 408f)
            assertTrue(active)
            scene.render(frame++ * 16_000_000L).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    File("build/previews/setup-soundcloud-wait.png").apply { parentFile.mkdirs() }.writeBytes(data.bytes)
                }
            }
            click(742f, 420f)
            assertFalse(active, "Cancel in the login card must restore the account choices")
            click(640f, 408f)
            assertTrue(active)
            assertEquals(2, opened)
            click(512f, 790f)
            assertFalse(active, "Back must cancel login without navigating away from Account")
            click(640f, 408f)
            assertTrue(active)
            assertEquals(3, opened)
        } finally { scene.close(); Prefs.restore(saved); Strings.appLanguage = language }
    }

    @Test fun `successful login confirms profile and permits switching before continuing setup`() {
        org.junit.Assume.assumeFalse(com.alananasss.kittytune.data.TokenManager.hasAccessToken())
        val wasGuest = com.alananasss.kittytune.data.TokenManager.isGuestMode()
        val saved = Prefs.flow.value
        val language = Strings.appLanguage
        PlayerPreferences().setAppLanguage(AppLanguage.FRENCH)
        PlayerPreferences().setHasCompletedSetup(true)
        var logoutEvents = 0
        val observerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val observer = observerScope.launch {
            com.alananasss.kittytune.data.TokenManager.logoutFlow.collect { logoutEvents++ }
        }
        var completed = 0
        var opened = 0
        var profiles = 0
        var success: () -> Unit = {}
        var cancel: () -> Unit = {}
        val scene = ImageComposeScene(width = 1280, height = 900) {
            KittyTuneTheme {
                SetupScreen(onSetupComplete = { completed++ }, initialStep = SetupStep.ACCOUNT.ordinal,
                    loadSoundCloudProfile = {
                        profiles++
                        com.alananasss.kittytune.domain.User(profiles.toLong(), "Compte $profiles", null)
                    }, soundCloudLoginContent = { onSuccess, onCancel ->
                        androidx.compose.runtime.DisposableEffect(Unit) {
                            opened++
                            success = onSuccess
                            cancel = onCancel
                            onDispose { }
                        }
                        com.alananasss.kittytune.ui.login.SoundCloudLoginStatus(null, false, true, {}, {}, onCancel)
                    })
            }
        }
        try {
            var frame = 0L
            fun settle() { repeat(24) { scene.render(frame++ * 16_000_000L).close() } }
            fun click(x: Float, y: Float) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                settle()
            }
            settle()
            click(640f, 408f)
            success()
            settle()
            assertEquals(0, completed, "Login success must not finish a previously completed setup")
            assertEquals(1, profiles, "The connected user's profile must be loaded")
            scene.render(frame++ * 16_000_000L).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    File("build/previews/setup-account-confirmation.png").writeBytes(data.bytes)
                }
            }
            click(606f, 488f)
            assertEquals(2, opened, "The confirmation must offer another login")
            cancel()
            settle()
            assertEquals(0, completed)
            click(606f, 488f)
            assertEquals(3, opened, "Cancelling must restore the account confirmation")
            val before = profiles
            success()
            settle()
            assertEquals(before + 1, profiles, "Another successful login must reload the profile")
            assertEquals(0, completed)
            click(785f, 488f)
            assertTrue(com.alananasss.kittytune.data.TokenManager.isGuestMode())
            assertFalse(com.alananasss.kittytune.data.TokenManager.hasAccessToken())
            assertEquals(0, logoutEvents, "Guest selection must not redirect the router out of setup")
            assertEquals(0, completed, "Guest selection must remain in the account step")
            click(1090f, 790f)
            assertEquals(0, completed, "Continue must advance one setup step, not exit")
        } finally {
            scene.close()
            observer.cancel()
            observerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            Prefs.restore(saved)
            Strings.appLanguage = language
            com.alananasss.kittytune.data.TokenManager.setGuestMode(wasGuest)
        }
    }

    @Test fun `closing a replay preserves completion and previous choices`() {
        val saved = Prefs.flow.value
        try {
            PlayerPreferences().setHasCompletedSetup(true)
            val before = Prefs.flow.value
            var cancelled = 0
            val scene = ImageComposeScene(width = 1280, height = 900) {
                KittyTuneTheme { SetupScreen(onSetupComplete = { error("Must not finish") }, onCancel = { cancelled++ }) }
            }
            try {
                repeat(24) { scene.render(it * 16_000_000L).close() }
                scene.sendPointerEvent(PointerEventType.Press, Offset(1132f, 108f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(1132f, 108f))
                repeat(24) { scene.render((it + 24) * 16_000_000L).close() }
                assertEquals(1, cancelled)
                assertEquals(before, Prefs.flow.value)
            } finally { scene.close() }
        } finally { Prefs.restore(saved) }
    }

    @Test fun `finishing saves completion and skips extras without installing them`() {
        val saved = Prefs.flow.value
        val wasGuest = com.alananasss.kittytune.data.TokenManager.isGuestMode()
        try {
            val prefs = PlayerPreferences()
            prefs.setHasCompletedSetup(false)
            var completed = 0
            val before = Prefs.flow.value
            val scene = ImageComposeScene(width = 1280, height = 900) {
                KittyTuneTheme { SetupScreen(onSetupComplete = { completed++ }) }
            }
            var frame = 0L
            fun settle() { repeat(30) { scene.render(frame++ * 16_000_000L).close() } }
            try {
                settle()
                repeat(SetupStep.entries.size) {
                    val x = if (SetupStep.entries[it] in listOf(SetupStep.LIBRARY, SetupStep.DISCORD, SetupStep.PROTECTED)) 960f else 1090f
                    val position = if (SetupStep.entries[it] == SetupStep.ACCOUNT && !com.alananasss.kittytune.data.TokenManager.hasAccessToken()) Offset(975f, 408f) else Offset(x, 790f)
                    scene.sendPointerEvent(PointerEventType.Press, position)
                    scene.sendPointerEvent(PointerEventType.Release, position)
                    settle()
                }
                assertEquals(1, completed)
                assertTrue(prefs.getHasCompletedSetup())
                assertEquals(before - "has_completed_setup", Prefs.flow.value - "has_completed_setup")
            } finally { scene.close() }
        } finally {
            Prefs.restore(saved)
            com.alananasss.kittytune.data.TokenManager.setGuestMode(wasGuest)
        }
    }
}
