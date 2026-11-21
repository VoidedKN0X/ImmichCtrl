package com.vknxworks.immichctrl.data.settings

import androidx.annotation.StringRes
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.data.backup.BackupRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Thrown for a file that isn't an ImmichCtrl settings file, or one from a newer, incompatible version. */
class SettingsFileException(val newer: Boolean) : Exception()

/**
 * ImmichCtrl's settings as a JSON file the user keeps, and reading one back.
 *
 * Only preferences are in the file. Never in it, and ignored when a file has them anyway: accounts and
 * anything used to sign in (tokens, API keys, passwords, custom headers, client certificates, trusted
 * certificates), the app lock and its PIN, when the app locks again, fingerprint for the locked folder,
 * read-only mode, "use without a server", and whether automatic backup is on and for which account.
 * A file can turn "Hide in screenshots", "Hide in recent apps", "Remove location when sharing" and
 * "Ask before moving to the trash" on, never off.
 */
object SettingsBackups {
    const val FORMAT = "immichctrl-settings"
    const val VERSION = 1

    /** A settings file is a few kilobytes; anything far bigger isn't one. */
    const val MAX_BYTES = 256 * 1024

    /**
     * One setting in the file. [read] gives its value as JSON; [write] gives the settings with the file's
     * value put in, or null when the value isn't one this setting can have (the setting is then left alone).
     */
    class Field(
        val key: String,
        @StringRes val label: Int,
        /** For settings that are a choice: what each stored value is called on screen. */
        val valueLabels: Map<String, Int> = emptyMap(),
        val read: (AppSettings) -> JsonElement,
        val write: (AppSettings, JsonElement) -> AppSettings?,
    )

    /** A setting the file would change: what it is now and what it would become. */
    class Change(val field: Field, val from: JsonElement, val to: JsonElement)

    private fun bool(
        key: String,
        @StringRes label: Int,
        get: (AppSettings) -> Boolean,
        set: (AppSettings, Boolean) -> AppSettings,
        /** A protection: a file may switch it on, never off. */
        onlyOn: Boolean = false,
    ) = Field(key, label, read = { JsonPrimitive(get(it)) }, write = { s, e ->
        val v = (e as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        if (v == null) null else if (onlyOn && !v) s else set(s, v)
    })

    private fun int(
        key: String,
        @StringRes label: Int,
        get: (AppSettings) -> Int,
        set: (AppSettings, Int) -> AppSettings,
        valid: (Int) -> Boolean,
    ) = Field(key, label, read = { JsonPrimitive(get(it)) }, write = { s, e ->
        val v = (e as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (v == null || !valid(v)) null else set(s, v)
    })

    private fun choice(
        key: String,
        @StringRes label: Int,
        labels: Map<String, Int>,
        get: (AppSettings) -> String,
        set: (AppSettings, String) -> AppSettings,
    ) = Field(key, label, labels, read = { JsonPrimitive(get(it)) }, write = { s, e ->
        val v = (e as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (v == null || v !in labels) null else set(s, v)
    })

    private fun strings(
        key: String,
        @StringRes label: Int,
        get: (AppSettings) -> Set<String>,
        set: (AppSettings, Set<String>) -> AppSettings,
    ) = Field(key, label, read = { s -> JsonArray(get(s).sorted().map { JsonPrimitive(it) }) }, write = { s, e ->
        val list = (e as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
        if (list == null || list.size > 2000 || list.any { it == null || it.length > 200 }) null else set(s, list.filterNotNull().toSet())
    })

    val fields: List<Field> = listOf(
        // Photos
        int("columns", R.string.photos_per_row, { it.columns }, { s, v -> s.copy(columns = v) }) { it in SettingsRepository.MIN_COLUMNS..SettingsRepository.MAX_COLUMNS },
        choice("groupBy", R.string.group_by, GroupBy.entries.associate { it.name to it.labelRes }, { it.groupBy.name }) { s, v -> s.copy(groupBy = GroupBy.valueOf(v)) },
        bool("showMemories", R.string.memories_title, { it.showMemories }, { s, v -> s.copy(showMemories = v) }),
        bool("withPartners", R.string.partners_photos_in_photos, { it.withPartners }, { s, v -> s.copy(withPartners = v) }),
        bool("confirmDelete", R.string.ask_before_moving_to_the_trash, { it.confirmDelete }, { s, v -> s.copy(confirmDelete = v) }, onlyOn = true),
        // Viewer
        bool("loadOriginal", R.string.load_originals_when_zooming, { it.loadOriginal }, { s, v -> s.copy(loadOriginal = v) }),
        bool("autoPlayVideos", R.string.play_videos_automatically, { it.autoPlayVideos }, { s, v -> s.copy(autoPlayVideos = v) }),
        bool("loopVideos", R.string.loop_videos, { it.loopVideos }, { s, v -> s.copy(loopVideos = v) }),
        // On this phone, sharing
        bool("showLocal", R.string.show_photos_from_this_phone, { it.showLocal }, { s, v -> s.copy(showLocal = v) }),
        bool("storageIndicator", R.string.backup_status_on_thumbnails, { it.storageIndicator }, { s, v -> s.copy(storageIndicator = v) }),
        choice("shareSize", R.string.share_size, ShareSize.entries.associate { it.name to it.labelRes }, { it.shareSize.name }) { s, v -> s.copy(shareSize = ShareSize.valueOf(v)) },
        bool("shareStripLocation", R.string.remove_location_when_sharing, { it.shareStripLocation }, { s, v -> s.copy(shareStripLocation = v) }, onlyOn = true),
        // Library
        choice("albumSort", R.string.settings_file_album_sort, AlbumSort.entries.associate { it.name to it.labelRes }, { it.albumSort.name }) { s, v -> s.copy(albumSort = AlbumSort.valueOf(v)) },
        bool("albumGrid", R.string.settings_file_album_grid, { it.albumGrid }, { s, v -> s.copy(albumGrid = v) }),
        // Slideshow
        int("slideSeconds", R.string.each_photo_shows_for, { it.slideSeconds }, { s, v -> s.copy(slideSeconds = v) }) { it in 1..120 },
        bool("slideShuffle", R.string.shuffle, { it.slideShuffle }, { s, v -> s.copy(slideShuffle = v) }),
        bool("slideLoop", R.string.start_again_at_the_end, { it.slideLoop }, { s, v -> s.copy(slideLoop = v) }),
        // Privacy (protections can only be switched on by a file)
        bool("hideScreenshots", R.string.hide_screenshots, { it.hideScreenshots }, { s, v -> s.copy(hideScreenshots = v) }, onlyOn = true),
        bool("hideRecents", R.string.hide_recents, { it.hideRecents }, { s, v -> s.copy(hideRecents = v) }, onlyOn = true),
        bool("showMaps", R.string.show_maps, { it.showMaps }, { s, v -> s.copy(showMaps = v) }),
        // Server alerts
        bool("adminAlerts", R.string.notify_me_about_the_server, { it.adminAlerts }, { s, v -> s.copy(adminAlerts = v) }),
        int("adminDiskPercent", R.string.warn_when_the_disk_is, { it.adminDiskPercent }, { s, v -> s.copy(adminDiskPercent = v) }) { it in 50..99 },
        // Storage
        int("cacheMb", R.string.image_cache, { it.cacheMb }, { s, v -> s.copy(cacheMb = v) }) { it in 64..16384 },
        // Backup: what and how, but never whether it is on or for which account
        bool("backupEverything", R.string.everything_on_this_phone, { it.backupEverything }, { s, v -> s.copy(backupEverything = v) }),
        strings("backupAlbums", R.string.albums_on_this_phone, { it.backupAlbums }) { s, v -> s.copy(backupAlbums = v) },
        strings("excludedAlbums", R.string.settings_file_excluded_albums, { it.excludedAlbums }) { s, v -> s.copy(excludedAlbums = v) },
        bool("backupWifiOnly", R.string.only_on_wi_fi, { it.backupWifiOnly }, { s, v -> s.copy(backupWifiOnly = v) }),
        bool("backupChargingOnly", R.string.only_while_charging, { it.backupChargingOnly }, { s, v -> s.copy(backupChargingOnly = v) }),
        bool("backupVideos", R.string.include_videos, { it.backupVideos }, { s, v -> s.copy(backupVideos = v) }),
        int("backupParallel", R.string.backup_parallel, { it.backupParallel }, { s, v -> s.copy(backupParallel = v) }) { it in 1..BackupRules.MAX_PARALLEL },
        bool("backupVideosWifiOnly", R.string.backup_videos_wifi_only, { it.backupVideosWifiOnly }, { s, v -> s.copy(backupVideosWifiOnly = v) }),
        int("backupMaxVideoMb", R.string.backup_skip_large_videos, { it.backupMaxVideoMb }, { s, v -> s.copy(backupMaxVideoMb = v) }) { it in 0..1_000_000 },
        Field(
            "backupSince", R.string.backup_since,
            read = { s -> s.backupSince?.let { JsonPrimitive(it) } ?: JsonNull },
            write = { s, e ->
                when {
                    e is JsonNull -> s.copy(backupSince = null)
                    e is JsonPrimitive && e.isString && BackupRules.sinceMs(e.content) != null -> s.copy(backupSince = e.content.trim())
                    else -> null
                }
            },
        ),
        bool("albumSync", R.string.sync_albums, { it.albumSync }, { s, v -> s.copy(albumSync = v) }),
    )

    private val json = Json { prettyPrint = true }

    /** The settings file's text. [now] is an ISO time, [appVersion] the version that wrote it; both for information. */
    fun export(settings: AppSettings, appVersion: String, now: String): String = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive(FORMAT),
                "version" to JsonPrimitive(VERSION),
                "appVersion" to JsonPrimitive(appVersion),
                "createdAt" to JsonPrimitive(now),
                "settings" to JsonObject(fields.associateTo(LinkedHashMap()) { it.key to it.read(settings) }),
            ),
        ),
    )

    /** A file that was read and checked: its settings, and when it says it was written. */
    class Parsed(val settings: JsonObject, val createdAt: String?)

    fun parse(text: String): Parsed {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: throw SettingsFileException(newer = false)
        if ((root["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) throw SettingsFileException(newer = false)
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: throw SettingsFileException(newer = false)
        if (version > VERSION) throw SettingsFileException(newer = true)
        val settings = root["settings"] as? JsonObject ?: throw SettingsFileException(newer = false)
        return Parsed(settings, (root["createdAt"] as? JsonPrimitive)?.contentOrNull?.take(40))
    }

    /** What applying [file] would change, setting by setting. Keys the app doesn't know are ignored. */
    fun changes(current: AppSettings, file: JsonObject): List<Change> = fields.mapNotNull { f ->
        val value = file[f.key] ?: return@mapNotNull null
        val next = f.write(current, value) ?: return@mapNotNull null
        val from = f.read(current)
        val to = f.read(next)
        if (from == to) null else Change(f, from, to)
    }

    /** [current] with every usable value from [file] put in; everything else stays as it is. */
    fun apply(current: AppSettings, file: JsonObject): AppSettings = fields.fold(current) { s, f ->
        file[f.key]?.let { f.write(s, it) } ?: s
    }
}
