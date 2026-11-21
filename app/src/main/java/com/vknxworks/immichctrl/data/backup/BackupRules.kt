package com.vknxworks.immichctrl.data.backup

import com.vknxworks.immichctrl.data.settings.AppSettings
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which photos and videos automatic backup and "Back up now" send, beyond the choice of albums.
 * Photos picked by hand ("Upload") and files shared to the app are never filtered by these.
 */
data class BackupRules(
    /** Videos are backed up at all. */
    val videos: Boolean = true,
    /** Videos bigger than this many bytes are left on the phone; 0 = no limit. */
    val maxVideoBytes: Long = 0L,
    /** Only what was taken at or after this moment (epoch ms); null = everything. */
    val sinceMs: Long? = null,
    /** Videos wait for a connection that isn't metered (Wi-Fi), even when photos may use mobile data. */
    val videosUnmeteredOnly: Boolean = false,
) {
    /** Whether an item belongs to the backup at all (what the "to back up" count shows). */
    fun wants(isVideo: Boolean, size: Long, takenMs: Long): Boolean {
        if (isVideo && !videos) return false
        if (isVideo && maxVideoBytes > 0L && size > maxVideoBytes) return false
        if (sinceMs != null && takenMs < sinceMs) return false
        return true
    }

    /** Whether an item that belongs to the backup may be sent on the connection in use right now. */
    fun allowedNow(isVideo: Boolean, metered: Boolean): Boolean = !(isVideo && videosUnmeteredOnly && metered)

    companion object {
        const val MAX_PARALLEL = 4

        /** "2026-01-31" -> the start of that day in [zone]; null for empty or unreadable text. */
        fun sinceMs(date: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
            val text = date?.trim().orEmpty()
            if (text.isEmpty()) return null
            return runCatching { LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        }

        fun of(s: AppSettings, zone: ZoneId = ZoneId.systemDefault()): BackupRules = BackupRules(
            videos = s.backupVideos,
            maxVideoBytes = s.backupMaxVideoMb.coerceAtLeast(0) * 1024L * 1024L,
            sinceMs = sinceMs(s.backupSince, zone),
            // Only matters when photos may use mobile data.
            videosUnmeteredOnly = s.backupVideosWifiOnly && !s.backupWifiOnly,
        )
    }
}
