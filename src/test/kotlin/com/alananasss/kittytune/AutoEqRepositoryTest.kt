package com.alananasss.kittytune

import com.alananasss.kittytune.data.audio.AutoEqRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AutoEqRepositoryTest {

    @Test
    fun graphicEqIsResampledToSixteenBands() {
        val data = AutoEqRepository.loadFromText(
            "Test Headphones",
            "GraphicEQ: 20 -6.0; 100 2.0; 1000 4.0; 10000 -3.0; 20000 1.0",
        )

        assertEquals(16, data.gainsDb.size)
        assertTrue(data.gainsDb.all { it.isFinite() })
    }

    @Test
    fun hyperXCloudIIHasABundledProfile() = runBlocking {
        val profile = AutoEqRepository.POPULAR_SEED_PROFILES
            .first { it.name == "HyperX Cloud II" }
        val data = AutoEqRepository.load(profile)

        assertEquals(16, data.gainsDb.size)
        assertTrue(data.gainsDb.any { it < -5f })
    }

    @Test
    fun seedProfilesAreSearchableOffline() {
        val found = AutoEqRepository.searchIn(
            AutoEqRepository.POPULAR_SEED_PROFILES,
            "Sony WH-1000XM5",
            5,
        )

        assertTrue(found.isNotEmpty())
        assertEquals("Sony WH-1000XM5", found.first().name)
    }

    @Test
    fun bluetoothOwnerPrefixDoesNotBreakSearch() {
        val found = AutoEqRepository.searchIn(
            AutoEqRepository.POPULAR_SEED_PROFILES,
            "Alan's Pixel Buds Pro 2",
            5,
        )

        assertTrue(found.any { it.name.contains("Pixel Buds Pro 2", ignoreCase = true) })
    }
}
