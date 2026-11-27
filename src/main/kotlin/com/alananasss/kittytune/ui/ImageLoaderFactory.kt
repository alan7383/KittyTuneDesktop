package com.alananasss.kittytune.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.SuccessResult
import coil3.size.pxOrElse
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.PerformanceSettings

import okio.Path.Companion.toOkioPath

object ImageLoaderFactory {

    @Volatile
    var diskCache: DiskCache? = null
        private set

    fun create(): ImageLoader {
        // Route image requests through the proxy when one is configured
        // (same as the Android app wiring Coil to ProxyManager's client).
        val okHttp = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()
        val performance = PerformanceSettings.active
        val builtDiskCache = DiskCache.Builder()
            .directory(AppDirs.imageCacheDir.toOkioPath())
            .maxSizeBytes(performance.diskCacheMiB.toLong() * 1024 * 1024)
            .build()
        diskCache = builtDiskCache
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttp }))
                add(Interceptor { chain ->
                    val request = chain.request
                    val url = request.data as? String ?: return@Interceptor chain.proceed()
                    val displayPixels = minOf(
                        chain.size.width.pxOrElse { 0 },
                        chain.size.height.pxOrElse { 0 }
                    )
                    for (candidate in youtubeArtworkUpgrades(url, displayPixels)) {
                        val upgraded = chain.withRequest(request.newBuilder().data(candidate).build()).proceed()
                        if (upgraded is SuccessResult) return@Interceptor upgraded
                    }
                    chain.proceed()
                })
            }
            // Decoded bitmaps are Skia memory, outside the Java heap and invisible to -Xmx, and the
            // GPU keeps its own texture copy of whatever is on screen. The default 32 MB holds a couple of
            // screens of covers — enough that scrolling back a page is a cache hit while the
            // footprint stays flat — and anything evicted comes back from the disk cache below in a
            // few ms (RAM target, issue #33). It was 64 MB, which for the same working set simply
            // kept twice as many covers resident in native memory.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(performance.imageCacheMiB.toLong() * 1024 * 1024)
                    .build()
            }
            .diskCache { builtDiskCache }
            // No global crossfade: it applied to every AsyncImage in the app, including 56-dp
            // thumbnails in fast-scrolling grids, where dozens of 150-300 ms fades read as flicker
            // and waste GPU compositing. Callers that want a crossfade on large artwork (full
            // player, dialog covers, player bar) should add `.crossfade(true)` per-call.
            // (audit Phase 3 CPU gain)
            .build()
    }
}
