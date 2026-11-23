import com.alananasss.kittytune.core.*
import org.junit.Assert.*
import org.junit.Test

class PerformanceSettingsTest {
    private fun options(os: String = "Windows 11", values: Map<String, String> = emptyMap()) =
        PerformanceSettings.read(os) { values[it] }

    @Test fun defaultsPreserveExistingBudgetsAndRendering() {
        assertEquals(PerformanceOptions(), options())
        val props = PerformanceSettings.startupProperties(options(), "Windows 11")
        assertFalse(props.containsKey("skiko.renderApi"))
        assertFalse(props.containsKey("skiko.gpu.priority"))
        assertEquals("true", props["skiko.vsync.enabled"])
        assertEquals("67108864", props["skiko.gpu.resourceCacheLimit"])
    }

    @Test fun windowsDiscretePreferenceReachesBothSkikoProperties() {
        val props = PerformanceSettings.startupProperties(options(values = mapOf(
            PerformanceSettings.GPU to "DISCRETE", PerformanceSettings.RENDERER to "DIRECT3D")), "Windows 11")
        assertEquals("DIRECT3D", props["skiko.renderApi"])
        assertEquals("discrete", props["skiko.gpu.priority"])
        assertEquals("discrete", props["skiko.directx.gpu.priority"])
    }

    @Test fun openGlDoesNotPretendToSelectAnAdapter() {
        val selected = options(values = mapOf(PerformanceSettings.GPU to "DISCRETE", PerformanceSettings.RENDERER to "OPENGL"))
        assertFalse(PerformanceSettings.gpuPreferenceSupported(selected.renderer, "Windows 11"))
        assertFalse(PerformanceSettings.startupProperties(selected, "Windows 11").containsKey("skiko.gpu.priority"))
    }

    @Test fun unsupportedOrCorruptImportedSettingsFallBackSafely() {
        val values = mapOf(PerformanceSettings.RENDERER to "DIRECT3D", PerformanceSettings.GPU to "bad",
            PerformanceSettings.VSYNC to "broken", PerformanceSettings.IMAGE_CACHE to "-1",
            PerformanceSettings.GPU_CACHE to "2147483647", PerformanceSettings.DISK_CACHE to "0")
        assertEquals(PerformanceOptions(), options("Linux", values))
        assertEquals(PerformanceOptions(), options("Mac OS X", values))
    }

    @Test fun metalAndLinuxExposeOnlySupportedBackends() {
        assertEquals(listOf(PerformanceRenderer.AUTO, PerformanceRenderer.METAL), PerformanceSettings.renderers("Mac OS X"))
        assertFalse(PerformanceSettings.renderers("Linux").contains(PerformanceRenderer.DIRECT3D))
        val selected = options("Mac OS X", mapOf(PerformanceSettings.RENDERER to "METAL", PerformanceSettings.GPU to "INTEGRATED"))
        assertEquals("integrated", PerformanceSettings.startupProperties(selected, "Mac OS X")["skiko.metal.gpu.priority"])
    }

    @Test fun validCacheBudgetsAndVsyncAreApplied() {
        val selected = options(values = mapOf(PerformanceSettings.IMAGE_CACHE to "16", PerformanceSettings.GPU_CACHE to "128",
            PerformanceSettings.DISK_CACHE to "512", PerformanceSettings.VSYNC to "false"))
        assertEquals(16, selected.imageCacheMiB)
        assertEquals(512, selected.diskCacheMiB)
        val props = PerformanceSettings.startupProperties(selected, "Windows 11")
        assertEquals("134217728", props["skiko.gpu.resourceCacheLimit"])
        assertEquals("false", props["skiko.vsync.enabled"])
    }

    @Test fun preferencesApplyOnlyAtStartupAndResetPreservesOtherSettings() {
        val original = Prefs.snapshot()
        val properties = System.getProperties().clone() as java.util.Properties
        try {
            PerformanceSettings.reset()
            PerformanceSettings.applyAtStartup()
            Prefs.putString("performance_test_unrelated", "keep")
            Prefs.putInt(PerformanceSettings.IMAGE_CACHE, 16)
            Prefs.putBoolean(PerformanceSettings.VSYNC, false)
            assertEquals(32, PerformanceSettings.active.imageCacheMiB)
            assertEquals(16, PerformanceSettings.read().imageCacheMiB)
            PerformanceSettings.applyAtStartup()
            assertEquals(16, PerformanceSettings.active.imageCacheMiB)
            assertEquals("false", System.getProperty("skiko.vsync.enabled"))
            PerformanceSettings.reset()
            assertEquals(PerformanceOptions(), PerformanceSettings.read())
            assertEquals("keep", Prefs.getString("performance_test_unrelated", null))
        } finally {
            Prefs.restore(original)
            PerformanceSettings.applyAtStartup()
            System.setProperties(properties)
            Prefs.flush()
        }
    }
}
