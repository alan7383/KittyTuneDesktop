import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

group = "com.alananasss"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
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

    implementation("io.github.alexzhirkevich:compottie:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0-alpha02")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.5")

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
    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("org.json:json:20260522")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    val javafxVersion = "21.0.2"
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
            isEnabled.set(true)
            configurationFiles.from(file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage
            )
            packageName = "KittyTune"
            packageVersion = "1.0.0"
            description = "KittyTuneDesktop"
            copyright = "© 2026 KittyTune. All rights reserved."
            vendor = "KittyTune"

            windows {
                shortcut = true
                menu = true
                upgradeUuid = "6f8d30e5-7971-4a7b-a19c-49fb1e5b1234"
            }

            linux {
                shortcut = true
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                packageName = "kitty-tune"
            }

            macOS {
                bundleID = "com.alananasss.kittytune"
                appCategory = "public.app-category.music"
            }
        }
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
}