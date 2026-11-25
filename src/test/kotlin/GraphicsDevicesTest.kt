import com.alananasss.kittytune.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class GraphicsDevicesTest {
    private val intel = GraphicsDevice("intel-pci", "Intel(R) HD Graphics 530")
    private val nvidia = GraphicsDevice("nvidia-pci", "NVIDIA GeForce GTX 960M")
    private val inventory = GraphicsDevices(listOf(intel, nvidia), intel.id, intel.id, nvidia.id)

    @Test fun namesMapToActualOsPreferenceOrder() {
        assertEquals(PerformanceGpu.INTEGRATED, inventory.preference(intel.id))
        assertEquals(PerformanceGpu.DISCRETE, inventory.preference(nvidia.id))
        assertNull(inventory.preference("removed-card"))
    }

    @Test fun ambiguousOrUnaddressableAdaptersAreNotPresentedAsSelectable() {
        val other = GraphicsDevice("other", "Another NVIDIA card")
        assertNull(inventory.copy(devices = inventory.devices + other).preference(other.id))
        assertNull(inventory.copy(devices = listOf(nvidia, nvidia)).preference(nvidia.id))
    }

    @Test fun hardwareAndSoftwareStatusAreReadFromTheRenderer() {
        val actual = GraphicsInUse.parse("GraphicsApi: OPENGL\nModel: NVIDIA GeForce GTX 960M/PCIe/SSE2\n")
        assertTrue(actual.matches(nvidia.name))
        assertTrue(GraphicsInUse.parse("Model: GeForce GTX 960M/PCIe/SSE2\n").matches(nvidia.name))
        assertFalse(actual.matches(intel.name))
        assertTrue(GraphicsInUse.parse("GraphicsApi: SOFTWARE_COMPAT\n").software)
        assertNull(GraphicsInUse.parse("GraphicsApi: OPENGL\n").name)
        assertTrue(GraphicsInUse.parse("Video card: Intel(R) HD Graphics 530\n").matches("intel hd graphics 530"))
    }

    @Test fun selectionPersistsTheNamedDeviceAndResetClearsIt() {
        val before = Prefs.snapshot()
        try {
            PerformanceSettings.selectGpu(nvidia, inventory)
            val saved = PerformanceSettings.read()
            assertEquals(nvidia.id, saved.gpuId)
            assertEquals(nvidia.name, saved.gpuName)
            assertEquals(PerformanceGpu.DISCRETE, saved.gpu)
            PerformanceSettings.selectGpu(null, inventory)
            assertNull(PerformanceSettings.read().gpuId)
            PerformanceSettings.selectGpu(intel, inventory)
            PerformanceSettings.reset()
            assertNull(PerformanceSettings.read().gpuName)
        } finally { Prefs.restore(before); Prefs.flush() }
    }

    @Test fun windowsInventoryReleasesAndReopensItsNativeHandles() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", true))
        val first = WindowsGraphicsDevices.read()
        val second = WindowsGraphicsDevices.read()
        assertEquals(first.devices, second.devices)
        assertTrue(first.devices.all { it.name.isNotBlank() && it.id.isNotBlank() })
        val expected = System.getenv("KITTYTUNE_TEST_EXPECTED_GPU")
        if (!expected.isNullOrBlank()) assertTrue("Expected GPU not detected: $expected", first.devices.any { it.name.contains(expected, true) })
        println("Detected GPUs: " + first.devices.joinToString { it.name })
    }
}
