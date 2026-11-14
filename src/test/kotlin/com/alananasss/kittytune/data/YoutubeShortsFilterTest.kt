package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.search.isYoutubeShort
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeShortsFilterTest {

    @Test
    fun `short form flag or shorts url marks a short`() {
        assertTrue(isYoutubeShort(true, "https://www.youtube.com/watch?v=abc"))
        assertTrue(isYoutubeShort(false, "https://www.youtube.com/shorts/abc"))
    }

    @Test
    fun `regular videos are kept`() {
        assertFalse(isYoutubeShort(false, "https://www.youtube.com/watch?v=abc"))
        assertFalse(isYoutubeShort(false, null))
    }
}
