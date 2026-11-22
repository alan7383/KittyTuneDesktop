package com.alananasss.kittytune.audio.ai.karaoke

import java.nio.file.Files
import org.junit.Test
import kotlin.test.*

class ConfiguredDemucsTest {
    @Test fun `a packaged launcher is accepted without copying or binary-size heuristics`() {
        val launcher = Files.createTempFile("demucs-launcher-", ".sh").toFile()
        try {
            launcher.writeText("#!/bin/sh\nexit 0\n")
            launcher.setExecutable(true)
            assertEquals(launcher, configuredDemucsBinary(launcher.absolutePath))
        } finally { launcher.delete() }
    }

    @Test fun `missing directory and relative overrides are rejected`() {
        val directory = Files.createTempDirectory("demucs-missing-").toFile()
        try {
            assertNull(configuredDemucsBinary(directory.absolutePath))
            assertNull(configuredDemucsBinary(directory.resolve("absent").absolutePath))
            assertNull(configuredDemucsBinary("relative/demucs"))
        } finally { directory.delete() }
    }

    @Test fun `unset override leaves ordinary helper discovery available`() {
        assertNull(configuredDemucsBinary(null))
        assertNull(configuredDemucsBinary(""))
    }
}
