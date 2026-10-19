package com.vknxworks.immichctrl.data.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.immichctrl.data.api.ApiException
import com.vknxworks.immichctrl.data.api.ApiJson
import com.vknxworks.immichctrl.data.api.AuthType
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.LoginResponse
import com.vknxworks.immichctrl.data.api.ServerConfigDto
import com.vknxworks.immichctrl.data.api.ServerFeaturesDto
import com.vknxworks.immichctrl.data.api.login
import com.vknxworks.immichctrl.data.api.logout
import com.vknxworks.immichctrl.data.api.majorVersion
import com.vknxworks.immichctrl.data.api.me
import com.vknxworks.immichctrl.data.api.oauthAuthorize
import com.vknxworks.immichctrl.data.api.oauthCallback
import com.vknxworks.immichctrl.data.api.ping
import com.vknxworks.immichctrl.data.api.serverConfig
import com.vknxworks.immichctrl.data.api.serverFeatures
import com.vknxworks.immichctrl.data.api.serverVersion
import com.vknxworks.immichctrl.data.appDataStore
import com.vknxworks.immichctrl.data.log.AppLog
import com.vknxworks.immichctrl.data.net.CleartextPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class Account(
    val serverUrl: String,
    val userId: String,
    val email: String,
    val name: String,
    val authType: AuthType,
    /** Access token or API key, encrypted with the Keystore. */
    val secretEnc: String,
    val isAdmin: Boolean = false,
    val serverVersion: String? = null,
    /** The server's address on the home network (e.g. http://192.168.1.10:2283), used only while [useLocalUrl] is on. */
    val localUrl: String? = null,
    /**
     * The user picked [localUrl] as the address to use. Never changed by the app itself; accounts saved by
     * older versions (which switched automatically) start on the public address.
     */
    val useLocalUrl: Boolean = false,
    /** Extra headers sent with every request to this server (proxy tokens and the like). */
    val headers: Map<String, String> = emptyMap(),
    /** Alias of a client certificate in Android's certificate store, for servers that require one. */
    val clientCert: String? = null,
    /** [headers] as saved on disk: Keystore-encrypted, since they often hold proxy secrets. */
    val headersEnc: String? = null,
) {
    val key: String get() = "$serverUrl|$userId"
    val serverName: String get() = Uri.parse(serverUrl).host ?: serverUrl
    val isEncrypted: Boolean get() = serverUrl.startsWith("https://", ignoreCase = true)
}

/**
 * Thrown when HTTPS couldn't be reached for an address typed without a scheme. The UI asks the user
 * whether to try plain HTTP (which sends credentials unencrypted) and probes again with [httpUrl].
 */
class HttpFallbackNeeded(val httpUrl: String, cause: Throwable) :
    Exception("Couldn't connect securely (HTTPS). The server may only support plain HTTP.", cause)

/** What the sign-in screen needs to know about a server before asking for credentials. */
data class ServerProbe(
    val baseUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val features: ServerFeaturesDto,
    val config: ServerConfigDto,
    val version: String?,
)

data class ActiveSession(val account: Account, val client: ImmichClient)

private data class PendingOAuth(val probe: ServerProbe, val state: String, val verifier: String)

/** Saved Immich accounts and the active one. Tokens and keys never leave the Keystore-encrypted store. */
class SessionManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val accountsKey = stringPreferencesKey("accounts")
    private val activeKey = stringPreferencesKey("active_account")

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Called after an account is signed out, to remove what the app kept for it on the phone. */
    var onSignedOut: (suspend (Account) -> Unit)? = null

    /** Set while the browser is open for an OAuth sign-in. */
    @Volatile private var pendingOAuth: PendingOAuth? = null

    /** Result of an OAuth sign-in that finished in the browser, for the sign-in screen to show. */
    private val _oauthError = MutableStateFlow<String?>(null)
    val oauthError: StateFlow<String?> = _oauthError.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val prefs = context.appDataStore.data.first()
        val stored = prefs[accountsKey]?.let {
            runCatching { ApiJson.decodeFromString(ListSerializer(Account.serializer()), it) }.getOrNull()
        }.orEmpty()
        val list = stored.map { it.withHeadersDecrypted() }
        _accounts.value = list
        // Accounts saved by older versions kept their headers in plain text: re-save them encrypted.
        if (stored.any { it.headers.isNotEmpty() }) persist(list, prefs[activeKey])
        val acc = list.firstOrNull { it.key == prefs[activeKey] } ?: list.firstOrNull()
        if (acc != null) activate(acc)
        _ready.value = true
    }

    /**
     * Finds the server and what sign-in methods it offers. An address without a scheme is tried over
     * HTTPS only; if that fails with a connection error (not a TLS/certificate error),
     * [HttpFallbackNeeded] is thrown so the UI can ask before using plain HTTP.
     */
    suspend fun probe(address: String, headers: Map<String, String> = emptyMap()): ServerProbe {
        val raw = address.trim().trimEnd('/')
        val explicit = raw.startsWith("http://", true) || raw.startsWith("https://", true)
        val url = if (explicit) raw else "https://$raw"
        val client = ImmichClient(url, "", AuthType.PASSWORD, http)
        client.extraHeaders = headers
        CleartextPolicy.allowServer(client.baseUrl)
        try {
            val pong = client.ping()
            if (!pong.res.equals("pong", ignoreCase = true)) throw ApiException("This doesn't look like an Immich server")
        } catch (e: ApiException) {
            if (e.code == 404) throw ApiException("No Immich server found at that address")
            throw e
        } catch (e: kotlinx.serialization.SerializationException) {
            throw ApiException("No Immich server found at that address")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw e
        } catch (e: java.io.IOException) {
            // TLS and certificate failures never lead to HTTP: something answered on the HTTPS port.
            if (explicit) throw e
            if (e.isTlsProblem()) {
                throw ApiException(
                    "Secure connection failed: ${e.message ?: "TLS error"}. " +
                        "If your server only supports plain HTTP, type http:// in front of the address.",
                )
            }
            throw HttpFallbackNeeded("http://$raw", e)
        }
        val features = runCatching { client.serverFeatures() }.getOrDefault(ServerFeaturesDto())
        val config = runCatching { client.serverConfig() }.getOrDefault(ServerConfigDto())
        val version = runCatching { client.serverVersion().toString() }.getOrNull()
        return ServerProbe(client.baseUrl, headers, features, config, version)
    }

    suspend fun signInWithPassword(probe: ServerProbe, email: String, password: String): Account {
        val anon = ImmichClient(probe.baseUrl, "", AuthType.PASSWORD, http).also { it.extraHeaders = probe.headers }
        val login = anon.login(email.trim(), password)
        return finishLogin(probe, login, AuthType.PASSWORD)
    }

    suspend fun signInWithApiKey(probe: ServerProbe, apiKey: String): Account {
        val client = ImmichClient(probe.baseUrl, apiKey.trim(), AuthType.API_KEY, http).also { it.extraHeaders = probe.headers }
        val me = try {
            client.me()
        } catch (e: ApiException) {
            if (e.code == 401) throw ApiException("That API key wasn't accepted. It needs at least the user.read permission.") else throw e
        }
        val account = Account(
            serverUrl = client.baseUrl,
            userId = me.id,
            email = me.email,
            name = me.name,
            authType = AuthType.API_KEY,
            secretEnc = KeystoreCrypto.encrypt(apiKey.trim()),
            isAdmin = me.isAdmin,
            serverVersion = probe.version,
            headers = probe.headers,
        )
        save(account, client)
        return account
    }

    /** Starts an OAuth sign-in; returns the URL to open in the browser. */
    suspend fun startOAuth(probe: ServerProbe): String {
        val verifier = randomToken(48)
        val state = randomToken(24)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val anon = ImmichClient(probe.baseUrl, "", AuthType.OAUTH, http).also { it.extraHeaders = probe.headers }
        val url = anon.oauthAuthorize(OAUTH_REDIRECT, state, challenge).url
        // Only ever open a web page in the browser, whatever the server sends.
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "https" && scheme != "http") throw ApiException("The server sent an invalid sign-in address")
        pendingOAuth = PendingOAuth(probe, state, verifier)
        _oauthError.value = null
        return url
    }

    /** Called with the redirect the browser sent back to the app. */
    fun completeOAuth(callback: Uri) {
        val pending = pendingOAuth ?: return
        // Must answer the sign-in this app started (another app could send a made-up redirect).
        if (callback.getQueryParameter("state") != pending.state) {
            AppLog.w("Auth", "Ignored an OAuth redirect that didn't match the sign-in in progress")
            return
        }
        pendingOAuth = null
        scope.launch {
            try {
                val anon = ImmichClient(pending.probe.baseUrl, "", AuthType.OAUTH, http).also { it.extraHeaders = pending.probe.headers }
                val login = anon.oauthCallback(callback.toString(), pending.state, pending.verifier)
                finishLogin(pending.probe, login, AuthType.OAUTH)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _oauthError.value = e.message ?: "OAuth sign-in failed"
            }
        }
    }

    fun clearOAuthError() {
        _oauthError.value = null
    }

    private suspend fun finishLogin(probe: ServerProbe, login: LoginResponse, type: AuthType): Account {
        val client = ImmichClient(probe.baseUrl, login.accessToken, type, http).also { it.extraHeaders = probe.headers }
        val me = runCatching { client.me() }.getOrNull()
        val account = Account(
            serverUrl = client.baseUrl,
            userId = login.userId,
            email = me?.email ?: login.userEmail,
            name = me?.name ?: login.name,
            authType = type,
            secretEnc = KeystoreCrypto.encrypt(login.accessToken),
            isAdmin = me?.isAdmin ?: login.isAdmin,
            serverVersion = probe.version,
            headers = probe.headers,
        )
        save(account, client)
        return account
    }

    private suspend fun save(account: Account, client: ImmichClient) {
        client.serverMajor = majorVersion(account.serverVersion) ?: 3
        val updated = _accounts.value.filterNot { it.key == account.key } + account
        persist(updated, account.key)
        _active.value = ActiveSession(account, client)
    }

    suspend fun switchTo(key: String) {
        val acc = _accounts.value.firstOrNull { it.key == key } ?: return
        context.appDataStore.edit { it[activeKey] = key }
        activate(acc)
    }

    suspend fun signOut(key: String) {
        val acc = _accounts.value.firstOrNull { it.key == key }
        if (acc != null && acc.authType != AuthType.API_KEY) {
            // End the session on the server too; ignore failures (offline, already expired).
            clientFor(acc)?.let { c -> runCatching { c.logout() } }
        }
        val remaining = _accounts.value.filterNot { it.key == key }
        val nextActive = if (_active.value?.account?.key == key) remaining.firstOrNull() else _active.value?.account
        persist(remaining, nextActive?.key)
        if (nextActive != null) activate(nextActive) else _active.value = null
        if (acc != null) runCatching { onSignedOut?.invoke(acc) }
    }

    /** Saves connection settings for an account (home address, headers, client certificate). */
    suspend fun updateNetworking(key: String, localUrl: String?, headers: Map<String, String>, clientCert: String?) {
        val acc = _accounts.value.firstOrNull { it.key == key } ?: return
        val home = localUrl?.trim()?.ifBlank { null }
        // Removing the home address goes back to the public one; otherwise the user's choice stays.
        val updated = acc.copy(localUrl = home, useLocalUrl = acc.useLocalUrl && home != null, headers = headers, clientCert = clientCert)
        replace(updated)
    }

    /** Which saved address the account uses: the home address ([useLocal]) or the public one. Only the user changes it. */
    suspend fun useLocalAddress(key: String, useLocal: Boolean) {
        val acc = _accounts.value.firstOrNull { it.key == key } ?: return
        val updated = acc.copy(useLocalUrl = useLocal && acc.localUrl != null)
        if (updated == acc) return
        replace(updated)
    }

    private suspend fun replace(updated: Account) {
        persist(_accounts.value.map { if (it.key == updated.key) updated else it }, _active.value?.account?.key)
        val s = _active.value
        if (s != null && s.account.key == updated.key) _active.value = ActiveSession(updated, s.client)
    }

    /** Refreshes name, admin flag and server version for the active account. */
    suspend fun refreshActive() {
        val s = _active.value ?: return
        val me = runCatching { s.client.me() }.getOrNull() ?: return
        val version = runCatching { s.client.serverVersion().toString() }.getOrNull() ?: s.account.serverVersion
        s.client.serverMajor = majorVersion(version) ?: s.client.serverMajor
        val updated = s.account.copy(name = me.name, email = me.email, isAdmin = me.isAdmin, serverVersion = version)
        if (updated == s.account) return
        _active.value = ActiveSession(updated, s.client)
        persist(_accounts.value.map { if (it.key == updated.key) updated else it }, updated.key)
    }

    private fun clientFor(acc: Account): ImmichClient? {
        val secret = KeystoreCrypto.decrypt(acc.secretEnc) ?: return null
        // Reconnects always use the saved address and scheme; never switch an https account to http.
        CleartextPolicy.allowServer(acc.serverUrl)
        return ImmichClient(acc.serverUrl, secret, acc.authType, http).also {
            it.serverMajor = majorVersion(acc.serverVersion) ?: 3
        }
    }

    private fun activate(acc: Account) {
        _active.value = clientFor(acc)?.let { ActiveSession(acc, it) }
        scope.launch { refreshActive() }
    }

    private suspend fun persist(list: List<Account>, activeKeyValue: String?) {
        _accounts.value = list
        context.appDataStore.edit { prefs ->
            prefs[accountsKey] = ApiJson.encodeToString(ListSerializer(Account.serializer()), list.map { it.withHeadersEncrypted() })
            if (activeKeyValue != null) prefs[activeKey] = activeKeyValue else prefs.remove(activeKey)
        }
    }

    private fun Account.withHeadersEncrypted(): Account = copy(
        headers = emptyMap(),
        headersEnc = headers.takeIf { it.isNotEmpty() }?.let {
            KeystoreCrypto.encrypt(ApiJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), it))
        },
    )

    private fun Account.withHeadersDecrypted(): Account {
        val enc = headersEnc ?: return this
        val map = KeystoreCrypto.decrypt(enc)?.let {
            runCatching { ApiJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull()
        }
        return copy(headers = map ?: headers, headersEnc = null)
    }

    private fun Throwable.isTlsProblem(): Boolean {
        var t: Throwable? = this
        while (t != null) {
            if (t is javax.net.ssl.SSLException ||
                t is java.security.cert.CertificateException ||
                t is java.security.cert.CertPathValidatorException
            ) return true
            t = t.cause
        }
        return false
    }

    private fun randomToken(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        /**
         * The redirect Immich's own mobile app uses. Servers set up for the official app already allow it,
         * and Immich's /api/oauth/mobile-redirect forwards to it when "mobile redirect override" is on.
         */
        const val OAUTH_REDIRECT = "app.immich:///oauth-callback"
    }
}
