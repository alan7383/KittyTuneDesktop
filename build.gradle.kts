import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0-beta01"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

group = "com.alananasss"
version = "1.0.32"

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            excludeGroup("com.github.hypfvieh")
        }
    }
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("com.github.z-huang.InnerTune:innertube:0.5.10")
    implementation("com.github.z-huang.InnerTune:lrclib:0.5.10")
    implementation("com.github.z-huang.InnerTune:kugou:0.5.10")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(project(":kizzy"))
    implementation(project(":shazamkit"))

    implementation("io.github.alexzhirkevich:compottie:2.2.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0-alpha02")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

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

    implementation("org.json:json:20260719")
    implementation("org.yaml:snakeyaml:2.6")
    implementation("com.github.pemistahl:lingua:1.2.2")
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")

    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.2.0")

    val javafxVersion = "21.0.3"
    val javafxClassifier = when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> "mac"
        osName.contains("linux") -> "linux"
        else -> null
    }
    if (javafxClassifier != null) {
        listOf("javafx-base", "javafx-graphics", "javafx-controls", "javafx-swing", "javafx-media", "javafx-web").forEach { module ->
            implementation("org.openjfx:$module:$javafxVersion:$javafxClassifier")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.alananasss.kittytune.MainKt"

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
            packageVersion = "1.0.25"
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
                "jdk.httpserver",
                "jdk.jfr",
                "jdk.jsobject",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.xml.dom",
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
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("sun.java2d.wm.className", "kitty-tune")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
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
