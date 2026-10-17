package com.vknxworks.immichctrl.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.BuildConfig
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AuthType
import com.vknxworks.immichctrl.data.api.assetStats
import com.vknxworks.immichctrl.data.api.changePassword
import com.vknxworks.immichctrl.data.api.me
import com.vknxworks.immichctrl.data.api.serverStorage
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.LocalBottomClearance
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.UserAvatar
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.nav.AccountsRoute
import com.vknxworks.immichctrl.ui.nav.AdminRoute
import com.vknxworks.immichctrl.ui.nav.BackupRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.SettingsRoute
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** The You tab: who you're signed in as, your storage, backup, and where the settings live. */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val acc = session?.account ?: return
    val me = rememberLoader("you-me") { c.api.me() }
    val stats = rememberLoader("you-stats") { c.api.assetStats(visibility = "timeline") }
    val storage = rememberLoader("you-storage") { c.api.serverStorage() }
    var confirmSignOut by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp + LocalBottomClearance.current)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val m = me.value
            UserAvatar(acc.userId, acc.name, true, m?.profileChangedAt, m?.avatarColor, Modifier.size(96.dp))
            Spacer(Modifier.height(14.dp))
            Text(acc.name.ifBlank { acc.email }, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(acc.email.takeIf { it.isNotBlank() && it != acc.name }, if (acc.isAdmin) "Admin" else null).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats.value?.let {
                Text(
                    "%,d photos  ·  %,d videos".format(it.images, it.videos),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // Storage: your quota if you have one, else the server's disk.
        val m = me.value
        val quota = m?.quotaSizeInBytes
        val used = m?.quotaUsageInBytes
        val disk = storage.value
        if ((quota != null && used != null) || disk != null) {
            Group("Storage") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    val (label, fraction) = if (quota != null && quota > 0 && used != null) {
                        "${formatBytes(used)} of ${formatBytes(quota)} used" to (used.toFloat() / quota)
                    } else if (disk != null) {
                        "${disk.diskUse} of ${disk.diskSize} used on the server" to (disk.diskUsagePercentage.toFloat() / 100f)
                    } else {
                        "${formatBytes(used ?: 0)} used" to 0f
                    }
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Group {
            GroupRow(
                "Backup",
                if (settings.backupEnabled) "On  ·  ${if (settings.backupWifiOnly) "Wi-Fi only" else "Wi-Fi and mobile data"}" else "Off",
                icon = Icons.Filled.CloudUpload,
                showChevron = true,
                onClick = { nav.navigate(BackupRoute) },
            )
        }

        Group("Server") {
            GroupRow(
                acc.serverUrl,
                listOfNotNull(
                    acc.serverVersion?.let { "Immich $it" },
                    if (acc.isEncrypted) null else "Not encrypted (HTTP)",
                    when (acc.authType) {
                        AuthType.API_KEY -> "Signed in with an API key"
                        AuthType.OAUTH -> "Signed in with OAuth"
                        AuthType.PASSWORD -> null
                    },
                ).joinToString("  ·  ").ifEmpty { null },
                icon = Icons.Filled.Dns,
            )
            GroupRow("Open in browser", icon = Icons.Filled.OpenInBrowser, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(acc.serverUrl))) }
            })
            if (acc.isAdmin) {
                GroupRow("Server administration", "Storage, users and background jobs", icon = Icons.Filled.AdminPanelSettings, showChevron = true, onClick = { nav.navigate(AdminRoute) })
            }
            if (storage.value != null && acc.isAdmin) {
                GroupRow("Server disk", "${storage.value!!.diskAvailable} free of ${storage.value!!.diskSize}", icon = Icons.Filled.Storage)
            }
        }

        Group {
            GroupRow("Settings", icon = Icons.Filled.Settings, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
            GroupRow("Servers and accounts", icon = Icons.Filled.SwapHoriz, showChevron = true, onClick = { nav.navigate(AccountsRoute) })
            if (acc.authType == AuthType.PASSWORD) {
                GroupRow("Change password", icon = Icons.Filled.Password, onClick = { changingPassword = true })
            }
            GroupRow(
                "Sign out",
                icon = Icons.AutoMirrored.Filled.Logout,
                iconTint = MaterialTheme.colorScheme.error,
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { confirmSignOut = true },
            )
        }
        Text(
            "ImmichCtrl ${BuildConfig.VERSION_NAME}  ·  VKNXWorks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            "Sign out of ${acc.serverName}?",
            "Backup stops for this account. Your photos stay on the server.",
            "Sign out",
            onDismiss = { confirmSignOut = false },
        ) {
            confirmSignOut = false
            scope.launch { c.sessions.signOut(acc.key) }
        }
    }
    if (changingPassword) {
        TextInputDialog("Current password", "Current password", "Next", secret = true, onDismiss = { changingPassword = false }) { old ->
            changingPassword = false
            newPassword = old
        }
    }
    newPassword?.let { old ->
        TextInputDialog("New password", "New password", "Change", secret = true, onDismiss = { newPassword = null }) { new ->
            newPassword = null
            c.runTask("Password changed") { c.api.changePassword(old, new) }
        }
    }
}
