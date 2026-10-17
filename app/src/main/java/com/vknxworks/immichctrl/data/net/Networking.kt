package com.vknxworks.immichctrl.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.security.KeyChain
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/** Request tag: send to exactly this URL (used to test the home address). */
object NoRewrite

/**
 * Per-account connection settings, applied to every request the app makes (API, images, video,
 * downloads, live updates):
 * - extra headers, for servers behind Cloudflare Access, Authelia and similar proxies;
 * - a home-network address used instead of the public one whenever it answers on Wi-Fi;
 * - a client certificate from Android's certificate store, for servers that require mTLS.
 */
class NetworkRouter(private val context: Context, private val scope: CoroutineScope) {
    data class Config(
        val external: HttpUrl? = null,
        val local: HttpUrl? = null,
        val headers: Map<String, String> = emptyMap(),
        val certAlias: String? = null,
    )

    @Volatile var config = Config()
        private set

    private val _usingLocal = MutableStateFlow(false)
    /** True while requests go to the home-network address. */
    val usingLocal: StateFlow<Boolean> = _usingLocal.asStateFlow()

    private var probeJob: Job? = null

    /** Plain client used only to test whether the home address answers. */
    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .addNetworkInterceptor(CleartextPolicy.interceptor)
            .build()
    }

    fun configure(external: String?, local: String?, headers: Map<String, String>, certAlias: String?) {
        val ext = external?.toHttpUrlOrNull()
        val loc = local?.trim()?.trimEnd('/')?.removeSuffix("/api")?.takeIf { it.isNotBlank() }?.toHttpUrlOrNull()
        loc?.let { CleartextPolicy.allowServer(it.toString()) }
        config = Config(ext, loc, headers.filterKeys { it.isNotBlank() }, certAlias?.ifBlank { null })
        keyManager.reset()
        _usingLocal.value = false
        recheck()
    }

    /** Whether [url] goes to the signed-in server, at either of its addresses. */
    fun isServer(url: HttpUrl): Boolean {
        val c = config
        return sameOrigin(url, c.external) || sameOrigin(url, c.local)
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl?) =
        b != null && a.host.equals(b.host, true) && a.port == b.port && a.scheme == b.scheme

    /** Application interceptor: route to the home address when it's reachable, and add custom headers. */
    val interceptor = Interceptor { chain ->
        var req: Request = chain.request()
        val c = config
        if (c.external != null && req.tag(NoRewrite::class.java) == null) {
            if (_usingLocal.value && c.local != null && sameOrigin(req.url, c.external)) {
                val path = c.local.encodedPath.trimEnd('/') + req.url.encodedPath
                val url = req.url.newBuilder()
                    .scheme(c.local.scheme)
                    .host(c.local.host)
                    .port(c.local.port)
                    .encodedPath(path)
                    .build()
                // The call started at the public address; tell the cleartext check where it really starts.
                req = req.newBuilder().url(url).tag(CleartextPolicy.RoutedTo::class.java, CleartextPolicy.RoutedTo(url)).build()
            }
            if (c.headers.isNotEmpty() && isServer(req.url)) {
                val b = req.newBuilder()
                c.headers.forEach { (k, v) -> b.header(k, v) }
                req = b.build()
            }
        }
        chain.proceed(req)
    }

    /** Re-test the home address, e.g. after the network changed. */
    fun recheck() {
        probeJob?.cancel()
        val local = config.local ?: run { _usingLocal.value = false; return }
        probeJob = scope.launch {
            delay(500)
            val onWifi = isOnLocalNetwork()
            val ok = onWifi && runCatching {
                val req = Request.Builder()
                    .url(local.newBuilder().addPathSegments("api/server/ping").build())
                    .tag(NoRewrite::class.java, NoRewrite)
                    .apply { config.headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                probeClient.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (_usingLocal.value != ok) AppLog.i("Network", if (ok) "Using the home address ${local.host}" else "Using the public address")
            _usingLocal.value = ok
        }
    }

    private fun isOnLocalNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Watches for network changes so the home address is picked up (or dropped) right away. */
    fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = recheck()
            override fun onLost(network: Network) = recheck()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (wifi != _usingLocal.value) recheck()
            }
        })
    }

    // ---------------- Client certificates ----------------

    /** Hands the chosen certificate from Android's store to TLS when a server asks for one. */
    private val keyManager = object : X509ExtendedKeyManager() {
        @Volatile private var cachedAlias: String? = null
        @Volatile private var chain: Array<X509Certificate>? = null
        @Volatile private var key: PrivateKey? = null

        fun reset() {
            cachedAlias = null
            chain = null
            key = null
        }

        private fun load(alias: String) {
            if (cachedAlias == alias && key != null) return
            chain = runCatching { KeyChain.getCertificateChain(context, alias) }.getOrNull()
            key = runCatching { KeyChain.getPrivateKey(context, alias) }.getOrNull()
            cachedAlias = alias
            if (key == null) AppLog.e("Network", "Client certificate \"$alias\" isn't available")
        }

        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = config.certAlias
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = config.certAlias
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = config.certAlias?.let { arrayOf(it) }
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = alias?.let { load(it); chain }
        override fun getPrivateKey(alias: String?): PrivateKey? = alias?.let { load(it); key }
    }

    /** The platform trust manager (respects the app's network security config, so user CAs work). */
    val trustManager: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustManager), null) }
    }
}

/** "Name: value" lines → headers. Blank lines and lines without a colon are ignored. */
fun parseHeaders(text: String): Map<String, String> = text.lines().mapNotNull { line ->
    val i = line.indexOf(':')
    if (i <= 0) null else line.substring(0, i).trim().takeIf { n -> n.isNotEmpty() && n.all { it.isLetterOrDigit() || it in "-_" } }?.let { it to line.substring(i + 1).trim() }
}.toMap()

fun formatHeaders(headers: Map<String, String>): String = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
