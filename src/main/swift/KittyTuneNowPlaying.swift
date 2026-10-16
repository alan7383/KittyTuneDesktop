import AppKit
import MediaPlayer

let app = NSApplication.shared
app.setActivationPolicy(.accessory)

let center = MPNowPlayingInfoCenter.default()

var lastArtworkURL = ""
var currentArtwork: MPMediaItemArtwork?

DispatchQueue.global().async {
    while let line = readLine() {
        if line == "QUIT" { break }

        let p = line.components(separatedBy: "\t")
        guard p.count >= 8, p[0] == "UPDATE" else {
            continue
        }

        let title = p[1]
        let artist = p[2]
        let album = p[3]
        let playing = p[5] == "1"
        let position = (Double(p[6]) ?? 0) / 1000
        let duration = (Double(p[7]) ?? 0) / 1000
        let artworkURL = p[4]

        DispatchQueue.main.async {
            if artworkURL != lastArtworkURL {
                lastArtworkURL = artworkURL
                currentArtwork = nil

                if let url = URL(string: artworkURL),
                   url.scheme?.lowercased() == "https" {
                    URLSession.shared.dataTask(with: url) { data, _, _ in
                        guard let data, data.count < 10000000 else { return }

                        DispatchQueue.main.async {
                            guard lastArtworkURL == artworkURL,
                                  let image = NSImage(data: data),
                                  image.size.width > 0,
                                  image.size.height > 0 else { return }

                            let artwork = MPMediaItemArtwork(
                                boundsSize: image.size
                            ) { _ in image }

                            currentArtwork = artwork

                            var info = center.nowPlayingInfo ?? [:]
                            info[MPMediaItemPropertyArtwork] = artwork
                            center.nowPlayingInfo = info
                        }
                    }.resume()
                }
            }

            var info: [String: Any] = [
                MPNowPlayingInfoPropertyMediaType: NSNumber(value: MPNowPlayingInfoMediaType.audio.rawValue),
                MPMediaItemPropertyTitle: title,
                MPMediaItemPropertyArtist: artist,
                MPMediaItemPropertyAlbumTitle: album,
                MPMediaItemPropertyPlaybackDuration: duration,
                MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
                MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0
            ]
            if let currentArtwork {
                info[MPMediaItemPropertyArtwork] = currentArtwork
            }
            center.nowPlayingInfo = info

            center.playbackState = playing ? .playing : .paused
        }
    }

    DispatchQueue.main.async {
        center.nowPlayingInfo = nil
        app.terminate(nil)
    }
}


let commands = MPRemoteCommandCenter.shared()

commands.playCommand.isEnabled = true
commands.pauseCommand.isEnabled = true
commands.nextTrackCommand.isEnabled = true
commands.previousTrackCommand.isEnabled = true

commands.playCommand.addTarget { _ in print("CMD:PLAY"); fflush(stdout); return .success }
commands.pauseCommand.addTarget { _ in print("CMD:PAUSE"); fflush(stdout); return .success }
commands.nextTrackCommand.addTarget { _ in print("CMD:NEXT"); fflush(stdout); return .success }
commands.previousTrackCommand.addTarget { _ in print("CMD:PREV"); fflush(stdout); return .success }


commands.changePlaybackPositionCommand.isEnabled = true
commands.changePlaybackPositionCommand.addTarget { event in
    guard let e = event as? MPChangePlaybackPositionCommandEvent else {
        return .commandFailed
    }
    print("SEEK:\(Int(e.positionTime * 1000))")
    fflush(stdout)
    return .success
}


commands.togglePlayPauseCommand.isEnabled = true

commands.togglePlayPauseCommand.addTarget { _ in
    if center.playbackState == .playing {
        print("CMD:PAUSE")
    } else {
        print("CMD:PLAY")
    }
    fflush(stdout)
    return .success
}

app.run()
