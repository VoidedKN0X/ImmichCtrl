package com.vknxworks.immichctrl

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File

class ImmichCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
        container = AppContainer(this)
    }

    /** Thumbnails and previews come through the app's HTTP client, which adds the account's credentials. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheMb = runBlocking { container.settings.current().cacheMb }.coerceAtLeast(64)
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.http }))
                add(AnimatedImageDecoder.Factory())
                // Thumbnails for videos stored on the phone or saved offline.
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "images").toOkioPath())
                    .maxSizeBytes(cacheMb * 1024L * 1024L)
                    .build()
            }
            .crossfade(150)
            .build()
    }
}
