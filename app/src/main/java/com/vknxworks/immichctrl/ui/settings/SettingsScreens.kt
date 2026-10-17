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

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Group("Appearance") {
                GroupChoice("Theme", ThemeChoice.entries.map { it to it.label }, s.theme, Icons.Outlined.DarkMode) { v -> update { it.copy(theme = v) } }
                GroupSwitch("Colours from your wallpaper", "Material You instead of ImmichCtrl's periwinkle", s.dynamicColor, Icons.Outlined.Palette) { v ->
                    update { it.copy(dynamicColor = v) }
                }
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
