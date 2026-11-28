package com.alananasss.kittytune.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupSettingsTest {
    @Test fun `fade and none survive reading the stored preference`() {
        val previous = Prefs.getString("startup_transition", null)
        try {
            StartupSplash.transition = StartupTransition.NONE
            assertEquals("NONE", Prefs.getString("startup_transition", null))
            assertEquals(StartupTransition.NONE, StartupSplash.transition)
            StartupSplash.transition = StartupTransition.FADE
            assertEquals("FADE", Prefs.getString("startup_transition", null))
            assertEquals(StartupTransition.FADE, StartupSplash.transition)
        } finally {
            Prefs.putString("startup_transition", previous)
        }
    }

    @Test fun `missing and corrupt preferences preserve the default fade`() {
        val previous = Prefs.getString("startup_transition", null)
        try {
            Prefs.putString("startup_transition", null)
            assertEquals(StartupTransition.FADE, StartupSplash.transition)
            Prefs.putString("startup_transition", "broken")
            assertEquals(StartupTransition.FADE, StartupSplash.transition)
        } finally {
            Prefs.putString("startup_transition", previous)
        }
    }
}
