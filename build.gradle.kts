import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

group = "com.alananasss"
version = "1.3.1"

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

dependencies {
    implementation("com.github.z-huang.InnerTune:lrclib:0.5.10")
    implementation("com.github.z-huang.InnerTune:kugou:0.5.10")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(project(":kizzy"))
    implementation(project(":shazamkit"))

    implementation("io.github.alexzhirkevich:compottie:2.2.4-compose-1.12-SNAPSHOT")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0-alpha02")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")

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

    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.materialkolor:material-kolor:5.0.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("org.slf4j:slf4j-simple:2.0.18")

    // QR rendering for the Discord remote-auth (scan-to-log-in) flow. Same artifact and
    // version the Android app already uses for its VK QR login.
    implementation("com.google.zxing:core:3.5.4")

    implementation("org.json:json:20260814")
    implementation("org.yaml:snakeyaml:2.6")
    implementation("com.github.pemistahl:lingua:1.2.2")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")

    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.2.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.alananasss.kittytune.MainKt"

        // Grants what the app already does, so the JDK stops warning about it on every launch:
        // sqlite-jdbc loads its native library, and Gson writes final fields when it
        // deserializes our data classes. Both are integrity-by-default warnings today and hard
        // errors in a later JDK, so declaring them now is also what keeps the app running then.
        // Gated on the build JDK because the packaged runtime is built from it, and an older
        // one would refuse to start on an option it does not know.
        val buildJdk = JavaVersion.current().majorVersion.toIntOrNull() ?: 0
        if (buildJdk >= 24) jvmArgs += "--enable-native-access=ALL-UNNAMED"
        if (buildJdk >= 25) jvmArgs += "--enable-final-field-mutation=ALL-UNNAMED"
        // jffi, pulled in by the D-Bus transport, reaches into sun.misc.Unsafe for memory
        // access. Nothing we can fix from here — the flag is the JDK's own way to say "known,
        // stop printing it", and it also keeps that code working once the default flips to
        // deny. The day Unsafe actually goes, jnr needs a release either way.
        if (buildJdk >= 24) jvmArgs += "--sun-misc-unsafe-memory-access=allow"

        // Memory, and why an audio app cares about the collector it gets (issue #33).
        //
        // Reported as "500 MB at launch, 1000 MB later, sometimes 4000 MB, and the song freezes for
        // an instant and then stops". Without -Xmx the JVM sets the ceiling to a quarter of physical
        // RAM, so on a 16 GB machine it will happily grow to 4 GB before collecting in earnest —
        // which is exactly the number reported. Nothing needs that: the image cache is capped at
        // 128 MB and everything else is small.
        //
        // The freeze and the stop are one event. A stop-the-world collection of a multi-gigabyte
        // heap takes long enough to starve the decode loop, the output line runs dry, and playback
        // stops. G1 is named explicitly rather than left to ergonomics because a machine the JVM
        // judges "client class" gets SerialGC, whose full collections are the longest of the lot,
        // and a pause target tells it to work in short increments instead of one long sweep.
        // 2 GB, not the quarter of physical RAM the JVM defaults to. The ceiling exists to stop the
        // 4 GB balloon that was reported, not to squeeze the app: a library of tens of thousands of
        // liked tracks genuinely needs several hundred megabytes while it hydrates, and a tighter
        // cap turns a stutter into an OutOfMemoryError in the middle of playback.
        jvmArgs += "-Xmx2g"
        jvmArgs += "-XX:+UseG1GC"
        jvmArgs += "-XX:MaxGCPauseMillis=50"
        // Give the pages back (issue #33).
        //
        // Measured on a real session: the heap held 266 MB committed for 80 MB of live objects. G1's
        // defaults are MinHeapFreeRatio=40/MaxHeapFreeRatio=70, which is a policy for a server that would
        // rather keep memory than ask for it again — on a desktop it reads as a leak, because the number in
        // the system monitor never comes down after the library finishes loading.
        //
        // A periodic cycle is what actually triggers the uncommit: a player sitting idle never allocates
        // hard enough to collect on its own, so without this the slack is simply held for ever. It is
        // pinned to a *concurrent* cycle on purpose — the default for this flag is a full collection, and a
        // stop-the-world pause on a multi-hundred-megabyte heap is what starved the decode loop and stopped
        // playback in the first place. Concurrent gives back less, and giving back less is the right trade
        // against an audible gap.
        jvmArgs += "-XX:MinHeapFreeRatio=10"
        jvmArgs += "-XX:MaxHeapFreeRatio=30"
        jvmArgs += "-XX:G1PeriodicGCInterval=60000"
        jvmArgs += "-XX:+G1PeriodicGCInvokesConcurrent"
        // Thousands of tracks repeat the same artist names, genres and CDN prefixes. Deduplicating
        // those strings costs a background pass and gives back real memory here.
        jvmArgs += "-XX:+UseStringDeduplication"

        /**
         * Where the memory actually goes, on demand: `./gradlew packageReleaseMsi -PmemDiag`.
         *
         * The heap is capped at 2 GB above, so the 4 GB that was reported has at least half of itself
         * somewhere the heap cannot account for, and no figure a task manager shows can say where.
         * Native memory tracking can, but the JVM only collects it when asked at startup, so it takes
         * a build of its own. `MemoryDiagnostics` reads the result from inside the process and writes
         * it to a file, so nobody has to install a JDK or open a terminal to send it back.
         *
         * Deliberately not on by default: tracking costs a few percent and a little memory of its
         * own, which is a poor trade for everyone who is not currently being asked about it.
         */
        if (project.hasProperty("memDiag")) {
            jvmArgs += "-XX:NativeMemoryTracking=summary"
            jvmArgs += "-Dkittytune.memlog=true"
        }


        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage
            )
            packageName = "KittyTune"
            packageVersion = "1.3.1"
            description = "KittyTuneDesktop"
            vendor = "KittyTune"

            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.net.http",
                "java.sql",
                "java.naming",
                "java.scripting",
                "java.prefs",
                "jdk.dynalink",
                // The diagnostic command MBean and the OS memory bean live here. Included in every
                // build so the diagnostic one differs from a release by launcher flags alone, which
                // is what makes what it measures worth anything (issue #33).
                "jdk.management",
                "jdk.httpserver",
                "jdk.jfr",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.security.auth"
            )

            windows {
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f8d30e5-7971-4a7b-a19c-49fb1e5b1234"
                iconFile.set(project.file("src/main/resources/icons/kittytune.ico"))
            }

            linux {
                shortcut = true
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                packageName = "kitty-tune"
                iconFile.set(project.file("src/main/resources/icons/kittytune_linux.png"))
            }

            macOS {
                bundleID = "com.alananasss.kittytune"
                appCategory = "public.app-category.music"
                // Without this jpackage bundles its own generic Java icon, and the variant
                // switcher then has nothing recognisable to fall back to.
                iconFile.set(project.file("src/main/resources/icons/kittytune.icns"))
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("sun.java2d.wm.className", "kitty-tune")
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    compilerOptions.freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
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

val compileNativeDSP by tasks.registering(Exec::class) {
    val cppDir = project.file("src/main/cpp")
    val outDir = project.file("src/main/resources/native")
    
    doFirst { outDir.mkdirs() }
    
    val osName = System.getProperty("os.name").lowercase()
    val isMac = osName.contains("mac")
    val isWin = osName.contains("win")
    val libExt = if (isWin) "dll" else if (isMac) "dylib" else "so"
    val osIncludeDir = if (isWin) "win32" else if (isMac) "darwin" else "linux"
    
    val outFile = File(outDir, "libkittytune_audio_dsp.$libExt")
    
    val javaHome = System.getProperty("java.home")
    val compiler = if (isWin) "g++" else "g++" // assuming MSYS2 or MinGW on Windows, or just gcc/clang
    
    commandLine(
        compiler, "-shared", "-fPIC", "-O3",
        "-I$javaHome/include",
        "-I$javaHome/include/$osIncludeDir",
        "-I${File(cppDir, "ebur128/queue").absolutePath}",
        File(cppDir, "KittyTuneAudioDSP.cpp").absolutePath,
        File(cppDir, "ebur128/ebur128.c").absolutePath,
        "-o", outFile.absolutePath
    )
}

tasks.named("processResources") {
    dependsOn(compileNativeDSP)
}
