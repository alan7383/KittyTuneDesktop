package com.alananasss.kittytune.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainWindowRaiserTest {

    @Test
    fun testPendingShowWhenHandlerNull() {
        MainWindowRaiser.handler = null
        MainWindowRaiser.pendingShow = false

        MainWindowRaiser.raise()

        assertTrue(MainWindowRaiser.pendingShow, "pendingShow should be true when handler is null")

        var handlerCalled = false
        MainWindowRaiser.handler = { handlerCalled = true }

        // Simulate DisposableEffect consumption
        if (MainWindowRaiser.pendingShow) {
            MainWindowRaiser.pendingShow = false
            MainWindowRaiser.handler?.invoke()
        }

        assertTrue(handlerCalled, "Handler should have been invoked after consuming pendingShow")
        assertFalse(MainWindowRaiser.pendingShow, "pendingShow should now be false")
    }

    @Test
    fun testImmediateInvocationWhenHandlerPresent() {
        MainWindowRaiser.pendingShow = false
        var count = 0
        MainWindowRaiser.handler = { count++ }

        MainWindowRaiser.raise()

        assertEquals(1, count)
        assertFalse(MainWindowRaiser.pendingShow)
    }
}
