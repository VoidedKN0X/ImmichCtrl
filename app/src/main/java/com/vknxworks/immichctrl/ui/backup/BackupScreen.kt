package com.vknxworks.immichctrl.ui.backup

import com.vknxworks.immichctrl.ui.components.CtrlProgress
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
import com.vknxworks.immichctrl.ui.components.NotificationsBlockedRow
import com.vknxworks.immichctrl.ui.components.rememberNotificationsAllowed
import com.vknxworks.immichctrl.ui.components.rememberNotifyPermission
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.RemoteImage
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

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

    val (notifyOk, setNotifyOk) = rememberNotificationsAllowed()
    val notify = rememberNotifyPermission { setNotifyOk(it) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        setNotifyOk(com.vknxworks.immichctrl.data.NotifyPermission.allowed(context))
    }
    fun askPermissions() {
        com.vknxworks.immichctrl.data.security.expectAppReturn(context)
        // Notifications are asked for together with the photos; that counts as the app's one automatic ask.
        if (com.vknxworks.immichctrl.data.NotifyPermission.permissionMissing(context)) com.vknxworks.immichctrl.data.NotifyPermission.markAsked(context)
        permissions.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    val albums = rememberLoader("local-albums:$permissionTick") { withContext(Dispatchers.IO) { if (hasAccess) LocalMedia.albums(context) else emptyList() } }
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val counts = rememberLoader("backup-counts:$permissionTick:${settings.backupAlbums.hashCode()}:${settings.backupVideos}:${settings.backupEverything}") { c.backup.counts() }
    val nav = LocalNavigator.current
    // Refresh the counts when a run finishes.
    LaunchedEffect(running != null) { if (running == null) counts.reload(silent = true) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.backup))
        if (!hasAccess) {
            MessageView(
                Icons.Outlined.PhotoLibrary,
                stringResource(R.string.allow_access_to_your_photos),
                if (partial) stringResource(R.string.immichctrl_can_only_see_the_photos)
                else stringResource(R.string.backup_needs_to_see_the_photos_and),
                actionLabel = stringResource(R.string.allow),
                onAction = {
                    if (partial) {
                        com.vknxworks.immichctrl.data.security.expectAppReturn(context)
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
                shape = com.vknxworks.immichctrl.ui.theme.CtrlShapes.Card,
                color = com.vknxworks.immichctrl.ui.theme.CtrlColors.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, com.vknxworks.immichctrl.ui.theme.CtrlColors.CardBorder),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cnt = counts.value
                    if (running != null) {
                        val done = running.progress.getInt(BackupWorker.KEY_DONE, 0)
                        val total = running.progress.getInt(BackupWorker.KEY_TOTAL, 0)
                        Text(if (total > 0) stringResource(R.string.backing_up_of, done, total) else stringResource(R.string.checking_for_new_photos), style = MaterialTheme.typography.titleLarge)
                        running.progress.getString(BackupWorker.KEY_CURRENT)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (total > 0) CtrlProgress(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        else CtrlProgress(Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = { c.backup.cancel() },
                            shape = com.vknxworks.immichctrl.ui.theme.CtrlShapes.Button,
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.vknxworks.immichctrl.ui.theme.CtrlColors.ControlBorder),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = com.vknxworks.immichctrl.ui.theme.CtrlColors.Text),
                        ) { Text(stringResource(R.string.stop)) }
                    } else {
                        Text(
                            when {
                                settings.backupAlbums.isEmpty() && !settings.backupEverything -> Strings.str(R.string.choose_what_to_back_up)
                                cnt == null -> Strings.str(R.string.counting)
                                cnt.remaining == 0 -> Strings.str(R.string.everything_is_backed_up)
                                else -> Strings.str(R.string.to_back_up, countLabel(cnt.remaining, R.plurals.count_items))
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (cnt != null && (settings.backupAlbums.isNotEmpty() || settings.backupEverything)) {
                            Text(
                                stringResource(R.string.of_on_the_server, "%,d".format(cnt.done), "%,d".format(cnt.total)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (cnt.total > 0) CtrlProgress(progress = { cnt.done.toFloat() / cnt.total }, modifier = Modifier.fillMaxWidth())
                        }
                        PillButton(
                            stringResource(R.string.back_up_now),
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
            if (settings.backupEnabled && (!batteryOk || !notifyOk)) {
                Group(stringResource(R.string.backups_may_stop_in_the_background)) {
                    if (!batteryOk) {
                        GroupRow(
                            stringResource(R.string.battery_optimisation_is_on),
                            stringResource(R.string.android_and_coloros_miui_one_ui_may),
                            icon = Icons.Outlined.BatteryAlert,
                            iconTint = MaterialTheme.colorScheme.error,
                            showChevron = true,
                            onClick = {
                                runCatching {
                                    com.vknxworks.immichctrl.data.security.expectAppReturn(context)
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }
                            },
                        )
                    }
                    if (!notifyOk) {
                        // Android's dialog while it still shows, otherwise ImmichCtrl's notification settings.
                        NotificationsBlockedRow(stringResource(R.string.long_backups_need_to_show_their), notify.request)
                    }
                }
            }

            Group(stringResource(R.string.automatic_backup), footer = stringResource(R.string.new_photos_are_uploaded_in_the)) {
                val me = c.sessions.active.value?.account?.key
                val otherAccount = settings.backupEnabled && settings.backupAccount != null && settings.backupAccount != me
                GroupSwitch(
                    stringResource(R.string.back_up_automatically),
                    if (otherAccount) stringResource(R.string.on_for_another_account_turn_it_off) else stringResource(R.string.when_new_photos_are_taken_and_every),
                    settings.backupEnabled && !otherAccount,
                    Icons.Outlined.CloudSync,
                ) { on ->
                    // Backups only ever go to the account they were turned on for.
                    update { it.copy(backupEnabled = on, backupAccount = if (on) me else null) }
                    if (on) askPermissions()
                }
                GroupSwitch(stringResource(R.string.only_on_wi_fi), null, settings.backupWifiOnly, Icons.Outlined.Wifi) { on -> update { it.copy(backupWifiOnly = on) } }
                GroupSwitch(stringResource(R.string.only_while_charging), null, settings.backupChargingOnly, Icons.Outlined.BatteryChargingFull) { on -> update { it.copy(backupChargingOnly = on) } }
                GroupSwitch(stringResource(R.string.include_videos), null, settings.backupVideos, Icons.Outlined.Videocam) { on -> update { it.copy(backupVideos = on) } }
            }

            Group(stringResource(R.string.albums), footer = stringResource(R.string.album_sync_puts_backed_up_photos)) {
                GroupSwitch(stringResource(R.string.sync_albums), stringResource(R.string.mirror_phone_albums_to_immich_albums), settings.albumSync, Icons.Outlined.PhotoAlbum) { on ->
                    update { it.copy(albumSync = on) }
                }
                if (settings.albumSync) {
                    GroupRow(
                        stringResource(R.string.reorganise_into_albums),
                        stringResource(R.string.also_file_everything_already_backed),
                        icon = Icons.Outlined.DriveFileMove,
                        onClick = {
                            c.backup.syncAlbumsNow()
                            android.widget.Toast.makeText(context, Strings.str(R.string.filing_backed_up_photos_into_albums), android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                GroupRow(stringResource(R.string.photos_on_this_phone), stringResource(R.string.browse_the_phone_s_albums_and_upload), icon = Icons.Outlined.PhoneAndroid, showChevron = true, onClick = { nav.navigate(LocalAlbumsRoute) })
                GroupRow(stringResource(R.string.free_up_space), stringResource(R.string.remove_phone_copies_of_photos_that), icon = Icons.Outlined.CleaningServices, showChevron = true, onClick = { nav.navigate(FreeUpSpaceRoute) })
            }

            Group(stringResource(R.string.what_to_back_up)) {
                GroupSwitch(stringResource(R.string.everything_on_this_phone), stringResource(R.string.every_album_including_ones_added), settings.backupEverything, Icons.Outlined.PhotoLibrary) { on ->
                    update { it.copy(backupEverything = on) }
                }
            }
            if (!settings.backupEverything) Group(stringResource(R.string.albums_on_this_phone), footer = stringResource(R.string.tick_the_albums_to_back_up_camera_is)) {
                val list = albums.value.orEmpty()
                if (list.isEmpty()) GroupRow(if (albums.value == null) stringResource(R.string.looking_for_albums) else stringResource(R.string.no_photos_found_on_this_phone))
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
                            Text(countLabel(a.count, R.plurals.count_items), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(on, onCheckedChange = { checked ->
                            update { s -> s.copy(backupAlbums = if (checked) s.backupAlbums + a.id else s.backupAlbums - a.id) }
                        })
                    }
                }
            }

            Group {
                GroupRow(
                    stringResource(R.string.check_everything_again),
                    stringResource(R.string.forget_what_this_phone_has_uploaded),
                    icon = Icons.Outlined.Refresh,
                    onClick = { confirmReset = true },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            stringResource(R.string.check_everything_again_2),
            stringResource(R.string.the_next_backup_compares_every_photo),
            stringResource(R.string.check_again),
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
