using System;
using System.IO;
using System.Reflection;
using System.Text;
using Windows.Foundation;
using Windows.Media;
using Windows.Media.Playback;
using Windows.Storage.Streams;

[assembly: AssemblyTitle("KittyTune")]
[assembly: AssemblyProduct("KittyTune")]
[assembly: AssemblyCompany("KittyTune")]
[assembly: AssemblyDescription("KittyTune Desktop Music Player")]
[assembly: AssemblyFileVersion("1.0.0.0")]
[assembly: AssemblyVersion("1.0.0.0")]

namespace KittyTuneSmtc {
    class Program {
        static MediaPlayer player;
        static SystemMediaTransportControls smtc;

        [MTAThread]
        static void Main(string[] args) {
            Console.OutputEncoding = Encoding.UTF8;
            Console.InputEncoding = Encoding.UTF8;
            Console.Title = "KittyTune";

            try {
                player = new MediaPlayer();
                player.CommandManager.IsEnabled = false;
                smtc = player.SystemMediaTransportControls;
                smtc.IsEnabled = true;
                smtc.IsPlayEnabled = true;
                smtc.IsPauseEnabled = true;
                smtc.IsNextEnabled = true;
                smtc.IsPreviousEnabled = true;

                smtc.ButtonPressed += (sender, e) => {
                    switch (e.Button) {
                        case SystemMediaTransportControlsButton.Play:
                            smtc.PlaybackStatus = MediaPlaybackStatus.Playing;
                            Console.WriteLine("CMD:PLAY");
                            break;
                        case SystemMediaTransportControlsButton.Pause:
                            smtc.PlaybackStatus = MediaPlaybackStatus.Paused;
                            Console.WriteLine("CMD:PAUSE");
                            break;
                        case SystemMediaTransportControlsButton.Next:
                            Console.WriteLine("CMD:NEXT");
                            break;
                        case SystemMediaTransportControlsButton.Previous:
                            Console.WriteLine("CMD:PREV");
                            break;
                    }
                };

                Console.WriteLine("READY");

                string line;
                while ((line = Console.ReadLine()) != null) {
                    if (line.StartsWith("UPDATE|")) {
                        var parts = line.Split('|');
                        if (parts.Length >= 5) {
                            string title = parts[1];
                            string artist = parts[2];
                            string artUrl = parts[3];
                            bool isPlaying = parts[4] == "1";

                            var updater = smtc.DisplayUpdater;
                            updater.Type = MediaPlaybackType.Music;
                            try {
                                updater.AppMediaId = "KittyTune";
                            } catch {}
                            updater.MusicProperties.Title = title;
                            updater.MusicProperties.Artist = artist;
                            if (!string.IsNullOrEmpty(artUrl)) {
                                try {
                                    updater.Thumbnail = RandomAccessStreamReference.CreateFromUri(new Uri(artUrl));
                                } catch {}
                            }
                            updater.Update();

                            smtc.PlaybackStatus = isPlaying ? MediaPlaybackStatus.Playing : MediaPlaybackStatus.Paused;
                        }
                    } else if (line == "QUIT") {
                        break;
                    }
                }
            } catch (Exception ex) {
                Console.WriteLine("ERROR:" + ex.Message);
            }
        }
    }
}
