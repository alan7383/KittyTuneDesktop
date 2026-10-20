import com.alananasss.kittytune.audio.ai.OnnxHelper
import org.junit.Test
import kotlin.test.assertNotNull

class OnnxNativeLoadTest {

    @Test
    fun bundled_onnx_runtime_environment_initializes() {
        OnnxHelper.ensureNativeLibrariesLoaded()
        assertNotNull(OnnxHelper.getOrInitEnvironment())
    }
}
