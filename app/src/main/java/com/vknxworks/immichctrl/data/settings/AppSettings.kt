package com.vknxworks.immichctrl.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vknxworks.immichctrl.data.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeChoice(@androidx.annotation.StringRes val label: Int) {
    SYSTEM(com.vknxworks.immichctrl.R.string.theme_system), DARK(com.vknxworks.immichctrl.R.string.theme_dark),
    LIGHT(com.vknxworks.immichctrl.R.string.theme_light), BLACK(com.vknxworks.immichctrl.R.string.theme_black),
}

enum class GroupBy(val label: String) { DAY("Day"), MONTH("Month") }

/** Size of photos sent to other apps. */
enum class ShareSize(val label: String) { ASK("Ask each time"), ORIGINAL("Original"), LARGE("Large (smaller file)") }

enum class AlbumSort(val label: String) {
    ACTIVITY("Recent activity"), NAME("Name"), CREATED("Date created"), NEWEST("Newest photo"), OLDEST("Oldest photo"), COUNT("Most items")
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Photos per row in grids; pinch to change. */
    val columns: Int = 4,
    val groupBy: GroupBy = GroupBy.DAY,
    val showMemories: Boolean = true,
    val withPartners: Boolean = true,
    /** Photos on this phone that aren't backed up yet show in Photos too. */
    val showLocal: Boolean = true,
    /** Small cloud icons on tiles: on the phone only, or backed up from here. */
    val storageIndicator: Boolean = true,
    /** Load the full-size original when zooming in, not just the preview. */
    val loadOriginal: Boolean = false,
    val autoPlayVideos: Boolean = true,
    val loopVideos: Boolean = true,
    val confirmDelete: Boolean = true,
    val shareSize: ShareSize = ShareSize.ASK,
    /** Remove GPS location from photos sent to other apps. */
    val shareStripLocation: Boolean = true,
    /** MB of thumbnails and previews kept on disk; takes effect after a restart. */
    val cacheMb: Int = 1024,
    val albumSort: AlbumSort = AlbumSort.ACTIVITY,
    val albumGrid: Boolean = true,

    // Slideshow
    val slideSeconds: Int = 5,
    val slideShuffle: Boolean = false,
    val slideLoop: Boolean = true,

    // Read-only (kid) mode: only the timeline, nothing can be changed.
    val readOnly: Boolean = false,

    // App lock
    val appLock: Boolean = false,
    /** Seconds in the background before the app locks again; 0 = as soon as you leave, -1 = only when the app restarts. */
    val appLockTimeout: Int = 0,
    /** Unlock the locked folder with a fingerprint once its PIN has been entered here. */
    val lockedFolderBiometric: Boolean = false,
    /** FLAG_SECURE on the whole app: screenshots, screen recordings and casting show a blank screen. */
    val hideScreenshots: Boolean = false,
    /** The recent-apps view shows a blank card instead of the app. */
    val hideRecents: Boolean = false,
    /** Show maps (the photo's mini map and the Map screen); tiles come from the server's map style. */
    val showMaps: Boolean = true,

    // Admin alerts
    val adminAlerts: Boolean = true,
    val adminDiskPercent: Int = 90,

    // Backup
    val backupEnabled: Boolean = false,
    /** The account automatic backup was turned on for; it never uploads to any other account. */
    val backupAccount: String? = null,
    /** Back up every album on the phone, including ones added later. */
    val backupEverything: Boolean = false,
    val backupAlbums: Set<String> = emptySet(),
    val excludedAlbums: Set<String> = emptySet(),
    val backupWifiOnly: Boolean = true,
    val backupChargingOnly: Boolean = false,
    val backupVideos: Boolean = true,
    /** Mirror phone albums to server albums with the same names. */
    val albumSync: Boolean = false,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val columns = intPreferencesKey("columns")
        val groupBy = stringPreferencesKey("group_by")
        val memories = booleanPreferencesKey("show_memories")
        val partners = booleanPreferencesKey("with_partners")
        val showLocal = booleanPreferencesKey("show_local")
        val indicator = booleanPreferencesKey("storage_indicator")
        val original = booleanPreferencesKey("load_original")
        val autoPlay = booleanPreferencesKey("auto_play")
        val loop = booleanPreferencesKey("loop_videos")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val shareSize = stringPreferencesKey("share_size")
        val shareStripLocation = booleanPreferencesKey("share_strip_location")
        val cache = intPreferencesKey("cache_mb")
        val albumSort = stringPreferencesKey("album_sort")
        val albumGrid = booleanPreferencesKey("album_grid")
        val slideSeconds = intPreferencesKey("slide_seconds")
        val slideShuffle = booleanPreferencesKey("slide_shuffle")
        val slideLoop = booleanPreferencesKey("slide_loop")
        val readOnly = booleanPreferencesKey("read_only")
        val appLock = booleanPreferencesKey("app_lock")
        val appLockTimeout = intPreferencesKey("app_lock_timeout")
        val lockedBio = booleanPreferencesKey("locked_folder_biometric")
        /** 0.3.11's single "Hide in screenshots and recent apps"; an explicit "on" turns both new switches on. */
        val hideContent = booleanPreferencesKey("hide_content")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        val showMaps = booleanPreferencesKey("show_maps")
        val adminAlerts = booleanPreferencesKey("admin_alerts")
        val adminDisk = intPreferencesKey("admin_disk_percent")
        val backup = booleanPreferencesKey("backup_enabled")
        val backupAccount = stringPreferencesKey("backup_account")
        val backupAll = booleanPreferencesKey("backup_everything")
        val backupAlbums = stringSetPreferencesKey("backup_albums")
        val excluded = stringSetPreferencesKey("excluded_albums")
        val wifi = booleanPreferencesKey("backup_wifi_only")
        val charging = booleanPreferencesKey("backup_charging_only")
        val videos = booleanPreferencesKey("backup_videos")
        val albumSync = booleanPreferencesKey("album_sync")
        /** Left over from the optional stats removed in 0.3.3; deleted on the next save and at start. */
        val legacy = listOf(booleanPreferencesKey("usage_stats"), booleanPreferencesKey("usage_stats_asked"))
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    init {
        // Delete what the removed optional stats stored, so nothing of them stays on the phone.
        scope.launch { context.appDataStore.edit { p -> K.legacy.forEach { p.remove(it) } } }
    }

    /** The stored settings as they're read and changed, without [settings]' placeholder defaults at start-up. */
    val stored: Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        columns = (this[K.columns] ?: 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
        groupBy = enumOr(this[K.groupBy], GroupBy.DAY),
        showMemories = this[K.memories] ?: true,
        withPartners = this[K.partners] ?: true,
        showLocal = this[K.showLocal] ?: true,
        storageIndicator = this[K.indicator] ?: true,
        loadOriginal = this[K.original] ?: false,
        autoPlayVideos = this[K.autoPlay] ?: true,
        loopVideos = this[K.loop] ?: true,
        confirmDelete = this[K.confirmDelete] ?: true,
        shareSize = enumOr(this[K.shareSize], ShareSize.ASK),
        shareStripLocation = this[K.shareStripLocation] ?: true,
        cacheMb = this[K.cache] ?: 1024,
        albumSort = enumOr(this[K.albumSort], AlbumSort.ACTIVITY),
        albumGrid = this[K.albumGrid] ?: true,
        slideSeconds = this[K.slideSeconds] ?: 5,
        slideShuffle = this[K.slideShuffle] ?: false,
        slideLoop = this[K.slideLoop] ?: true,
        readOnly = this[K.readOnly] ?: false,
        appLock = this[K.appLock] ?: false,
        // 30 seconds isn't offered any more: it becomes "as soon as you leave" rather than longer.
        appLockTimeout = (this[K.appLockTimeout] ?: 0).let { if (it == 30) 0 else it },
        hideScreenshots = this[K.hideScreenshots] ?: this[K.hideContent] ?: false,
        hideRecents = this[K.hideRecents] ?: this[K.hideContent] ?: false,
        lockedFolderBiometric = this[K.lockedBio] ?: false,
        showMaps = this[K.showMaps] ?: true,
        adminAlerts = this[K.adminAlerts] ?: true,
        adminDiskPercent = this[K.adminDisk] ?: 90,
        backupEnabled = this[K.backup] ?: false,
        backupAccount = this[K.backupAccount],
        backupEverything = this[K.backupAll] ?: false,
        backupAlbums = this[K.backupAlbums] ?: emptySet(),
        excludedAlbums = this[K.excluded] ?: emptySet(),
        backupWifiOnly = this[K.wifi] ?: true,
        backupChargingOnly = this[K.charging] ?: false,
        backupVideos = this[K.videos] ?: true,
        albumSync = this[K.albumSync] ?: false,
    )

    /** The stored settings, read fresh (for background work started before [settings] has loaded). */
    suspend fun current(): AppSettings = context.appDataStore.data.first().toSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.columns] = s.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            p[K.groupBy] = s.groupBy.name
            p[K.memories] = s.showMemories
            p[K.partners] = s.withPartners
            p[K.showLocal] = s.showLocal
            p[K.indicator] = s.storageIndicator
            p[K.original] = s.loadOriginal
            p[K.autoPlay] = s.autoPlayVideos
            p[K.loop] = s.loopVideos
            p[K.confirmDelete] = s.confirmDelete
            p[K.shareSize] = s.shareSize.name
            p[K.shareStripLocation] = s.shareStripLocation
            p[K.cache] = s.cacheMb
            p[K.albumSort] = s.albumSort.name
            p[K.albumGrid] = s.albumGrid
            p[K.slideSeconds] = s.slideSeconds
            p[K.slideShuffle] = s.slideShuffle
            p[K.slideLoop] = s.slideLoop
            p[K.readOnly] = s.readOnly
            p[K.appLock] = s.appLock
            p[K.appLockTimeout] = s.appLockTimeout
            p.remove(K.hideContent)
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p[K.lockedBio] = s.lockedFolderBiometric
            p[K.showMaps] = s.showMaps
            p[K.adminAlerts] = s.adminAlerts
            p[K.adminDisk] = s.adminDiskPercent
            p[K.backup] = s.backupEnabled
            if (s.backupAccount != null) p[K.backupAccount] = s.backupAccount else p.remove(K.backupAccount)
            p[K.backupAll] = s.backupEverything
            p[K.backupAlbums] = s.backupAlbums
            p[K.excluded] = s.excludedAlbums
            p[K.wifi] = s.backupWifiOnly
            p[K.charging] = s.backupChargingOnly
            p[K.videos] = s.backupVideos
            p[K.albumSync] = s.albumSync
            K.legacy.forEach { p.remove(it) }
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 7
    }
}
