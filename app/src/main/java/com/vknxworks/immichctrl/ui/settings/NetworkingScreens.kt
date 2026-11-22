package com.vknxworks.immichctrl.ui.settings

import com.vknxworks.immichctrl.ui.components.ctrlFieldColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import android.security.KeyChain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.net.formatHeaders
import com.vknxworks.immichctrl.data.net.parseHeaders
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.findActivity
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

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
        ScreenTopBar(stringResource(R.string.networking))
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            fun pick(home: Boolean) {
                if (home == usingLocal) return
                scope.launch {
                    c.useAddress(home)
                    c.toast(if (home) Strings.str(R.string.using_the_home_address) else Strings.str(R.string.using_the_public_address))
                }
            }
            fun described(url: String) = if (url.startsWith("http://", ignoreCase = true)) Strings.str(R.string.not_encrypted_http, url) else url
            Group(
                stringResource(R.string.address_in_use),
                footer = stringResource(R.string.address_in_use_footer),
            ) {
                GroupRow(
                    stringResource(R.string.use_public_address),
                    described(acc.serverUrl),
                    icon = Icons.Outlined.Public,
                    onClick = { pick(false) },
                    trailing = { RadioButton(selected = !usingLocal, onClick = { pick(false) }) },
                )
                val home = acc.localUrl
                GroupRow(
                    stringResource(R.string.use_home_address),
                    home?.let { described(it) } ?: stringResource(R.string.add_a_home_network_address_below),
                    icon = Icons.Outlined.Home,
                    onClick = if (home != null) ({ pick(true) }) else null,
                    trailing = { RadioButton(selected = usingLocal, onClick = { pick(true) }, enabled = home != null) },
                )
            }
            Group(
                stringResource(R.string.home_network_address),
                footer = stringResource(R.string.home_address_footer),
            ) {
                OutlinedTextField(
                    localUrl, { localUrl = it },
                    label = { Text(stringResource(R.string.e_g_http_192_168_1_20_2283)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = CtrlShapes.Field, colors = ctrlFieldColors(),
                )
            }
            Group(
                stringResource(R.string.custom_headers),
                footer = stringResource(R.string.one_per_line_as_name_value_sent_with),
            ) {
                OutlinedTextField(
                    headers, { headers = it },
                    label = { Text(stringResource(R.string.cf_access_client_id)) },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = CtrlShapes.Field, colors = ctrlFieldColors(),
                )
            }
            Group(
                stringResource(R.string.client_certificate),
                footer = stringResource(R.string.for_servers_that_ask_the_phone_for_a),
            ) {
                GroupRow(
                    cert ?: stringResource(R.string.none),
                    if (cert == null) stringResource(R.string.tap_to_choose_one) else stringResource(R.string.tap_to_choose_another),
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = {
                        val activity = context.findActivity() ?: return@GroupRow
                        val host = runCatching { android.net.Uri.parse(acc.serverUrl) }.getOrNull()
                        KeyChain.choosePrivateKeyAlias(activity, { alias ->
                            if (alias != null) activity.runOnUiThread { cert = alias }
                        }, null, null, host?.host, host?.port ?: -1, cert)
                    },
                    trailing = if (cert != null) {
                        { IconButton(onClick = { cert = null }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.don_t_use_a_certificate)) } }
                    } else null,
                )
            }
            fun save() {
                val parsed = parseHeaders(headers)
                scope.launch {
                    c.sessions.updateNetworking(acc.key, localUrl, parsed, cert)
                    c.toast(Strings.str(R.string.saved))
                }
            }
            PillButton(
                stringResource(R.string.save),
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
                    stringResource(R.string.use_an_unencrypted_home_address),
                    stringResource(R.string.http_home_address_body),
                    stringResource(R.string.use_http),
                    onDismiss = { confirmHttp = false },
                ) {
                    confirmHttp = false
                    save()
                }
            }
        }
    }
}
