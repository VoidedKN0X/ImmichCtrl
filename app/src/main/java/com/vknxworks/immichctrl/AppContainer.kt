package com.vknxworks.immichctrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import com.vknxworks.immichctrl.data.net.CleartextPolicy
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import com.vknxworks.immichctrl.data.timeline.Sources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
                req.url.host.equals(client.origin.host, ignoreCase = true) &&
                req.url.port == client.origin.port &&
                req.url.scheme == client.origin.scheme
            if (needsAuth) {
                val (h, v) = client!!.authHeader
                chain.proceed(req.newBuilder().header(h, v).build())
            } else {
                chain.proceed(req)
            }
        }
        // Plain HTTP only to the user's own server (after they chose it), never via a redirect from HTTPS.
        .addNetworkInterceptor(CleartextPolicy.interceptor)
        .build()

    val sessions = SessionManager(context, http, appScope)
    val settings = SettingsRepository(context, appScope)
    val backupDb = BackupDb(context)
    val backup = BackupManager(context, settings, sessions, backupDb, appScope)

    /** Server capabilities for the active account (smart search, map, OAuth...). */
    private val _features = MutableStateFlow(ServerFeaturesDto())
    val features: StateFlow<ServerFeaturesDto> = _features.asStateFlow()
    private val _serverConfig = MutableStateFlow(ServerConfigDto())
    val serverConfig: StateFlow<ServerConfigDto> = _serverConfig.asStateFlow()

    init {
        createChannels()
        appScope.launch {
            sessions.active.distinctUntilChangedBy { it?.account?.key }.collect { s ->
                Sources.clear()
                if (s == null) return@collect
                runCatching { s.client.serverFeatures() }.onSuccess { _features.value = it }
                runCatching { s.client.serverConfig() }.onSuccess { _serverConfig.value = it }
            }
        }
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
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ImmichCtrlApp).container
