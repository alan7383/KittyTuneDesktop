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

    @Test
    fun resolveZapretRootFromBinSubfolder() {
        val root = Files.createTempDirectory("zapret-test").toFile()
        val bin = root.resolve("bin").apply { mkdirs() }
        val winws = bin.resolve("winws.exe").apply { writeText("dummy") }
        val lists = root.resolve("lists").apply { mkdirs() }
        lists.resolve("list-general.txt").writeText("example.com")

        assertEquals(root, com.alananasss.kittytune.data.zapret.ZapretManager.resolveZapretRoot(winws))
        assertEquals(root, com.alananasss.kittytune.data.zapret.ZapretManager.resolveZapretRoot(bin))
        assertEquals(root, com.alananasss.kittytune.data.zapret.ZapretManager.resolveZapretRoot(root))
        root.deleteRecursively()
    }

    @Test
    fun resolveZapretRootFromNestedBolVanBinSubfolder() {
        val root = Files.createTempDirectory("zapret-test").toFile()
        val nestedBin = root.resolve("bin/x86_64").apply { mkdirs() }
        val winws = nestedBin.resolve("winws.exe").apply { writeText("dummy") }
        val lists = root.resolve("lists").apply { mkdirs() }
        lists.resolve("list-general.txt").writeText("example.com")

        assertEquals(root, com.alananasss.kittytune.data.zapret.ZapretManager.resolveZapretRoot(winws))
        root.deleteRecursively()
    }

    @Test
    fun resolveZapretRootFromListFile() {
        val root = Files.createTempDirectory("zapret-test").toFile()
        val lists = root.resolve("lists").apply { mkdirs() }
        val listFile = lists.resolve("list-general.txt").apply { writeText("example.com") }

        assertEquals(root, com.alananasss.kittytune.data.zapret.ZapretManager.resolveZapretRoot(listFile))
        root.deleteRecursively()
    }

    @Test
    fun expandEnvVarsResolvesKnownWindowsVariables() {
        val expanded = com.alananasss.kittytune.data.zapret.ZapretManager.expandEnvVars("%SystemDrive%\\zapret\\bin\\winws.exe")
        assertTrue(expanded.contains("zapret\\bin\\winws.exe"))
    }

    @Test
    fun extractPathsFromCommandLineAndRegistryStrings() {
        val text = """
            HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\zapret
                ImagePath    REG_EXPAND_SZ    "C:\zapret\bin\winws.exe" --wf-l3=ipv4 --wf-tcp=80,443 --hostlist="C:\zapret\lists\list-general.txt"
        """.trimIndent()
        val paths = com.alananasss.kittytune.data.zapret.ZapretManager.extractPaths(text).map { it.path.replace('/', '\\') }.toList()
        assertTrue(paths.any { it.contains("C:\\zapret\\bin\\winws.exe") })
        assertTrue(paths.any { it.contains("C:\\zapret\\lists\\list-general.txt") })
    }

    @Test
    fun nonZapretDirectoryIsNotValid() {
        val root = Files.createTempDirectory("not-zapret").toFile()
        root.resolve("lists").mkdirs() // Empty lists directory
        assertFalse(ZapretInstall(root).isValid)
        assertEquals(null, com.alananasss.kittytune.data.zapret.ZapretManager.resolveZapretRoot(root))
        root.deleteRecursively()
    }
}
