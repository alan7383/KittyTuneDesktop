import QtQuick
import Quickshell
import Quickshell.Services.Mpris

ShellRoot {
    id: root
    property var player: Mpris.players.values.find(p => p.dbusName === Quickshell.env("KITTYTUNE_TEST_BUS")) ?? null
    property var desktopApplications: DesktopEntries.applications
    property var originalId
    property Image cover: Image { source: root.step >= 6 && root.player?.trackArtUrl?.startsWith("file:") ? (root.player?.trackArtUrl ?? "") : "" }
    property int step: 0
    property int attempts: 0

    function check(ok, message) {
        if (!ok) throw new Error(message)
    }

    Timer {
        interval: 250
        running: true
        repeat: true
        onTriggered: {
            try {
                if (!root.player || (!root.player.trackTitle && root.step < 9)) {
                    if (++root.attempts > 40) throw new Error("KittyTune was not discovered")
                    return
                }
                const p = root.player
                switch (root.step++) {
                case 0:
                    root.originalId = p.uniqueId
                    root.check(p.identity === "KittyTune", "identity")
                    root.check(p.trackTitle === "Song" && p.trackArtist === "Alice", "track metadata: " + p.trackTitle + " / " + p.trackArtist)
                    root.check(p.trackArtUrl.indexOf("sndcdn.com") !== -1, "cover URL")
                    root.check(p.lengthSupported && p.length === 200, "length in seconds")
                    root.check(p.positionSupported && Math.abs(p.position - 10) < 0.1, "position in seconds")
                    root.check(p.canControl && p.canTogglePlaying && p.canGoNext && p.canGoPrevious && p.canSeek && p.canRaise, "controls")
                    root.check(p.volumeSupported && p.shuffleSupported && p.loopSupported, "writable properties")
                    p.position = 42
                    break
                case 1:
                    root.check(Math.abs(p.position - 42) < 0.1, "SetPosition / Seeked")
                    p.volume = 0.42
                    p.shuffle = true
                    p.loopState = MprisLoopState.Playlist
                    p.rate = 1.5
                    break
                case 2:
                    root.check(Math.abs(p.volume - 0.42) < 0.001 && p.shuffle && p.loopState === MprisLoopState.Playlist && p.rate === 1.5, "property echoes")
                    p.togglePlaying()
                    break
                case 3:
                    root.check(p.isPlaying && p.position > 42, "play and extrapolation")
                    p.pause()
                    break
                case 4:
                    root.check(!p.isPlaying && p.playbackState === MprisPlaybackState.Paused, "pause")
                    p.next()
                    p.previous()
                    p.raise()
                    p.stop()
                    break
                case 5:
                    root.check(p.playbackState === MprisPlaybackState.Stopped && p.position < 0.1, "stop resets position")
                    console.log("KITTYTUNE_REQUEST_TRACK")
                    break
                case 6:
                    if (p.trackTitle !== "Second Song" || root.cover.status !== Image.Ready) { root.step--; return }
                    root.check(p.uniqueId !== root.originalId, "track identity changes")
                    root.check(p.length === 120 && p.position < 0.1, "new track duration and position")
                    root.check(p.trackArtUrl.startsWith("file:"), "local artwork URI")
                    console.log("KITTYTUNE_REQUEST_UNTIMED")
                    break
                case 7:
                    if (p.trackTitle !== "Untimed Song") { root.step--; return }
                    root.check(!p.lengthSupported && p.trackArtUrl.endsWith("/mpris/kittytune.png"), "unknown duration and fallback artwork")
                    console.log("KITTYTUNE_REQUEST_BACKFILL")
                    break
                case 8:
                    if (!p.lengthSupported || p.length !== 90) { root.step--; return }
                    root.check(Math.abs(p.position - 27) < 0.1, "late duration and seek synchronization: " + p.position)
                    console.log("KITTYTUNE_REQUEST_CLEAR")
                    break
                case 9:
                    if (p.trackTitle) { root.step--; return }
                    root.check(p.trackArtUrl.endsWith("/mpris/kittytune.png") && p.playbackState === MprisPlaybackState.Stopped, "unloaded player")
                    console.log("KITTYTUNE_QUICKSHELL_OK")
                    Qt.quit()
                }
            } catch (error) {
                console.error("KITTYTUNE_QUICKSHELL_FAILED: " + error)
                Qt.exit(1)
            }
        }
    }
}
