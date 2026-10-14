pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "KittyTuneWindows"
include(":shazamkit")
include(":kizzy")
include(":innertube")
// The Windows entry point can configure only the desktop modules; the website
// remains available in the normal multi-project build.
if (providers.gradleProperty("desktopOnly").orNull != "true") include(":website")
