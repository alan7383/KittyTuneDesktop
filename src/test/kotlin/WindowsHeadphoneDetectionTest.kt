package com.alananasss.kittytune.data.audio

import org.junit.Test
import kotlin.test.*

class WindowsHeadphoneDetectionTest {
    @Test fun `speakers do not trigger a bluetooth scan`() {
        var scans = 0
        val result = HeadphoneDetector.windowsOutput(
            { listOf("Speakers (Realtek Audio)", "Microphone (Realtek Audio)") },
            { scans++; listOf("Bluetooth Controller") },
        )
        assertNull(result)
        assertEquals(0, scans)
    }

    @Test fun `wired headsets keep their autoeq identity`() {
        val result = HeadphoneDetector.windowsOutput({ listOf("Headphones (HyperX Cloud II)") }, { emptyList() })
        assertNotNull(result)
        assertEquals("HyperX Cloud II", result.name)
        assertEquals("hyperx-cloud-ii", result.key)
        assertFalse(result.isBluetooth)
    }

    @Test fun `bluetooth model names still identify the transport`() {
        val result = HeadphoneDetector.windowsOutput(
            { listOf("Headphones (HyperX Cloud II)") }, { listOf("HYPERX CLOUD II") },
        )
        assertNotNull(result)
        assertTrue(result.isBluetooth)
        assertEquals("Bluetooth", result.connectionLabel)
    }

    @Test fun `wireless endpoint names work without a matching bluetooth entry`() {
        val result = HeadphoneDetector.windowsOutput({ listOf("Wireless Headset") }, { emptyList() })
        assertNotNull(result)
        assertTrue(result.isBluetooth)
    }

    @Test fun `localized device names are preserved`() {
        val result = HeadphoneDetector.windowsOutput({ listOf("Наушники Модель") }, { emptyList() })
        assertNotNull(result)
        assertEquals("Наушники Модель", result.name)
    }

    @Test fun `the first matching endpoint is selected and blanks are ignored`() {
        val result = HeadphoneDetector.windowsOutput(
            { listOf(" ", "Speakers", " Headset A ", "Headset B") }, { emptyList() },
        )
        assertNotNull(result)
        assertEquals("Headset A", result.name)
    }
}
