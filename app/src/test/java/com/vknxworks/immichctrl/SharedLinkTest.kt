package com.vknxworks.immichctrl

import com.vknxworks.immichctrl.data.api.Null
import com.vknxworks.immichctrl.data.api.sharedLinkCreateBody
import com.vknxworks.immichctrl.ui.photos.LinkOptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SharedLinkTest {
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
}
