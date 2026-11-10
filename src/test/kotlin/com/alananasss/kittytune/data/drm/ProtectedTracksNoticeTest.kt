package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.drm.NativeCdmSetup
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Transcoding
import com.alananasss.kittytune.domain.Format
import com.alananasss.kittytune.ui.profile.protectedTracksAnnouncementDue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Who gets told about protected tracks, and how often. Runs against the test profile's own preferences. */
class ProtectedTracksNoticeTest {

    private val keys = listOf(
        "protected_tracks_enabled", "protected_tracks_announced", "protected_tracks_hint_shown", "drm_onboarding_done",
        "protected_announced_version", "protected_hint_version",
    )


    /** These read and write preferences: only ever against headlessTest's throwaway profile, never a real one. */
    private fun requireTestProfile() =
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)

    @Before
    @After
    fun clean() {
        requireTestProfile()
        keys.forEach(Prefs::remove)
        ProtectedTracksNotice.dismiss()
    }

    private fun transcoding(protocol: String) = Transcoding(url = "https://x/$protocol", preset = "p", format = Format(protocol, "audio/mp4"))

    @Test
    fun `only a track with nothing but encrypted streams counts as protected-only`() {
        assertTrue(ProtectedTracksNotice.onlyProtected(listOf(transcoding("ctr-encrypted-hls"), transcoding("cbc-encrypted-hls"))))
        assertFalse(ProtectedTracksNotice.onlyProtected(listOf(transcoding("ctr-encrypted-hls"), transcoding("hls"))))
        assertFalse(ProtectedTracksNotice.onlyProtected(emptyList()))
    }

    @Test
    fun `off by default on Windows only`() {
        assertEquals(!NativeCdmSetup.windows, PlayerPreferences().getProtectedTracksEnabled())
        assertFalse(PlayerPreferences().hasProtectedTracksChoice())
    }

    @Test
    fun `the offer is made once, and never to someone who chose`() {
        assumeTrue("the offer is Windows-only", NativeCdmSetup.windows)
        ProtectedTracksNotice.onProtectedTrackSkipped()
        assertEquals(ProtectedTracksNotice.Kind.OFFER_ENABLE, ProtectedTracksNotice.current.value?.kind)

        ProtectedTracksNotice.dismiss()
        ProtectedTracksNotice.onProtectedTrackSkipped()
        assertNull("a second protected track must not offer again", ProtectedTracksNotice.current.value)

        Prefs.remove("protected_tracks_hint_shown")
        PlayerPreferences().setProtectedTracksEnabled(false)
        ProtectedTracksNotice.onProtectedTrackSkipped()
        assertNull("a switch turned off by hand needs no notice", ProtectedTracksNotice.current.value)
    }

    @Test
    fun `a new announcement reaches everyone, including whoever dismissed the last one`() {
        assertTrue("a fresh install is owed it", protectedTracksAnnouncementDue())

        PlayerPreferences().setProtectedAnnouncementVersion(ProtectedTracksNotice.CURRENT)
        assertFalse("already shown", protectedTracksAnnouncementDue())

        PlayerPreferences().setProtectedAnnouncementVersion(ProtectedTracksNotice.CURRENT - 1)
        assertTrue("an older announcement must not silence this one", protectedTracksAnnouncementDue())

        // 1.5.0 stored a boolean. It counts as "saw version 1", so those users get this one too.
        Prefs.remove("protected_announced_version")
        PlayerPreferences().setProtectedTracksAnnounced(true)
        assertTrue("whoever put the 1.5.0 card off sees this one", protectedTracksAnnouncementDue())

        // A choice made in the assistant no longer silences it either: the method behind it changed.
        Prefs.remove("protected_tracks_announced")
        PlayerPreferences().setDrmOnboardingDone(true)
        assertTrue(protectedTracksAnnouncementDue())
    }
}
