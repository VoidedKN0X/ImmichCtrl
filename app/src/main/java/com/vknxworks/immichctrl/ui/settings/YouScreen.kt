package com.vknxworks.immichctrl.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.vknxworks.immichctrl.data.api.deleteProfileImage
import com.vknxworks.immichctrl.data.api.majorVersion
import com.vknxworks.immichctrl.data.api.setProfileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.BuildConfig
import com.vknxworks.immichctrl.R
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
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.UserAvatar
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.AccountsRoute
import com.vknxworks.immichctrl.ui.nav.AdminRoute
import com.vknxworks.immichctrl.ui.nav.BackupRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.SettingsRoute
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/ImmichCtrl"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The You page, opened from your profile picture: who you're signed in as, your storage, backup, and where the settings live. */
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
    var avatarMenu by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) c.runTask("Profile picture updated") {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("Couldn't read that picture")
            c.api.setProfileImage(bytes, mime, if (mime.endsWith("png")) "profile.png" else "profile.jpg")
            me.reload(silent = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("You")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp + LocalBottomClearance.current)) {
            Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val m = me.value
                Box {
                    UserAvatar(acc.userId, acc.name, true, m?.profileChangedAt, m?.avatarColor, Modifier.size(96.dp).clip(CircleShape).clickable { avatarMenu = true })
                    Icon(
                        Icons.Filled.PhotoCamera, "Change profile picture",
                        Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(5.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    DropdownMenu(avatarMenu, onDismissRequest = { avatarMenu = false }) {
                        DropdownMenuItem(text = { Text("Choose a new picture") }, onClick = {
                            avatarMenu = false
                            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        if (!m?.profileImagePath.isNullOrBlank()) DropdownMenuItem(text = { Text("Remove picture") }, onClick = {
                            avatarMenu = false
                            c.runTask("Profile picture removed") { c.api.deleteProfileImage(); me.reload(silent = true) }
                        })
                    }
                }
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

            // Immich changes its API between major versions; warn if this server is outside what the app knows.
            val major = majorVersion(acc.serverVersion)
            if (major != null && major !in 2..3) {
                Group {
                    GroupRow(
                        "Server version not supported",
                        "This server runs Immich ${acc.serverVersion}. ImmichCtrl is built for Immich 2.x and 3.x, so some things may not work." +
                            if (major > 3) " Check for an app update." else " Update the server if you can.",
                        icon = Icons.Filled.Warning,
                        iconTint = MaterialTheme.colorScheme.error,
                    )
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
                // Two saved addresses: switching is always the user's choice, never automatic.
                val home = acc.localUrl
                if (home != null) {
                    val onHome = acc.useLocalUrl
                    GroupRow(
                        if (onHome) "Use public address" else "Use home address",
                        "Now using the ${if (onHome) "home" else "public"} address: ${if (onHome) home else acc.serverUrl}",
                        icon = if (onHome) Icons.Filled.Public else Icons.Filled.Home,
                        onClick = {
                            scope.launch {
                                c.useAddress(!onHome)
                                c.toast(if (onHome) "Using the public address" else "Using the home address")
                            }
                        },
                    )
                }
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

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.support_title),
                    stringResource(R.string.support_subtitle),
                    icon = Icons.Filled.Favorite,
                    showChevron = true,
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPONSOR_URL))) } },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Filled.Code,
                    showChevron = true,
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) } },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Filled.Gavel,
                    showChevron = true,
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LICENCE_URL))) } },
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
