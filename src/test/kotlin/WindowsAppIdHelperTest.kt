import com.alananasss.kittytune.core.WindowsAppIdHelper
import org.junit.Test
import kotlin.test.assertEquals

class WindowsAppIdHelperTest {

    @Test
    fun testWindowsAppIdHelperAppliesWithoutError() {
        WindowsAppIdHelper.apply()
        assertEquals("KittyTune.KittyTune", WindowsAppIdHelper.APP_ID)
    }
}
