package com.alananasss.kittytune

import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.canonicalMediaPath
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlaylistTrackCrossRef
import com.alananasss.kittytune.data.localMediaTrackId
import com.alananasss.kittytune.data.pruneWaveformFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageOwnershipTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `clearing downloads keeps imported files and their library entries`() = runBlocking {
        assumeTrue("Use Gradle's isolated test profile", System.getenv("APPDATA")?.contains("test-profile") == true)
        val local = temporary.newFile("local.mp3").apply { writeText("user audio") }
        val cover = temporary.newFile("local.jpg").apply { writeText("user cover") }
        val downloaded = temporary.newFile("download.mp3").apply { writeText("app download") }
        val localRow = LocalTrack(
            localMediaTrackId(local.absolutePath), "Local", "Artist", cover.absolutePath, 2_000,
            local.absolutePath, cover.absolutePath,
        )
        val remoteRow = LocalTrack(
            8_000_000_000 + downloaded.absolutePath.hashCode().toLong(), "Downloaded", "Artist", "", 2_000,
            downloaded.absolutePath, "", source = "soundcloud",
        )
        AppDatabase.init()
        val dao = AppDatabase.downloadDao
        val playlistId = remoteRow.id + 1
        try {
            dao.insertTrack(localRow)
            dao.insertTrack(remoteRow)
            dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, localRow.id))
            DownloadManager.removeAllContent(includeAudio = true, includeImages = true)

            assertEquals("user audio", local.readText())
            assertEquals("user cover", cover.readText())
            assertEquals(local.absolutePath, dao.getTrack(localRow.id)?.localAudioPath)
            assertEquals(localRow.id, dao.getRef(playlistId, localRow.id)?.trackId)
            assertFalse(downloaded.exists())
            assertEquals("", dao.getTrack(remoteRow.id)?.localAudioPath)
        } finally {
            dao.deletePlaylistRefs(playlistId)
            dao.deleteTrack(localRow.id)
            dao.deleteTrack(remoteRow.id)
        }
        Unit
    }

    @Test
    fun `different paths to the same file use the same scan key`() {
        val nested = temporary.newFolder("album")
        val file = temporary.newFile("track.mp3")

        assertEquals(
            canonicalMediaPath(file.absolutePath),
            canonicalMediaPath(File(nested, "../track.mp3").absolutePath),
        )
    }

    @Test
    fun `waveform eviction removes old entries and leaves unrelated files alone`() {
        val cache = temporary.newFolder("waveforms")
        val old = File(cache, "wfdec_1.bin").apply { writeBytes(ByteArray(8)); setLastModified(1_000L) }
        val recent = File(cache, "wfdec_2.bin").apply { writeBytes(ByteArray(8)); setLastModified(60_000L) }
        val unrelated = File(cache, "unrelated.bin").apply { writeBytes(ByteArray(32)) }

        pruneWaveformFiles(cache, maxBytes = 8)

        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(unrelated.exists())
    }
}
