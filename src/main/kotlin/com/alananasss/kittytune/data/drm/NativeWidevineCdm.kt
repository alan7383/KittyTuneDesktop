package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.utils.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File

/**
 * Drives the Widevine CDM already installed on the machine (the library a browser downloaded —
 * `libwidevinecdm.so`, `libwidevinecdm.dylib` or `widevinecdm.dll`) through its public CDM API, and
 * nothing else.
 *
 * This is what makes SoundCloud's `ctr-encrypted-hls` playable on a JVM desktop at all: the CDM signs
 * the license request, holds the content key and decrypts the samples, exactly as it does inside a
 * browser. The key never crosses into Kotlin. On Android the same job is done by `MediaDrm`, which
 * the OS provides; here the only CDM on a desktop is the browser's.
 */
class NativeWidevineCdm private constructor(
    private val native: WidevineBridge,
    private val handle: Pointer,
    override val name: String,
) : WidevineCdm {

    private val lock = Any()
    private var closed = false

    override fun fork(): WidevineCdm = synchronized(Companion) {
        val path = cdmPath ?: throw DrmUnavailableException("the Widevine module is no longer installed")
        val error = ByteArray(ERROR_SIZE)
        val instance = native.ktwv_create(path, error, ERROR_SIZE)
            ?: throw DrmUnavailableException("could not open another Widevine instance: ${cString(error)}")
        NativeWidevineCdm(native, instance, "$name (playback)")
    }

    override fun close() = shutdown()

    override fun openSession(pssh: ByteArray): WidevineLicenseSession = synchronized(lock) {
        check(!closed) { "the Widevine instance was released" }
        native.ktwv_reset_session(handle)
        NativeWidevineSession(native, handle, pssh, lock)
    }

    internal fun shutdown() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { native.ktwv_destroy(handle) }
        }
    }

    /** Raw C API exposed by the native bridge (`libkittytune_widevine.so`, `.dylib` or `.dll`). */
    internal interface WidevineBridge : Library {
        fun ktwv_create(cdmPath: String, err: ByteArray, errLen: Int): Pointer?
        fun ktwv_destroy(handle: Pointer)
        fun ktwv_reset_session(handle: Pointer)

        fun ktwv_license_request(
            handle: Pointer,
            pssh: ByteArray,
            psshLen: Int,
            out: PointerByReference,
            outLen: IntByReference,
            err: ByteArray,
            errLen: Int,
        ): Int

        fun ktwv_provide_license(
            handle: Pointer,
            license: ByteArray,
            licenseLen: Int,
            err: ByteArray,
            errLen: Int,
        ): Int

        fun ktwv_decrypt(
            handle: Pointer,
            data: ByteArray,
            dataLen: Int,
            iv: ByteArray,
            ivLen: Int,
            subsamples: IntArray?,
            subsampleCount: Int,
            keyId: ByteArray,
            keyIdLen: Int,
            out: PointerByReference,
            outLen: IntByReference,
            err: ByteArray,
            errLen: Int,
        ): Int

        fun ktwv_free(pointer: Pointer)
    }

    companion object {
        private const val TAG = "NativeWidevineCdm"

        /** The bridge `compileNativeWidevine` builds for each platform, named the way JNA loads it. */
        private const val BRIDGE_SO = "libkittytune_widevine.so"
        private const val BRIDGE_DYLIB = "libkittytune_widevine.dylib"
        private const val BRIDGE_DLL = "libkittytune_widevine.dll"

        /** The CDM a browser leaves behind, named per platform. */
        private val CDM_FILE_NAMES = setOf("libwidevinecdm.so", "libwidevinecdm.dylib", "widevinecdm.dll")
        private const val ERROR_SIZE = 512

        /** How long the quick lookup may take while a track is being resolved. */
        private const val QUICK_SCAN_BUDGET_MS = 1_500L

        /** How long "detect automatically" may spend sweeping the machine. */
        private const val DEEP_SCAN_BUDGET_MS = 8_000L

        /** A hard cap on the walk, so a pathological tree cannot run forever. */
        private const val MAX_SCAN_ENTRIES = 400_000

        /** Directories that never hold a CDM: caches, VCS data, and the kernel's own trees. */
        private val SKIPPED_DIRS = setOf(
            "Cache", "cache", "cache2", ".cache", "GPUCache", "Code Cache", "CacheStorage",
            "Service Worker", "node_modules", ".git", "__pycache__", ".venv", "build",
            "proc", "sys", "dev", "run", "Trash", ".Trash-1000", "lost+found",
        )

        @Volatile
        private var loaded: WidevineBridge? = null

        @Volatile
        private var cdmPath: String? = null

        @Volatile
        private var installAttempted = false

        /** Set once [secondaryCdm] has built the download-side instance. */
        @Volatile
        private var secondary: NativeWidevineCdm? = null

        /** Why the second instance could not be built, for the message a download reports. */
        @Volatile
        var secondaryFailureReason: String? = null
            private set

        /** True when a native bridge and a CDM were both found; i.e. protected tracks can play. */
        @Volatile
        var isReady: Boolean = false
            private set

        /** The CDM binary in use, for diagnostics. */
        val currentCdmPath: String? get() = cdmPath

        /**
         * Extracts the bridge, locates a CDM, and registers it.
         *
         * Returns true when a CDM was installed, so a build without one (or without a browser) simply
         * keeps the previous behaviour instead of failing at the first protected track.
         */
        fun installIfAvailable(): Boolean {
            if (isReady) return true
            synchronized(this) {
                if (isReady) return true
                if (installAttempted) return false
                installAttempted = true
            }
            val bridge = loadBridge()
            if (bridge == null) {
                loadFailure = "the native bridge is not usable in this build: " +
                    (bridgeFailure ?: "unknown reason")
                Logger.d(TAG, "no native Widevine bridge in this build")
                return false
            }
            val path = locateCdm()
            if (path == null) {
                loadFailure = "no Widevine CDM found in any location"
                Logger.d(TAG, "no Widevine CDM found on this machine")
                return false
            }

            val err = ByteArray(ERROR_SIZE)
            val handle = try {
                bridge.ktwv_create(path, err, ERROR_SIZE)
            } catch (e: Throwable) {
                loadFailure = "loading the CDM failed: ${e.message}"
                Logger.e(TAG, "loading the CDM at $path failed: ${e.message}")
                null
            }
            if (handle == null) {
                loadFailure = "the CDM refused to initialise: ${cString(err)}"
                Logger.e(TAG, "the CDM at $path refused to initialise: ${cString(err)}")
                return false
            }

            val cdm = NativeWidevineCdm(bridge, handle, "Widevine (${File(path).parentFile?.name ?: "local"})")
            WidevineCdmRegistry.install(cdm)
            cdmPath = path
            loaded = bridge
            isReady = true
            loadFailure = null
            Logger.d(TAG, "Widevine CDM ready: $path")
            return true
        }

        /**
         * A second CDM instance, kept apart from the one playback uses.
         *
         * The bridge holds one session per instance, and opening a session resets it. A download that
         * borrowed the playback instance would therefore wipe the key of the track currently playing
         * the moment it started. A CDM library can back several instances in one process — that is
         * exactly what several browser profiles do — so the download side gets its own.
         *
         * Returns null when no CDM was installed, exactly like [installIfAvailable] failing.
         */
        @Synchronized
        fun secondaryCdm(): WidevineCdm? {
            secondary?.let { return it }
            if (!installIfAvailable()) return null
            val bridge = loaded ?: return null
            val path = cdmPath ?: return null
            val err = ByteArray(ERROR_SIZE)
            val handle = try {
                bridge.ktwv_create(path, err, ERROR_SIZE)
            } catch (e: Throwable) {
                secondaryFailureReason = e.message ?: "the CDM could not be loaded twice"
                Logger.e(TAG, "second CDM instance failed to load: ${e.message}")
                null
            }
            if (handle == null) {
                secondaryFailureReason = cString(err).ifBlank { "the CDM refused a second instance" }
                Logger.e(TAG, "second CDM instance refused to initialise: $secondaryFailureReason")
                return null
            }
            return NativeWidevineCdm(bridge, handle, "Widevine (download)").also { secondary = it }
        }

        /** Why the bridge could not be loaded, for [diagnostics]. */
        @Volatile
        private var bridgeFailure: String? = null

        private fun loadBridge(): WidevineBridge? {
            loaded?.let { return it }
            val fileName = bridgeFileName()

            val stream = NativeWidevineCdm::class.java.getResourceAsStream("/native/$fileName")
                ?: return null
            val dir = File(System.getProperty("java.io.tmpdir"), "kittytune_native")
            dir.mkdirs()
            val file = File(dir, fileName)
            try {
                stream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
            } catch (_: Throwable) {
                // Already extracted by a previous run and possibly in use: fall through and load it.
            }
            bridgePath = file.absolutePath
            return try {
                Native.load(file.absolutePath, WidevineBridge::class.java).also { loaded = it }
            } catch (e: Throwable) {
                bridgeFailure = e.message ?: e::class.simpleName
                Logger.e(TAG, "could not load the native bridge: ${e.message}")
                null
            }
        }

        /** The bridge library this platform's build produced, under the name JNA has to load. */
        private fun bridgeFileName(): String {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> BRIDGE_DLL
                os.contains("mac") || os.contains("darwin") -> BRIDGE_DYLIB
                else -> BRIDGE_SO
            }
        }

        /** Where the last search found a CDM, even when it then failed to load. */
        @Volatile
        var lastDiscoveredPath: String? = null
            private set

        /** Why the last load attempt failed, in the words of the OS or of the CDM itself. */
        @Volatile
        private var loadFailure: String? = null

        /** The extracted native bridge, once it was loaded. */
        @Volatile
        private var bridgePath: String? = null

        /** True once a whole-machine sweep has run, so reopening the settings does not repeat it. */
        @Volatile
        var fullScanDone: Boolean = false
            private set

        /**
         * Looks for a Widevine CDM the way a browser would leave one behind.
         *
         * `KITTYTUNE_WIDEVINE_CDM` wins so a CDM kept elsewhere can be pointed at explicitly.
         *
         * This is the *quick* path: the profile directories browsers are known to use, plus a short
         * bounded walk, cheap enough to run while a track is being resolved. [deepScan] is the
         * thorough one, and only a human asking for it pays for it.
         */
        internal fun locateCdm(): String? {
            // A path the user picked in settings wins over everything: it is the only way to use a CDM
            // that lives somewhere we do not know about (Nix store, custom install, another user).
            runCatching {
                com.alananasss.kittytune.data.local.PlayerPreferences().getWidevineCdmPath()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { chosen ->
                if (File(chosen).isFile) return chosen.also { lastDiscoveredPath = it }
                Logger.e(TAG, "the configured CDM path does not exist: $chosen")
            }
            System.getenv("KITTYTUNE_WIDEVINE_CDM")?.let { explicit ->
                if (explicit.isNotBlank() && File(explicit).isFile) {
                    return explicit.also { lastDiscoveredPath = it }
                }
            }

            val roots = knownRoots()
            val found = scanForCdm(roots, maxDepth = 7, budgetMs = QUICK_SCAN_BUDGET_MS)
            lastDiscoveredPath = found
            if (found == null) {
                // Worth saying out loud: the fix is almost always "use a browser that has one", and
                // that is only obvious once you know where we looked.
                Logger.d(
                    TAG,
                    "no CDM under the ${roots.size} known locations; set " +
                        "KITTYTUNE_WIDEVINE_CDM=<path to the CDM library> or run the full scan"
                )
            }
            return found
        }

        /**
         * Sweeps the whole machine for a CDM, with a deadline.
         *
         * A list of browser profiles can never be exhaustive — Opera GX, Vivaldi, a Chromium in a
         * container, another user's home — so "detect automatically" has to mean it. Two limits keep
         * that honest: a time budget and a cap on entries visited. A machine with no CDM ends up with
         * nothing after a few seconds instead of a walk that never returns.
         */
        internal fun deepScan(budgetMs: Long = DEEP_SCAN_BUDGET_MS): String? {
            val found = scanForCdm(deepScanRoots(), maxDepth = Int.MAX_VALUE, budgetMs = budgetMs)
            fullScanDone = true
            if (found != null) lastDiscoveredPath = found
            return found
        }

        /**
         * Breadth-first search for a CDM library under [roots].
         *
         * Symlinks are not entered (a loop would keep the queue fed forever) and directories that
         * never hold a CDM are skipped, which is what makes a whole-machine walk finish.
         */
        internal fun scanForCdm(
            roots: List<String>,
            maxDepth: Int,
            budgetMs: Long,
            maxEntries: Int = MAX_SCAN_ENTRIES,
        ): String? {
            val deadline = System.currentTimeMillis() + budgetMs
            val queue = java.util.ArrayDeque<Pair<File, Int>>()
            for (root in roots) {
                val dir = File(root)
                if (dir.isDirectory) queue.addLast(dir to 0)
            }
            var visited = 0
            while (queue.isNotEmpty() && visited < maxEntries && System.currentTimeMillis() <= deadline) {
                val (dir, depth) = queue.removeFirst()
                val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
                for (child in children) {
                    visited++
                    if (child.isDirectory) {
                        if (depth >= maxDepth || child.name in SKIPPED_DIRS) continue
                        val isLink = runCatching { java.nio.file.Files.isSymbolicLink(child.toPath()) }
                            .getOrDefault(true)
                        if (isLink) continue
                        queue.addLast(child to depth + 1)
                    } else if (child.name in CDM_FILE_NAMES) {
                        return child.absolutePath
                    }
                }
            }
            return null
        }

        /** The profile directories a CDM hides in, newest browser layouts first. */
        private fun knownRoots(): List<String> {
            val home = System.getProperty("user.home") ?: return emptyList()
            val roaming = System.getenv("APPDATA")
            val localAppData = System.getenv("LOCALAPPDATA")
            return listOfNotNull(
                // Windows: the CDM lives in the browser's user data, not next to its executable.
                localAppData?.let { "$it/Google/Chrome/User Data" },
                localAppData?.let { "$it/Microsoft/Edge/User Data" },
                localAppData?.let { "$it/BraveSoftware" },
                roaming?.let { "$it/Opera Software" },
                localAppData?.let { "$it/Programs/Opera" },
                localAppData?.let { "$it/Programs/Opera GX" },
                localAppData?.let { "$it/Vivaldi/User Data" },
                localAppData?.let { "$it/Google/Chrome/Application" },
                localAppData?.let { "$it/Microsoft/Edge/Application" },
                roaming?.let { "$it/Mozilla/Firefox/Profiles" },
                "C:/Program Files/Google/Chrome",
                "C:/Program Files (x86)/Google/Chrome",
                "C:/Program Files (x86)/Microsoft/Edge",
                // Edge keeps its CDM in a versioned EdgeCore folder, and WebView2 keeps another copy;
                // neither sits under the "Edge/Application" directory the naive guess points at.
                "C:/Program Files (x86)/Microsoft/EdgeCore",
                "C:/Program Files/Microsoft/EdgeCore",
                "C:/Program Files (x86)/Microsoft/EdgeWebView",
                "C:/Program Files/Microsoft/EdgeWebView",
                localAppData?.let { "$it/Microsoft/EdgeWebView" },
                // macOS: browser profiles live in Application Support.
                "$home/Library/Application Support/Google/Chrome",
                "$home/Library/Application Support/Microsoft Edge",
                "$home/Library/Application Support/BraveSoftware",
                "$home/Library/Application Support/Firefox/Profiles",
                "$home/Library/Application Support/com.operasoftware.Opera",
                "$home/Library/Application Support/com.operasoftware.OperaGX",
                "$home/Library/Application Support/Vivaldi",
                // Linux: native installs, Opera and Opera GX included.
                "$home/.config/mozilla/firefox",
                "$home/.mozilla/firefox",
                "$home/.config/google-chrome",
                "$home/.config/chromium",
                "$home/.config/microsoft-edge",
                "$home/.config/BraveSoftware",
                "$home/.config/opera",
                "$home/.config/opera-gx",
                "$home/.config/opera-beta",
                "$home/.config/opera-developer",
                "$home/.config/vivaldi",
                "$home/.config/yandex-browser",
                "/opt/google/chrome",
                "/usr/lib/chromium",
                "/usr/lib64/chromium",
                // Opera's Linux packages keep their libraries here; Debian and Arch builds differ.
                "/usr/lib/opera",
                "/usr/lib/opera-stable",
                "/usr/lib/opera-beta",
                "/usr/lib/opera-developer",
                "/opt/opera",
                // Flatpak browsers keep their profile, and therefore their CDM, inside the sandbox.
                "$home/.var/app/org.mozilla.firefox",
                "$home/.var/app/com.google.Chrome",
                "$home/.var/app/org.chromium.Chromium",
                "$home/.var/app/com.brave.Browser",
                "$home/.var/app/com.microsoft.Edge",
                "$home/.var/app/com.opera.Opera",
                "$home/.var/app/com.vivaldi.Vivaldi",
                // Snap browsers likewise.
                "$home/snap/firefox/common/.mozilla/firefox",
                "$home/snap/chromium/current/.config/chromium",
                "$home/snap/opera/current/.config/opera",
            )
        }

        /** Where a whole-machine sweep starts, most promising first. */
        private fun deepScanRoots(): List<String> {
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").lowercase()
            val roots = ArrayList<String>()
            if (home != null) {
                // App-data directories first: a CDM is almost always in one, and the deadline may cut
                // the walk short before a plain home sweep gets there.
                roots.add("$home/.config")
                roots.add("$home/.local/share")
                roots.add("$home/.var/app")
                roots.add(home)
            }
            if (os.contains("win")) {
                System.getenv("LOCALAPPDATA")?.let { roots.add(it) }
                System.getenv("APPDATA")?.let { roots.add(it) }
                roots.add("C:/Program Files")
                roots.add("C:/Program Files (x86)")
                runCatching { File.listRoots().forEach { roots.add(it.absolutePath) } }
            } else if (os.contains("mac") || os.contains("darwin")) {
                roots.add("/Applications")
                roots.add("/Library")
                roots.add("/Users")
            } else {
                // Everything else is reachable from "/"; home goes first because that is where the
                // CDM almost always is, and the deadline may cut the walk short.
                roots.add("/")
            }
            return roots
        }

        /**
         * What the last lookup and load did, for a bug report.
         *
         * Plain text on purpose: the user copies it out of the settings page and sends it verbatim,
         * so it has to say where we looked, what was found, and what the CDM answered.
         */
        fun diagnostics(): String = buildString {
            appendLine("KittyTune Widevine diagnostics")
            appendLine("version: ${com.alananasss.kittytune.BuildConfig.VERSION_NAME}")
            appendLine("os: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
            appendLine("java: ${System.getProperty("java.version")}")
            appendLine("bridge: ${bridgePath ?: "(not loaded)"}")
            bridgeFailure?.let { appendLine("bridge error: $it") }
            val configured = runCatching {
                com.alananasss.kittytune.data.local.PlayerPreferences().getWidevineCdmPath()
            }.getOrNull().orEmpty()
            appendLine("configured path: ${configured.ifBlank { "(none)" }}")
            appendLine("KITTYTUNE_WIDEVINE_CDM: ${System.getenv("KITTYTUNE_WIDEVINE_CDM") ?: "(unset)"}")
            appendLine("found: ${lastDiscoveredPath ?: "(nothing)"}")
            appendLine("loaded: ${currentCdmPath ?: "(no)"} (ready=$isReady)")
            loadFailure?.let { appendLine("last error: $it") }
            // Only the directories that exist: on Windows and macOS most of the list never can, and a
            // log where two thirds of the lines are "absent" is a log nobody reads.
            val roots = knownRoots()
            val present = roots.filter { File(it).isDirectory }
            appendLine("locations present (${present.size} of ${roots.size}):")
            if (present.isEmpty()) appendLine("  none") else present.forEach { appendLine("  ok  $it") }
        }

        /**
         * Runs the search again even when a previous attempt failed.
         *
         * [installIfAvailable] is deliberately one-shot so a machine without a CDM does not pay for a
         * lookup on every resolution. Opening the CDM settings page is an explicit "look again", and
         * that is the only thing allowed to lift the guard.
         */
        @Synchronized
        fun retryDiscovery(): Boolean {
            if (isReady) return true
            installAttempted = false
            return installIfAvailable()
        }

        /**
         * Points the app at [path] and re-installs. Used by the settings field's "test" button so a
         * path can be checked without restarting.
         */
        fun applyCustomPath(path: String): Boolean {
            if (path.isBlank() || !File(path).isFile) {
                loadFailure = if (path.isBlank()) "no path given" else "no such file: $path"
                return false
            }
            synchronized(this) {
                isReady = false
                installAttempted = false
                cdmPath = null
            }
            runCatching { com.alananasss.kittytune.data.local.PlayerPreferences().setWidevineCdmPath(path) }
            return installIfAvailable()
        }

        internal fun cString(buffer: ByteArray): String {
            var end = 0
            while (end < buffer.size && buffer[end] != 0.toByte()) end++
            return String(buffer, 0, end, Charsets.UTF_8)
        }

        /**
         * Writes a license exchange blob to disk when `KITTYTUNE_DRM_DUMP` names a directory.
         *
         * The license request carries what the CDM says it is — platform, architecture, version and
         * the device certificate — and a server refusing one platform but not another is only
         * visible by comparing those bytes. Off unless asked for: they are not bytes to leave lying
         * around.
         */
        internal fun dump(name: String, bytes: ByteArray) {
            val directory = System.getenv("KITTYTUNE_DRM_DUMP")?.trim().orEmpty()
            if (directory.isEmpty()) return
            runCatching {
                val dir = File(directory)
                dir.mkdirs()
                val file = File(dir, name)
                file.writeBytes(bytes)
                Logger.d(TAG, "wrote ${bytes.size} B to ${file.absolutePath}")
            }
        }
    }
}

/** One license exchange, living as long as the track it belongs to. */
internal class NativeWidevineSession(
    private val native: NativeWidevineCdm.WidevineBridge,
    private val handle: Pointer,
    private val pssh: ByteArray,
    private val lock: Any,
) : WidevineLicenseSession, KeyIdScopedSession {

    @Volatile
    private var keyId: ByteArray = ByteArray(0)

    override fun licenseRequest(): ByteArray = synchronized(lock) {
        val out = PointerByReference()
        val outLen = IntByReference()
        val err = ByteArray(512)
        val ok = native.ktwv_license_request(handle, pssh, pssh.size, out, outLen, err, err.size)
        if (ok == 0) throw DrmLicenseException(NativeWidevineCdm.cString(err))
        takeAndFree(out, outLen).also { NativeWidevineCdm.dump("license-request.bin", it) }
    }

    override fun provideLicense(license: ByteArray): List<ContentKey> = synchronized(lock) {
        NativeWidevineCdm.dump("license-response.bin", license)
        val err = ByteArray(512)
        val ok = native.ktwv_provide_license(handle, license, license.size, err, err.size)
        if (ok == 0) throw DrmLicenseException(NativeWidevineCdm.cString(err))
        // The CDM does not expose key bytes, by design; the KID is enough to report what was granted.
        emptyList()
    }

    /**
     * Decrypts one sample. The CDM resolves the key internally from [keyId], so nothing here can
     * read, log or cache it.
     */
    override fun decryptSample(
        data: ByteArray,
        iv: ByteArray,
        subsamples: List<Pair<Int, Int>>,
    ): ByteArray = synchronized(lock) {
        val flat = if (subsamples.isEmpty()) null else IntArray(subsamples.size * 2).also { arr ->
            subsamples.forEachIndexed { i, (clear, encrypted) ->
                arr[i * 2] = clear
                arr[i * 2 + 1] = encrypted
            }
        }
        val out = PointerByReference()
        val outLen = IntByReference()
        val err = ByteArray(512)
        val ok = native.ktwv_decrypt(
            handle, data, data.size, iv, iv.size,
            flat, subsamples.size, keyId, keyId.size,
            out, outLen, err, err.size,
        )
        if (ok == 0) throw DrmLicenseException(NativeWidevineCdm.cString(err))
        takeAndFree(out, outLen)
    }

    /** Called by [SoundCloudDrm] once the init segment revealed the KID. */
    override fun useKeyId(kid: ByteArray) {
        keyId = kid
    }

    override fun close() {
        // The CDM is reused for the next track; SoundCloudDrm closes the session through the bridge.
    }

    private fun takeAndFree(out: PointerByReference, outLen: IntByReference): ByteArray {
        val pointer = out.value ?: return ByteArray(0)
        return try {
            pointer.getByteArray(0, outLen.value)
        } finally {
            native.ktwv_free(pointer)
        }
    }
}
