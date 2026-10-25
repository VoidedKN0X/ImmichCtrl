package com.vknxworks.immichctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import com.vknxworks.immichctrl.data.settings.ShareSize
import com.vknxworks.immichctrl.ui.components.findActivity
import com.vknxworks.immichctrl.ui.nav.LogsRoute
import com.vknxworks.immichctrl.ui.nav.NetworkingRoute
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.settings.AppSettings
import com.vknxworks.immichctrl.data.settings.GroupBy
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupChoice
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.GroupSwitch
import com.vknxworks.immichctrl.ui.components.NotificationsBlockedRow
import com.vknxworks.immichctrl.ui.components.rememberNotificationsAllowed
import com.vknxworks.immichctrl.ui.components.rememberNotifyPermission
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.nav.AddAccountRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import kotlinx.coroutines.launch
import com.vknxworks.immichctrl.R
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.data.lock.LockVault
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Layers
import com.vknxworks.immichctrl.data.security.PinRequest
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.data.sync.SyncEngine
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Info

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    // The server alerts switch only shows "on" while ImmichCtrl may actually show notifications.
    val (canNotify, setCanNotify) = rememberNotificationsAllowed()
    val notify = rememberNotifyPermission { setCanNotify(it) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            // No Appearance group: Ctrl / Signal is dark only, with one accent (no light theme or wallpaper colours).
            if (session?.account?.isAdmin == true) {
                Group(stringResource(R.string.notifications), footer = stringResource(R.string.server_alerts_are_checked_about_once)) {
                    if (!canNotify) NotificationsBlockedRow(stringResource(R.string.server_alerts_can_t_reach_you), notify.request)
                    GroupSwitch(stringResource(R.string.notify_me_about_the_server), stringResource(R.string.failed_jobs_low_disk_space_and_new), s.adminAlerts && canNotify, Icons.Outlined.NotificationsActive) { v ->
                        update { it.copy(adminAlerts = v) }
                        if (v && !canNotify) notify.request()
                    }
                    if (s.adminAlerts && canNotify) {
                        GroupChoice(stringResource(R.string.warn_when_the_disk_is), listOf(75, 80, 85, 90, 95).map { it to Strings.str(R.string.disk_percent_full, it) }, s.adminDiskPercent, Icons.Outlined.SdStorage) { v ->
                            update { it.copy(adminDiskPercent = v) }
                        }
                    }
                }
            }
            Group(
                stringResource(R.string.group_privacy),
                footer = stringResource(R.string.privacy_footer),
            ) {
                val lockOn = c.vault.enabled
                GroupSwitch(
                    stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    lockOn,
                    Icons.Outlined.Lock,
                ) { v -> c.appLock.request = if (v) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { c.appLock.request = PinRequest.CHANGE },
                    )
                    val bioAvailable = remember { c.appLock.biometricAvailable() }
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_settings_subtitle else R.string.lock_fingerprint_unavailable),
                        c.vault.biometricEnabled,
                        Icons.Outlined.Fingerprint,
                        enabled = bioAvailable || c.vault.biometricEnabled,
                    ) { v ->
                        if (!v) {
                            c.vault.disableBiometric()
                        } else if (bioAvailable) {
                            val activity = context.findActivity() ?: return@GroupSwitch
                            val cipher = c.vault.biometricEnrolCipher() ?: run {
                                android.widget.Toast.makeText(context, R.string.lock_fingerprint_setup_failed, android.widget.Toast.LENGTH_SHORT).show()
                                return@GroupSwitch
                            }
                            c.appLock.biometricPrompt(activity, context.getString(R.string.lock_fingerprint_enable_title), cipher, subtitle = context.getString(R.string.lock_fingerprint_enable_subtitle)) { unlocked ->
                                if (!c.vault.finishBiometricEnrol(unlocked)) {
                                    android.widget.Toast.makeText(context, R.string.lock_fingerprint_setup_failed, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        listOf(
                            0 to R.string.lock_again_immediately,
                            60 to R.string.lock_again_1m,
                            300 to R.string.lock_again_5m,
                            900 to R.string.lock_again_15m,
                            3600 to R.string.lock_again_1h,
                            -1 to R.string.lock_again_restart,
                        ).map { (v, label) -> v to stringResource(label) },
                        s.appLockTimeout,
                        Icons.Outlined.Timer,
                    ) { v -> update { it.copy(appLockTimeout = v) } }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    s.hideScreenshots,
                    Icons.Outlined.VisibilityOff,
                ) { v -> update { it.copy(hideScreenshots = v) } }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    s.hideRecents,
                    Icons.Outlined.Layers,
                ) { v -> update { it.copy(hideRecents = v) } }
                val canBioPin = remember { c.appLock.canUseBiometricPin() }
                GroupSwitch(
                    stringResource(R.string.fingerprint_for_the_locked_folder),
                    if (canBioPin) stringResource(R.string.remembers_your_locked_folder_pin_on) else stringResource(R.string.needs_a_fingerprint_or_secure_face),
                    s.lockedFolderBiometric && canBioPin,
                    Icons.Outlined.Fingerprint,
                ) { v ->
                    if (!canBioPin) return@GroupSwitch
                    update { it.copy(lockedFolderBiometric = v) }
                    if (!v) scope.launch { session?.account?.key?.let { c.appLock.forgetPin(it) } }
                }
                GroupSwitch(stringResource(R.string.read_only_mode), stringResource(R.string.only_the_timeline_nothing_can_be), s.readOnly, Icons.Outlined.ChildCare) { v ->
                    update { it.copy(readOnly = v) }
                }
                GroupSwitch(
                    stringResource(R.string.show_maps),
                    stringResource(R.string.map_tiles_come_from_your_server_s),
                    s.showMaps,
                    Icons.Outlined.Map,
                ) { v -> update { it.copy(showMaps = v) } }
            }
            Group(stringResource(R.string.photos), footer = stringResource(R.string.pinch_the_grid_to_change_how_many)) {
                GroupChoice(
                    stringResource(R.string.photos_per_row),
                    (SettingsRepository.MIN_COLUMNS..SettingsRepository.MAX_COLUMNS).map { it to it.toString() },
                    s.columns,
                    Icons.Outlined.GridView,
                ) { v -> update { it.copy(columns = v) } }
                GroupChoice(stringResource(R.string.group_by), GroupBy.entries.map { it to it.label }, s.groupBy, Icons.Outlined.CalendarViewDay) { v -> update { it.copy(groupBy = v) } }
                GroupSwitch(stringResource(R.string.widget_memories), stringResource(R.string.on_this_day_at_the_top_of_photos), s.showMemories, Icons.Outlined.AutoAwesome) { v -> update { it.copy(showMemories = v) } }
                GroupSwitch(stringResource(R.string.partners_photos_in_photos), stringResource(R.string.for_partners_you_ve_chosen_to_show), s.withPartners, Icons.Outlined.People) { v ->
                    update { it.copy(withPartners = v) }
                    Sources.refreshAll()
                }
                GroupSwitch(stringResource(R.string.ask_before_moving_to_the_trash), null, s.confirmDelete, Icons.Outlined.DeleteOutline) { v -> update { it.copy(confirmDelete = v) } }
            }
            Group(stringResource(R.string.viewer)) {
                GroupSwitch(stringResource(R.string.load_originals_when_zooming), stringResource(R.string.sharper_zoomed_in_photos_uses_more), s.loadOriginal, Icons.Outlined.HighQuality) { v ->
                    update { it.copy(loadOriginal = v) }
                }
                GroupSwitch(stringResource(R.string.play_videos_automatically), null, s.autoPlayVideos, Icons.Outlined.PlayCircle) { v -> update { it.copy(autoPlayVideos = v) } }
                GroupSwitch(stringResource(R.string.loop_videos), null, s.loopVideos, Icons.Outlined.Repeat) { v -> update { it.copy(loopVideos = v) } }
            }
            Group(stringResource(R.string.on_this_phone)) {
                GroupSwitch(stringResource(R.string.show_photos_from_this_phone), stringResource(R.string.photos_not_backed_up_yet_appear_in), s.showLocal, Icons.Outlined.PhoneAndroid) { v ->
                    update { it.copy(showLocal = v) }
                    Sources.refreshAll()
                }
                GroupSwitch(stringResource(R.string.backup_status_on_thumbnails), stringResource(R.string.a_cloud_badge_on_photos_that_are), s.storageIndicator, Icons.Outlined.CloudDone) { v ->
                    update { it.copy(storageIndicator = v) }
                }
                GroupChoice(stringResource(R.string.share_size), ShareSize.entries.map { it to it.label }, s.shareSize, Icons.Outlined.Share) { v -> update { it.copy(shareSize = v) } }
                GroupSwitch(
                    stringResource(R.string.remove_location_when_sharing),
                    stringResource(R.string.takes_the_gps_position_out_of_jpeg),
                    s.shareStripLocation,
                    Icons.Outlined.LocationOff,
                ) { v -> update { it.copy(shareStripLocation = v) } }
            }
            Group(stringResource(R.string.slideshow)) {
                GroupChoice(stringResource(R.string.each_photo_shows_for), listOf(3, 5, 8, 12, 20).map { it to Strings.str(R.string.seconds, it) }, s.slideSeconds, Icons.Outlined.Timer) { v ->
                    update { it.copy(slideSeconds = v) }
                }
                GroupSwitch(stringResource(R.string.shuffle), null, s.slideShuffle, Icons.Outlined.Shuffle) { v -> update { it.copy(slideShuffle = v) } }
                GroupSwitch(stringResource(R.string.start_again_at_the_end), null, s.slideLoop, Icons.Outlined.Repeat) { v -> update { it.copy(slideLoop = v) } }
            }
            TimelineSyncGroup()
            Group(stringResource(R.string.connection_and_help)) {
                GroupRow(stringResource(R.string.networking), stringResource(R.string.public_or_home_address_custom), icon = Icons.Outlined.Wifi, showChevron = true, onClick = { nav.navigate(NetworkingRoute) })
                GroupRow(stringResource(R.string.logs), stringResource(R.string.what_the_app_has_been_doing_to_help), icon = Icons.Outlined.BugReport, showChevron = true, onClick = { nav.navigate(LogsRoute) })
            }
            Group(stringResource(R.string.storage_on_this_phone), footer = stringResource(R.string.thumbnails_and_previews_are_kept_so)) {
                GroupChoice(
                    stringResource(R.string.image_cache),
                    listOf(256 to Strings.str(R.string.n_256_mb), 512 to Strings.str(R.string.n_512_mb), 1024 to Strings.str(R.string.n_1_gb), 2048 to Strings.str(R.string.n_2_gb), 4096 to Strings.str(R.string.n_4_gb)),
                    s.cacheMb,
                    Icons.Outlined.SdStorage,
                ) { v -> update { it.copy(cacheMb = v) } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AccountsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val accounts by c.sessions.accounts.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.servers_and_accounts))
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Group(footer = stringResource(R.string.tap_an_account_to_switch_to_it_or)) {
                accounts.forEach { a ->
                    val isActive = a.key == active?.account?.key
                    GroupRow(
                        a.name.ifBlank { a.email },
                        listOfNotNull(a.serverName, a.serverVersion, if (a.isEncrypted) null else "HTTP").joinToString(Strings.str(R.string.list_separator_wide)),
                        onClick = { if (!isActive) scope.launch { c.sessions.switchTo(a.key) } },
                        trailing = {
                            if (isActive) Icon(Icons.Filled.Check, stringResource(R.string.active), tint = MaterialTheme.colorScheme.primary)
                            else androidx.compose.material3.IconButton(onClick = { removing = a.key }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.sign_out)) }
                        },
                    )
                }
                GroupRow(stringResource(R.string.add_an_account), icon = Icons.Filled.Add, onClick = { nav.navigate(AddAccountRoute) })
            }
        }
    }
    removing?.let { key ->
        ConfirmDialog(stringResource(R.string.sign_out_of_this_account), stringResource(R.string.you_can_add_it_again_later), stringResource(R.string.sign_out), onDismiss = { removing = null }) {
            removing = null
            c.appScope.launch { c.sessions.signOut(key) }
        }
    }
}

/** Settings › Timeline sync: what the phone's copy of the timeline is doing, Sync now and Reset sync. */
@Composable
private fun TimelineSyncGroup() {
    val c = LocalContext.current.container
    val status by c.sync.status.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val usable = status is SyncEngine.Status.Ready || status is SyncEngine.Status.Failed || status is SyncEngine.Status.Syncing
    val text = when (val st = status) {
        SyncEngine.Status.Off -> stringResource(R.string.sync_off)
        is SyncEngine.Status.Unsupported -> stringResource(
            if (st.reason == SyncEngine.Reason.API_KEY) R.string.sync_unsupported_api_key else R.string.sync_unsupported_server,
        )
        is SyncEngine.Status.Syncing -> if (st.first) stringResource(R.string.sync_first, "%,d".format(st.received)) else stringResource(R.string.sync_running)
        is SyncEngine.Status.Ready -> stringResource(
            R.string.sync_ready,
            "%,d".format(st.items),
            if (st.syncedAt <= 0L || System.currentTimeMillis() - st.syncedAt < 60_000L) stringResource(R.string.sync_just_now)
            else android.text.format.DateUtils.getRelativeTimeSpanString(st.syncedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(),
        )
        is SyncEngine.Status.Failed -> stringResource(R.string.sync_failed, st.message)
    }
    Group(stringResource(R.string.timeline_sync), footer = stringResource(R.string.timeline_sync_footer)) {
        GroupRow(stringResource(R.string.sync_status), text, icon = Icons.Outlined.Info)
        if (usable) {
            GroupRow(
                stringResource(R.string.sync_now),
                stringResource(R.string.sync_now_subtitle),
                icon = Icons.Outlined.Sync,
                onClick = { c.sync.requestSync() },
            )
            GroupRow(
                stringResource(R.string.sync_reset),
                stringResource(R.string.sync_reset_subtitle),
                icon = Icons.Outlined.RestartAlt,
                onClick = { confirmReset = true },
            )
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            stringResource(R.string.sync_reset_title),
            stringResource(R.string.sync_reset_body),
            stringResource(R.string.sync_reset),
            onDismiss = { confirmReset = false },
        ) {
            confirmReset = false
            c.sync.reset()
        }
    }
}
