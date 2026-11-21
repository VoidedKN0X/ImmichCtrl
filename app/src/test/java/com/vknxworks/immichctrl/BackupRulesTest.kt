package com.vknxworks.immichctrl

import com.vknxworks.immichctrl.data.backup.BackupRules
import com.vknxworks.immichctrl.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BackupRulesTest {
    private val utc = ZoneId.of("UTC")
    private val mb = 1024L * 1024L

    @Test
    fun defaultsBackUpEverything() {
        val r = BackupRules.of(AppSettings(), utc)
        assertTrue(r.wants(isVideo = false, size = 1, takenMs = 0))
        assertTrue(r.wants(isVideo = true, size = 50_000 * mb, takenMs = 0))
        assertTrue(r.allowedNow(isVideo = true, metered = true))
    }

    @Test
    fun videosOffSkipsOnlyVideos() {
        val r = BackupRules.of(AppSettings(backupVideos = false), utc)
        assertFalse(r.wants(isVideo = true, size = 1, takenMs = 0))
        assertTrue(r.wants(isVideo = false, size = 1, takenMs = 0))
    }

    @Test
    fun sizeLimitOnlyAppliesToVideos() {
        val r = BackupRules.of(AppSettings(backupMaxVideoMb = 100), utc)
        assertTrue(r.wants(isVideo = true, size = 100 * mb, takenMs = 0))
        assertFalse(r.wants(isVideo = true, size = 100 * mb + 1, takenMs = 0))
        assertTrue(r.wants(isVideo = false, size = 500 * mb, takenMs = 0))
    }

    @Test
    fun sinceIsTheStartOfThatDay() {
        assertEquals(86_400_000L, BackupRules.sinceMs("1970-01-02", utc))
        assertNull(BackupRules.sinceMs("", utc))
        assertNull(BackupRules.sinceMs(null, utc))
        assertNull(BackupRules.sinceMs("2 January", utc))
        val r = BackupRules.of(AppSettings(backupSince = "1970-01-02"), utc)
        assertFalse(r.wants(isVideo = false, size = 1, takenMs = 86_399_999L))
        assertTrue(r.wants(isVideo = false, size = 1, takenMs = 86_400_000L))
    }

    @Test
    fun videosWaitForWifiOnlyWhenPhotosMayUseMobileData() {
        val mobile = BackupRules.of(AppSettings(backupWifiOnly = false, backupVideosWifiOnly = true), utc)
        assertFalse(mobile.allowedNow(isVideo = true, metered = true))
        assertTrue(mobile.allowedNow(isVideo = true, metered = false))
        assertTrue(mobile.allowedNow(isVideo = false, metered = true))
        // With "Only on Wi-Fi" on, the whole backup already waits for Wi-Fi.
        val wifi = BackupRules.of(AppSettings(backupWifiOnly = true, backupVideosWifiOnly = true), utc)
        assertTrue(wifi.allowedNow(isVideo = true, metered = true))
    }
}
