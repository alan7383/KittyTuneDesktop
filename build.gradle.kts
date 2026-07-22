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
    implementation("org.bytedeco:javacv-platform:1.5.10")

    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")

    implementation("org.json:json:20260522")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    val javafxVersion = "21.0.2"
    listOf("win", "mac", "linux").forEach { platform ->
        listOf("javafx-base", "javafx-graphics", "javafx-controls", "javafx-swing", "javafx-media", "javafx-web").forEach { module ->
            implementation("org.openjfx:$module:$javafxVersion:$platform")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.alananasss.kittytune.MainKt"

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

            linux {
                shortcut = true
                appCategory = "AudioVideo"
            }
        }
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
}