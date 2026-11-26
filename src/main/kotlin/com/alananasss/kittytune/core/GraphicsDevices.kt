package com.alananasss.kittytune.core

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.Window

internal data class GraphicsDevice(val id: String, val name: String)

internal data class GraphicsDevices(
    val devices: List<GraphicsDevice>,
    val automatic: String?,
    val powerSaving: String?,
    val highPerformance: String?,
) {
    fun preference(id: String): PerformanceGpu? {
        if (devices.count { it.id == id } != 1) return null
        return when (id) {
            highPerformance -> PerformanceGpu.DISCRETE
            powerSaving -> PerformanceGpu.INTEGRATED
            automatic -> PerformanceGpu.AUTO
            else -> null
        }
    }
}

internal object WindowsGraphicsDevices {
    private interface Dxgi : StdCallLibrary {
        fun CreateDXGIFactory1(iid: Guid.GUID, factory: PointerByReference): Int
    }

    private fun call(obj: Pointer, slot: Int, vararg arguments: Any?): Int =
        Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
            .invokeInt(arrayOf(obj, *arguments))

    fun read(): GraphicsDevices {
        check(System.getProperty("os.name").startsWith("Windows", true))
        val api = Native.load("dxgi", Dxgi::class.java)
        val output = PointerByReference()
        check(api.CreateDXGIFactory1(Guid.GUID("{C1B6694F-FF09-44A9-B03C-77900A0A1D17}"), output) >= 0) {
            "DXGI adapter enumeration is unavailable"
        }
        val factory = checkNotNull(output.value)
        try {
            fun enumerate(preference: Int): List<GraphicsDevice> = buildList {
                for (index in 0 until 64) {
                    val adapterOutput = PointerByReference()
                    val result = call(factory, 29, index, preference,
                        Guid.GUID("{29038F61-3839-4626-91FD-086879011A05}"), adapterOutput)
                    if (result == 0x887A0002.toInt()) break
                    check(result >= 0) { "DXGI adapter enumeration failed: $result" }
                    val adapter = checkNotNull(adapterOutput.value)
                    try {
                        // DXGI_ADAPTER_DESC1: WCHAR[128], four UINTs, three SIZE_Ts, LUID, flags.
                        Memory(312).use { description ->
                            description.clear()
                            check(call(adapter, 10, description) >= 0)
                            val flags = description.getInt((272 + 3 * Native.POINTER_SIZE + 8).toLong())
                            if (flags and 2 == 0) {
                                val name = description.getWideString(0).trim()
                                if (name.isNotBlank()) {
                                    val pci = (256L..268L step 4).joinToString(":") {
                                        Integer.toUnsignedString(description.getInt(it), 16)
                                    }
                                    add(GraphicsDevice("$pci:$name", name))
                                }
                            }
                        }
                    } finally { call(adapter, 2) }
                }
            }
            val all = enumerate(0)
            return GraphicsDevices(all, all.firstOrNull()?.id, enumerate(1).firstOrNull()?.id,
                enumerate(2).firstOrNull()?.id)
        } finally { call(factory, 2) }
    }
}

internal data class GraphicsInUse(val name: String?, val software: Boolean) {
    fun matches(requested: String): Boolean {
        fun normalized(value: String) = value.substringBefore("/PCIe", value).replace("(R)", "", true)
            .replace("(TM)", "", true).trim().lowercase(java.util.Locale.ROOT).removePrefix("nvidia ").replace(Regex("\\s+"), " ")
        return name != null && normalized(name) == normalized(requested)
    }

    companion object {
        private val modelLine = Regex("(?m)^(?:Model|Video card|Device name|Device|GPU):[ \\t]*([^\\r\\n]*)")
        private val softwareApi = Regex("(?m)^GraphicsApi:[ \\t]*(?:SOFTWARE_FAST|SOFTWARE_COMPAT)[ \\t]*\\r?$")

        fun parse(info: String): GraphicsInUse {
            val name = modelLine.find(info)?.groupValues?.get(1)?.trim()
                ?.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
            return GraphicsInUse(name, softwareApi.containsMatchIn(info))
        }

        fun current(): GraphicsInUse {
            fun layer(component: Component): SkiaLayer? = when (component) {
                is SkiaLayer -> component
                is Container -> component.components.firstNotNullOfOrNull(::layer)
                else -> null
            }
            val window = Window.getWindows().firstOrNull { it is Frame && it.title == "KittyTune" && it.isVisible }
            return window?.let(::layer)?.let { parse(it.renderInfo) } ?: GraphicsInUse(null, false)
        }
    }
}
