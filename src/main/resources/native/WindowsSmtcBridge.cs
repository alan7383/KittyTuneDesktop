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
// The app passes its own launcher path as argv[1]: that is what the Start Menu shortcut must point
// at on a portable install, where no installer creates one.
//
// Build (from this directory, .NET Framework 4 csc + the Windows 10 SDK metadata; CI runs
// scripts/build-smtc-bridge.ps1 so releases always carry a freshly compiled bridge, and the
// checked-in exe is only a local dev fallback):
//   csc /target:winexe /win32icon:..\icons\kittytune.ico /out:WindowsSmtcBridge.exe
//       /r:<Windows.winmd> /r:<System.Runtime.WindowsRuntime.dll> /r:<System.Runtime.dll> WindowsSmtcBridge.cs

using System;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using Windows.Foundation;
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

        // What the flyout shows as the session's owner is not the exe's file name and not its
        // version resource: Windows resolves the session's AppUserModelID to a Start Menu shortcut
        // carrying the same ID, and takes the displayed name and icon from that shortcut. A
        // JVM-spawned exe in a temp dir has no identity Windows can resolve, which is exactly what
        // shows up as "Unknown Application" (issue #66). So: claim an explicit ID for the process,
        // and make sure a Start Menu shortcut carrying it exists. Details in
        // https://learn.microsoft.com/en-us/windows/win32/shell/appids
        const string AppUserModelId = "KittyTune.KittyTune";
        const ushort VtLpwstr = 31; // VT_LPWSTR

        [MTAThread]
        static int Main(string[] args) {
            // Explicit streams rather than Console encodings: as a windowless process there is no
            // console whose code page could be set, only the pipes the app handed over.
            var utf8 = new UTF8Encoding(false);
            var input = new StreamReader(Console.OpenStandardInput(), utf8);
            output = new StreamWriter(Console.OpenStandardOutput(), utf8) { AutoFlush = true };

            ApplyAppIdentity(args.Length > 0 && args[0].Length > 0 ? args[0] : null);

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

        static void ApplyAppIdentity(string mainExe) {
            // Before the SMTC session exists: the flyout resolves the owner's identity when the
            // session appears, not afterwards.
            try {
                SetCurrentProcessExplicitAppUserModelID(AppUserModelId);
            } catch (Exception ex) {
                Send("ERROR:identity:" + ex.Message);
            }
            // Shell link COM objects want an STA, and Main is MTA for the WinRT events.
            Exception failure = null;
            var worker = new Thread(delegate() {
                try { EnsureStartMenuShortcut(mainExe); } catch (Exception ex) { failure = ex; }
            });
            worker.SetApartmentState(ApartmentState.STA);
            worker.Start();
            if (!worker.Join(3000)) {
                Send("ERROR:shortcut:timeout");
            }
            // Cosmetic failure only: the session keeps working, it just stays anonymous.
            if (failure != null) Send("ERROR:shortcut:" + failure.Message);
        }

        /// <summary>
        /// Gives the AppUserModelID something to resolve to. An existing KittyTune shortcut gets the
        /// ID property added in place (and repointed if the portable install moved); if there is
        /// none, one is created pointing at the launcher path the app passed. Dev runs (no launcher)
        /// only patch what exists.
        /// </summary>
        static void EnsureStartMenuShortcut(string mainExe) {
            string[] menuDirs = StartMenuDirs();
            string launcher = null;
            if (mainExe != null && Path.GetFileName(mainExe).Equals("KittyTune.exe", StringComparison.OrdinalIgnoreCase))
                launcher = mainExe;

            // By expected name first, then anything in the Start Menu that actually launches us —
            // the user may have renamed or localised the shortcut.
            foreach (string path in NamedShortcuts(menuDirs)) {
                if (File.Exists(path) && PatchShortcut(path, launcher)) return;
            }
            foreach (string path in AllStartMenuShortcuts(menuDirs)) {
                if (PatchShortcut(path, launcher)) return;
            }

            if (launcher == null) return;
            string created = Path.Combine(menuDirs[0], "KittyTune.lnk");
            Directory.CreateDirectory(menuDirs[0]);
            var fresh = (IShellLinkW)new ShellLinkCoClass();
            fresh.SetPath(launcher);
            fresh.SetDescription("KittyTune");
            // The launcher carries the app icon from jpackage; naming it keeps the flyout's icon
            // right even if that ever changes.
            fresh.SetIconLocation(launcher, 0);
            SetAppUserModelId(fresh);
            ((IPersistFile)fresh).Save(created, false);
        }

        /// <summary>Returns true if this shortcut launches KittyTune and now carries the ID.</summary>
        static bool PatchShortcut(string path, string launcher) {
            try {
                var link = (IShellLinkW)new ShellLinkCoClass();
                var file = (IPersistFile)link;
                file.Load(path, 0 /* STGM_READ */);
                string target = ReadShortcutTarget(link);
                // A same-named .lnk belonging to something else is left alone.
                if (target.IndexOf("KittyTune", StringComparison.OrdinalIgnoreCase) < 0) return false;

                bool alreadyHasId = HasAppUserModelId(link);
                bool needsRepoint = launcher != null && !File.Exists(target);

                if (alreadyHasId && !needsRepoint) return true;

                if (needsRepoint) {
                    // The portable install was moved after the shortcut was written: repoint it.
                    link.SetPath(launcher);
                    link.SetIconLocation(launcher, 0);
                }
                SetAppUserModelId(link);
                file.Save(path, false);
                return true;
            } catch (Exception) {
                return false;
            }
        }

        static bool HasAppUserModelId(IShellLinkW link) {
            try {
                var store = (IPropertyStore)link;
                var key = new PROPERTYKEY {
                    fmtid = new Guid(0x9F4C2855, 0x9F79, 0x4B39, 0xA8, 0xD0, 0xE1, 0xD4, 0x2D, 0xE4, 0xD4, 0x36),
                    pid = 5
                };
                var value = default(PropVariant);
                try {
                    store.GetValue(ref key, out value);
                    if (value.vt == VtLpwstr && value.pointerValue != IntPtr.Zero) {
                        string s = Marshal.PtrToStringUni(value.pointerValue);
                        return string.Equals(s, AppUserModelId, StringComparison.OrdinalIgnoreCase);
                    }
                } finally {
                    PropVariantClear(ref value);
                }
            } catch { }
            return false;
        }

        static string ReadShortcutTarget(IShellLinkW link) {
            var path = new StringBuilder(1024);
            // GetPath wants a WIN32_FIND_DATAW buffer it can write to; 640 bytes covers it.
            IntPtr findData = Marshal.AllocHGlobal(640);
            try {
                link.GetPath(path, path.Capacity, findData, 0);
            } finally {
                Marshal.FreeHGlobal(findData);
            }
            return path.ToString();
        }

        static void SetAppUserModelId(IShellLinkW link) {
            var store = (IPropertyStore)link;
            var key = new PROPERTYKEY {
                fmtid = new Guid(0x9F4C2855, 0x9F79, 0x4B39, 0xA8, 0xD0, 0xE1, 0xD4, 0x2D, 0xE4, 0xD4, 0x36),
                pid = 5
            };
            IntPtr s = Marshal.StringToCoTaskMemUni(AppUserModelId);
            var value = default(PropVariant);
            try {
                value.vt = VtLpwstr;
                value.pointerValue = s;
                store.SetValue(ref key, ref value);
                store.Commit();
            } finally {
                // The documented way to release the value; the store copied what it needs.
                PropVariantClear(ref value);
            }
        }

        static string[] StartMenuDirs() {
            var relative = Path.Combine("Microsoft", "Windows", "Start Menu", "Programs");
            return new[] {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), relative),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), relative)
            };
        }

        static string[] NamedShortcuts(string[] menuDirs) {
            var relativePaths = new[] {
                "KittyTune.lnk",
                "Kitty Tune.lnk",
                Path.Combine("KittyTune", "KittyTune.lnk"),
                Path.Combine("Kitty Tune", "Kitty Tune.lnk")
            };
            var paths = new string[menuDirs.Length * relativePaths.Length];
            int i = 0;
            foreach (string dir in menuDirs)
                foreach (string rel in relativePaths)
                    paths[i++] = Path.Combine(dir, rel);
            return paths;
        }

        static string[] AllStartMenuShortcuts(string[] menuDirs) {
            var found = new System.Collections.Generic.List<string>();
            foreach (string dir in menuDirs) {
                try {
                    if (!Directory.Exists(dir)) continue;
                    found.AddRange(Directory.GetFiles(dir, "*.lnk"));
                    // One level of subfolders: enough to cover a menuGroup if one is ever set,
                    // without walking every shortcut of every app on the machine.
                    foreach (string sub in Directory.GetDirectories(dir)) {
                        try { found.AddRange(Directory.GetFiles(sub, "*.lnk")); } catch (Exception) { }
                    }
                } catch (Exception) { }
            }
            // Put any shortcuts containing "Kitty" or "Tune" first so they are tested first
            found.Sort((a, b) => {
                bool aMatch = a.IndexOf("Kitty", StringComparison.OrdinalIgnoreCase) >= 0 || a.IndexOf("Tune", StringComparison.OrdinalIgnoreCase) >= 0;
                bool bMatch = b.IndexOf("Kitty", StringComparison.OrdinalIgnoreCase) >= 0 || b.IndexOf("Tune", StringComparison.OrdinalIgnoreCase) >= 0;
                if (aMatch && !bMatch) return -1;
                if (!aMatch && bMatch) return 1;
                return 0;
            });
            if (found.Count > 512) found.RemoveRange(512, found.Count - 512);
            return found.ToArray();
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

        // ---- Native interop for the app identity ----

        [DllImport("shell32.dll", PreserveSig = false)]
        static extern void SetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] string appId);

        [DllImport("ole32.dll")]
        static extern int PropVariantClear(ref PropVariant pvar);

        [StructLayout(LayoutKind.Sequential)]
        struct PROPERTYKEY {
            public Guid fmtid;
            public uint pid;
        }

        // PROPVARIANT sized like the native one (8-byte header + 16-byte union) on x86 and x64.
        // Only the VT_LPWSTR member is ever set; the string's memory is managed by hand around
        // each SetValue call because the property store copies what it reads.
        [StructLayout(LayoutKind.Explicit, Size = 24)]
        struct PropVariant {
            [FieldOffset(0)] public ushort vt;
            [FieldOffset(8)] public IntPtr pointerValue;
        }

        [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
        class ShellLinkCoClass { }

        [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IShellLinkW {
            // Every member in vtable order, used or not.
            void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszFile, int cch, IntPtr pfd, uint fFlags);
            void GetIDList(out IntPtr ppidl);
            void SetIDList(IntPtr pidl);
            void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszName, int cch);
            void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string pszName);
            void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszDir, int cch);
            void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string pszDir);
            void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszArgs, int cch);
            void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string pszArgs);
            void GetHotkey(out ushort pwHotkey);
            void SetHotkey(ushort wHotkey);
            void GetShowCmd(out int piShowCmd);
            void SetShowCmd(int iShowCmd);
            void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszIconPath, int cch, out int piIcon);
            void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string pszIconPath, int iIcon);
            void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string pszPathRel, uint dwReserved);
            void Resolve(IntPtr hwnd, uint fFlags);
            void SetPath([MarshalAs(UnmanagedType.LPWStr)] string pszFile);
        }

        [ComImport, Guid("0000010B-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IPersistFile {
            void GetClassID(out Guid pClassID);
            void IsDirty();
            void Load([MarshalAs(UnmanagedType.LPWStr)] string pszFileName, uint dwMode);
            void Save([MarshalAs(UnmanagedType.LPWStr)] string pszFileName, [MarshalAs(UnmanagedType.Bool)] bool fRemember);
            void SaveCompleted([MarshalAs(UnmanagedType.LPWStr)] string pszFileName);
            void GetCurFile([MarshalAs(UnmanagedType.LPWStr)] out string ppszFileName);
        }

        [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IPropertyStore {
            void GetCount(out uint cProps);
            void GetAt(uint iProp, out PROPERTYKEY pkey);
            void GetValue(ref PROPERTYKEY key, out PropVariant pv);
            void SetValue(ref PROPERTYKEY key, ref PropVariant propvar);
            void Commit();
        }
    }
}
