# ProGuard rules for KittyTune Desktop
-dontobfuscate
-dontoptimize

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Application entry point & all application classes
-keep class com.alananasss.kittytune.MainKt { *; }
-keep class com.alananasss.kittytune.** { *; }
-keep class com.my.kizzy.** { *; }
-keep class com.alananasss.shazamkit.** { *; }
-keep class com.zhuang.innertube.** { *; }

# Compose Multiplatform & Skiko
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class !androidx.compose.material.icons.**,androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
# Allow unused icons in material-icons-extended to be pruned:
# Unused icon classes are discarded by dead-code elimination.
# Only keep the Icons root class and group singletons if referenced.
-keep class androidx.compose.material.icons.Icons { *; }
-keep class androidx.compose.material.icons.Icons$* { *; }
-keep class org.jetbrains.compose.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }

# Native bridges & JNI (JavaCPP, FFmpeg, ONNX, TFLite, SQLite, JNA, DBus, JNR, JFFI)
-keep class org.bytedeco.** { *; }
-keep class ai.onnxruntime.** { *; }
-keep class org.sqlite.** { *; }
-keep class com.sun.jna.** { *; }
-keep class jnr.** { *; }
-keep class com.kenai.** { *; }
-keep class com.github.hypfvieh.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class org.slf4j.** { *; }
-keep class org.eclipse.paho.** { *; }

# Networking, Serialization, Media
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep class io.ktor.** { *; }
-keep class com.google.gson.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class io.coil-kt.** { *; }
-keep class coil3.** { *; }
-keep class io.github.alexzhirkevich.compottie.** { *; }
-keep class net.jthink.jaudiotagger.** { *; }
-keep class com.mpatric.mp3agic.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }

-dontwarn **

# ZXing QR: QRCodeWriter resolves ErrorCorrectionLevel through Enum.valueOf at runtime.
# Shrinking the enum made that call throw "ErrorCorrectionLevel is not an enum class",
# which the UI turned into the blank white QR plate in release builds.
-keep class com.google.zxing.** { *; }
