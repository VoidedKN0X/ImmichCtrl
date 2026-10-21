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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
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
 * Per-account connection options: which saved address is in use (public or home network, picked by
 * the user and never switched automatically), the home address itself, extra headers for servers
 * behind an auth proxy, and a client certificate for servers that require mTLS.
 */
@Composable
fun NetworkingScreen() {
    // Custom headers often hold proxy secrets: never in screenshots, recordings or casting.
    com.vknxworks.immichctrl.data.security.SecureScreen()
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val usingLocal by c.router.usingLocal.collectAsStateWithLifecycle()
    val acc = session?.account ?: return

    var localUrl by rememberSaveable(acc.key) { mutableStateOf(acc.localUrl.orEmpty()) }
    // Headers often hold proxy secrets: kept in memory only, not in the saved screen state.
    var headers by remember(acc.key) { mutableStateOf(formatHeaders(acc.headers)) }
    var confirmHttp by remember { mutableStateOf(false) }
    var cert by rememberSaveable(acc.key) { mutableStateOf(acc.clientCert) }
    val changed = localUrl.trim() != acc.localUrl.orEmpty() || parseHeaders(headers) != acc.headers || cert != acc.clientCert

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Networking")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            fun pick(home: Boolean) {
                if (home == usingLocal) return
                scope.launch {
                    c.useAddress(home)
                    c.toast(if (home) "Using the home address" else "Using the public address")
                }
            }
            fun described(url: String) = if (url.startsWith("http://", ignoreCase = true)) "$url  ·  Not encrypted (HTTP)" else url
            Group(
                "Address in use",
                footer = "The app always uses the address you pick here and never switches by itself. " +
                    "Pick the public address before you leave your home network.",
            ) {
                GroupRow(
                    "Use public address",
                    described(acc.serverUrl),
                    icon = Icons.Outlined.Public,
                    onClick = { pick(false) },
                    trailing = { RadioButton(selected = !usingLocal, onClick = { pick(false) }) },
                )
                val home = acc.localUrl
                GroupRow(
                    "Use home address",
                    home?.let { described(it) } ?: "Add a home network address below first",
                    icon = Icons.Outlined.Home,
                    onClick = if (home != null) ({ pick(true) }) else null,
                    trailing = { RadioButton(selected = usingLocal, onClick = { pick(true) }, enabled = home != null) },
                )
            }
            Group(
                "Home network address",
                footer = "Your server's address on your home network, e.g. its IP and port: faster, and it works even if the internet is down. " +
                    "Save it, then pick it under Address in use. Leave empty to remove it.",
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
            fun save() {
                val parsed = parseHeaders(headers)
                scope.launch {
                    c.sessions.updateNetworking(acc.key, localUrl, parsed, cert)
                    AppLog.i("Network", "Connection settings changed")
                    c.toast("Saved")
                }
            }
            PillButton(
                "Save",
                onClick = {
                    val newHttp = localUrl.trim().startsWith("http://", ignoreCase = true) &&
                        !localUrl.trim().equals(acc.localUrl.orEmpty(), ignoreCase = true)
                    if (newHttp) confirmHttp = true else save()
                },
                enabled = changed,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
            if (confirmHttp) {
                ConfirmDialog(
                    "Use an unencrypted home address?",
                    "This address uses plain HTTP. While you use it, the app sends your sign-in and photos to it unencrypted, " +
                        "so anyone on that network can read them. The app doesn't check which network you're on: on another network, " +
                        "a different device could be at the same address. Only use it on a home network you trust, and pick the " +
                        "public address before you leave.",
                    "Use HTTP",
                    onDismiss = { confirmHttp = false },
                ) {
                    confirmHttp = false
                    save()
                }
            }
        }
    }
}

/** What the app has logged since it started: backup, sync, network and errors. */
@Composable
fun LogsScreen() {
    val c = LocalContext.current.container
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) state.scrollToItem(entries.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Logs") {
            IconButton(onClick = { com.vknxworks.immichctrl.data.security.copySensitive(c.context, AppLog.text(), "ImmichCtrl log"); c.toast("Copied") }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "ImmichCtrl log")
                    .putExtra(Intent.EXTRA_TEXT, AppLog.text())
                com.vknxworks.immichctrl.data.security.expectAppReturn(c.context)
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
