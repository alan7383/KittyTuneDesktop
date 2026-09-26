package com.alananasss.kittytune.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun backFromASubPageReturnsToItsCategoryAndForwardReopensIt() {
        val nav = SettingsNavigation
        nav.go(SettingsPlace(SettingsCategory.INTERFACE))
        nav.go(SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)))

        nav.back()
        assertEquals(SettingsPlace(SettingsCategory.INTERFACE), nav.current)

        nav.forward()
        assertEquals(SettingsSubPage.THEMES, nav.current.subPage)
    }

    @Test
    fun theCategoryIsRememberedAndBackWalksCategories() {
        val nav = SettingsNavigation
        nav.go(SettingsPlace(SettingsCategory.AUDIO))
        nav.go(SettingsPlace(SettingsCategory.SYNC))

        assertEquals(SettingsCategory.SYNC, nav.current.category)
        nav.back()
        assertEquals(SettingsCategory.AUDIO, nav.current.category)
    }

    @Test
    fun upReusesTheHistoryStepInsteadOfAddingOne() {
        val nav = SettingsNavigation
        nav.go(SettingsPlace(SettingsCategory.INTERFACE))
        nav.go(SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.PLAYER)))
        nav.up()

        assertEquals(SettingsPlace(SettingsCategory.INTERFACE), nav.current)
        assertEquals(SettingsSubPage.PLAYER, nav.let { it.forward(); it.current.subPage })
        nav.back()
        assertFalse(nav.current.depth > 0)
    }
}
