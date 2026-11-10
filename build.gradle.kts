import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

group = "com.alananasss"
version = "1.5.0"

val desktopIsWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
// Keep Windows app images separate by version, including when an older build is running.
val desktopDistributionDirectory = layout.buildDirectory.dir(
    if (desktopIsWindows) "compose/binaries/${project.version}" else "compose/binaries"
)

/** Major version of the JDK the packaged runtime is built from (see `javaHome` in compose.desktop). */
val PACKAGING_JDK = 25
val packagingJdkHome: String by lazy {
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(PACKAGING_JDK))
        vendor.set(JvmVendorSpec.AZUL)
    }.get().metadata.installationPath.asFile.absolutePath
}

repositories {
    google()
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://jitpack.io") {
        content {
            excludeGroup("com.github.hypfvieh")
        }
    }
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

configurations.all {
    exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-all")
}

dependencies {
    implementation("com.github.z-huang.InnerTune:lrclib:0.5.10")
    implementation("com.github.z-huang.InnerTune:kugou:0.5.10")
    implementation(compose.desktop.currentOs)
    implementation("com.mocharealm.accompanist:lyrics-ui:1.0.19") {
        if (!desktopIsWindows) {
            exclude(group = "org.jetbrains.compose.desktop", module = "desktop-jvm-windows-x64")
            exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")
        }
    }
    implementation("com.mocharealm.accompanist:lyrics-core:0.5.0")
    implementation("org.jetbrains.compose.material3:material3:1.13.0-beta02")
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(project(":kizzy"))
    implementation(project(":shazamkit"))
    implementation(project(":innertube"))

    implementation("io.github.alexzhirkevich:compottie:2.3.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.1")

    val ktorVersion = "3.6.1"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")

    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.mpatric:mp3agic:0.9.1")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.2")

    val javacvVersion = "1.5.10"
    implementation("org.bytedeco:javacv:$javacvVersion") {
        exclude(group = "org.bytedeco", module = "opencv")
    }
    implementation("org.bytedeco:ffmpeg:6.1.1-$javacvVersion")

    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val platformClassifier = when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("mac") && osArch.contains("aarch64") -> "macosx-arm64"
        osName.contains("mac") -> "macosx-x86_64"
        osName.contains("linux") && osArch.contains("aarch64") -> "linux-arm64"
        osName.contains("linux") -> "linux-x86_64"
        else -> null
    }
    if (platformClassifier != null) {
        implementation("org.bytedeco:ffmpeg:6.1.1-$javacvVersion:$platformClassifier")
        implementation("org.bytedeco:javacpp:$javacvVersion:$platformClassifier")
    }

    // ArtifactNet AI-music detection (issue #41): genuine TensorFlow Lite runtime with
    // desktop natives (Linux/Windows/macOS) + XNNPACK, via the JavaCPP preset. Runs the
    // exact same .tflite model file as the Android app. The classifier mirrors the
    // ffmpeg setup above: the main jar plus the current OS's native jar only.
    val tfliteVersion = "2.21.0-1.5.14"
    implementation("org.bytedeco:tensorflow-lite:$tfliteVersion")
    val tfliteClassifier = when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("mac") && osArch.contains("aarch64") -> "macosx-arm64"
        osName.contains("mac") -> "macosx-x86_64"
        osName.contains("linux") && osArch.contains("aarch64") -> "linux-arm64"
        osName.contains("linux") -> "linux-x86_64"
        else -> null
    }
    if (tfliteClassifier != null) {
        implementation("org.bytedeco:tensorflow-lite:$tfliteVersion:$tfliteClassifier")
    }

    val onnxVersion = "1.31.1"
    implementation("com.microsoft.onnxruntime:onnxruntime:$onnxVersion")

    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.materialkolor:material-kolor:5.0.1")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.20")

    // QR rendering for the Discord remote-auth (scan-to-log-in) flow. Same artifact and
    // version the Android app already uses for its VK QR login.
    implementation("com.google.zxing:core:3.5.4")
    // Shared playlists: the relay between listeners is MQTT (see data/together).
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    implementation("org.json:json:20260814")
    implementation("org.yaml:snakeyaml:2.7")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")

    implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
    implementation("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.2.2")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

// `-PuiScale=1.5` runs the tests as on a display scaled to 150%. AWT reads the property once at start-up,
// which is why it is a JVM argument of the test task and not something a test could set for itself.
tasks.withType<Test>().configureEach {
    // Private session buses change between runs; do not reuse a cached, dead bus address
    // or a previous skipped test result from another bus/configuration.
    providers.environmentVariable("ALSA_CONFIG_PATH").orNull?.let {
        environment("ALSA_CONFIG_PATH", it)
        inputs.file(it).withPropertyName("linuxAudioTestBackend")
    }
    inputs.property("liveLinuxAudioTest", providers.environmentVariable("KITTYTUNE_LINUX_AUDIO_TEST").orElse(""))
    providers.environmentVariable("KITTYTUNE_LINUX_AUDIO_TEST").orNull?.let { environment("KITTYTUNE_LINUX_AUDIO_TEST", it) }
    inputs.property("mprisSessionBus", providers.environmentVariable("DBUS_SESSION_BUS_ADDRESS").orElse(""))
    inputs.property("mprisIiConfig", providers.environmentVariable("KITTYTUNE_II_CONFIG").orElse(""))
    providers.environmentVariable("DBUS_SESSION_BUS_ADDRESS").orNull?.let { environment("DBUS_SESSION_BUS_ADDRESS", it) }
    providers.environmentVariable("KITTYTUNE_II_CONFIG").orNull?.let {
        environment("KITTYTUNE_II_CONFIG", it)
        inputs.files(file("$it/services/MprisController.qml"), file("$it/services/CoverArt.qml"))
            .withPropertyName("mprisIiSources")
    }
    providers.gradleProperty("uiScale").orNull?.let { systemProperty("sun.java2d.uiScale", it) }
    jvmArgs("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
    systemProperty("ORT_DISABLE_TELEMETRY", "1")
    environment("ORT_DISABLE_TELEMETRY", "1")
    providers.environmentVariable("DISPLAY").orNull?.let { environment("DISPLAY", it) }
    providers.environmentVariable("XAUTHORITY").orNull?.let { environment("XAUTHORITY", it) }
    providers.environmentVariable("WAYLAND_DISPLAY").orNull?.let { environment("WAYLAND_DISPLAY", it) }
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("ORT_DISABLE_TELEMETRY", "1")
    environment("ORT_DISABLE_TELEMETRY", "1")
    providers.environmentVariable("DISPLAY").orNull?.let { environment("DISPLAY", it) }
    providers.environmentVariable("XAUTHORITY").orNull?.let { environment("XAUTHORITY", it) }
    providers.environmentVariable("WAYLAND_DISPLAY").orNull?.let { environment("WAYLAND_DISPLAY", it) }
}

compose.desktop {
    application {
        mainClass = "com.alananasss.kittytune.MainKt"
        val localJdk21 = File(System.getProperty("user.home"), ".gradle/jdks/eclipse_adoptium-21-amd64-linux.2")
        if (localJdk21.exists()) {
            javaHome = localJdk21.absolutePath
        } else {
            // The runtime shipped in the packages is a JDK 25, whatever JDK runs Gradle: compact object headers
            // and SoftMaxHeapSize below only exist from there, and the code still compiles to 21 bytecode.
            // Provisioned by Gradle (foojay) so a local build and CI ship the same runtime with nothing to
            // install. Zulu rather than Temurin because Temurin 25 no longer ships `jmods` (JEP 493), and the
            // ProGuard step reads the JDK's classes from there: without them it sees no java.* class at all.
            javaHome = packagingJdkHome
        }

        // Grants what the app already does, so the JDK stops warning about it on every launch:
        // sqlite-jdbc loads its native library, and Gson writes final fields when it
        // deserializes our data classes. Both are integrity-by-default warnings today and hard
        // errors in a later JDK, so declaring them now is also what keeps the app running then.
        // Gated on the build JDK because the packaged runtime is built from it, and an older
        // one would refuse to start on an option it does not know.
        val buildJdk = if (localJdk21.exists()) 21 else PACKAGING_JDK
        if (buildJdk >= 24) jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // JEP 500's option only exists from JDK 26; a JDK 25 launcher refuses to start on it.
        if (buildJdk >= 26) jvmArgs += "--enable-final-field-mutation=ALL-UNNAMED"
        // jffi, pulled in by the D-Bus transport, reaches into sun.misc.Unsafe for memory
        // access. Nothing we can fix from here — the flag is the JDK's own way to say "known,
        // stop printing it", and it also keeps that code working once the default flips to
        // deny. The day Unsafe actually goes, jnr needs a release either way.
        if (buildJdk >= 24) jvmArgs += "--sun-misc-unsafe-memory-access=allow"
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        jvmArgs += "-DORT_DISABLE_TELEMETRY=1"

        // Memory, and why an audio app cares about the collector it gets (issue #33).
        //
        // Reported as "500 MB at launch, 1000 MB later, sometimes 4000 MB, and the song freezes for
        // an instant and then stops". Without -Xmx the JVM sets the ceiling to a quarter of physical
        // RAM, so on a 16 GB machine it will happily grow to 4 GB before collecting in earnest —
        // which is exactly the number reported.
        //
        // The freeze and the stop are one event. A stop-the-world collection of a multi-gigabyte
        // heap takes long enough to starve the decode loop, the output line runs dry, and playback
        // stops. G1 is named explicitly rather than left to ergonomics because a machine the JVM
        // judges "client class" gets SerialGC, whose full collections are the longest of the lot,
        // and a pause target tells it to work in short increments instead of one long sweep.
        //
        // 512 MB, down from the 2 GB this ceiling used to allow. That 2 GB was never sized for
        // what the app needed to stay alive — it was headroom for the drift it used to accumulate:
        // native DSP state leaked per crossfade, karaoke stems held whole in the heap, the likes
        // library as one resident JSON string, a sync log with no end, and a doubled image cache.
        // Those are fixed in code now rather than tolerated in memory (issue #33), and what
        // legitimately lives on the heap is the UI state, a liked library that reaches tens of
        // megabytes for tens of thousands of tracks, bounded caches, and the transient churn of a
        // track change. 512 MB accommodates that with G1 room to evacuate into; being close to the
        // live set is also itself the pressure that makes the collector work rather than let the
        // committed heap wander up to wherever the ceiling allows.
        jvmArgs += "-Xmx512m"
        // The size G1 aims at day to day: expand past it only under real allocation pressure, and
        // give the expansion back once it passes. G1 honours this from JDK 24 (JDK-8302927); a
        // runtime built from an older JDK ignores the option rather than refusing to start, which
        // is why it is gated like the flags above. With the periodic concurrent cycle below it
        // keeps the committed heap near the live set without buying the number back in full-GC
        // pauses the way the free-ratio flags once did.
        if (buildJdk >= 24) jvmArgs += "-XX:SoftMaxHeapSize=384m"
        jvmArgs += "-XX:+UseG1GC"
        jvmArgs += "-XX:MaxGCPauseMillis=50"
        // Measured idle on the home screen with native memory tracking (issue #66), 505 MB private at the
        // start of this: the rest of G1's footprint is per-thread. Two workers and one marking thread
        // instead of one per core took its bookkeeping from 56 to 39 MB; at the few hundred megabytes of heap
        // this app has, a young pause with two workers is still a few milliseconds against the output line's
        // 200 ms buffer. SerialGC and ParallelGC were measured too: Serial saves more, but its full
        // collections stop the world, which is the stutter G1 was chosen to end, and Parallel saves nothing.
        jvmArgs += "-XX:ParallelGCThreads=2"
        jvmArgs += "-XX:ConcGCThreads=1"
        // The concurrent-mark stack is committed up front at MarkStackSize entries of 8 bytes, and the default of
        // 4M entries is 32 MB — most of the "GC" line in native memory tracking, measured at 38 MB, for a heap whose
        // live set is around a hundred megabytes. 256K entries brought that line to 8 MB with no other change
        // (fresh profile, idle: 417 → 392 MB private). It is a starting size, not a limit: on overflow G1 doubles
        // the stack up to MarkStackSizeMax and carries on, so a large library costs a resize, never a failure.
        jvmArgs += "-XX:MarkStackSize=262144"
        // How much time G1 may spend collecting before it grows the heap instead: 1/(1+N), so 20% rather than the
        // default 12's 8%. Growth is G1's answer to the allocation rate, and an animating interface allocates in
        // bursts that the default met by committing more young regions and keeping them. Measured while listening
        // with the full player open: 146 MB committed instead of 179, for the same CPU time over three minutes
        // (74 s against 76 s) — the extra young collections are a few milliseconds each on two workers.
        jvmArgs += "-XX:GCTimeRatio=4"
        // Start small and grow on demand. The default first commit is 1/64 of physical RAM, 256 MB on a
        // 16 GB machine, for an app whose live data at idle is about 45 MB.
        jvmArgs += "-Xms16m"
        // 8-byte object headers instead of 12 (JEP 519, a product option from JDK 25): every object in the
        // heap gets smaller, measured at about 15 MB less heap at idle and more with a large library.
        if (buildJdk >= 25) jvmArgs += "-XX:+UseCompactObjectHeaders"
        // Give the pages back, but never by stopping the world (issue #33).
        //
        // This used to also carry `-XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30`, to stop the system
        // monitor showing 266 MB committed against 80 MB live. Three diagnostic logs from the reporter say
        // plainly what that cost, and it is the bug he has been describing all along:
        //
        //   G1 Old Generation: 2 collections, 67 ms      (up 1 min)
        //   G1 Old Generation: 20 collections, 540 ms    (up 10 min)
        //   G1 Old Generation: 40 collections, 1104 ms   (up 22 min)
        //
        // Two *full*, stop-the-world collections a minute, from the first minute, in every session in
        // every log. A healthy G1 application does none at all: the whole point of the collector is that
        // old-generation work happens concurrently. Squeezing committed memory to within 30% of live
        // leaves G1 no room to evacuate into, and when a concurrent cycle cannot keep up the fallback is a
        // full compacting pause. So the flag that was meant to make the memory number look honest was
        // buying it with a pause twice a minute, forever.
        //
        // At an 80 MB live set those pauses are 25 ms and nobody notices, which is why they were measured
        // as harmless. At the 1.2 GB the same log later reaches, the same policy produced thirteen of them
        // in nine minutes at 200 to 411 ms each — "the song stops for a millisecond and then continues,
        // several times per song", and once "froze 1 sec". Two a minute is several per song exactly.
        //
        // What is left is the half that was already pauseless. A periodic cycle is what triggers an
        // uncommit at all — a player sitting idle never allocates hard enough to collect on its own — and
        // pinned to a *concurrent* cycle it hands memory back at the end of the cycle without a pause. It
        // gives back less than the ratios did. Giving back less is the right trade against an audible gap,
        // and the number in the system monitor is not what anybody is listening to.
        jvmArgs += "-XX:G1PeriodicGCInterval=60000"
        jvmArgs += "-XX:+G1PeriodicGCInvokesConcurrent"
        // And if anything asks for a collection by hand, it is served concurrently rather than as a full
        // pause. The app asks for one itself once it has been minimised for a while (TrimMemoryWhileHidden),
        // to hand the heap back while nobody is looking; this flag is what keeps that, or a dependency
        // doing the same, from reintroducing the pause that took three diagnostic builds to find. (The diagnostic build's own class histogram is unaffected — a
        // heap inspection still collects for real, which is what makes its numbers live ones.)
        jvmArgs += "-XX:+ExplicitGCInvokesConcurrent"
        // Thousands of tracks repeat the same artist names, genres and CDN prefixes. Deduplicating
        // those strings costs a background pass and gives back real memory here.
        jvmArgs += "-XX:+UseStringDeduplication"
        // Skia's GPU resource cache defaults to 256 MB of textures and render targets, which it fills
        // and keeps. Everything this app draws is covers and flat UI, re-uploaded in microseconds from
        // the decoded copies Coil already holds, so a quarter of that is plenty. Measured over six
        // minutes of track changes: steady state ~710 MB instead of ~760 MB private memory.
        jvmArgs += "-Dskiko.gpu.resourceCacheLimit=67108864"

        /**
         * Where the memory actually goes, on demand: `./gradlew packageReleaseMsi -PmemDiag`.
         *
         * The heap is capped at 512 MB above, so any number the system monitor shows beyond a few
         * hundred megabytes is somewhere the heap cannot account for, and no figure a task manager
         * shows can say where. Native memory tracking can, but the JVM only collects it when asked
         * at startup, so it takes a build of its own. `MemoryDiagnostics` reads the result from
         * inside the process and writes it to a file, so nobody has to install a JDK or open a
         * terminal to send it back.
         *
         * Deliberately not on by default: tracking costs a few percent and a little memory of its
         * own, which is a poor trade for everyone who is not currently being asked about it.
         */
        if (project.hasProperty("memDiag")) {
            jvmArgs += "-XX:NativeMemoryTracking=summary"
            jvmArgs += "-Dkittytune.memlog=true"
        }


        buildTypes.release.proguard {
            isEnabled.set(true)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            outputBaseDir.set(desktopDistributionDirectory)
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage
            )
            packageName = "KittyTune"
            // Must track the project version: the MSI ProductVersion has to increase with
            // every release, otherwise Windows Installer refuses the upgrade with
            // "Another version of this product is already installed" (issue #73).
            // Never hardcode this again — it drifted (1.3.9) while version moved to 1.4.0.
            packageVersion = project.version.toString()
            description = "KittyTuneDesktop"
            vendor = "KittyTune"

            modules(
                // Required by runtime dependencies (Compose suggestRuntimeModules). A Gradle run
                // has the full JDK, so missing modules otherwise surface only in the packaged app.
                "java.compiler",
                "java.instrument",
                "java.scripting",
                "jdk.dynalink",
                "java.management",
                "java.net.http",
                "java.sql",
                "java.naming",
                "java.prefs",
                // The diagnostic command MBean and the OS memory bean live here. Included in every
                // build so the diagnostic one differs from a release by launcher flags alone, which
                // is what makes what it measures worth anything (issue #33).
                "jdk.management",
                "jdk.httpserver",
                "jdk.jfr",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                // Day and month names in any language but English. Without it the trimmed runtime only
                // knows the root locale, so the listening stats read "Mon Tue" and "October" in Russian
                // while a run from the full JDK looked fine (issue #66).
                "jdk.localedata",
                // dbus-java loads UnixSystem reflectively for EXTERNAL authentication. jdeps
                // cannot infer it: keep these explicit in every trimmed runtime (issue #107).
                "jdk.security.auth",
                "java.security.jgss"
            )

            // "Open with KittyTune" and the music entry in the OS's default-apps settings. Neither
            // Windows nor the Linux desktops hand over an existing default without the user saying
            // so; this only makes KittyTune one of the choices. Files arrive as main() arguments.
            val audioFileTypes = mapOf(
                "mp3" to "audio/mpeg",
                "flac" to "audio/flac",
                "m4a" to "audio/mp4",
                "aac" to "audio/aac",
                "ogg" to "audio/ogg",
                "opus" to "audio/opus",
                "wav" to "audio/wav",
                "wma" to "audio/x-ms-wma",
            )

            windows {
                audioFileTypes.forEach { (extension, mimeType) ->
                    fileAssociation(mimeType, extension, "Audio file (.$extension)", project.file("src/main/resources/icons/kittytune.ico"))
                }
                shortcut = true
                menu = true
                menuGroup = "KittyTune"
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f8d30e5-7971-4a7b-a19c-49fb1e5b1234"
                iconFile.set(project.file("src/main/resources/icons/kittytune.ico"))
            }

            linux {
                audioFileTypes.forEach { (extension, mimeType) ->
                    fileAssociation(mimeType, extension, "Audio file (.$extension)", project.file("src/main/resources/icons/kittytune_linux.png"))
                }
                shortcut = true
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo;Audio;Music;Player;"
                packageName = "kitty-tune"
                iconFile.set(project.file("src/main/resources/icons/kittytune_linux.png"))
            }

            macOS {
                bundleID = "com.alananasss.kittytune"
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array><dict>
                            <key>CFBundleURLName</key><string>SoundCloud OAuth</string>
                            <key>CFBundleURLSchemes</key><array><string>sc</string></array>
                            <key>CFBundleTypeRole</key><string>Viewer</string>
                        </dict></array>
                    """.trimIndent()
                }
                appCategory = "public.app-category.music"
                // Without this jpackage bundles its own generic Java icon, and the variant
                // switcher then has nothing recognisable to fall back to.
                iconFile.set(project.file("src/main/resources/icons/kittytune.icns"))
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("sun.java2d.wm.className", "kittytune")
    jvmArgs("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
}
kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    compilerOptions.freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
}

composeCompiler {
    // Use the compiler DSL rather than raw -P arguments containing Windows drive paths.
    if (providers.gradleProperty("composeReports").orNull == "true") {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
    }
}

// Auto-generate BuildConfig.kt from the project version so it's always in sync.
val generateBuildConfig by tasks.registering {
    val versionName = project.version.toString()
    val outDir = layout.projectDirectory.dir("src/main/kotlin/com/alananasss/kittytune")
    outputs.file(outDir.file("BuildConfig.kt"))
    doLast {
        outDir.file("BuildConfig.kt").asFile.writeText(
            "package com.alananasss.kittytune\n\n" +
            "/**\n" +
            " * Desktop replacement for the Android generated BuildConfig.\n" +
            " * AUTO-GENERATED \u2014 do not edit manually. Change `version` in build.gradle.kts instead.\n" +
            " */\n" +
            "object BuildConfig {\n" +
            "    const val APPLICATION_ID = \"com.alananasss.kittytune\"\n" +
            "    const val VERSION_NAME = \"$versionName\"\n" +
            "    const val VERSION_CODE = 1\n" +
            "    const val DEBUG = false\n" +
            "}\n"
        )
    }
}

tasks.named("compileKotlin") { dependsOn(generateBuildConfig) }

val compileNativeDSP = tasks.register<Exec>("compileNativeDSP") {
    val cppDir = project.file("src/main/cpp")
    val outDir = layout.buildDirectory.dir("generated/native-resources/native").get().asFile
    val linuxNativeDir = File(outDir, "linux-x86_64")
    
    val osName = System.getProperty("os.name").lowercase()
    val isMac = osName.contains("mac")
    val isWin = osName.contains("win")
    val libExt = if (isWin) "dll" else if (isMac) "dylib" else "so"
    val osIncludeDir = if (isWin) "win32" else if (isMac) "darwin" else "linux"

    doFirst {
        outDir.mkdirs()
        if (!isWin && !isMac) linuxNativeDir.mkdirs()
    }
    
    val outFile = File(outDir, "libkittytune_audio_dsp.$libExt")
    
    val javaHome = System.getProperty("java.home")
    inputs.dir(cppDir)
    inputs.file("scripts/build-native-windows.ps1")
    inputs.property("javaHome", javaHome)
    inputs.property("osName", osName)
    outputs.file(outFile)
    if (!isWin && !isMac) outputs.file(File(linuxNativeDir, "libkt_vk_priority.so"))

    if (isWin) {
        commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", project.file("scripts/build-native-windows.ps1").absolutePath,
            "-JavaHome", javaHome, "-OutputDirectory", outDir.absolutePath)
    } else {
        commandLine(
            "g++", "-shared", "-fPIC", "-O3",
            "-I$javaHome/include", "-I$javaHome/include/$osIncludeDir",
            "-I${File(cppDir, "ebur128/queue").absolutePath}",
            File(cppDir, "KittyTuneAudioDSP.cpp").absolutePath,
            File(cppDir, "ebur128/ebur128.c").absolutePath,
            "-o", outFile.absolutePath
        )
    }

    doLast {
        if (!isWin && !isMac) {
            val vkPriorityC = File(cppDir, "KittyTuneVkPriority.c")
            val vkPrioritySo = File(linuxNativeDir, "libkt_vk_priority.so")
            if (vkPriorityC.exists()) {
                val proc = ProcessBuilder("gcc", "-shared", "-fPIC", "-O2", "-Wall", vkPriorityC.absolutePath, "-ldl", "-o", vkPrioritySo.absolutePath).start()
                val exit = proc.waitFor()
                if (exit != 0) {
                    val err = proc.errorStream.bufferedReader().readText()
                    throw GradleException("Failed compiling libkt_vk_priority.so: $err")
                }
            }
        }
    }
}

val compileNativeWidevine = tasks.register<Exec>("compileNativeWidevine") {
    description = "Builds the Widevine CDM bridge from source into generated resources."
    val cppDir = project.file("src/main/cpp/widevine")
    val outDir = layout.buildDirectory.dir("generated/native-resources/native").get().asFile
    val osName = System.getProperty("os.name").lowercase()
    val libExt = if (osName.contains("win")) "dll" else if (osName.contains("mac")) "dylib" else "so"
    val outFile = File(outDir, "libkittytune_widevine.$libExt")

    inputs.dir(cppDir)
    inputs.property("osName", osName)
    outputs.file(outFile)

    doFirst { outDir.mkdirs() }

    doLast {
        if (!outFile.exists()) {
            logger.warn("Widevine bridge was not built; protected tracks will fall back to being skipped")
        }
    }

    if (osName.contains("win")) {
        // Expects MinGW g++ on PATH; the CDM is loaded with LoadLibrary either way. `-static` also
        // pulls in winpthread: the bridge starts its own timer thread, and without it the DLL would
        // need libwinpthread-1.dll next to it, which the portable app does not ship.
        commandLine(
            "g++", "-shared", "-O2", "-std=c++17",
            "-I${cppDir.absolutePath}",
            File(cppDir, "WidevineBridge.cpp").absolutePath,
            "-o", outFile.absolutePath,
            "-static"
        )
    } else if (osName.contains("mac") || osName.contains("darwin")) {
        commandLine(
            "g++", "-dynamiclib", "-fPIC", "-O2", "-std=c++17",
            "-I${cppDir.absolutePath}",
            File(cppDir, "WidevineBridge.cpp").absolutePath,
            "-o", outFile.absolutePath
        )
    } else {
        commandLine(
            "g++", "-shared", "-fPIC", "-O2", "-std=c++17",
            "-I${cppDir.absolutePath}",
            File(cppDir, "WidevineBridge.cpp").absolutePath,
            "-o", outFile.absolutePath,
            "-ldl", "-pthread"
        )
    }
}

val compileWindowsSmtcBridge = tasks.register<Exec>("compileWindowsSmtcBridge") {
    description = "Builds the Windows media bridge from source into generated resources."
    enabled = desktopIsWindows
    val outDir = layout.buildDirectory.dir("generated/native-resources/native")
    inputs.files("scripts/build-smtc-bridge.ps1", "src/main/resources/native/WindowsSmtcBridge.cs",
        "src/main/resources/icons/kittytune.ico")
    outputs.file(outDir.map { it.file("WindowsSmtcBridge.exe") })
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
        "-File", project.file("scripts/build-smtc-bridge.ps1").absolutePath,
        "-OutDir", outDir.get().asFile.absolutePath)
}

val compileWindowsPlayReady = tasks.register<Exec>("compileWindowsPlayReady") {
    description = "Builds the unpackaged Windows PlayReady renderer from source."
    enabled = desktopIsWindows
    val outDir = layout.buildDirectory.dir("generated/native-resources/native/playready")
    inputs.files("scripts/build-windows-playready.ps1", "src/main/cpp/playready/DesktopProtectedPlayer.cpp",
        "src/main/cpp/playready/PlayReadyCdm.h", "src/main/cpp/playready/MediaFoundationCompat.h")
    outputs.file(outDir.map { it.file("KittyTuneProtectedPlayer.exe") })
    commandLine("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
        "-File", project.file("scripts/build-windows-playready.ps1").absolutePath,
        "-OutputDirectory", outDir.get().asFile.absolutePath)
}

val compileMacNowPlayingHelper = tasks.register<Exec>("compileMacNowPlayingHelper") {
    description = "Builds the native Swift macOS Now Playing helper app."
    val osName = System.getProperty("os.name").lowercase()
    enabled = osName.contains("mac")
    inputs.files("scripts/build-macos-nowplaying.sh", "src/main/swift/KittyTuneNowPlaying.swift",
        "src/main/resources/icons/kittytune.icns")
    outputs.file(layout.buildDirectory.file("macos-nowplaying/KittyTune-NowPlaying-Helper.app/Contents/MacOS/KittyTuneNowPlaying"))
    commandLine("bash", project.file("scripts/build-macos-nowplaying.sh").absolutePath, "--helper-only")
}

// Exclude non-target native resources so packages don't bundle binaries from other operating systems.
sourceSets.main {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val isMac = osName.contains("mac")
    val isLinux = osName.contains("linux")
    val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")

    val extension = if (desktopIsWindows) "dll" else if (isMac) "dylib" else "so"
    resources.exclude("native/libkittytune_audio_dsp.$extension")
    // Demucs CLI binaries (31-34 MB) are downloaded on-demand with the model when Karaoke is enabled.
    resources.exclude("native/**/demucs*")
    if (desktopIsWindows) {
        resources.exclude("icons/kittytune.icns")
        resources.exclude("native/WindowsSmtcBridge.exe")
        resources.exclude("native/linux-x86_64/**")
        resources.exclude("native/*.so")
        resources.exclude("native/*.dylib")
    } else if (isLinux) {
        resources.exclude("icons/kittytune.icns")
        resources.exclude("native/windows-x86_64/**")
        resources.exclude("native/WindowsSmtcBridge.*")
        resources.exclude("native/*.dll")
        resources.exclude("native/*.dylib")
        if (isArm64) {
            resources.exclude("native/linux-x86_64/demucs")
        }
        resources.exclude("native/linux-x86_64/libkt_vk_priority.so")
    } else if (isMac) {
        resources.exclude("icons/kittytune.ico")
        resources.exclude("native/windows-x86_64/**")
        resources.exclude("native/linux-x86_64/**")
        resources.exclude("native/WindowsSmtcBridge.*")
        resources.exclude("native/*.dll")
        resources.exclude("native/*.so")
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(compileNativeDSP)
    dependsOn(compileNativeWidevine)
    if (desktopIsWindows) dependsOn(compileWindowsSmtcBridge)
    if (desktopIsWindows) dependsOn(compileWindowsPlayReady)
    from(layout.buildDirectory.dir("generated/native-resources")) {
        if (desktopIsWindows) {
            include("native/*.dll", "native/WindowsSmtcBridge.exe", "native/playready/KittyTuneProtectedPlayer.exe")
        } else if (System.getProperty("os.name").lowercase().contains("mac")) {
            include("native/*.dylib")
        } else {
            include("native/*.so", "native/linux-x86_64/*.so")
        }
    }
}

abstract class StripForeignNativesTask : DefaultTask() {
    @get:Internal
    abstract val targetDirectory: DirectoryProperty

    @get:Internal
    abstract val targetDirectories: ListProperty<Directory>

    @TaskAction
    fun execute() {
        val singleDir = targetDirectory.orNull?.asFile
        val multiDirs = targetDirectories.orNull?.map { it.asFile } ?: emptyList()
        val allDirs = (listOfNotNull(singleDir) + multiDirs).filter { it.exists() }.distinct()
        if (allDirs.isEmpty()) return

        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val isWin = osName.contains("win")
        val isMac = osName.contains("mac")
        val isArm64 = osArch.contains("aarch64") || osArch.contains("arm64")

        val targetOnnxSubdir = when {
            isWin -> "ai/onnxruntime/native/win-x64/"
            isMac && isArm64 -> "ai/onnxruntime/native/osx-aarch64/"
            !isWin && !isMac && isArm64 -> "ai/onnxruntime/native/linux-aarch64/"
            else -> "ai/onnxruntime/native/linux-x64/"
        }

        val targetSqliteSubdir = when {
            isWin -> "org/sqlite/native/Windows/x86_64/"
            isMac && isArm64 -> "org/sqlite/native/Mac/aarch64/"
            isMac -> "org/sqlite/native/Mac/x86_64/"
            !isWin && !isMac && isArm64 -> "org/sqlite/native/Linux/aarch64/"
            else -> "org/sqlite/native/Linux/x86_64/"
        }

        fun stripJar(jarFile: File, predicate: (String) -> Boolean) {
            val tmp = File(jarFile.parentFile, jarFile.name + ".tmp")
            try {
                ZipFile(jarFile).use { zipIn ->
                    ZipOutputStream(FileOutputStream(tmp)).use { zipOut ->
                        val entries = zipIn.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (predicate(entry.name)) {
                                zipOut.putNextEntry(ZipEntry(entry.name))
                                zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
                tmp.copyTo(jarFile, overwrite = true)
            } catch (e: Exception) {
                logger.warn("Failed stripping ${jarFile.name}: ${e.message}")
            } finally {
                tmp.delete()
            }
        }

        for (dir in allDirs) {
            dir.walkTopDown().filter { file -> file.isFile && file.name.startsWith("onnxruntime-") && file.name.endsWith(".jar") }.forEach { jar ->
                stripJar(jar) { name -> !name.startsWith("ai/onnxruntime/native/") || name.startsWith(targetOnnxSubdir) }
            }

            dir.walkTopDown().filter { file -> file.isFile && file.name.startsWith("sqlite-jdbc-") && file.name.endsWith(".jar") }.forEach { jar ->
                stripJar(jar) { name -> !name.startsWith("org/sqlite/native/") || name.startsWith(targetSqliteSubdir) }
            }
        }
    }
}

val stripForeignNativesProguard = tasks.register<StripForeignNativesTask>("stripForeignNativesProguard") {
    description = "Strips foreign native binaries from ProGuard output JARs before packaging."
    targetDirectory.set(layout.buildDirectory.dir("compose/tmp/main-release/proguard"))
    dependsOn("proguardReleaseJars")
}

val stripForeignNativesDistributable = tasks.register<StripForeignNativesTask>("stripForeignNativesDistributable") {
    description = "Strips foreign native binaries from unpacked release app distributions."
    targetDirectory.set(desktopDistributionDirectory.map { it.dir("main-release/app") })
}

val stripForeignNativesDistributableMain = tasks.register<StripForeignNativesTask>("stripForeignNativesDistributableMain") {
    description = "Strips foreign native binaries from unpacked debug app distributions."
    targetDirectory.set(desktopDistributionDirectory.map { it.dir("main/app") })
}

tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    finalizedBy(stripForeignNativesProguard)
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    finalizedBy(stripForeignNativesDistributableMain)
}

tasks.matching { it.name == "createReleaseDistributable" }.configureEach {
    finalizedBy(stripForeignNativesDistributable)
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (name.startsWith("packageRelease") || name == "createReleaseDistributable") {
        dependsOn(stripForeignNativesProguard)
    }
}

abstract class EmbedMacNowPlayingHelperTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val helperAppDirectory: DirectoryProperty

    @get:Internal
    abstract val desktopDistributionDir: DirectoryProperty

    @get:Internal
    abstract val buildDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("mac")) return
        val helperApp = helperAppDirectory.orNull?.asFile ?: return
        if (!helperApp.exists()) return

        val baseDir = desktopDistributionDir.orNull?.asFile ?: return
        val bDir = buildDir.orNull?.asFile ?: return
        val appDirs = listOf(
            File(baseDir, "main/app/KittyTune.app"),
            File(baseDir, "main-release/app/KittyTune.app"),
            File(bDir, "compose/binaries/main/app/KittyTune.app"),
            File(bDir, "compose/binaries/main-release/app/KittyTune.app")
        ).distinct()

        for (app in appDirs) {
            if (app.exists()) {
                val helpersDir = File(app, "Contents/Helpers")
                helpersDir.mkdirs()
                val targetHelper = File(helpersDir, "KittyTune-NowPlaying-Helper.app")
                val proc = ProcessBuilder("ditto", helperApp.absolutePath, targetHelper.absolutePath).start()
                proc.waitFor()
            }
        }
    }
}

val embedMacNowPlayingHelper = tasks.register<EmbedMacNowPlayingHelperTask>("embedMacNowPlayingHelper") {
    description = "Embeds the macOS Now Playing helper into the packaged KittyTune.app bundle."
    val osName = System.getProperty("os.name").lowercase()
    val isMac = osName.contains("mac")
    enabled = isMac
    if (isMac) {
        dependsOn(compileMacNowPlayingHelper)
    }
    helperAppDirectory.set(layout.buildDirectory.dir("macos-nowplaying/KittyTune-NowPlaying-Helper.app"))
    desktopDistributionDir.set(desktopDistributionDirectory)
    buildDir.set(layout.buildDirectory)
}

tasks.matching { it.name in listOf("createDistributable", "createReleaseDistributable") }.configureEach {
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        finalizedBy(embedMacNowPlayingHelper)
    }
}

/**
 * Opts the Windows launcher into the segment heap (scripts/enable-segment-heap.ps1 has the why).
 *
 * jpackage's KittyTune.exe hosts the JVM in-process, so its manifest decides the allocator behind every native
 * allocation the app makes. Patched in the app image, which is what both the MSI (jpackage --app-image) and the
 * portable zip are built from.
 */
abstract class EnableSegmentHeapTask : DefaultTask() {
    @get:InputFile
    abstract val script: RegularFileProperty

    @get:Internal
    abstract val distributionDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val base = distributionDir.get().asFile
        val exes = listOf("main/app/KittyTune/KittyTune.exe", "main-release/app/KittyTune/KittyTune.exe")
            .map { File(base, it) }.filter { it.isFile }
        if (exes.isEmpty()) return
        val command = listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.get().asFile.absolutePath, "-Executable") + exes.map { it.absolutePath }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) throw GradleException("Enabling the segment heap failed:\n$output")
        logger.lifecycle(output.trim())
    }
}

val enableSegmentHeap = tasks.register<EnableSegmentHeapTask>("enableSegmentHeap") {
    description = "Opts the packaged Windows launcher into the segment heap."
    enabled = desktopIsWindows
    script.set(layout.projectDirectory.file("scripts/enable-segment-heap.ps1"))
    distributionDir.set(desktopDistributionDirectory)
    // Never patch an image that is still being written or stripped.
    mustRunAfter(stripForeignNativesDistributable, stripForeignNativesDistributableMain)
}

tasks.matching { it.name in listOf("createDistributable", "createReleaseDistributable") }.configureEach {
    if (desktopIsWindows) finalizedBy(enableSegmentHeap)
}

tasks.matching { it.name == "zipWindowsPortable" || (it.name.startsWith("package") && it.name.endsWith("Msi")) }.configureEach {
    if (desktopIsWindows) dependsOn(enableSegmentHeap)
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask>().configureEach {
    val localJdk21 = File(System.getProperty("user.home"), ".gradle/jdks/eclipse_adoptium-21-amd64-linux.2")
    // ProGuard reads the packaged JDK's jmods as its library classes, so it has to be that same JDK.
    javaHome.set(if (localJdk21.exists()) localJdk21.absolutePath else packagingJdkHome)
    maxHeapSize.set("2048m")
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    runCatching {
        @Suppress("UNCHECKED_CAST")
        val stripProp = this::class.java.methods.firstOrNull { it.name.startsWith("getStripDebug") }?.invoke(this) as? org.gradle.api.provider.Property<Boolean>
        stripProp?.set(true)
        @Suppress("UNCHECKED_CAST")
        val noHeadersProp = this::class.java.methods.firstOrNull { it.name.startsWith("getNoHeaderFiles") }?.invoke(this) as? org.gradle.api.provider.Property<Boolean>
        noHeadersProp?.set(true)
        @Suppress("UNCHECKED_CAST")
        val noManProp = this::class.java.methods.firstOrNull { it.name.startsWith("getNoManPages") }?.invoke(this) as? org.gradle.api.provider.Property<Boolean>
        noManProp?.set(true)
        @Suppress("UNCHECKED_CAST")
        val stripCmdsProp = this::class.java.methods.firstOrNull { it.name.startsWith("getStripNativeCommands") }?.invoke(this) as? org.gradle.api.provider.Property<Boolean>
        stripCmdsProp?.set(true)
        @Suppress("UNCHECKED_CAST")
        val compressProp = this::class.java.methods.firstOrNull { it.name.startsWith("getCompressionLevel") }?.invoke(this) as? org.gradle.api.provider.Property<Any>
        val zipLevel = Class.forName("org.jetbrains.compose.desktop.application.internal.RuntimeCompressionLevel").enumConstants?.firstOrNull { it.toString() == "ZIP" }
        if (zipLevel != null) compressProp?.set(zipLevel)
    }
}

tasks.register<JavaExec>("runProtectedTracksDemo") {
    description = "Opens the real app with the protected tracks card and notice controls in an isolated guest profile."
    group = "application"
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.alananasss.kittytune.MainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    systemProperty("kittytune.protectedTracksDemo", "true")
    val profile = layout.buildDirectory.dir("protected-tracks-demo").get().asFile
    environment("APPDATA", File(profile, "roaming").absolutePath)
    environment("LOCALAPPDATA", File(profile, "local").absolutePath)
    doFirst { listOf("roaming", "local").forEach { File(profile, it).mkdirs() } }
}

tasks.register<Test>("headlessTest") {
    description = "Runs deterministic desktop regressions without windows or audio output."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("java.awt.headless", "true")
    val profile = layout.buildDirectory.dir("test-profile").get().asFile
    systemProperty("user.home", File(profile, "home").absolutePath)
    environment("APPDATA", File(profile, "roaming").absolutePath)
    environment("LOCALAPPDATA", File(profile, "local").absolutePath)
    systemProperty("java.io.tmpdir", File(profile, "tmp").absolutePath)
    doFirst { listOf("home", "roaming", "local", "tmp").forEach { File(profile, it).mkdirs() } }
    maxHeapSize = "768m"
    maxParallelForks = 1
    filter {
        listOf("SignedUrlTest", "SidebarNavLayoutTest", "QueuePlayedMarkingTest",
            "PlayerRemainingTimeTest", "PlaylistTotalDurationTest", "SettingsSearchTest",
            "VolumeCurveTest", "TrackTrimTest", "TrimTimeParseTest", "SyncMergeTest", "DownloadJobsTest",
            "WindowBoundsRestorationTest", "TextFieldShortcutGuardTest", "PaletteCacheTest", "PeakLimiterTest",
            "WindowsNativeDspTest", "DesktopRenderTest", "DesktopDensityTest",
            "ConnectNetworkStateTest", "ConnectRefactorTest", "SyncPlaybackTest", "ConnectWireTest",
            "ConnectSocketListenerTest", "ConnectHandoffTest", "ConnectLanServerTest", "ConnectRenderTest", "LocalizationParityTest",
            "PlayerButtonRowsTest", "PlayheadClockTest", "WheelSeekTest", "PlayingBarsTest", "*WslSidecarSetupTest", "*WslUninstallRenderTest", "*ProtectedTracksNoticeTest", "*ProtectedTracksCardsRenderTest",
            "OnnxNativeLoadTest", "ToolWindowStyleRaiseToFrontTest", "*SoundCloudOAuthTest",
            "*WindowsProtectedPlaybackTest", "*WindowsProtectedMediaTest",
            "Issue93BugfixesTest.testWindowsUpdaterScriptConstruction*", "InstallerProcessTest", "KittyTuneProcessesTest"
        ).forEach(::includeTestsMatching)
    }
}

tasks.register<Zip>("zipWindowsPortable") {
    description = "Packages a Windows app with its Java runtime; no installer or system Java required."
    group = "distribution"
    dependsOn("createReleaseDistributable")
    dependsOn(stripForeignNativesDistributable)
    from(desktopDistributionDirectory.map { it.dir("main-release/app") })
    destinationDirectory.set(layout.projectDirectory.dir("release"))
    archiveFileName.set("KittyTune-${project.version}-Windows-Portable-x64.zip")
    entryCompression = ZipEntryCompression.DEFLATED
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<JavaExec>("connectBenchmark") {
    description = "Reports Connect allocation and packet-size baselines without audio or user profiles."
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ConnectBenchmark")
    systemProperty("java.awt.headless", "true")
}
