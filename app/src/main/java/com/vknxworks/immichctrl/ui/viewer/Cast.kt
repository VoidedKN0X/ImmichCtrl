package com.vknxworks.immichctrl.ui.viewer

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.createSession
import com.vknxworks.immichctrl.data.api.deleteSession
import kotlinx.coroutines.launch
import com.vknxworks.immichctrl.data.log.AppLog
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant

/** Cast setup: Google's Default Media Receiver, which can show photos and play videos from a URL. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/**
 * Sends the photo or video on screen to a Cast device. The TV fetches it itself, so the URL carries
 * a short-lived Immich session key (15 minutes, renewed as needed) instead of the app's credentials,
 * and uses the home address when the phone is on it.
 */
object CastController {
    private var token: String? = null
    private var sessionId: String? = null
    /** The account the session key belongs to: never reused for another account. */
    private var tokenAccount: String? = null
    private var expires: Instant = Instant.EPOCH

    /**
     * Starts Google's Cast library. Only called once the user taps the Cast button: starting it talks to
     * Google Play services, which reports Cast usage to Google.
     */
    fun context(ctx: Context): CastContext? = runCatching { CastContext.getSharedInstance(ctx) }.getOrNull()

    /** The Cast library if it was already started in this run of the app (never starts it). */
    @Suppress("DEPRECATION")
    fun started(): CastContext? = runCatching { CastContext.getSharedInstance() }.getOrNull()

    fun connected(): Boolean = started()?.sessionManager?.currentCastSession?.isConnected == true

    suspend fun show(ctx: Context, asset: Asset) {
        val c = ctx.container
        val session = started()?.sessionManager?.currentCastSession ?: return
        if (!session.isConnected || !asset.isRemote) return
        val client = c.api
        val account = c.sessions.active.value?.account?.key
        if (token == null || tokenAccount != account || Instant.now().isAfter(expires.minusSeconds(60))) {
            revoke(ctx)
            val s = client.createSession(15 * 60)
            token = s.token
            sessionId = s.id.ifBlank { null }
            tokenAccount = account
            expires = s.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now().plusSeconds(15 * 60)
        }
        val base = if (asset.isImage) client.thumbnailUrl(asset.id, "preview") else client.videoUrl(asset.id)
        val url = reachable(ctx, base) + "&sessionKey=$token"
        val metadata = MediaMetadata(if (asset.isImage) MediaMetadata.MEDIA_TYPE_PHOTO else MediaMetadata.MEDIA_TYPE_MOVIE)
        val info = MediaInfo.Builder(url)
            .setContentType(if (asset.isImage) "image/jpeg" else "video/mp4")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
    }

    /** The URL the TV should use: the home address when the phone is using it (both are on the same network). */
    private fun reachable(ctx: Context, url: String): String {
        val router = ctx.container.router
        val local = router.config.local ?: return url
        if (!router.usingLocal.value) return url
        val u = url.toHttpUrl()
        return u.newBuilder()
            .scheme(local.scheme).host(local.host).port(local.port)
            .encodedPath(local.encodedPath.trimEnd('/') + u.encodedPath)
            .build().toString()
    }

    fun stop(ctx: Context) {
        started()?.sessionManager?.endCurrentSession(true)
        revoke(ctx)
    }

    /** Ends the short-lived session the TV was given, so its key stops working right away. */
    fun revoke(ctx: Context) {
        val id = sessionId
        val owner = tokenAccount
        token = null
        sessionId = null
        tokenAccount = null
        expires = Instant.EPOCH
        if (id == null) return
        val c = ctx.applicationContext.container
        val session = c.sessions.active.value ?: return
        if (session.account.key != owner) return
        c.appScope.launch { runCatching { session.client.deleteSession(id) } }
    }
}

/**
 * The Cast icon: pick a device, or (when casting) see and stop the session. Google's Cast library is only
 * started when the icon is tapped, so just opening a photo never involves Google Play services.
 */
@Composable
fun CastButton(current: Asset?, tint: Color) {
    val ctx = LocalContext.current
    var cast by remember { mutableStateOf(CastController.started()) }
    // No Google Play services (or Cast failed to start): the icon goes away.
    var unavailable by remember { mutableStateOf(false) }
    if (unavailable) return
    var connected by remember { mutableStateOf(CastController.connected()) }

    DisposableEffect(cast) {
        val castContext = cast ?: return@DisposableEffect onDispose { }
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) { connected = true }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { connected = true }
            override fun onSessionEnded(session: CastSession, error: Int) { connected = false; CastController.revoke(ctx) }
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) { connected = false }
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) { connected = false }
            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        }
        castContext.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        onDispose { castContext.sessionManager.removeSessionManagerListener(listener, CastSession::class.java) }
    }

    // Follow the viewer: whatever is on screen is on the TV.
    androidx.compose.runtime.LaunchedEffect(connected, current?.id) {
        if (connected && current != null) {
            runCatching { CastController.show(ctx, current) }.onFailure { AppLog.e("Cast", "Couldn't cast", it) }
        }
    }

    IconButton(onClick = {
        val started = cast ?: CastController.context(ctx)
        if (started == null) {
            unavailable = true
            return@IconButton
        }
        cast = started
        val themed = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog)
        if (connected) {
            MediaRouteControllerDialog(themed).show()
        } else {
            val selector = started.mergedSelector ?: return@IconButton
            MediaRouteChooserDialog(themed).apply { routeSelector = selector }.show()
        }
    }) {
        Icon(if (connected) Icons.Filled.CastConnected else Icons.Filled.Cast, "Cast", tint = tint)
    }
}
