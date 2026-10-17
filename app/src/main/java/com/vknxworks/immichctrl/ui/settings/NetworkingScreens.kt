package com.vknxworks.immichctrl.ui.settings

import android.content.Intent
import android.security.KeyChain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.log.AppLog
import com.vknxworks.immichctrl.data.net.formatHeaders
import com.vknxworks.immichctrl.data.net.parseHeaders
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.findActivity
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/**
 * Per-account connection options: a home-network address used whenever it answers on Wi-Fi, extra
 * headers for servers behind an auth proxy, and a client certificate for servers that require mTLS.
 */
@Composable
fun NetworkingScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val usingLocal by c.router.usingLocal.collectAsStateWithLifecycle()
    val acc = session?.account ?: return

    var localUrl by rememberSaveable(acc.key) { mutableStateOf(acc.localUrl.orEmpty()) }
    var headers by rememberSaveable(acc.key) { mutableStateOf(formatHeaders(acc.headers)) }
    var cert by rememberSaveable(acc.key) { mutableStateOf(acc.clientCert) }
    val changed = localUrl.trim() != acc.localUrl.orEmpty() || parseHeaders(headers) != acc.headers || cert != acc.clientCert

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Networking")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Group("Now connected through") {
                GroupRow(
                    if (usingLocal) "Home network" else "Public address",
                    if (usingLocal) acc.localUrl else acc.serverUrl,
                    icon = if (usingLocal) Icons.Outlined.Home else Icons.Outlined.Public,
                )
            }
            Group(
                "Home network address",
                footer = "When you're on Wi-Fi and this address answers, the app uses it instead of ${acc.serverName}: faster, and it works even if the internet is down. Leave empty to always use the public address.",
            ) {
                OutlinedTextField(
                    localUrl, { localUrl = it },
                    label = { Text("e.g. http://192.168.1.20:2283") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Group(
                "Custom headers",
                footer = "One per line, as Name: value. Sent with every request to this server, for Cloudflare Access, an auth proxy and the like.",
            ) {
                OutlinedTextField(
                    headers, { headers = it },
                    label = { Text("CF-Access-Client-Id: …") },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Group(
                "Client certificate",
                footer = "For servers that ask the phone for a certificate (mutual TLS). Install the certificate in Android's settings first (Security → Encryption & credentials), then pick it here.",
            ) {
                GroupRow(
                    cert ?: "None",
                    if (cert == null) "Tap to choose one" else "Tap to choose another",
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = {
                        val activity = context.findActivity() ?: return@GroupRow
                        val host = runCatching { android.net.Uri.parse(acc.serverUrl) }.getOrNull()
                        KeyChain.choosePrivateKeyAlias(activity, { alias ->
                            if (alias != null) activity.runOnUiThread { cert = alias }
                        }, null, null, host?.host, host?.port ?: -1, cert)
                    },
                    trailing = if (cert != null) {
                        { IconButton(onClick = { cert = null }) { Icon(Icons.Outlined.DeleteOutline, "Don't use a certificate") } }
                    } else null,
                )
            }
            PillButton(
                "Save",
                onClick = {
                    val parsed = parseHeaders(headers)
                    scope.launch {
                        c.sessions.updateNetworking(acc.key, localUrl, parsed, cert)
                        AppLog.i("Network", "Connection settings changed")
                        c.toast("Saved")
                    }
                },
                enabled = changed,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 16.dp),
            )
            TextButton(onClick = { c.router.recheck(); c.toast("Checking the home address…") }, modifier = Modifier.padding(horizontal = Gutter)) {
                Text("Check the home address again")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** What the app has logged since it started: backup, sync, network and errors. */
@Composable
fun LogsScreen() {
    val c = LocalContext.current.container
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val state = rememberLazyListState()
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) state.scrollToItem(entries.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Logs") {
            IconButton(onClick = { clipboard.setText(AnnotatedString(AppLog.text())); c.toast("Copied") }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "ImmichCtrl log")
                    .putExtra(Intent.EXTRA_TEXT, AppLog.text())
                runCatching { c.context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Icon(Icons.Outlined.Share, "Share") }
            IconButton(onClick = { AppLog.clear() }) { Icon(Icons.Outlined.DeleteOutline, "Clear") }
        }
        if (entries.isEmpty()) {
            MessageView(Icons.Outlined.VerifiedUser, "Nothing logged yet", "Backup, sync and connection events show up here.")
        } else {
            LazyColumn(state = state, modifier = Modifier.navigationBarsPadding()) {
                items(entries) { e ->
                    Text(
                        e.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = when (e.level) {
                            AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
                            AppLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
                            AppLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
