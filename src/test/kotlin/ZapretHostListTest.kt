import com.alananasss.kittytune.data.zapret.ZapretHostList
import com.alananasss.kittytune.data.zapret.ZapretInstall
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZapretHostListTest {

    /** The user's own file ends without a newline; our block must not glue onto its last domain. */
    @Test
    fun addingToAFileWithoutATrailingNewlineKeepsItsLastLine() {
        val text = "domain.example.abc\nrootapp.com\nrootappcdn.com"
        val result = ZapretHostList.withOwnDomains(text, listOf("sndcdn.com"))
        assertEquals(
            "domain.example.abc\nrootapp.com\nrootappcdn.com\n${ZapretHostList.BEGIN}\nsndcdn.com\n${ZapretHostList.END}\n",
            result,
        )
    }

    @Test
    fun removingOurBlockLeavesTheRestUntouched() {
        val text = "a.com\n${ZapretHostList.BEGIN}\nsndcdn.com\n${ZapretHostList.END}\nb.com\n"
        assertEquals("a.com\nb.com\n", ZapretHostList.withOwnDomains(text, emptyList()))
        assertEquals(listOf("sndcdn.com"), ZapretHostList.ownDomains(text))
    }

    @Test
    fun anEmptiedListKeepsAPlaceholderBecauseZapretRejectsEmptyOnes() {
        val text = "${ZapretHostList.BEGIN}\nsndcdn.com\n${ZapretHostList.END}\n"
        assertEquals("domain.example.abc\n", ZapretHostList.withOwnDomains(text, emptyList()))
    }

    @Test
    fun aParentDomainCoversItsSubdomains() {
        val covered = setOf("youtube.com")
        assertTrue(ZapretHostList.isCovered("music.youtube.com", covered))
        assertFalse(ZapretHostList.isCovered("youtube.co", covered))
    }

    @Test
    fun addSkipsWhatTheBundledListsAlreadyHave() {
        val folder = Files.createTempDirectory("zapret").toFile()
        val lists = folder.resolve("lists").apply { mkdirs() }
        lists.resolve("list-general.txt").writeText("youtube.com\ngooglevideo.com\n")
        lists.resolve("list-general-user.txt").writeText("# Never leave this file empty\ndomain.example.abc")
        val install = ZapretInstall(folder)

        val added = install.add(listOf("youtube.com", "music.youtube.com", "sndcdn.com"))

        assertEquals(listOf("sndcdn.com"), added)
        assertEquals(listOf("sndcdn.com"), install.kittyTuneDomains())
        install.removeOwn()
        assertEquals("# Never leave this file empty\ndomain.example.abc\n", lists.resolve("list-general-user.txt").readText())
        folder.deleteRecursively()
    }
}
