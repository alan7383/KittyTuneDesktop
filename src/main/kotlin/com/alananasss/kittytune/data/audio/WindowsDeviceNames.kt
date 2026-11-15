package com.alananasss.kittytune.data.audio

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.SetupApi
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

internal object WindowsDeviceNames {
    private interface DeviceStatus : StdCallLibrary {
        fun CM_Get_DevNode_Status(status: IntByReference, problem: IntByReference, instance: Int, flags: Int): Int
    }

    private val setup by lazy { Native.load("setupapi", SetupApi::class.java, W32APIOptions.UNICODE_OPTIONS) }
    private val config by lazy { Native.load("cfgmgr32", DeviceStatus::class.java) }

    fun audioEndpoints(): List<String> = names("{C166523C-FE0C-4A94-A586-F1A80CFBBF3E}")
    fun bluetoothDevices(): List<String> = names("{E0CBF06C-CD8B-4647-BB8A-263B43F0F974}")

    private fun names(deviceClass: String): List<String> = runCatching {
        val devices = setup.SetupDiGetClassDevs(GUID(deviceClass), null, null, SetupApi.DIGCF_PRESENT)
        if (devices == null || devices == WinBase.INVALID_HANDLE_VALUE) return emptyList()
        try {
            Memory(65536).use { buffer ->
                buildList {
                    var index = 0
                    while (true) {
                        val info = SetupApi.SP_DEVINFO_DATA()
                        if (!setup.SetupDiEnumDeviceInfo(devices, index++, info)) {
                            val error = Native.getLastError()
                            if (error != WinError.ERROR_NO_MORE_ITEMS) throw Win32Exception(error)
                            break
                        }
                        val status = IntByReference()
                        val problem = IntByReference()
                        if (config.CM_Get_DevNode_Status(status, problem, info.DevInst, 0) != 0 || problem.value != 0) continue
                        // FriendlyName is optional; DeviceDesc is the same fallback used by PnP.
                        for (property in intArrayOf(12, SetupApi.SPDRP_DEVICEDESC)) {
                            if (setup.SetupDiGetDeviceRegistryProperty(
                                    devices, info, property, null, buffer, buffer.size().toInt(), null,
                                )) {
                                buffer.getWideString(0).trim().takeIf { it.isNotEmpty() }?.let(::add)
                                break
                            }
                        }
                    }
                }
            }
        } finally {
            setup.SetupDiDestroyDeviceInfoList(devices)
        }
    }.getOrDefault(emptyList())
}
