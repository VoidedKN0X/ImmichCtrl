package com.vknxworks.immichctrl

import com.vknxworks.immichctrl.data.settings.AppSettings
import com.vknxworks.immichctrl.data.settings.GroupBy
import com.vknxworks.immichctrl.data.settings.SettingsBackups
import com.vknxworks.immichctrl.data.settings.SettingsFileException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private fun file(settings: String): JsonObject =
        SettingsBackups.parse("""{"format":"immichctrl-settings","version":1,"settings":$settings}""").settings

    @Test
    fun exportThenRestoreGivesTheSamePreferences() {
        val mine = AppSettings(
            columns = 6, groupBy = GroupBy.MONTH, showMemories = false, slideSeconds = 12, hideScreenshots = true,
            backupAlbums = setOf("a", "b"), backupParallel = 3, backupSince = "2026-01-31", backupMaxVideoMb = 500,
        )
        val text = SettingsBackups.export(mine, "1.0.0", "2026-11-21T10:00:00Z")
        val parsed = SettingsBackups.parse(text)
        assertEquals("2026-11-21T10:00:00Z", parsed.createdAt)
        assertEquals(mine, SettingsBackups.apply(AppSettings(), parsed.settings))
        assertTrue(SettingsBackups.changes(mine, parsed.settings).isEmpty())
    }

    @Test
    fun theFileHoldsNoSignInOrLockDetails() {
        val text = SettingsBackups.export(AppSettings(appLock = true, backupEnabled = true, backupAccount = "https://example.org|me"), "1.0.0", "now")
        listOf("appLock", "backupEnabled", "backupAccount", "example.org", "readOnly", "phoneOnly", "lockedFolder").forEach {
            assertFalse("$it must not be in the file", text.contains(it))
        }
    }

    @Test
    fun aFileCannotTurnLocksBackupOrProtectionsAround() {
        val current = AppSettings(
            appLock = true, appLockTimeout = 0, readOnly = true, backupEnabled = false, backupAccount = null,
            hideScreenshots = true, hideRecents = true, shareStripLocation = true, confirmDelete = true,
        )
        val hostile = file(
            """{"appLock":false,"appLockTimeout":-1,"readOnly":false,"phoneOnly":true,"backupEnabled":true,
               "backupAccount":"x","lockedFolderBiometric":true,"hideScreenshots":false,"hideRecents":false,
               "shareStripLocation":false,"confirmDelete":false}""",
        )
        assertEquals(current, SettingsBackups.apply(current, hostile))
        assertTrue(SettingsBackups.changes(current, hostile).isEmpty())
    }

    @Test
    fun aFileCanTurnProtectionsOn() {
        val next = SettingsBackups.apply(AppSettings(), file("""{"hideScreenshots":true,"hideRecents":true}"""))
        assertTrue(next.hideScreenshots)
        assertTrue(next.hideRecents)
    }

    @Test
    fun valuesTheAppCannotHaveAreLeftAlone() {
        val current = AppSettings()
        val odd = file(
            """{"columns":99,"groupBy":"YEAR","slideSeconds":"5","showMemories":"false","backupParallel":9,
               "backupSince":"last week","backupAlbums":"Camera","cacheMb":1,"unknownSetting":true}""",
        )
        assertEquals(current, SettingsBackups.apply(current, odd))
    }

    @Test
    fun changesListOnlyWhatDiffers() {
        val current = AppSettings(columns = 4, slideShuffle = false)
        val changes = SettingsBackups.changes(current, file("""{"columns":5,"slideShuffle":false,"backupSince":null}"""))
        assertEquals(listOf("columns"), changes.map { it.field.key })
    }

    @Test
    fun otherFilesAreRefused() {
        for ((text, newer) in listOf(
            "not json" to false,
            "[]" to false,
            """{"format":"printctrl-settings","version":1,"settings":{}}""" to false,
            """{"format":"immichctrl-settings","settings":{}}""" to false,
            """{"format":"immichctrl-settings","version":1}""" to false,
            """{"format":"immichctrl-settings","version":2,"settings":{}}""" to true,
        )) {
            try {
                SettingsBackups.parse(text)
                fail("accepted: $text")
            } catch (e: SettingsFileException) {
                assertEquals(text, newer, e.newer)
            }
        }
    }

    @Test
    fun everyKeyIsUnique() {
        val keys = SettingsBackups.fields.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(Json.parseToJsonElement(SettingsBackups.export(AppSettings(), "v", "t")) is JsonObject)
    }
}
