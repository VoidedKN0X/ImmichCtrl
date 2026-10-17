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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeChoice(val label: String) { SYSTEM("Follow system"), DARK("Dark"), LIGHT("Light"), BLACK("Pure black") }

enum class GroupBy(val label: String) { DAY("Day"), MONTH("Month") }

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Photos per row in grids; pinch to change. */
    val columns: Int = 4,
    val groupBy: GroupBy = GroupBy.DAY,
    val showMemories: Boolean = true,
    val withPartners: Boolean = true,
    /** Load the full-size original when zooming in, not just the preview. */
    val loadOriginal: Boolean = false,
    val autoPlayVideos: Boolean = true,
    val loopVideos: Boolean = true,
    val confirmDelete: Boolean = true,
    /** MB of thumbnails and previews kept on disk; takes effect after a restart. */
    val cacheMb: Int = 1024,

    // Backup
    val backupEnabled: Boolean = false,
    val backupAlbums: Set<String> = emptySet(),
    val excludedAlbums: Set<String> = emptySet(),
    val backupWifiOnly: Boolean = true,
    val backupChargingOnly: Boolean = false,
    val backupVideos: Boolean = true,
)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val columns = intPreferencesKey("columns")
        val groupBy = stringPreferencesKey("group_by")
        val memories = booleanPreferencesKey("show_memories")
        val partners = booleanPreferencesKey("with_partners")
        val original = booleanPreferencesKey("load_original")
        val autoPlay = booleanPreferencesKey("auto_play")
        val loop = booleanPreferencesKey("loop_videos")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val cache = intPreferencesKey("cache_mb")
        val backup = booleanPreferencesKey("backup_enabled")
        val backupAlbums = stringSetPreferencesKey("backup_albums")
        val excluded = stringSetPreferencesKey("excluded_albums")
        val wifi = booleanPreferencesKey("backup_wifi_only")
        val charging = booleanPreferencesKey("backup_charging_only")
        val videos = booleanPreferencesKey("backup_videos")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        columns = (this[K.columns] ?: 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
        groupBy = enumOr(this[K.groupBy], GroupBy.DAY),
        showMemories = this[K.memories] ?: true,
        withPartners = this[K.partners] ?: true,
        loadOriginal = this[K.original] ?: false,
        autoPlayVideos = this[K.autoPlay] ?: true,
        loopVideos = this[K.loop] ?: true,
        confirmDelete = this[K.confirmDelete] ?: true,
        cacheMb = this[K.cache] ?: 1024,
        backupEnabled = this[K.backup] ?: false,
        backupAlbums = this[K.backupAlbums] ?: emptySet(),
        excludedAlbums = this[K.excluded] ?: emptySet(),
        backupWifiOnly = this[K.wifi] ?: true,
        backupChargingOnly = this[K.charging] ?: false,
        backupVideos = this[K.videos] ?: true,
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
            p[K.original] = s.loadOriginal
            p[K.autoPlay] = s.autoPlayVideos
            p[K.loop] = s.loopVideos
            p[K.confirmDelete] = s.confirmDelete
            p[K.cache] = s.cacheMb
            p[K.backup] = s.backupEnabled
            p[K.backupAlbums] = s.backupAlbums
            p[K.excluded] = s.excludedAlbums
            p[K.wifi] = s.backupWifiOnly
            p[K.charging] = s.backupChargingOnly
            p[K.videos] = s.backupVideos
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 7
    }
}
