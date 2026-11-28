package com.alananasss.kittytune.core

import org.junit.Assert.*
import org.junit.Test

class StartupProgressTest {
    @Test fun `default and unknown transition settings use fade`() {
        assertEquals(StartupTransition.FADE, StartupTransition.fromStored(null))
        assertEquals(StartupTransition.FADE, StartupTransition.fromStored("unknown"))
        assertEquals(StartupTransition.NONE, StartupTransition.fromStored("NONE"))
    }

    @Test fun `progress starts at zero and only ready reaches one hundred`() {
        val progress = StartupProgress()
        assertEquals(0, progress.percent)
        for (stage in StartupStage.entries.dropLast(1)) {
            progress.advance(stage)
            assertTrue(progress.percent < 100)
        }
        progress.advance(StartupStage.READY)
        assertEquals(100, progress.percent)
    }

    @Test fun `late or duplicate milestones cannot move progress backwards`() {
        val progress = StartupProgress()
        progress.advance(StartupStage.PLAYER)
        val percent = progress.percent
        progress.advance(StartupStage.DATABASE)
        progress.advance(StartupStage.PLAYER)
        assertEquals(percent, progress.percent)
        assertEquals(StartupStage.PLAYER, progress.stage)
    }

    @Test fun `all completed stages stay completed`() {
        val progress = StartupProgress()
        progress.advance(StartupStage.READY)
        StartupStage.entries.reversed().forEach(progress::advance)
        assertEquals(100, progress.percent)
    }

    @Test fun `each actual milestone increases completed stage percentage`() {
        val progress = StartupProgress()
        var before = progress.percent
        for (stage in StartupStage.entries.drop(1)) {
            progress.advance(stage)
            assertTrue(progress.percent > before)
            before = progress.percent
        }
    }
}
