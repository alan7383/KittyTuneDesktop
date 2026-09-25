import com.alananasss.kittytune.audio.VolumeCurve
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCurveTest {

    @Test
    fun halfWayIsAboutMinusEighteenDecibels() {
        val db = 20 * log10(VolumeCurve.sliderToAmplitude(0.5f))
        assertEquals(-18.06f, db, 0.1f)
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
}
