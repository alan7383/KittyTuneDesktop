package com.alananasss.kittytune.audio.ai.karaoke

import com.alananasss.kittytune.audio.ai.GpuDevice
import java.nio.file.Files
import org.junit.Test
import kotlin.test.*

class KaraokeOnnxFallbackTest {
    @Test fun `native failure forces CPU despite GPU preference`() {
        withModel(".onnx") { model ->
            val request = onnxSeparationRequest(model, true, true, GpuDevice.ID_AUTO)
            assertEquals(model, request.model)
            assertFalse(request.useGpu)
            assertEquals(GpuDevice.ID_CPU, request.selectedGpuId)
        }
    }

    @Test fun `ordinary ONNX execution retains configured GPU preference`() {
        withModel(".onnx") { model ->
            val request = onnxSeparationRequest(model, false, true, GpuDevice.ID_AUTO)
            assertTrue(request.useGpu)
            assertEquals(GpuDevice.ID_AUTO, request.selectedGpuId)
        }
    }

    @Test fun `missing ONNX model fails before opening native runtime`() {
        assertFailsWith<IllegalStateException> {
            onnxSeparationRequest(null, true, true, GpuDevice.ID_AUTO)
        }
    }

    @Test fun `native weights cannot be passed to ONNX runtime`() {
        withModel(".safetensors") { model ->
            assertFailsWith<IllegalStateException> {
                onnxSeparationRequest(model, true, true, GpuDevice.ID_AUTO)
            }
        }
    }

    private fun withModel(suffix: String, test: (java.io.File) -> Unit) {
        val file = Files.createTempFile("karaoke-model-", suffix).toFile()
        try { test(file) } finally { file.delete() }
    }
}
