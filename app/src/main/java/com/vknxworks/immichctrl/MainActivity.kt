package com.vknxworks.immichctrl

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.vknxworks.immichctrl.data.settings.ThemeChoice
import com.vknxworks.immichctrl.ui.nav.AppRoot
import com.vknxworks.immichctrl.ui.theme.ImmichCtrlTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data = intent.data
        when {
            // Back from the browser after an OAuth sign-in.
            intent.action == Intent.ACTION_VIEW && data?.scheme == "app.immich" -> container.sessions.completeOAuth(data)
            intent.action == Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uploadShared(listOf(it)) }
            }
            intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uploadShared(it) }
            }
        }
    }

    /** Photos shared to ImmichCtrl from another app are uploaded to the signed-in account. */
    private fun uploadShared(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val c = container
        lifecycleScope.launch {
            c.sessions.ready.first { it }
            if (c.sessions.active.value == null) {
                Toast.makeText(this@MainActivity, "Sign in to an Immich server first", Toast.LENGTH_LONG).show()
                return@launch
            }
            val n = c.backup.uploadShared(uris)
            val msg = if (n == 0) "Couldn't read what was shared" else "Uploading $n ${if (n == 1) "item" else "items"} to Immich"
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
