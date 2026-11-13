package com.vknxworks.immichctrl

import com.vknxworks.immichctrl.data.api.Null
import com.vknxworks.immichctrl.data.api.sharedLinkCreateBody
import com.vknxworks.immichctrl.ui.photos.LinkOptions
import com.vknxworks.immichctrl.ui.widget.WidgetRules
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SharedLinkAndWidgetTest {
    private val now = Instant.parse("2026-11-13T10:00:00Z")

    @Test fun albumLinkBody() {
        val b = sharedLinkCreateBody("album-1", null, "Holiday", "2026-11-20T10:00:00Z", true, true, false, "secret")
        assertEquals("ALBUM", b["type"]!!.jsonPrimitive.content)
        assertEquals("album-1", b["albumId"]!!.jsonPrimitive.content)
        assertFalse(b.containsKey("assetIds"))
        assertTrue(b["allowUpload"]!!.jsonPrimitive.boolean)
        assertFalse(b["showMetadata"]!!.jsonPrimitive.boolean)
        assertEquals("secret", b["password"]!!.jsonPrimitive.content)
        assertEquals("2026-11-20T10:00:00Z", b["expiresAt"]!!.jsonPrimitive.content)
    }

    @Test fun photoLinkBodyLeavesOutUnsetFieldsAndNeverAllowsUploads() {
        val b = sharedLinkCreateBody(null, listOf("a", "b"), null, null, false, true, true, "  ")
        assertEquals("INDIVIDUAL", b["type"]!!.jsonPrimitive.content)
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))), b["assetIds"])
        assertFalse(b.containsKey("albumId"))
        assertFalse(b.containsKey("expiresAt")) // never expires
        assertFalse(b.containsKey("password")) // blank = no password
        assertFalse(b.containsKey("description"))
        assertFalse(b["allowUpload"]!!.jsonPrimitive.boolean)
        assertFalse(b["allowDownload"]!!.jsonPrimitive.boolean)
    }

    @Test fun expiryFromDays() {
        assertEquals("2026-11-20T10:00:00Z", LinkOptions(expiresInDays = 7).expiresAt(now))
        assertEquals("2027-11-13T10:00:00Z", LinkOptions(expiresInDays = 365).expiresAt(now))
        assertNull(LinkOptions(expiresInDays = null).expiresAt(now))
    }

    @Test fun editingKeepsTheExpiryUnlessChanged() {
        val kept = LinkOptions.forEditing("d", "2027-03-01T00:00:00Z", null, true, false, true)
        assertTrue(kept.keepExpiry)
        val f = kept.editFields(serverMajor = 3, now = now).toMap()
        assertFalse(f.containsKey("expiresAt"))
        assertFalse(f.containsKey("changeExpiryTime"))
        assertEquals("d", f["description"])
        // A link that never expires has nothing to keep.
        assertFalse(LinkOptions.forEditing(null, null, null, true, false, true).keepExpiry)
    }

    @Test fun editingToNeverOrANewDate() {
        val never = LinkOptions(expiresInDays = null).editFields(3, now).toMap()
        assertEquals(Null, never["expiresAt"])
        assertFalse(never.containsKey("changeExpiryTime"))
        val legacy = LinkOptions(expiresInDays = 30).editFields(2, now).toMap()
        assertEquals(true, legacy["changeExpiryTime"])
        assertEquals("2026-12-13T10:00:00Z", legacy["expiresAt"])
    }

    @Test fun nextMidnightRefresh() {
        val zone = ZoneId.of("Europe/Brussels")
        val evening = ZonedDateTime.of(2026, 11, 13, 22, 30, 0, 0, zone)
        assertEquals(Duration.ofMinutes(95), WidgetRules.delayToNextDay(evening))
        val justAfter = ZonedDateTime.of(2026, 11, 14, 0, 1, 0, 0, zone)
        assertEquals(Duration.ofHours(24).plusMinutes(4), WidgetRules.delayToNextDay(justAfter))
        // The night the clocks go back has 25 hours.
        val dst = ZonedDateTime.of(2026, 10, 24, 23, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(1).plusMinutes(5), WidgetRules.delayToNextDay(dst))
        val dstDay = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(25).plusMinutes(5), WidgetRules.delayToNextDay(dstDay))
    }

    @Test fun samePhotoAllDayAndItChangesWithTheDate() {
        val memories = listOf(listOf("a1", "a2", "a3"), emptyList(), listOf("b1", "b2"), listOf("c1"))
        val day = LocalDate.of(2026, 11, 13)
        val pick = WidgetRules.pickOfDay(memories, day)!!
        assertEquals(pick, WidgetRules.pickOfDay(memories, day))
        assertTrue(memories[pick.first].isNotEmpty())
        assertTrue(pick.second in memories[pick.first].indices)
        // Over a few weeks it doesn't always show the same photo.
        val picks = (0L until 30L).map { WidgetRules.pickOfDay(memories, day.plusDays(it)) }.toSet()
        assertTrue(picks.size > 1)
        assertNull(WidgetRules.pickOfDay(listOf(emptyList(), emptyList()), day))
        assertNull(WidgetRules.pickOfDay(emptyList(), day))
    }

    @Test fun cacheKeyRoundTripAndSampleSize() {
        val k = WidgetRules.CacheKey("https://photos.example|user", "asset-1")
        assertEquals(k, WidgetRules.parseKey(WidgetRules.formatKey(k)))
        assertNull(WidgetRules.parseKey(null))
        assertNull(WidgetRules.parseKey("only-one-line"))
        assertNotEquals(k, WidgetRules.CacheKey("other", "asset-1"))
        assertEquals(1, WidgetRules.sampleSize(500, 400, 512))
        assertEquals(2, WidgetRules.sampleSize(1440, 1080, 512))
        val s = WidgetRules.sampleSize(4000, 3000, 512)
        assertTrue(4000 / s <= 1024 && 4000 / s >= 256)
    }
}
