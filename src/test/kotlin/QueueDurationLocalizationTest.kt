package com.alananasss.kittytune

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.utils.formatQueueDuration
import com.alananasss.kittytune.utils.makeTimeString
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDurationLocalizationTest {

    @Test
    fun testQueueTimeLeftInAllLanguages() {
        val originalLang = Strings.appLanguage
        try {
            val twoHoursFourteenMinutesMs = (2 * 3600 + 14 * 60) * 1000L
            val fortyFiveMinutesMs = 45 * 60 * 1000L
            val thirtySecondsMs = 30 * 1000L

            // English
            Strings.appLanguage = "en"
            assertEquals("2h 14min", formatQueueDuration(twoHoursFourteenMinutesMs))
            assertEquals("45 min", formatQueueDuration(fortyFiveMinutesMs))
            assertEquals("30s", formatQueueDuration(thirtySecondsMs))
            assertEquals("2h 14min left", str("queue_time_left", formatQueueDuration(twoHoursFourteenMinutesMs)))
            assertEquals("45 min left", str("queue_time_left", formatQueueDuration(fortyFiveMinutesMs)))

            // French
            Strings.appLanguage = "fr"
            assertEquals("2h 14min", formatQueueDuration(twoHoursFourteenMinutesMs))
            assertEquals("45 min", formatQueueDuration(fortyFiveMinutesMs))
            assertEquals("30s", formatQueueDuration(thirtySecondsMs))
            assertEquals("Encore 2h 14min", str("queue_time_left", formatQueueDuration(twoHoursFourteenMinutesMs)))
            assertEquals("Encore 45 min", str("queue_time_left", formatQueueDuration(fortyFiveMinutesMs)))

            // Russian
            Strings.appLanguage = "ru"
            assertEquals("2 ч. 14 мин.", formatQueueDuration(twoHoursFourteenMinutesMs))
            assertEquals("45 мин.", formatQueueDuration(fortyFiveMinutesMs))
            assertEquals("30 с", formatQueueDuration(thirtySecondsMs))
            assertEquals("Осталось 2 ч. 14 мин.", str("queue_time_left", formatQueueDuration(twoHoursFourteenMinutesMs)))
            assertEquals("Осталось 45 мин.", str("queue_time_left", formatQueueDuration(fortyFiveMinutesMs)))

            // German
            Strings.appLanguage = "de"
            assertEquals("2 Std. 14 Min.", formatQueueDuration(twoHoursFourteenMinutesMs))
            assertEquals("45 Min.", formatQueueDuration(fortyFiveMinutesMs))
            assertEquals("30 Sek.", formatQueueDuration(thirtySecondsMs))
            assertEquals("Noch 2 Std. 14 Min.", str("queue_time_left", formatQueueDuration(twoHoursFourteenMinutesMs)))
            assertEquals("Noch 45 Min.", str("queue_time_left", formatQueueDuration(fortyFiveMinutesMs)))

            // Hungarian
            Strings.appLanguage = "hu"
            assertEquals("2ó 14p", formatQueueDuration(twoHoursFourteenMinutesMs))
            assertEquals("45 perc", formatQueueDuration(fortyFiveMinutesMs))
            assertEquals("30mp", formatQueueDuration(thirtySecondsMs))
            assertEquals("Még 2ó 14p", str("queue_time_left", formatQueueDuration(twoHoursFourteenMinutesMs)))
            assertEquals("Még 45 perc", str("queue_time_left", formatQueueDuration(fortyFiveMinutesMs)))

            // Vietnamese
            Strings.appLanguage = "vi"
            assertEquals("2 giờ 14 phút", formatQueueDuration(twoHoursFourteenMinutesMs))
            assertEquals("45 phút", formatQueueDuration(fortyFiveMinutesMs))
            assertEquals("30 giây", formatQueueDuration(thirtySecondsMs))
            assertEquals("Còn 2 giờ 14 phút", str("queue_time_left", formatQueueDuration(twoHoursFourteenMinutesMs)))
            assertEquals("Còn 45 phút", str("queue_time_left", formatQueueDuration(fortyFiveMinutesMs)))
        } finally {
            Strings.appLanguage = originalLang
        }
    }

    @Test
    fun testMakeTimeStringLongDurationLocalization() {
        val originalLang = Strings.appLanguage
        try {
            val twoHoursFourteenMinutesMs = (2 * 3600 + 14 * 60) * 1000L

            Strings.appLanguage = "en"
            assertEquals("2h 14min", makeTimeString(twoHoursFourteenMinutesMs))

            Strings.appLanguage = "ru"
            assertEquals("2 ч. 14 мин.", makeTimeString(twoHoursFourteenMinutesMs))

            Strings.appLanguage = "de"
            assertEquals("2 Std. 14 Min.", makeTimeString(twoHoursFourteenMinutesMs))

            // Standard short track (< 1 hour) remains standard mm:ss
            assertEquals("03:45", makeTimeString(225_000L))
        } finally {
            Strings.appLanguage = originalLang
        }
    }
}
