import com.alananasss.kittytune.data.DownloadFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun store(id: Long, title: String, contents: String): File {
        val source = temporary.newFile().apply { writeText(contents) }
        return DownloadFiles.store(source, temporary.root, id, "artist", title, "m4a")
    }

    @Test fun differentRecordingsKeepTheirOwnAudio() {
        val first = store(101, "song", "first")
        val second = store(202, "song", "second")
        assertNotEquals(first, second)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
        first.delete()
        assertEquals("second", second.readText())
    }

    @Test fun sanitizedTitlesCannotOverwriteEachOther() {
        val first = store(303, "song:live", "first")
        val second = store(404, "song?live", "second")
        assertNotEquals(first, second)
        assertEquals("first", first.readText())
    }

    @Test fun failedReplacementKeepsThePreviousDownload() {
        val first = store(101, "song", "complete")
        val missing = File(temporary.root, "missing")
        assertThrows(Exception::class.java) {
            DownloadFiles.store(missing, temporary.root, 101, "artist", "song", "m4a")
        }
        assertEquals("complete", first.readText())
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "part" })
    }

    @Test fun longUnicodeMetadataFitsOneFilename() {
        val source = temporary.newFile().apply { writeText("audio") }
        val result = DownloadFiles.store(source, temporary.root, Long.MIN_VALUE,
            "🎵".repeat(100), "song/".repeat(100), "mp3")
        assertTrue(result.name.toByteArray(Charsets.UTF_8).size <= 255)
        assertEquals("audio", result.readText())
    }
}
