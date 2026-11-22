package com.alananasss.kittytune.audio.ai.karaoke

internal data class NativeDemucsPlatform(val resourceSubdir: String, val archive: String, val executable: String)

internal fun nativeDemucsPlatform(osName: String, arch: String): NativeDemucsPlatform? {
    val os = osName.lowercase()
    val x64 = arch.lowercase() in setOf("amd64", "x86_64", "x64")
    val arm64 = arch.lowercase() in setOf("aarch64", "arm64")
    return when {
        os.startsWith("windows") && x64 -> NativeDemucsPlatform("windows-x86_64", "demucs-x86_64-pc-windows-msvc.zip", "demucs.exe")
        os.contains("mac") && arm64 -> NativeDemucsPlatform("macos-aarch64", "demucs-aarch64-apple-darwin.tar.gz", "demucs")
        os == "linux" && x64 -> NativeDemucsPlatform("linux-x86_64", "demucs-x86_64-unknown-linux-gnu.tar.gz", "demucs")
        else -> null
    }
}
