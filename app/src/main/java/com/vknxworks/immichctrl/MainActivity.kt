package com.vknxworks.immichctrl

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.immichctrl.data.backup.DeviceActions
import com.vknxworks.immichctrl.data.settings.ThemeChoice
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
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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
            intent.action == Intent.ACTION_VIEW && data != null -> c.externalUris.value = listOf(data)
            intent.action == Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uploadShared(listOf(it)) }
            }
            intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uploadShared(it) }
            }
        }
    }

    /** Photos shared to ImmichCtrl from another app are uploaded to the signed-in account. */
    fun uploadShared(uris: List<Uri>, albumId: String? = null) {
        if (uris.isEmpty()) return
        val c = container
        lifecycleScope.launch {
            c.sessions.ready.first { it }
            if (c.sessions.active.value == null) {
                Toast.makeText(this@MainActivity, "Sign in to an Immich server first", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (c.settings.current().readOnly) {
                Toast.makeText(this@MainActivity, "Uploading is off in read-only mode", Toast.LENGTH_LONG).show()
                return@launch
            }
            val n = c.backup.uploadShared(uris, albumId)
            val msg = if (n == 0) "Couldn't read what was shared" else "Uploading $n ${if (n == 1) "item" else "items"} to Immich"
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Intent extra: what to open ("memory:<id>", "asset:<id>", "admin"). */
        const val EXTRA_OPEN = "open"
    }
}
