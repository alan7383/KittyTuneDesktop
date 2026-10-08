import com.alananasss.kittytune.data.artist.ArtistClips
import com.alananasss.kittytune.data.artist.Clip
import org.junit.Test
import kotlin.test.assertEquals

/** Which search results count as the artist's own music videos (tester's list, 5.5). */
class ArtistClipsTest {

    private fun clip(title: String, uploader: String, durationSec: Long = 220L) =
        Clip("https://youtube.com/watch?v=" + title.hashCode(), title, uploader, null, durationSec, 1_000L)

    @Test
    fun `keeps the artist's own videos and drops everyone else's`() {
        val found = listOf(
            clip("Rihanna - Umbrella (Official Music Video) ft. JAY-Z", "Rihanna"),
            clip("Shakira - Can't Remember to Forget You ft. Rihanna", "Shakira"),
            clip("Rihanna - Diamonds", "RihannaVEVO"),
            clip("Rihanna - Stay (Lyrics)", "Lyrics Channel"),
            clip("Umbrella", "Rihanna - Topic"),
            clip("Rihanna - Work (Teaser)", "Rihanna", durationSec = 30L),
            clip("Rihanna - Umbrella (Orange Version)", "Rihanna"),
        )
        assertEquals(
            listOf("Rihanna - Umbrella (Official Music Video) ft. JAY-Z", "Rihanna - Diamonds"),
            ArtistClips.pickClips("Rihanna", found).map { it.title },
        )
    }

    @Test
    fun `a title starting with the artist counts even from another channel`() {
        val found = listOf(clip("9mice - NEW-YORK (клип)", "Some Label"), clip("NEW-YORK", "Some Label"))
        assertEquals(listOf("9mice - NEW-YORK (клип)"), ArtistClips.pickClips("9mice", found).map { it.title })
    }
}
