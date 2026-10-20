package com.vknxworks.immichctrl.ui.backup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoAlbum
import com.vknxworks.immichctrl.ui.nav.FreeUpSpaceRoute
import com.vknxworks.immichctrl.ui.nav.LocalAlbumsRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.backup.BackupWorker
import com.vknxworks.immichctrl.data.backup.LocalMedia
import com.vknxworks.immichctrl.data.settings.AppSettings
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.GroupSwitch
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.RemoteImage
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val work by c.backup.work.collectAsStateWithLifecycle(emptyList())
    var permissionTick by remember { mutableIntStateOf(0) }
    val hasAccess = remember(permissionTick) { LocalMedia.hasPermission(context) }
    val partial = remember(permissionTick) { LocalMedia.isPartialAccess(context) }
    var confirmReset by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++ }
    fun askPermissions() = permissions.launch(
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ),
    )
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    val albums = rememberLoader("local-albums:$permissionTick") { withContext(Dispatchers.IO) { if (hasAccess) LocalMedia.albums(context) else emptyList() } }
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val counts = rememberLoader("backup-counts:$permissionTick:${settings.backupAlbums.hashCode()}:${settings.backupVideos}:${settings.backupEverything}") { c.backup.counts() }
    val nav = LocalNavigator.current
    // Refresh the counts when a run finishes.
    LaunchedEffect(running != null) { if (running == null) counts.reload(silent = true) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Backup")
        if (!hasAccess) {
            MessageView(
                Icons.Outlined.PhotoLibrary,
                "Allow access to your photos",
                if (partial) "ImmichCtrl can only see the photos you picked. Allow all photos so backup can find new ones."
                else "Backup needs to see the photos and videos on this phone. Location access keeps where photos were taken.",
                actionLabel = "Allow",
                onAction = {
                    if (partial) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    } else {
                        askPermissions()
                    }
                },
            )
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            // ---- Status ----
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cnt = counts.value
                    if (running != null) {
                        val done = running.progress.getInt(BackupWorker.KEY_DONE, 0)
                        val total = running.progress.getInt(BackupWorker.KEY_TOTAL, 0)
                        Text(if (total > 0) "Backing up $done of $total" else "Checking for new photos…", style = MaterialTheme.typography.titleLarge)
                        running.progress.getString(BackupWorker.KEY_CURRENT)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (total > 0) LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { c.backup.cancel() }) { Text("Stop") }
                    } else {
                        Text(
                            when {
                                settings.backupAlbums.isEmpty() && !settings.backupEverything -> "Choose what to back up"
                                cnt == null -> "Counting…"
                                cnt.remaining == 0 -> "Everything is backed up"
                                else -> "${countLabel(cnt.remaining, "item")} to back up"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (cnt != null && (settings.backupAlbums.isNotEmpty() || settings.backupEverything)) {
                            Text(
                                "${"%,d".format(cnt.done)} of ${"%,d".format(cnt.total)} on the server",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (cnt.total > 0) LinearProgressIndicator(progress = { cnt.done.toFloat() / cnt.total }, modifier = Modifier.fillMaxWidth())
                        }
                        PillButton(
                            "Back up now",
                            onClick = { c.backup.backupNow() },
                            enabled = settings.backupAlbums.isNotEmpty() || settings.backupEverything,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Things that quietly stop background backups.
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            val batteryOk = remember(permissionTick, settings.backupEnabled) { pm?.isIgnoringBatteryOptimizations(context.packageName) == true }
            val notifyOk = remember(permissionTick) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (settings.backupEnabled && (!batteryOk || !notifyOk)) {
                Group("Backups may stop in the background") {
                    if (!batteryOk) {
                        GroupRow(
                            "Battery optimisation is on",
                            "Android (and ColorOS, MIUI, One UI) may stop background backups. Set ImmichCtrl to \"Unrestricted\" or \"Allow background activity\".",
                            icon = Icons.Outlined.BatteryAlert,
                            iconTint = MaterialTheme.colorScheme.error,
                            showChevron = true,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }
                            },
                        )
                    }
                    if (!notifyOk) {
                        GroupRow(
                            "Notifications are off",
                            "Long backups need to show their progress to keep running.",
                            icon = Icons.Outlined.NotificationsOff,
                            iconTint = MaterialTheme.colorScheme.error,
                            showChevron = true,
                            onClick = { askPermissions() },
                        )
                    }
                }
            }

            Group("Automatic backup", footer = "New photos are uploaded in the background when the conditions below are met. Photos already on the server are skipped.") {
                val me = c.sessions.active.value?.account?.key
                val otherAccount = settings.backupEnabled && settings.backupAccount != null && settings.backupAccount != me
                GroupSwitch(
                    "Back up automatically",
                    if (otherAccount) "On for another account. Turn it off and on again to back up to this one." else "When new photos are taken and every few hours",
                    settings.backupEnabled && !otherAccount,
                    Icons.Outlined.CloudSync,
                ) { on ->
                    // Backups only ever go to the account they were turned on for.
                    update { it.copy(backupEnabled = on, backupAccount = if (on) me else null) }
                    if (on) askPermissions()
                }
                GroupSwitch("Only on Wi-Fi", null, settings.backupWifiOnly, Icons.Outlined.Wifi) { on -> update { it.copy(backupWifiOnly = on) } }
                GroupSwitch("Only while charging", null, settings.backupChargingOnly, Icons.Outlined.BatteryChargingFull) { on -> update { it.copy(backupChargingOnly = on) } }
                GroupSwitch("Include videos", null, settings.backupVideos, Icons.Outlined.Videocam) { on -> update { it.copy(backupVideos = on) } }
            }

            Group("Albums", footer = "Album sync puts backed-up photos into Immich albums named like the phone albums they're in, making the albums if needed. It only adds; deleting or moving on the phone doesn't change the server.") {
                GroupSwitch("Sync albums", "Mirror phone albums to Immich albums", settings.albumSync, Icons.Outlined.PhotoAlbum) { on ->
                    update { it.copy(albumSync = on) }
                }
                if (settings.albumSync) {
                    GroupRow(
                        "Reorganise into albums",
                        "Also file everything already backed up from this phone",
                        icon = Icons.Outlined.DriveFileMove,
                        onClick = {
                            c.backup.syncAlbumsNow()
                            android.widget.Toast.makeText(context, "Filing backed-up photos into albums", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                GroupRow("Photos on this phone", "Browse the phone's albums and upload single photos", icon = Icons.Outlined.PhoneAndroid, showChevron = true, onClick = { nav.navigate(LocalAlbumsRoute) })
                GroupRow("Free up space", "Remove phone copies of photos that are backed up", icon = Icons.Outlined.CleaningServices, showChevron = true, onClick = { nav.navigate(FreeUpSpaceRoute) })
            }

            Group("What to back up") {
                GroupSwitch("Everything on this phone", "Every album, including ones added later", settings.backupEverything, Icons.Outlined.PhotoLibrary) { on ->
                    update { it.copy(backupEverything = on) }
                }
            }
            if (!settings.backupEverything) Group("Albums on this phone", footer = "Tick the albums to back up. Camera is usually the one you want.") {
                val list = albums.value.orEmpty()
                if (list.isEmpty()) GroupRow(if (albums.value == null) "Looking for albums…" else "No photos found on this phone")
                list.forEach { a ->
                    val on = a.id in settings.backupAlbums
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small)) {
                            RemoteImage(a.coverUri?.toString(), Modifier.fillMaxSize(), placeholderIcon = Icons.Outlined.PhotoLibrary)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge)
                            Text(countLabel(a.count, "item"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(on, onCheckedChange = { checked ->
                            update { s -> s.copy(backupAlbums = if (checked) s.backupAlbums + a.id else s.backupAlbums - a.id) }
                        })
                    }
                }
            }

            Group {
                GroupRow(
                    "Check everything again",
                    "Forget what this phone has uploaded and re-check every photo with the server. Nothing is uploaded twice.",
                    icon = Icons.Outlined.Refresh,
                    onClick = { confirmReset = true },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            "Check everything again?",
            "The next backup compares every photo on this phone with the server. It takes a while but won't make duplicates.",
            "Check again",
            destructive = false,
            onDismiss = { confirmReset = false },
        ) {
            confirmReset = false
            scope.launch {
                c.backup.resetRecord()
                counts.reload()
                c.backup.backupNow()
            }
        }
    }
}
