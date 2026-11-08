import QtQuick
import Quickshell
ShellRoot {
    property var applications: DesktopEntries.applications
    FloatingWindow {
        visible: true
        implicitWidth: 288
        implicitHeight: 96
        Row {
            id: icons
            Image { width: 96; height: 96; source: Quickshell.iconPath(Quickshell.env("KITTYTUNE_WINDOW_ID"), true) }
            Image { width: 96; height: 96; source: Quickshell.iconPath(DesktopEntries.byId("kittytune")?.icon ?? "", true) }
            Image { width: 96; height: 96; source: Quickshell.iconPath("kitty", true) }
        }
    }
    Timer {
        interval: 800
        running: true
        onTriggered: {
            if (DesktopEntries.byId("kittytune")?.startupClass !== "kittytune") {
                console.error("Desktop entry does not match WM_CLASS")
                Qt.exit(1)
                return
            }
            icons.grabToImage(function(result) {
                if (!result.saveToFile(Quickshell.env("KITTYTUNE_ICON_CAPTURE"))) { Qt.exit(1); return }
                Qt.quit()
            })
        }
    }
}
