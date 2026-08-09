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

object ImageLoaderFactory {

    fun create(): ImageLoader {
        val okHttp = OkHttpClient.Builder().build()
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
