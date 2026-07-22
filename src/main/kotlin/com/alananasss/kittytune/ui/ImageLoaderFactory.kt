package com.alananasss.kittytune.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.alananasss.kittytune.core.AppDirs
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Global Coil 3 ImageLoader — the desktop replacement for the Android Coil 2 setup used by
 * every AsyncImage. Disk cache lives under the app image cache dir, matching Android behavior.
 */
object ImageLoaderFactory {

    fun create(): ImageLoader {
        val okHttp = OkHttpClient.Builder().build()
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttp }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(PlatformContext.INSTANCE, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(AppDirs.imageCacheDir.toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
