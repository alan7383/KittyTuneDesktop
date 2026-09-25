import com.alananasss.kittytune.util.LanguageDetection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Low-accuracy mode must still answer the only question the translate button asks. */
class LanguageDetectionTest {

    @Test
    fun recognisesCommonCommentLanguages() {
        assertEquals("ru", LanguageDetection.identifyLanguage("этот трек просто разрывает, слушаю весь день"))
        assertEquals("en", LanguageDetection.identifyLanguage("this track goes so hard, been looping it all day"))
        assertEquals("fr", LanguageDetection.identifyLanguage("ce morceau est incroyable, je l'écoute en boucle"))
    }
}
