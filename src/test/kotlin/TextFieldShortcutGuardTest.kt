import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Every text field in the app has to opt out of the keyboard shortcuts, and this is what remembers (issue #33).
 *
 * ## The bug this exists for
 *
 * "Écrire sur le truc où on écrit sur le mix, ça prend les raccourcis."
 *
 * The window handles shortcuts in `onPreviewKeyEvent`, which runs *before* the focused component sees the key —
 * that is deliberate, because Escape and the media keys have to work whatever has focus. The guard against
 * stealing somebody's typing is `TextInputTracker`, and a field only appears in it if it was written with
 * `Modifier.trackTextInput()`.
 *
 * Which makes the guard opt-in, and opt-in guards are forgotten. When he reported it the mix's artist field was
 * missing it — along with **forty others**: the whole upload screen, the proxy settings, the sync settings,
 * playlist editing, the trim dialog, the Yandex token box. Typing a playlist name pressed L for like, S for
 * shuffle and space for pause.
 *
 * ## Why this is a test and not a comment
 *
 * Adding the modifier forty-one times fixes today. It does nothing about the forty-second field, which somebody
 * will write next month without knowing this paragraph exists — and the symptom is subtle enough to ship: the
 * text mostly arrives, and only some letters do something strange.
 *
 * So the invariant is checked instead of documented. A source scan is an unusual shape for a unit test, but it is
 * the only thing that can see this: the modifier is invisible at runtime without an instrumented UI harness, and
 * the rule is about source code rather than behaviour.
 */
class TextFieldShortcutGuardTest {

    private val textFieldCall = Regex("""\b(OutlinedTextField|BasicTextField|TextField)\s*\(""")

    @Test
    fun `every text field opts out of the keyboard shortcuts`() {
        val offenders = mutableListOf<String>()

        sourceFiles().forEach { file ->
            val lines = file.readText().split("\n")
            lines.forEachIndexed { index, line ->
                if (!textFieldCall.containsMatchIn(line)) return@forEachIndexed
                // `OutlinedTextFieldDefaults.colors(...)` and friends are configuration, not a field.
                if (line.contains("Defaults")) return@forEachIndexed

                val body = lines.subList(index, minOf(index + CALL_WINDOW, lines.size)).joinToString("\n")
                if (!body.contains("trackTextInput")) {
                    offenders += "${file.name}:${index + 1}"
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "these text fields will steal keystrokes as shortcuts — add Modifier.trackTextInput():\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * How far past the call to look for the modifier.
     *
     * Generously wide. A false pass costs one field missing the guard, which is the status quo; a false failure
     * costs somebody chasing a test that is wrong, and the upload screen has fields whose parameter lists run to
     * forty lines.
     */
    private val CALL_WINDOW = 45

    private fun sourceFiles(): List<File> {
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "expected to run from the project root, got ${root.absolutePath}")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The modifier's own definition, which naturally mentions no field.
            .filterNot { it.name == "TextInputTracker.kt" }
            .toList()
    }
}
