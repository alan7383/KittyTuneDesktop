import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerBarButtonsTest {

    private val prefs = PlayerPreferences()

    @Before
    fun setUp() {
        Prefs.remove("player_bar_buttons")
        Prefs.remove("player_bar_devices_migrated")
    }

    @Test
    fun testDevicesConstant() {
        assertEquals("devices", PlayerPreferences.PLAYER_BAR_BUTTON_DEVICES)
    }

    @Test
    fun testDefaultButtonsIncludeDevices() {
        assertTrue(
            "Default player bar buttons should include devices",
            PlayerPreferences.PLAYER_BAR_BUTTONS_DEFAULT.contains(PlayerPreferences.PLAYER_BAR_BUTTON_DEVICES)
        )
        val buttons = prefs.getPlayerBarButtons()
        assertTrue(buttons.contains(PlayerPreferences.PLAYER_BAR_BUTTON_DEVICES))
    }

    @Test
    fun testLegacyMigrationAddsDevices() {
        // User had older preferences without "devices"
        Prefs.putString("player_bar_buttons", "like,panel,queue,lyrics,miniplayer,shuffle,repeat")
        Prefs.remove("player_bar_devices_migrated")

        val buttons = prefs.getPlayerBarButtons()
        assertTrue(
            "Migration should include devices for legacy users",
            buttons.contains(PlayerPreferences.PLAYER_BAR_BUTTON_DEVICES)
        )
        assertTrue(buttons.contains(PlayerPreferences.PLAYER_BAR_BUTTON_LIKE))
        assertTrue(buttons.contains(PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE))
    }

    @Test
    fun testExplicitlyDisabledDevicesDoesNotResurrect() {
        // User explicitly toggles off devices in settings
        val customized = setOf(
            PlayerPreferences.PLAYER_BAR_BUTTON_LIKE,
            PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE,
        )
        prefs.setPlayerBarButtons(customized)

        val retrieved = prefs.getPlayerBarButtons()
        assertFalse(
            "Disabled devices button should remain disabled",
            retrieved.contains(PlayerPreferences.PLAYER_BAR_BUTTON_DEVICES)
        )
        assertTrue(retrieved.contains(PlayerPreferences.PLAYER_BAR_BUTTON_LIKE))
        assertTrue(retrieved.contains(PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE))
    }
}
