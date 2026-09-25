// KittyTune's presence in the Windows media flyout, lock screen and media keys.
//
// A separate process because System Media Transport Controls are a WinRT API the JVM cannot reach
// without a native shim. It talks to the app over stdin/stdout, one message per line, fields
// separated by tabs, and exits as soon as stdin closes, so it can never outlive the app.
//
//   app -> bridge  UPDATE \t title \t artist \t album \t artworkUrl \t playing(0|1) \t positionMs \t durationMs
//                  QUIT
//   bridge -> app  READY | CMD:PLAY | CMD:PAUSE | CMD:NEXT | CMD:PREV | SEEK:<ms> | ERROR:<message>
//
// Build (from this directory, .NET Framework 4 csc + the Windows 10 SDK metadata):
//   csc /target:winexe /win32icon:..\icons\kittytune.ico /out:WindowsSmtcBridge.exe
//       /r:<Windows.winmd> /r:<System.Runtime.WindowsRuntime.dll> /r:<System.Runtime.dll> WindowsSmtcBridge.cs

using System;
using System.IO;
using System.Reflection;
using System.Text;
using Windows.Media;
using Windows.Media.Playback;
using Windows.Storage.Streams;

[assembly: AssemblyTitle("KittyTune")]
[assembly: AssemblyProduct("KittyTune")]
[assembly: AssemblyCompany("KittyTune")]
[assembly: AssemblyDescription("KittyTune")]
[assembly: AssemblyFileVersion("2.0.0.0")]
[assembly: AssemblyVersion("2.0.0.0")]

namespace KittyTuneSmtc {
    static class Program {
        static readonly object OutputLock = new object();
        static SystemMediaTransportControls smtc;
        static string lastArtworkUrl = "";
        static TextWriter output;

        [MTAThread]
        static int Main() {
            // Explicit streams rather than Console encodings: as a windowless process there is no
            // console whose code page could be set, only the pipes the app handed over.
            var utf8 = new UTF8Encoding(false);
            var input = new StreamReader(Console.OpenStandardInput(), utf8);
            output = new StreamWriter(Console.OpenStandardOutput(), utf8) { AutoFlush = true };

            MediaPlayer player;
            try {
                // A MediaPlayer is the supported way for a desktop process to own an SMTC session.
                // Its own command handling is off: every button is forwarded to the app instead.
                player = new MediaPlayer();
                player.CommandManager.IsEnabled = false;
                smtc = player.SystemMediaTransportControls;
                smtc.IsEnabled = true;
                smtc.IsPlayEnabled = true;
                smtc.IsPauseEnabled = true;
                smtc.IsStopEnabled = true;
                smtc.IsNextEnabled = true;
                smtc.IsPreviousEnabled = true;
                smtc.ButtonPressed += OnButtonPressed;
                smtc.PlaybackPositionChangeRequested += (sender, e) =>
                    Send("SEEK:" + (long)e.RequestedPlaybackPosition.TotalMilliseconds);
            } catch (Exception ex) {
                Send("ERROR:" + ex.Message);
                return 1;
            }

            Send("READY");

            string line;
            while ((line = input.ReadLine()) != null) {
                if (line == "QUIT") break;
                if (!line.StartsWith("UPDATE\t")) continue;
                try {
                    Apply(line.Split('\t'));
                } catch (Exception ex) {
                    Send("ERROR:" + ex.Message);
                }
            }

            GC.KeepAlive(player);
            return 0;
        }

        static void OnButtonPressed(SystemMediaTransportControls sender, SystemMediaTransportControlsButtonPressedEventArgs e) {
            switch (e.Button) {
                case SystemMediaTransportControlsButton.Play: Send("CMD:PLAY"); break;
                case SystemMediaTransportControlsButton.Pause:
                case SystemMediaTransportControlsButton.Stop: Send("CMD:PAUSE"); break;
                case SystemMediaTransportControlsButton.Next: Send("CMD:NEXT"); break;
                case SystemMediaTransportControlsButton.Previous: Send("CMD:PREV"); break;
            }
        }

        static void Apply(string[] f) {
            if (f.Length < 8) return;
            string title = f[1], artist = f[2], album = f[3], artworkUrl = f[4];
            bool isPlaying = f[5] == "1";
            long positionMs = ParseLong(f[6]);
            long durationMs = ParseLong(f[7]);

            var updater = smtc.DisplayUpdater;
            updater.Type = MediaPlaybackType.Music;
            updater.MusicProperties.Title = title;
            updater.MusicProperties.Artist = artist;
            updater.MusicProperties.AlbumTitle = album;
            // Only on change: every assignment makes Windows fetch the image again.
            if (artworkUrl != lastArtworkUrl) {
                lastArtworkUrl = artworkUrl;
                Uri uri;
                updater.Thumbnail = Uri.TryCreate(artworkUrl, UriKind.Absolute, out uri)
                    ? RandomAccessStreamReference.CreateFromUri(uri)
                    : null;
            }
            updater.Update();

            var timeline = new SystemMediaTransportControlsTimelineProperties();
            if (durationMs > 0) {
                var end = TimeSpan.FromMilliseconds(durationMs);
                timeline.StartTime = TimeSpan.Zero;
                timeline.MinSeekTime = TimeSpan.Zero;
                timeline.EndTime = end;
                timeline.MaxSeekTime = end;
                timeline.Position = TimeSpan.FromMilliseconds(Math.Min(Math.Max(positionMs, 0), durationMs));
            }
            smtc.UpdateTimelineProperties(timeline);

            smtc.PlaybackStatus = isPlaying ? MediaPlaybackStatus.Playing : MediaPlaybackStatus.Paused;
        }

        static long ParseLong(string s) {
            long v;
            return long.TryParse(s, out v) ? v : 0;
        }

        static void Send(string message) {
            lock (OutputLock) {
                output.WriteLine(message);
            }
        }
    }
}
