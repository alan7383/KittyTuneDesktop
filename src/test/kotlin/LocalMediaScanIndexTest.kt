package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.LocalTrack
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class LocalMediaScanIndexTest {
    @get:Rule val tmp = TemporaryFolder()
    private val indexFile get() = File(tmp.root, "scan-index.bin")

    private fun track(file: File, artwork: String = "") = LocalTrack(
        localMediaTrackId(file.absolutePath), "title", "artist", artwork, 1234L, file.absolutePath, artwork,
    )

    private fun LocalTrack.asDatabaseRows() = mapOf(id to this)

    private fun prime(file: File, artwork: String = ""): LocalTrack {
        val value = track(file, artwork)
        val index = LocalMediaScanIndex(indexFile)
        assertEquals(value, index.scan(file, emptyMap()) { value })
        index.save()
        return value
    }

    @Test fun unchangedFilesAreSkippedAfterRestart() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        var reads = 0
        val result = LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { reads++; value }
        assertNull(result)
        assertEquals(0, reads)
    }

    @Test fun changedLengthInvalidatesEvenWithPreservedTimestamp() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        val time = Files.getLastModifiedTime(file.toPath())
        file.appendText("changed")
        Files.setLastModifiedTime(file.toPath(), time)
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun changedTimestampInvalidatesEvenWithUnchangedLength() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        val time = Files.getLastModifiedTime(file.toPath()).toMillis()
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(time + 5000))
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun missingDatabaseRowIsImportedAgain() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, emptyMap()) { value })
    }

    @Test fun differentPathInDatabaseCannotHideAHashCollision() {
        val file = tmp.newFile("Aa.mp3").apply { writeText("audio") }
        val value = prime(file)
        val collision = track(tmp.newFile("BB.mp3"))
        assertEquals(value.id, collision.id)
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, collision.asDatabaseRows()) { value })
    }

    @Test fun downloadAtTheSamePathDoesNotHideMissingLocalTrack() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        val download = value.copy(id = 42L)
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, download.asDatabaseRows()) { value })
    }

    @Test fun missingArtworkIsExtractedAgain() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val artwork = tmp.newFile("cover.jpg")
        val value = prime(file, artwork.absolutePath)
        assertTrue(artwork.delete())
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun failedReadsAreRetried() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        file.appendText("changed")
        val index = LocalMediaScanIndex(indexFile)
        assertNull(index.scan(file, value.asDatabaseRows()) { null })
        index.save()
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun fileChangedDuringReadIsNotRemembered() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = track(file)
        val index = LocalMediaScanIndex(indexFile)
        index.scan(file, emptyMap()) { file.appendText("changed"); value }
        index.save()
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun unsavedScanCannotSuppressTheNextImport() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = track(file)
        LocalMediaScanIndex(indexFile).scan(file, emptyMap()) { value }
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun corruptIndexFallsBackToReadingAudio() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        indexFile.writeBytes(byteArrayOf(0, 0, 0, 1, 127))
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }

    @Test fun entriesNotSeenInTheNextScanArePruned() {
        val file = tmp.newFile("song.mp3").apply { writeText("audio") }
        val value = prime(file)
        LocalMediaScanIndex(indexFile).save()
        assertEquals(value, LocalMediaScanIndex(indexFile).scan(file, value.asDatabaseRows()) { value })
    }
}
