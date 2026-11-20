package com.alananasss.kittytune.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.SystemTray
import java.awt.TrayIcon
import java.util.concurrent.TimeUnit

internal object TogetherJoinNotification {
    private const val TITLE = "KittyTune Together"

    suspend fun show(osName: String, message: String) {
        when {
            osName.contains("win") -> runCatching {
                SystemTray.getSystemTray().trayIcons
                    .firstOrNull { it.toolTip == "KittyTune" }
                    ?.displayMessage(TITLE, message, TrayIcon.MessageType.NONE)
            }
            osName.contains("mac") -> withContext(Dispatchers.IO) { showOnMac(message) }
            osName.contains("linux") || osName.contains("nix") -> withContext(Dispatchers.IO) {
                showOnLinux(message)
            }
        }
    }

    private fun showOnMac(message: String) {
        runCatching {
            // Pass the member name as an argument so it is never interpreted as AppleScript.
            val process = ProcessBuilder(
                "/usr/bin/osascript",
                "-e", "on run argv",
                "-e", "display notification (item 1 of argv) with title (item 2 of argv)",
                "-e", "end run",
                "--", message, TITLE,
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    private fun showOnLinux(message: String) {
        runCatching {
            val service = "org.freedesktop.Notifications"
            DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
                val notifications = connection.getRemoteObject(
                    service, "/org/freedesktop/Notifications", DesktopNotifications::class.java,
                )
                notifications.Notify(
                    TITLE, UInt32(0), "", message, "", emptyList(),
                    mapOf("suppress-sound" to Variant(true)), -1,
                )
            }
        }
    }
}

@DBusInterfaceName("org.freedesktop.Notifications")
internal interface DesktopNotifications : DBusInterface {
    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32
}
