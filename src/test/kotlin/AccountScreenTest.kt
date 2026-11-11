import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.ui.setup.AccountScreen
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.test.*

/**
 * The account screen and the saved accounts behind it.
 *
 * [TokenManager] is the real, file-backed one, so every test here only runs on a profile with nobody signed in
 * and no account saved, and puts it back that way.
 */
class AccountScreenTest {
    private fun assumeEmptyProfile() {
        Assume.assumeFalse(TokenManager.hasAccessToken())
        Assume.assumeTrue(TokenManager.accounts.value.isEmpty())
    }

    private fun cleanUp(wasGuest: Boolean) {
        TokenManager.accounts.value.forEach { TokenManager.forgetAccount(it.id) }
        TokenManager.clearTokens()
        TokenManager.setGuestMode(wasGuest)
    }

    private fun ImageComposeScene.save(name: String, time: Long) = render(time).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
            File("build/previews/$name.png").apply { parentFile.mkdirs() }.writeBytes(data.bytes)
        }
    }

    @Test fun `saved accounts follow their own token refreshes and can be brought back`() {
        assumeEmptyProfile()
        val wasGuest = TokenManager.isGuestMode()
        try {
            TokenManager.replaceSession("access-1", "refresh-1", 3600, null)
            TokenManager.rememberActiveAccount(1, "Alice", null)
            // A refresh while Alice is active rotates both tokens; her saved copy has to follow.
            TokenManager.saveTokens("access-1b", "refresh-1b", 3600)

            // A second login starts out belonging to nobody, so it must not overwrite Alice.
            TokenManager.replaceSession("access-2", "refresh-2", 3600, null)
            assertEquals(0L, TokenManager.activeAccountId())
            TokenManager.rememberActiveAccount(2, "Bob", "https://example.invalid/bob.jpg")
            assertEquals(listOf("Alice", "Bob"), TokenManager.accounts.value.map { it.username })
            assertEquals(2L, TokenManager.activeAccountId())

            assertTrue(TokenManager.activateSavedSession(1))
            assertEquals("access-1b", TokenManager.getAccessToken())
            assertEquals("refresh-1b", TokenManager.getRefreshToken())
            assertEquals(1L, TokenManager.activeAccountId())
            assertFalse(TokenManager.isGuestMode())

            TokenManager.saveTokens("access-1c", "refresh-1c", 3600)
            assertTrue(TokenManager.activateSavedSession(2))
            assertEquals("access-2", TokenManager.getAccessToken())
            assertTrue(TokenManager.activateSavedSession(1))
            assertEquals("access-1c", TokenManager.getAccessToken())

            // Leaving for guest mode ends the session and keeps the account for later.
            TokenManager.clearTokens()
            assertFalse(TokenManager.hasAccessToken())
            assertEquals(2, TokenManager.accounts.value.size)

            TokenManager.forgetAccount(2)
            assertEquals(listOf(1L), TokenManager.accounts.value.map { it.id })
            assertFalse(TokenManager.activateSavedSession(2))
        } finally { cleanUp(wasGuest) }
    }

    @Test fun `signed out after setup only asks for the account and guest enters the app`() {
        assumeEmptyProfile()
        val saved = Prefs.flow.value
        val language = Strings.appLanguage
        val wasGuest = TokenManager.isGuestMode()
        try {
            TokenManager.setGuestMode(false)
            PlayerPreferences().setAppLanguage(AppLanguage.FRENCH)
            PlayerPreferences().setHasCompletedSetup(true)
            val before = Prefs.flow.value
            var done = 0
            for ((width, height) in listOf(1280 to 720, 760 to 600)) {
                val scene = ImageComposeScene(width = width, height = height) { KittyTuneTheme { AccountScreen(onDone = { done++ }) } }
                try {
                    repeat(30) { scene.render(it * 16_000_000L).close() }
                    scene.save("account-$width-$height", 500_000_000L)
                } finally { scene.close() }
            }
            val scene = ImageComposeScene(width = 1280, height = 900) { KittyTuneTheme { AccountScreen(onDone = { done++ }) } }
            try {
                repeat(30) { scene.render(it * 16_000_000L).close() }
                scene.save("account-1280-900", 500_000_000L)
                assertEquals(0, done)
                scene.sendPointerEvent(PointerEventType.Press, GUEST_TILE)
                scene.sendPointerEvent(PointerEventType.Release, GUEST_TILE)
                repeat(24) { scene.render((it + 40) * 16_000_000L).close() }
                assertEquals(1, done, "Guest must go straight into the app")
                assertTrue(TokenManager.isGuestMode())
                assertEquals(before, Prefs.flow.value, "Choosing an account must not touch the setup or its choices")
            } finally { scene.close() }
        } finally {
            Prefs.restore(saved)
            Strings.appLanguage = language
            cleanUp(wasGuest)
        }
    }

    @Test fun `saved accounts are listed and one can be removed from the screen`() {
        assumeEmptyProfile()
        val saved = Prefs.flow.value
        val language = Strings.appLanguage
        val wasGuest = TokenManager.isGuestMode()
        try {
            PlayerPreferences().setAppLanguage(AppLanguage.FRENCH)
            TokenManager.replaceSession("access-1", "refresh-1", 3600, null)
            TokenManager.rememberActiveAccount(1, "Alice", null)
            TokenManager.replaceSession("access-2", "refresh-2", 3600, null)
            TokenManager.rememberActiveAccount(2, "Bob", null)
            var done = 0
            var cancelled = 0
            val scene = ImageComposeScene(width = 1280, height = 900) {
                KittyTuneTheme { AccountScreen(onDone = { done++ }, onCancel = { cancelled++ }) }
            }
            try {
                repeat(30) { scene.render(it * 16_000_000L).close() }
                scene.save("account-switch-1280-900", 500_000_000L)
                // Alice is not the one in use: removing her is only a matter of forgetting her.
                scene.sendPointerEvent(PointerEventType.Press, FORGET_FIRST)
                scene.sendPointerEvent(PointerEventType.Release, FORGET_FIRST)
                repeat(30) { scene.render((it + 40) * 16_000_000L).close() }
                assertEquals(listOf("Bob"), TokenManager.accounts.value.map { it.username })
                assertEquals("access-2", TokenManager.getAccessToken(), "Removing another account must leave the session alone")
                scene.save("account-switch-removed", 1_200_000_000L)
                // Picking the account already in use is the same as closing the screen.
                scene.sendPointerEvent(PointerEventType.Press, FIRST_ROW)
                scene.sendPointerEvent(PointerEventType.Release, FIRST_ROW)
                repeat(24) { scene.render((it + 80) * 16_000_000L).close() }
                assertEquals(1, cancelled)
                assertEquals(0, done)
            } finally { scene.close() }
        } finally {
            Prefs.restore(saved)
            Strings.appLanguage = language
            cleanUp(wasGuest)
        }
    }

    private companion object {
        val GUEST_TILE = Offset(975f, 520f)
        val FORGET_FIRST = Offset(1120f, 380f)
        val FIRST_ROW = Offset(700f, 380f)
    }
}
