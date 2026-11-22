package com.alananasss.kittytune.audio.ai.karaoke

import com.alananasss.kittytune.audio.ai.GpuDevice
import java.io.File

internal data class OnnxSeparationRequest(val model: File, val useGpu: Boolean, val selectedGpuId: String?)

internal fun onnxSeparationRequest(
    model: File?, forceCpu: Boolean, useGpu: Boolean, selectedGpuId: String?
): OnnxSeparationRequest {
    check(model != null && model.isFile && model.extension.equals("onnx", ignoreCase = true)) {
        "An ONNX model is required for CPU separation. Import one in Karaoke settings."
    }
    return OnnxSeparationRequest(
        model, useGpu && !forceCpu && selectedGpuId != GpuDevice.ID_CPU,
        if (forceCpu) GpuDevice.ID_CPU else selectedGpuId
    )
}
