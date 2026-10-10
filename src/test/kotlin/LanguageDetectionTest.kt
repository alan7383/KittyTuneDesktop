import com.alananasss.kittytune.util.LanguageDetection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Low-accuracy mode must still answer the only question the translate button asks. */
class LanguageDetectionTest {

    @Test
    fun aShortRussianCommentIsNotOfferedToARussianReader() {
        // Issue #66: the button offered "Translate to Russian" on Russian comments, because short
        // Cyrillic text is often guessed as Ukrainian or Bulgarian first.
        listOf("топ", "жиза", "кто в 2026?", "лучший трек", "ахах да", "слушаю на повторе", "ЭТО ШЕДЕВР").forEach {
            assertFalse(it, LanguageDetection.needsTranslation(it, "ru"))
        }
    }

    @Test
    fun aCommentInAnotherLanguageIsOffered() {
        assertTrue(LanguageDetection.needsTranslation("this track goes so hard, been looping it all day", "ru"))
        assertTrue(LanguageDetection.needsTranslation("этот трек просто разрывает, слушаю весь день", "en"))
        assertTrue(LanguageDetection.needsTranslation("ce morceau est incroyable, je l'écoute en boucle", "en-US"))
        assertTrue(LanguageDetection.needsTranslation("love this song so much", "fr"))
        assertTrue(LanguageDetection.needsTranslation("qué buena canción la verdad", "en"))
        assertTrue(LanguageDetection.needsTranslation("ця пісня просто неймовірна, слухаю її цілий день", "ru"))
        assertTrue(LanguageDetection.needsTranslation("この曲は最高です", "ru"))
    }

    @Test
    fun aShortEnglishCommentIsNotOfferedToAnEnglishReader() {
        listOf("this track goes so hard, been looping it all day", "banger", "this is fire", "love this song so much").forEach {
            assertFalse(it, LanguageDetection.needsTranslation(it, "en"))
        }
    }

    @Test
    fun emojiAndNumbersAreNeverOffered() {
        assertFalse(LanguageDetection.needsTranslation("🔥🔥🔥 10/10", "en"))
    }
}
