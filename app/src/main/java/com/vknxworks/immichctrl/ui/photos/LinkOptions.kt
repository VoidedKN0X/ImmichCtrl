package com.vknxworks.immichctrl.ui.photos

import com.vknxworks.immichctrl.data.api.Null
import java.time.Instant

/** Options for a new (or existing) shared link. */
data class LinkOptions(
    val description: String = "",
    /** Days until it stops working; null for never. Ignored while [keepExpiry] is on. */
    val expiresInDays: Int? = null,
    val password: String = "",
    val allowDownload: Boolean = true,
    val allowUpload: Boolean = false,
    val showMetadata: Boolean = true,
    /** Editing: leave the link's current expiry date as it is. */
    val keepExpiry: Boolean = false,
) {
    fun expiresAt(now: Instant = Instant.now()): String? = expiresInDays?.let { now.plusSeconds(it * 86_400L).toString() }

    /**
     * The fields of PATCH /api/shared-links/{id} for these options. The expiry is left out when it's kept (the
     * server then doesn't change it). "Never" is an explicit null; servers before Immich v3 also need
     * `changeExpiryTime` to accept a new expiry or none.
     */
    fun editFields(serverMajor: Int, now: Instant = Instant.now()): List<Pair<String, Any?>> = buildList {
        if (!keepExpiry) {
            if (serverMajor < 3) add("changeExpiryTime" to true)
            add("expiresAt" to (expiresAt(now) ?: Null))
        }
        add("description" to description)
        add("password" to password)
        add("allowDownload" to allowDownload)
        add("allowUpload" to allowUpload)
        add("showMetadata" to showMetadata)
    }

    companion object {
        /** The options of an existing link, for editing it: its expiry is kept unless you choose another. */
        fun forEditing(
            description: String?, expiresAt: String?, password: String?,
            allowDownload: Boolean, allowUpload: Boolean, showMetadata: Boolean,
        ) = LinkOptions(
            description = description.orEmpty(),
            expiresInDays = null,
            password = password.orEmpty(),
            allowDownload = allowDownload,
            allowUpload = allowUpload,
            showMetadata = showMetadata,
            keepExpiry = expiresAt != null,
        )
    }
}
