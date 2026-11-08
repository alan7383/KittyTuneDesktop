import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class YoutubeFallbackVideoTest {
    private fun track(title: String, artist: String) = Track(76, title, null, 30_000,
        User(1, artist, null), policy = "SNIP")
    private fun video(id: String, title: String, channel: String, seconds: Long = 214) =
        StreamInfoItem(0, "https://youtube.com/watch?v=$id", title, StreamType.VIDEO_STREAM).apply {
            duration = seconds
            uploaderName = channel
        }

    @Test fun `a same length cover cannot replace the tatu recording`() {
        val requested = track("All The Things She Said", "t.A.T.u.")
        val selected = StreamResolver.matchYoutubeFallbackVideo(requested, listOf(
            video("cover", "All The Things She Said", "First To Eleven"),
            video("original", "t.A.T.u. – All The Things She Said (Official Video)", "Universal Music", 218)))
        assertEquals("https://youtube.com/watch?v=original", selected?.url)
    }

    @Test fun `the named remix wins over the original and a different remix`() {
        val requested = track("Strongest (Alan Walker Remix)", "Ina Wroldsen")
        val selected = StreamResolver.matchYoutubeFallbackVideo(requested, listOf(
            video("original", "Strongest", "Ina Wroldsen"),
            video("wrong", "Strongest (Other Remix)", "Ina Wroldsen"),
            video("correct", "Ina Wroldsen - Strongest (Alan Walker Remix)", "Universal Music", 216)))
        assertEquals("https://youtube.com/watch?v=correct", selected?.url)
    }

    @Test fun `no confident recording yields no substitute`() {
        assertNull(StreamResolver.matchYoutubeFallbackVideo(track("Strongest (Alan Walker Remix)", "Ina Wroldsen"),
            listOf(video("original", "Strongest", "Ina Wroldsen"))))
    }
}
