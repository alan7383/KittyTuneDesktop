package com.alananasss.kittytune

import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.common.Discord
import com.alananasss.kittytune.ui.profile.SettingsSubPage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DiscordIconTest {

    @Test
    fun testDiscordIconVectorProperties() {
        val discord = Icons.Rounded.Discord
        assertNotNull(discord, "Discord icon vector must not be null")
        assertEquals("Discord", discord.name)
        assertEquals(24.dp, discord.defaultWidth)
        assertEquals(24.dp, discord.defaultHeight)
        assertEquals(24f, discord.viewportWidth)
        assertEquals(24f, discord.viewportHeight)
    }

    @Test
    fun testSettingsSubPageUsesDiscordIcon() {
        assertEquals(
            Icons.Rounded.Discord,
            SettingsSubPage.MISC_DISCORD.icon,
            "SettingsSubPage.MISC_DISCORD should use Icons.Rounded.Discord instead of forum icon"
        )
    }
}
