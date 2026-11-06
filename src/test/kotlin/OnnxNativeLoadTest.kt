import com.alananasss.kittytune.audio.ai.OnnxHelper
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import java.net.URLClassLoader

class OnnxNativeLoadTest {

    @Test
    fun disabling_telemetry_does_not_initialize_native_runtime() {
        val urls = listOf(
            OnnxHelper::class.java,
            ai.onnxruntime.OrtEnvironment::class.java,
            com.sun.jna.Native::class.java,
            kotlin.Unit::class.java,
        ).map { it.protectionDomain.codeSource.location }.distinct().toTypedArray()
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            val helper = Class.forName(OnnxHelper::class.java.name, true, loader)
            val instance = helper.getField("INSTANCE").get(null)
            helper.getMethod("disableNativeTelemetry").invoke(instance)
            val loaded = helper.getDeclaredField("nativeLoaded").apply { isAccessible = true }
            val environment = helper.getDeclaredField("sharedEnv").apply { isAccessible = true }
            assertFalse(loaded.getBoolean(null), "Telemetry setup must not load ONNX")
            assertNull(environment.get(null), "Telemetry setup must not create an ONNX environment")
        }
    }

    @Test
    fun bundled_onnx_runtime_environment_initializes() {
        OnnxHelper.ensureNativeLibrariesLoaded()
        assertNotNull(OnnxHelper.getOrInitEnvironment())
    }
}
