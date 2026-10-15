package com.alananasss.kittytune.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
            // Decoded bitmaps are Skia memory, outside the Java heap and invisible to -Xmx, and the
            // GPU keeps its own texture copy of whatever is on screen. 32 MB still holds a couple of
            // screens of covers — enough that scrolling back a page is a cache hit while the
            // footprint stays flat — and anything evicted comes back from the disk cache below in a
            // few ms (RAM target, issue #33). It was 64 MB, which for the same working set simply
            // kept twice as many covers resident in native memory.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(AppDirs.imageCacheDir.toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            // No global crossfade: it applied to every AsyncImage in the app, including 56-dp
            // thumbnails in fast-scrolling grids, where dozens of 150-300 ms fades read as flicker
            // and waste GPU compositing. Callers that want a crossfade on large artwork (full
            // player, dialog covers, player bar) should add `.crossfade(true)` per-call.
            // (audit Phase 3 CPU gain)
            .build()
    }
}
