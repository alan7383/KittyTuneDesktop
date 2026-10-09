package com.alananasss.kittytune.util

import java.util.Locale

/**
 * The country the computer is set to, as a two-letter code ("FI"), or null when it cannot be told.
 *
 * The interface language is not the country: a Russian interface in Finland is a listener in Finland. The language also
 * cannot be asked for it, since the app sets the JVM's default locale to its own language, and a JVM started by Gradle is
 * handed `-Duser.country` from the interface language as well. So on Windows the region the user chose in Settings is
 * read from where it is kept (`HKCU\Control Panel\International\Geo\Name`); elsewhere, and if that fails, the region of
 * the number and date formats the JVM started with.
 */
object DeviceRegion {

    val code: String? by lazy {
        (windowsRegion()
            ?: System.getProperty("user.country.format")?.takeIf { it.length == 2 }
            ?: Locale.getDefault(Locale.Category.FORMAT).country.takeIf { it.length == 2 })?.uppercase()
    }

    private fun windowsRegion(): String? {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return null
        return runCatching {
            com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Control Panel\\International\\Geo",
                "Name",
            )
        }.getOrNull()?.takeIf { it.length == 2 }
    }
}
