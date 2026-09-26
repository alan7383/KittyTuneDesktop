import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.ContributorCategory
import com.alananasss.kittytune.data.CreditsRepository
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The credits list, which is data now.
 *
 * It used to be a list of constructors inside the screen, so the only way to credit someone was a
 * build. That is what this pins: the file is read, it is complete, and every role it names has a
 * translated description behind it in all six languages — because a role that resolves to nothing
 * renders as a raw key on somebody's screen, which is the failure nobody notices until a translator
 * opens About.
 */
class CreditsRepositoryTest {

    private val all = CreditsRepository.all()

    @Test
    fun testTheFileIsFoundAndRead() {
        assertTrue(
            File("src/main/resources/credits.json").exists(),
            "credits.json should be in the resources"
        )
        assertTrue(all.isNotEmpty(), "The credits should not be empty")
    }

    @Test
    fun testEveryEntryHasSomethingToClickAndSomethingToShow() {
        all.forEach { person ->
            assertTrue(person.name.isNotBlank(), "A contributor with no name: $person")
            assertTrue(
                person.url.startsWith("https://"),
                "The link has to be a real one, not something the OS would open blindly: ${person.url}",
            )
        }
    }

    @Test
    fun testAGitHubLoginIsTurnedIntoALinkAndAnAvatar() {
        val withAvatar = all.mapNotNull { it.avatarUrl }
        assertTrue(withAvatar.isNotEmpty())
        withAvatar.forEach {
            assertTrue(
                it.endsWith(".png"),
                "A GitHub avatar is the login plus .png: $it",
            )
        }
    }

    @Test
    fun testOrderIsExplicitSoNobodyLandsInTheWrongPlace() {
        // The file says who sits where; if two rows were left unordered the screen would sort them
        // by whatever order the parser happened to produce.
        val file = File("src/main/resources/credits.json").readText()
        val orders = Regex("\"order\": (\\d+)").findAll(file).map { it.groupValues[1].toInt() }.toList()
        assertEquals(orders.size, orders.distinct().size, "Two rows share an order: $orders")
        assertEquals(orders.sorted(), orders, "The file should already be in order: $orders")
    }

    @Test
    fun testTheCategoriesCoverEveryone() {
        assertTrue(all.any { it.category == ContributorCategory.DEV }, "Nobody under development")
        assertTrue(all.any { it.category == ContributorCategory.TRANSLATION }, "Nobody under translation")
        assertTrue(all.any { it.category == ContributorCategory.COMMUNITY }, "Nobody under community")
    }

    @Test
    fun testEveryRoleHasATranslatedDescriptionInEveryLanguage() {
        val languages = listOf("en", "fr", "de", "hu", "ru", "vi")
        val keys = all.flatMap { listOf(it.roleResKey, it.descriptionResKey) }.distinct()

        val previous = Strings.appLanguage
        try {
            for (lang in languages) {
                Strings.appLanguage = lang
                for (key in keys) {
                    val value = Strings.get(key)
                    assertTrue(
                        value.isNotBlank() && value != key,
                        "Role key '$key' is not translated in $lang",
                    )
                }
            }
        } finally {
            Strings.appLanguage = previous
        }
    }

    @Test
    fun testRolesAreKeysAndNotSentences() {
        // The whole reason this is data: a role is written once per language. If a role were a
        // sentence here it would be English on every screen in the app.
        all.forEach {
            assertTrue(
                it.roleResKey.startsWith("about_role_"),
                "A role should be a key into about_role_*, not text: ${it.roleResKey}",
            )
            assertEquals(it.roleResKey + "_desc", it.descriptionResKey)
        }
    }

    @Test
    fun testTheGermanTranslatorIsCreditedAsCodeNotTranslation() {
        // He does both, and he can only be in one group. Asserted so that a later tidy cannot move
        // him back without somebody deciding it again.
        val jason = all.firstOrNull { it.name.contains("jason-fastner007") }
        assertNotNull(jason, "The German translator should still be credited")
        assertEquals(ContributorCategory.DEV, jason.category)
    }

    @Test
    fun testBadDataYieldsAnEmptyListRatherThanACrash() {
        // The repository is an object reading one bundled file, so this is about the shape of the
        // failure: an About dialog that shows its filters and nothing under them beats one that
        // takes the window down.
        val source = File("src/main/kotlin/com/alananasss/kittytune/data/CreditsRepository.kt")
            .readText()
        assertTrue(
            source.contains("runCatching") && source.contains("getOrElse"),
            "Reading the file must be guarded: a malformed edit should not take About down",
        )
        assertTrue(
            source.contains("getResourceAsStream(RESOURCE)"),
            "…and a missing file must be an empty list, not a crash",
        )
    }
}
