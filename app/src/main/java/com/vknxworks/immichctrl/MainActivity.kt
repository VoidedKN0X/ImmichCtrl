package com.vknxworks.immichctrl

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.immichctrl.data.backup.DeviceActions
import com.vknxworks.immichctrl.data.security.ScreenPrivacy
import com.vknxworks.immichctrl.data.security.isForeignContentUri
import com.vknxworks.immichctrl.ui.nav.AppRoot
import com.vknxworks.immichctrl.ui.theme.ImmichCtrlTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    /** The system dialog for deleting or trashing photos on the phone. */
    private var pendingDevice: DeviceActions.Request? = null
    private val deviceRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pendingDevice?.onResult?.invoke(result.resultCode == Activity.RESULT_OK)
        pendingDevice = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // FLAG_SECURE with "Hide in screenshots", and always while sign-in, Networking, the locked folder,
            // the lock screen or a PIN screen is showing.
            val secure = settings.hideScreenshots || ScreenPrivacy.secure
            // "Hide in recent apps", and always with the app lock on: a blank card instead of a snapshot.
            val blankRecents = settings.hideRecents || settings.appLock || c.vault.enabled
            LaunchedEffect(blankRecents) { setRecentsScreenshotEnabled(!blankRecents) }
            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            // Ctrl / Signal follows the phone's dark theme: edge to edge over the app's background, with light bar
            // icons in dark mode and dark icons in light mode.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.decorView.setBackgroundColor((if (dark) CtrlColors.Dark else CtrlColors.Light).background.toArgb())
                onDispose { }
            }
            ImmichCtrlTheme(settings.theme, settings.dynamicColor) {
                AppRoot()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                c.device.requests.collect { req ->
                    pendingDevice = req
                    com.vknxworks.immichctrl.data.security.expectAppReturn(this@MainActivity)
                    runCatching { deviceRequest.launch(IntentSenderRequest.Builder(req.sender).build()) }
                        .onFailure { req.onResult(false); pendingDevice = null }
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val c = container
        intent.getStringExtra(EXTRA_OPEN)?.let { c.openRequests.value = it }
        val data = intent.data
        when {
            // Back from the browser after an OAuth sign-in.
            intent.action == Intent.ACTION_VIEW && data?.scheme == "app.immich" -> c.sessions.completeOAuth(data)
            // "Open with ImmichCtrl": look at it first, then upload if wanted.
            intent.action == Intent.ACTION_VIEW && data != null -> {
                if (isForeignContentUri(this, data)) c.externalUris.value = listOf(data)
            }
            // "Share to ImmichCtrl": ask before uploading, so another app can't upload photos on its own.
            intent.action == Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { askUpload(listOf(it)) }
            }
            intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { askUpload(it) }
            }
        }
    }

    private fun askUpload(uris: List<Uri>) {
        val safe = uris.filter { isForeignContentUri(this, it) }.distinct()
        if (safe.isEmpty()) return
        container.pendingUploads.value = safe
    }

    /** Photos shared to ImmichCtrl from another app are uploaded to the signed-in account. */
    fun uploadShared(uris: List<Uri>, albumId: String? = null) {
        @Suppress("NAME_SHADOWING")
        val uris = uris.filter { isForeignContentUri(this, it) }
        if (uris.isEmpty()) return
        val c = container
        lifecycleScope.launch {
            c.sessions.ready.first { it }
            if (c.sessions.active.value == null) {
                Toast.makeText(this@MainActivity, Strings.str(R.string.sign_in_to_an_immich_server_first), Toast.LENGTH_LONG).show()
                return@launch
            }
            if (c.settings.current().readOnly) {
                Toast.makeText(this@MainActivity, Strings.str(R.string.uploading_is_off_in_read_only_mode), Toast.LENGTH_LONG).show()
                return@launch
            }
            val n = c.backup.uploadShared(uris, albumId)
            val msg = if (n == 0) Strings.str(R.string.couldn_t_read_what_was_shared) else Strings.plural(R.plurals.uploading_to_immich, n, n)
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Intent extra: what to open ("memory:<id>", "asset:<id>", "admin"). */
        const val EXTRA_OPEN = "open"
    }
}
