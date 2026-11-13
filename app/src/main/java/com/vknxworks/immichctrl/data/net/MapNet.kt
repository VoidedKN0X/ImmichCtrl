package com.vknxworks.immichctrl.data.net

import android.content.Context
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.IOException

/**
 * MapLibre downloads map styles, tiles, fonts and icons with its own HTTP client, outside the app's
 * [CleartextPolicy]. This gives it an https-only client instead: every request and every redirect hop
 * must be https (map styles are already https-only, see `rememberMapStyleUrl`), so a style can never
 * pull tiles or fonts over plain HTTP.
 *
 * MapLibre must be set up before its HTTP classes are touched, so this replaces `MapLibre.getInstance`
 * wherever a map is created; maps stay off (and MapLibre unloaded) until one is shown.
 */
object MapNet {
    @Volatile private var installed = false

    private val httpsOnly = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps) throw IOException("Map request blocked: not https (${url.host})")
        chain.proceed(chain.request())
    }

    /** Same limit as MapLibre's own client, so tiles load just as fast. */
    private fun client() = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
        .addNetworkInterceptor(httpsOnly)
        .build()

    /** Main thread only, like `MapLibre.getInstance`. */
    fun init(context: Context): MapLibre {
        val instance = MapLibre.getInstance(context)
        if (!installed) {
            HttpRequestUtil.setOkHttpClient(client())
            installed = true
        }
        return instance
    }
}
