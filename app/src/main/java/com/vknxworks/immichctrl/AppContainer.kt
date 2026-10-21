package com.vknxworks.immichctrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vknxworks.immichctrl.data.admin.AdminAlertsWorker
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.NoAuth
import com.vknxworks.immichctrl.data.api.ServerConfigDto
import com.vknxworks.immichctrl.data.api.ServerFeaturesDto
import com.vknxworks.immichctrl.data.api.serverConfig
import com.vknxworks.immichctrl.data.api.serverFeatures
import com.vknxworks.immichctrl.data.auth.SessionManager
import com.vknxworks.immichctrl.data.backup.BackupDb
import com.vknxworks.immichctrl.data.backup.BackupManager
import com.vknxworks.immichctrl.data.backup.BackupWorker
import com.vknxworks.immichctrl.data.backup.DeviceActions
import com.vknxworks.immichctrl.data.backup.LocalLibrary
import com.vknxworks.immichctrl.data.log.AppLog
import com.vknxworks.immichctrl.data.net.CleartextPolicy
import com.vknxworks.immichctrl.data.net.LiveUpdates
import com.vknxworks.immichctrl.data.net.NetworkRouter
import com.vknxworks.immichctrl.data.offline.OfflineStore
import com.vknxworks.immichctrl.data.offline.OfflineWorker
import com.vknxworks.immichctrl.data.security.AppLock
import com.vknxworks.immichctrl.data.lock.LockVault
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.widget.WidgetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Address in use (public or home), extra headers and client certificate for the signed-in account. */
    val router = NetworkRouter(context)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .sslSocketFactory(router.sslContext.socketFactory, router.trustManager)
        .addInterceptor(router.interceptor)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "ImmichCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        // Images, video and downloads go through this client too: give requests to the signed-in
        // server its credentials, so URLs stay clean (and cacheable) and nothing else ever sees them.
        .addInterceptor { chain ->
            val req = chain.request()
            val client = sessions.active.value?.client
            val needsAuth = client != null &&
                req.tag(NoAuth::class.java) == null &&
                req.header("Authorization") == null &&
                req.header("x-api-key") == null &&
                router.isServer(req.url)
            if (needsAuth) {
                val (h, v) = client!!.authHeader
                chain.proceed(req.newBuilder().header(h, v).build())
            } else {
                chain.proceed(req)
            }
        }
        // Failed requests go to the in-app log (without query strings, which may carry keys).
        .addInterceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            try {
                chain.proceed(req).also { if (it.code >= 400 && it.code != 404) AppLog.w("HTTP", "${req.method} $path -> ${it.code}") }
            } catch (e: IOException) {
                if (!path.contains("socket.io")) AppLog.w("HTTP", "${req.method} $path failed: ${e.message ?: e::class.simpleName}")
                throw e
            }
        }
        // Plain HTTP only to the user's own server (after they chose it), never via a redirect from HTTPS.
        .addNetworkInterceptor(CleartextPolicy.interceptor)
        // Credentials and proxy headers never follow a redirect to another host.
        .addNetworkInterceptor(router.credentialScope)
        .build()

    val settings = SettingsRepository(context, appScope)

    /** The app lock's PIN, fingerprint key and the key that seals logins while the app is locked. */
    val vault = LockVault(context, "immichctrl")
    val sessions = SessionManager(context, http, appScope, vault)
    val backupDb = BackupDb(context)
    val backup = BackupManager(context, settings, sessions, backupDb, appScope)
    val local = LocalLibrary(context, backupDb, sessions, settings)
    val device = DeviceActions(context)
    val offline = OfflineStore(context)
    val appLock = AppLock(context, settings, vault)

    /** Turns the app lock on: the PIN seals the logins from now on (except the one backups need). */
    suspend fun enableLock(pin: String) {
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        settings.update { it.copy(appLock = true) }
        sessions.reseal()
        appLock.unlocked()
    }

    /** Turns the app lock off (after the PIN was confirmed): logins go back to Keystore-only encryption. */
    suspend fun disableLock() {
        sessions.unsealAll()
        settings.update { it.copy(appLock = false) }
        vault.disable()
    }

    /** "Forgot PIN?": the sealed logins can't be decrypted any more, so sign out everywhere and start over. */
    suspend fun forgetEverything() {
        sessions.forgetAll()
        settings.update { it.copy(appLock = false) }
        vault.disable()
        appLock.unlocked()
    }
    val live = LiveUpdates(http, appScope)

    /** Server capabilities for the active account (smart search, map, OAuth...). */
    private val _features = MutableStateFlow(ServerFeaturesDto())
    val features: StateFlow<ServerFeaturesDto> = _features.asStateFlow()
    private val _serverConfig = MutableStateFlow(ServerConfigDto())
    val serverConfig: StateFlow<ServerConfigDto> = _serverConfig.asStateFlow()

    /** Something to open, from a notification, widget or another app ("memory:<id>", "asset:<id>", "admin"). */
    val openRequests = MutableStateFlow<String?>(null)

    /** Photos another app shared to us, waiting for the user to confirm the upload. */
    val pendingUploads = MutableStateFlow<List<Uri>?>(null)

    /** Photos another app asked us to open ("Open with ImmichCtrl"). */
    val externalUris = MutableStateFlow<List<Uri>?>(null)

    init {
        createChannels()
        // The login automatic backup (and admin alerts) use stays usable while the app is locked;
        // every other one is sealed by the app lock.
        sessions.backgroundUse = { acc ->
            val s = settings.settings.value
            (s.backupEnabled && (s.backupAccount == null || s.backupAccount == acc.key)) || (s.adminAlerts && acc.isAdmin)
        }
        appLock.onLocked = { appScope.launch { sessions.onLocked() } }
        appLock.onUnlocking = { sessions.onUnlocked() }
        appScope.launch {
            settings.settings.map { it.appLock }.distinctUntilChanged().collect { WidgetWorker.refreshAll(context) }
        }
        appScope.launch {
            settings.settings.map { Triple(it.backupEnabled, it.backupAccount, it.adminAlerts) }
                .distinctUntilChanged()
                .collect { sessions.reseal() }
        }
        // Share and upload copies of photos never stay in the app's cache for long.
        appScope.launch(Dispatchers.IO) { runCatching { com.vknxworks.immichctrl.data.share.Exporter.cleanUp(context) } }
        // Signing out leaves nothing of that account's photos on the phone.
        sessions.onSignedOut = { acc ->
            withContext(Dispatchers.IO) {
                // Each step on its own, so one failing doesn't leave the rest behind.
                runCatching { offline.deleteAccount(acc.key) }
                runCatching { backupDb.reset(acc.key) }
                runCatching { appLock.forgetPin(acc.key) }
                // Automatic backup was for this account: it stops, and doesn't quietly resume if the
                // account (or someone else with the same server address and user id) is added again.
                runCatching {
                    if (settings.current().backupAccount == acc.key) {
                        settings.update { it.copy(backupEnabled = false, backupAccount = null) }
                    }
                }
                runCatching { java.io.File(context.cacheDir, "shared").deleteRecursively() }
                runCatching {
                    val loader = coil3.SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                // Map tiles cached by the photo's mini map and the Map screen show where photos were taken.
                withContext(Dispatchers.Main) {
                    runCatching {
                        org.maplibre.android.MapLibre.getInstance(context)
                        org.maplibre.android.offline.OfflineManager.getInstance(context).clearAmbientCache(
                            object : org.maplibre.android.offline.OfflineManager.FileSourceCallback {
                                override fun onSuccess() = Unit
                                override fun onError(message: String) = Unit
                            },
                        )
                    }
                }
                AppLog.i("Account", "Signed out of ${acc.serverName}; removed its offline albums and cached photos")
            }
        }
        local.watchGallery()
        appScope.launch {
            sessions.active.distinctUntilChangedBy { it?.account }.collect { s ->
                val acc = s?.account
                val wasLocal = router.usingLocal.value
                router.configure(acc?.serverUrl, acc?.localUrl, acc?.useLocalUrl == true, acc?.headers.orEmpty(), acc?.clientCert)
                // Same account, other address: live updates move over too.
                if (wasLocal != router.usingLocal.value) live.reconnect()
                s?.client?.extraHeaders = acc?.headers.orEmpty()
            }
        }
        appScope.launch {
            sessions.active.distinctUntilChangedBy { it?.account?.key }.collect { s ->
                Sources.clear()
                live.setClient(s?.client)
                offline.setAccount(s?.account?.key)
                local.changed()
                // Never another server's settings (its map provider, external domain for shared links...).
                _features.value = ServerFeaturesDto()
                _serverConfig.value = ServerConfigDto()
                if (s == null) return@collect
                runCatching { s.client.serverFeatures() }.onSuccess { _features.value = it }
                runCatching { s.client.serverConfig() }.onSuccess { _serverConfig.value = it }
                WidgetWorker.refreshAll(context)
            }
        }
        // Admin alerts run only while an admin account is signed in and they're turned on.
        appScope.launch {
            combine(sessions.active, settings.settings) { s, st -> s?.account?.isAdmin == true && st.adminAlerts }
                .distinctUntilChanged()
                .collect { AdminAlertsWorker.schedule(context, it) }
        }
        appScope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    live.setForeground(true)
                    appScope.launch { appLock.onForeground() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    live.setForeground(false)
                    appLock.onBackground()
                }
            })
        }
    }

    /**
     * Switches the signed-in account to its home address ([useLocal]) or its public address, because the
     * user asked to, and waits until requests go there. The app never calls this on its own.
     */
    suspend fun useAddress(useLocal: Boolean) {
        val acc = sessions.active.value?.account ?: return
        val target = useLocal && acc.localUrl != null
        sessions.useLocalAddress(acc.key, target)
        withTimeoutOrNull(3_000) { router.usingLocal.first { it == target } }
    }

    /** Client for the signed-in account. Screens are only shown while signed in. */
    val api: ImmichClient
        get() = sessions.active.value?.client ?: error("Not signed in")

    val me: String
        get() = sessions.active.value?.account?.userId.orEmpty()

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(BackupWorker.CHANNEL, "Backup", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while photos and videos are backed up"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(OfflineWorker.CHANNEL, "Offline albums", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while albums are saved for offline viewing"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(AdminAlertsWorker.CHANNEL, "Server alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "For admins: failed jobs, low disk space and new Immich releases"
            },
        )
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ImmichCtrlApp).container
