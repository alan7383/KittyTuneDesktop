import com.alananasss.kittytune.audio.VolumeCurve
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeCurveTest {

    private fun db(position: Float) = 20 * log10(VolumeCurve.sliderToAmplitude(position))

    @Test
    fun theSliderMovesInEqualDecibelSteps() {
        assertEquals(-20f, db(0.5f), 0.01f)
        assertEquals(-32f, db(0.2f), 0.01f)
        assertEquals(db(0.3f) - db(0.2f), db(0.9f) - db(0.8f), 0.01f)
    }

    /** The bottom of the slider was inaudible with the cubic curve: 20 % was -42 dB, 1 % below -80 dB. */
    @Test
    fun theLowestStepsStayAudible() {
        assertTrue(db(0.01f) > -41f)
        assertTrue(db(0.2f) > -35f)
    }

    @Test
    fun endsStayPut() {
        assertEquals(0f, VolumeCurve.sliderToAmplitude(0f), 0f)
        assertEquals(1f, VolumeCurve.sliderToAmplitude(1f), 0f)
    }

    /** The one-off migration must leave a saved level exactly as loud as it was. */
    @Test
    fun migratingASavedAmplitudeKeepsItsLoudness() {
        for (saved in listOf(0.04f, 0.25f, 0.8f)) {
            val slider = VolumeCurve.amplitudeToSlider(saved)
            assertEquals(saved, VolumeCurve.sliderToAmplitude(slider), 1e-5f)
        }
    }

    @Test
    fun aVeryQuietSavedLevelIsNotTurnedIntoMute() {
        assertEquals(0.01f, VolumeCurve.amplitudeToSlider(0.0001f), 0f)
        assertEquals(0f, VolumeCurve.amplitudeToSlider(0f), 0f)
    }
}
