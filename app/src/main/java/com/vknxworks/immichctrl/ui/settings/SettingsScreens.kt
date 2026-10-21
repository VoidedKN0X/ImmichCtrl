package com.vknxworks.immichctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Palette
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
import com.vknxworks.immichctrl.data.settings.ThemeChoice
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupChoice
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.GroupSwitch
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

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Group(stringResource(R.string.group_appearance)) {
                GroupChoice(stringResource(R.string.theme), ThemeChoice.entries.map { it to stringResource(it.label) }, s.theme, Icons.Outlined.DarkMode) { v -> update { it.copy(theme = v) } }
                GroupSwitch(stringResource(R.string.dynamic_colour), stringResource(R.string.dynamic_colour_subtitle), s.dynamicColor, Icons.Outlined.Palette) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }
            if (session?.account?.isAdmin == true) {
                Group("Notifications", footer = "Server alerts are checked about once an hour. Only shown on admin accounts.") {
                    GroupSwitch("Notify me about the server", "Failed jobs, low disk space and new Immich releases", s.adminAlerts, Icons.Outlined.NotificationsActive) { v ->
                        update { it.copy(adminAlerts = v) }
                    }
                    if (s.adminAlerts) {
                        GroupChoice("Warn when the disk is", listOf(75, 80, 85, 90, 95).map { it to "$it% full" }, s.adminDiskPercent, Icons.Outlined.SdStorage) { v ->
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
                    "Fingerprint for the locked folder",
                    if (canBioPin) "Remembers your locked-folder PIN on this phone, protected by the fingerprint" else "Needs a fingerprint (or secure face unlock) set up on the phone",
                    s.lockedFolderBiometric && canBioPin,
                    Icons.Outlined.Fingerprint,
                ) { v ->
                    if (!canBioPin) return@GroupSwitch
                    update { it.copy(lockedFolderBiometric = v) }
                    if (!v) scope.launch { session?.account?.key?.let { c.appLock.forgetPin(it) } }
                }
                GroupSwitch("Read-only mode", "Only the timeline, nothing can be changed. Good for handing the phone to a child.", s.readOnly, Icons.Outlined.ChildCare) { v ->
                    update { it.copy(readOnly = v) }
                }
                GroupSwitch(
                    "Show maps",
                    "Map tiles come from your server's map provider (Immich's tile server unless your admin chose another), which sees your IP address and the area shown",
                    s.showMaps,
                    Icons.Outlined.Map,
                ) { v -> update { it.copy(showMaps = v) } }
            }
            Group("Photos", footer = "Pinch the grid to change how many photos fit in a row.") {
                GroupChoice(
                    "Photos per row",
                    (SettingsRepository.MIN_COLUMNS..SettingsRepository.MAX_COLUMNS).map { it to it.toString() },
                    s.columns,
                    Icons.Outlined.GridView,
                ) { v -> update { it.copy(columns = v) } }
                GroupChoice("Group by", GroupBy.entries.map { it to it.label }, s.groupBy, Icons.Outlined.CalendarViewDay) { v -> update { it.copy(groupBy = v) } }
                GroupSwitch("Memories", "\"On this day\" at the top of Photos", s.showMemories, Icons.Outlined.AutoAwesome) { v -> update { it.copy(showMemories = v) } }
                GroupSwitch("Partners' photos in Photos", "For partners you've chosen to show there", s.withPartners, Icons.Outlined.People) { v ->
                    update { it.copy(withPartners = v) }
                    Sources.refreshAll()
                }
                GroupSwitch("Ask before moving to the trash", null, s.confirmDelete, Icons.Outlined.DeleteOutline) { v -> update { it.copy(confirmDelete = v) } }
            }
            Group("Viewer") {
                GroupSwitch("Load originals when zooming", "Sharper zoomed-in photos, uses more data", s.loadOriginal, Icons.Outlined.HighQuality) { v ->
                    update { it.copy(loadOriginal = v) }
                }
                GroupSwitch("Play videos automatically", null, s.autoPlayVideos, Icons.Outlined.PlayCircle) { v -> update { it.copy(autoPlayVideos = v) } }
                GroupSwitch("Loop videos", null, s.loopVideos, Icons.Outlined.Repeat) { v -> update { it.copy(loopVideos = v) } }
            }
            Group("On this phone") {
                GroupSwitch("Show photos from this phone", "Photos not backed up yet appear in the timeline", s.showLocal, Icons.Outlined.PhoneAndroid) { v ->
                    update { it.copy(showLocal = v) }
                    Sources.refreshAll()
                }
                GroupSwitch("Backup status on thumbnails", "A cloud badge on photos that are only on the phone, or on both", s.storageIndicator, Icons.Outlined.CloudDone) { v ->
                    update { it.copy(storageIndicator = v) }
                }
                GroupChoice("Share size", ShareSize.entries.map { it to it.label }, s.shareSize, Icons.Outlined.Share) { v -> update { it.copy(shareSize = v) } }
                GroupSwitch(
                    "Remove location when sharing",
                    "Takes the GPS position out of JPEG, PNG and WebP photos you send to other apps. Videos and HEIC photos keep theirs.",
                    s.shareStripLocation,
                    Icons.Outlined.LocationOff,
                ) { v -> update { it.copy(shareStripLocation = v) } }
            }
            Group("Slideshow") {
                GroupChoice("Each photo shows for", listOf(3, 5, 8, 12, 20).map { it to "$it seconds" }, s.slideSeconds, Icons.Outlined.Timer) { v ->
                    update { it.copy(slideSeconds = v) }
                }
                GroupSwitch("Shuffle", null, s.slideShuffle, Icons.Outlined.Shuffle) { v -> update { it.copy(slideShuffle = v) } }
                GroupSwitch("Start again at the end", null, s.slideLoop, Icons.Outlined.Repeat) { v -> update { it.copy(slideLoop = v) } }
            }
            Group("Connection and help") {
                GroupRow("Networking", "Public or home address, custom headers, client certificate", icon = Icons.Outlined.Wifi, showChevron = true, onClick = { nav.navigate(NetworkingRoute) })
                GroupRow("Logs", "What the app has been doing, to help track down problems", icon = Icons.Outlined.BugReport, showChevron = true, onClick = { nav.navigate(LogsRoute) })
            }
            Group("Storage on this phone", footer = "Thumbnails and previews are kept so the grid scrolls fast offline. Takes effect next time the app starts.") {
                GroupChoice(
                    "Image cache",
                    listOf(256 to "256 MB", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB"),
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
        ScreenTopBar("Servers and accounts")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Group(footer = "Tap an account to switch to it, or the bin to sign out of it.") {
                accounts.forEach { a ->
                    val isActive = a.key == active?.account?.key
                    GroupRow(
                        a.name.ifBlank { a.email },
                        listOfNotNull(a.serverName, a.serverVersion, if (a.isEncrypted) null else "HTTP").joinToString("  ·  "),
                        onClick = { if (!isActive) scope.launch { c.sessions.switchTo(a.key) } },
                        trailing = {
                            if (isActive) Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                            else androidx.compose.material3.IconButton(onClick = { removing = a.key }) { Icon(Icons.Outlined.DeleteOutline, "Sign out") }
                        },
                    )
                }
                GroupRow("Add an account", icon = Icons.Filled.Add, onClick = { nav.navigate(AddAccountRoute) })
            }
        }
    }
    removing?.let { key ->
        ConfirmDialog("Sign out of this account?", "You can add it again later.", "Sign out", onDismiss = { removing = null }) {
            removing = null
            scope.launch { c.sessions.signOut(key) }
        }
    }
}
