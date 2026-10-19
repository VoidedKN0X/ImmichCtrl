package com.vknxworks.immichctrl.data.net

import android.content.Context
import android.security.KeyChain
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Per-account connection settings, applied to every request the app makes (API, images, video,
 * downloads, live updates):
 * - extra headers, for servers behind Cloudflare Access, Authelia and similar proxies;
 * - which saved address is in use: the public one, or the home-network one. The user picks it; the app
 *   never tests addresses or switches between them by itself;
 * - a client certificate from Android's certificate store, for servers that require mTLS.
 */
class NetworkRouter(private val context: Context) {
    data class Config(
        val external: HttpUrl? = null,
        val local: HttpUrl? = null,
        /** The user chose the home address (only honoured while one is saved). */
        val useLocal: Boolean = false,
        val headers: Map<String, String> = emptyMap(),
        val certAlias: String? = null,
    )

    @Volatile var config = Config()
        private set

    private val _usingLocal = MutableStateFlow(false)
    /** True while requests go to the home-network address. */
    val usingLocal: StateFlow<Boolean> = _usingLocal.asStateFlow()

    fun configure(external: String?, local: String?, useLocal: Boolean, headers: Map<String, String>, certAlias: String?) {
        val ext = external?.toHttpUrlOrNull()
        val loc = local?.trim()?.trimEnd('/')?.removeSuffix("/api")?.takeIf { it.isNotBlank() }?.toHttpUrlOrNull()
        loc?.let { CleartextPolicy.allowServer(it.toString()) }
        val home = useLocal && loc != null
        config = Config(ext, loc, home, headers.filterKeys { it.isNotBlank() }, certAlias?.ifBlank { null })
        keyManager.reset()
        if (_usingLocal.value != home && ext != null) {
            AppLog.i("Network", if (home) "Using the home address ${loc!!.host}" else "Using the public address")
        }
        _usingLocal.value = home
    }

    /** Whether [url] goes to the signed-in server, at either of its addresses. */
    fun isServer(url: HttpUrl): Boolean {
        val c = config
        return sameOrigin(url, c.external) || sameOrigin(url, c.local)
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl?) =
        b != null && a.host.equals(b.host, true) && a.port == b.port && a.scheme == b.scheme

    /** Application interceptor: route to the home address when the user picked it, and add custom headers. */
    val interceptor = Interceptor { chain ->
        var req: Request = chain.request()
        val c = config
        if (c.external != null) {
            if (c.useLocal && c.local != null && sameOrigin(req.url, c.external)) {
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

    /**
     * Network interceptor: credentials only ever go to the address a call was started for. Runs on
     * every hop, so a redirect to another host (or port, or scheme) loses the access token, API key,
     * cookies and the account's custom proxy headers instead of handing them to that host.
     * (OkHttp itself only drops "Authorization" on a cross-host redirect.)
     */
    val credentialScope = Interceptor { chain ->
        val req = chain.request()
        val start = req.tag(CleartextPolicy.RoutedTo::class.java)?.url ?: chain.call().request().url
        if (sameOrigin(req.url, start)) return@Interceptor chain.proceed(req)
        val strip = HashSet<String>()
        strip += CREDENTIAL_HEADERS
        strip += config.headers.keys.map { it.lowercase() }
        // Anything the caller set itself (e.g. sign-in proxy headers) apart from plain HTTP plumbing.
        chain.call().request().headers.names().forEach { n -> if (n.lowercase() !in HARMLESS_HEADERS) strip += n.lowercase() }
        val b = req.newBuilder()
        req.headers.names().forEach { n -> if (n.lowercase() in strip) b.removeHeader(n) }
        chain.proceed(b.build())
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

private val CREDENTIAL_HEADERS = setOf("authorization", "x-api-key", "cookie", "proxy-authorization", "x-immich-user-token", "x-immich-session-token", "x-immich-share-key")
private val HARMLESS_HEADERS = setOf(
    "accept", "accept-encoding", "accept-language", "user-agent", "content-type", "content-length",
    "range", "if-range", "if-none-match", "if-modified-since", "cache-control", "connection", "host",
)

/** "Name: value" lines → headers. Blank lines and lines without a colon are ignored. */
fun parseHeaders(text: String): Map<String, String> = text.lines().mapNotNull { line ->
    val i = line.indexOf(':')
    if (i <= 0) null else line.substring(0, i).trim().takeIf { n -> n.isNotEmpty() && n.all { it.isLetterOrDigit() || it in "-_" } }?.let { it to line.substring(i + 1).trim() }
}.toMap()

fun formatHeaders(headers: Map<String, String>): String = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
