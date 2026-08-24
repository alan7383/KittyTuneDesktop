package com.alananasss.kittytune.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.alananasss.kittytune.core.AppDirs

import okio.Path.Companion.toOkioPath

object ImageLoaderFactory {

    fun create(): ImageLoader {
        // Route image requests through the proxy when one is configured
        // (same as the Android app wiring Coil to ProxyManager's client).
        val okHttp = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttp }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(AppDirs.imageCacheDir.toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
