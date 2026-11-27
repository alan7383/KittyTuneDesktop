package com.alananasss.kittytune.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun theLocalArrowFollowsTheSettingsHierarchy() {
        val nav = SettingsNavigation
        val start = nav.current
        try {
            nav.go(SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES, SettingsSubPage.CUSTOM_THEME)))
            nav.up()
            assertEquals(SettingsSubPage.THEMES, nav.current.subPage)
            nav.up()
            assertEquals(SettingsPlace(SettingsCategory.INTERFACE), nav.current)
        } finally {
            nav.go(start)
        }
    }

    @Test
    fun externalSettingsPagesAreClosedTogetherWhenOpenedFromSettings() {
        assertEquals("feed", settingsBackDestination(listOf("home", "feed", "settings", "qobuz_settings")))
        assertEquals("home", settingsBackDestination(listOf("home", "settings", "settings")))
        assertNull(settingsBackDestination(listOf("home", "qobuz_settings")))
        assertNull(settingsBackDestination(listOf("home", "settings", "feed")))
    }
}
