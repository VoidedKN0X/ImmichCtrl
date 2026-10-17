package com.vknxworks.immichctrl.data.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

// ---------- JSON helpers ----------

internal fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Enum<*> -> JsonPrimitive(v.name)
    is Collection<*> -> JsonArray(v.map { toJson(it) })
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
    else -> JsonPrimitive(v.toString())
}

/** A JSON object from pairs; null values are left out. */
fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.filter { it.second != null }.associate { it.first to toJson(it.second) })

/** Explicit JSON null, for fields that must be cleared. */
val Null: JsonElement = JsonNull

internal suspend inline fun <reified T> ImmichClient.get(path: String, vararg q: Pair<String, Any?>): T =
    call("GET", path, serializer<T>(), null, *q)

internal suspend inline fun <reified T> ImmichClient.send(method: String, path: String, body: JsonElement? = null, vararg q: Pair<String, Any?>): T =
    call(method, path, serializer<T>(), body, *q)

internal suspend fun ImmichClient.sendUnit(method: String, path: String, body: JsonElement? = null, vararg q: Pair<String, Any?>) {
    raw(method, path, body, *q)
}

// ---------- Server ----------

suspend fun ImmichClient.ping(): PingDto = get("/server/ping")
suspend fun ImmichClient.serverFeatures(): ServerFeaturesDto = get("/server/features")
suspend fun ImmichClient.serverConfig(): ServerConfigDto = get("/server/config")
suspend fun ImmichClient.serverVersion(): ServerVersionDto = get("/server/version")
suspend fun ImmichClient.serverAbout(): ServerAboutDto = get("/server/about")
suspend fun ImmichClient.serverStorage(): ServerStorageDto = get("/server/storage")
suspend fun ImmichClient.serverStats(): ServerStatsDto = get("/server/statistics")

// ---------- Auth ----------

suspend fun ImmichClient.login(email: String, password: String): LoginResponse =
    send("POST", "/auth/login", obj("email" to email, "password" to password))

suspend fun ImmichClient.logout() = sendUnit("POST", "/auth/logout")
suspend fun ImmichClient.authStatus(): AuthStatusDto = get("/auth/status")
suspend fun ImmichClient.unlockSession(pin: String) = sendUnit("POST", "/auth/session/unlock", obj("pinCode" to pin))
suspend fun ImmichClient.lockSession() = sendUnit("POST", "/auth/session/lock")
suspend fun ImmichClient.setupPin(pin: String) = sendUnit("POST", "/auth/pin-code", obj("pinCode" to pin))
suspend fun ImmichClient.changePin(oldPin: String, newPin: String) =
    sendUnit("PUT", "/auth/pin-code", obj("pinCode" to oldPin, "newPinCode" to newPin))

suspend fun ImmichClient.changePassword(old: String, new: String) =
    sendUnit("POST", "/auth/change-password", obj("password" to old, "newPassword" to new))

suspend fun ImmichClient.oauthAuthorize(redirectUri: String, state: String, challenge: String): OAuthAuthorizeDto =
    send("POST", "/oauth/authorize", obj("redirectUri" to redirectUri, "state" to state, "codeChallenge" to challenge))

suspend fun ImmichClient.oauthCallback(url: String, state: String, verifier: String): LoginResponse =
    send("POST", "/oauth/callback", obj("url" to url, "state" to state, "codeVerifier" to verifier))

// ---------- Users ----------

suspend fun ImmichClient.me(): UserMeDto = get("/users/me")
suspend fun ImmichClient.users(): List<UserDto> = get("/users")
suspend fun ImmichClient.updateMe(name: String) = sendUnit("PUT", "/users/me", obj("name" to name))

// ---------- Timeline ----------

/** Filters shared by the bucket list and each bucket's contents. */
data class TimelineQuery(
    val albumId: String? = null,
    val personId: String? = null,
    val tagId: String? = null,
    val userId: String? = null,
    val isFavorite: Boolean? = null,
    val isTrashed: Boolean? = null,
    val visibility: String? = null,
    val withPartners: Boolean? = null,
    val withStacked: Boolean? = null,
    val order: String? = null,
    /** "createdAt" sorts by upload date (Immich 3.x only); null means date taken. */
    val orderBy: String? = null,
) {
    fun params(): Array<Pair<String, Any?>> = arrayOf(
        "albumId" to albumId,
        "personId" to personId,
        "tagId" to tagId,
        "userId" to userId,
        "isFavorite" to isFavorite,
        "isTrashed" to isTrashed,
        "visibility" to visibility,
        "withPartners" to withPartners,
        "withStacked" to withStacked,
        "order" to order,
        "orderBy" to orderBy,
    )
}

suspend fun ImmichClient.timeBuckets(q: TimelineQuery): List<TimeBucketDto> = get("/timeline/buckets", *q.params())

suspend fun ImmichClient.timeBucket(q: TimelineQuery, bucket: String): List<Asset> =
    get<TimeBucketAssetsDto>("/timeline/bucket", "timeBucket" to bucket, *q.params()).toAssets()

// ---------- Assets ----------

suspend fun ImmichClient.asset(id: String): AssetDto = get("/assets/$id")

/** Updates one asset. Fields: isFavorite, visibility, description, rating, dateTimeOriginal, latitude, longitude. */
suspend fun ImmichClient.updateAsset(id: String, vararg fields: Pair<String, Any?>): AssetDto =
    send("PUT", "/assets/$id", obj(*fields))

suspend fun ImmichClient.updateAssets(ids: Collection<String>, vararg fields: Pair<String, Any?>) =
    sendUnit("PUT", "/assets", obj("ids" to ids.toList(), *fields))

/** Moves to the trash, or deletes permanently when [force] (or when the server's trash is off). */
suspend fun ImmichClient.deleteAssets(ids: Collection<String>, force: Boolean = false) =
    sendUnit("DELETE", "/assets", obj("ids" to ids.toList(), "force" to force))

suspend fun ImmichClient.restoreAssets(ids: Collection<String>): TrashResultDto =
    send("POST", "/trash/restore/assets", obj("ids" to ids.toList()))

suspend fun ImmichClient.restoreAllTrash(): TrashResultDto = send("POST", "/trash/restore")
suspend fun ImmichClient.emptyTrash(): TrashResultDto = send("POST", "/trash/empty")

@kotlinx.serialization.Serializable
data class AssetStatsDto(val images: Long = 0, val videos: Long = 0, val total: Long = 0)

suspend fun ImmichClient.assetStats(visibility: String? = null, isFavorite: Boolean? = null, isTrashed: Boolean? = null): AssetStatsDto =
    get("/assets/statistics", "visibility" to visibility, "isFavorite" to isFavorite, "isTrashed" to isTrashed)

/** name: refresh-metadata, regenerate-thumbnail, transcode-video, refresh-faces */
suspend fun ImmichClient.runAssetJob(ids: Collection<String>, name: String) =
    sendUnit("POST", "/assets/jobs", obj("assetIds" to ids.toList(), "name" to name))

suspend fun ImmichClient.uploadCheck(items: List<Pair<String, String>>): UploadCheckDto =
    send("POST", "/assets/bulk-upload-check", obj("assets" to items.map { mapOf("id" to it.first, "checksum" to it.second) }))

// ---------- Albums ----------

suspend fun ImmichClient.albums(assetId: String? = null): List<AlbumDto> = get("/albums", "assetId" to assetId)
suspend fun ImmichClient.album(id: String): AlbumDto = get("/albums/$id", "withoutAssets" to true)

suspend fun ImmichClient.createAlbum(name: String, assetIds: Collection<String> = emptyList(), description: String? = null): AlbumDto =
    send("POST", "/albums", obj("albumName" to name, "assetIds" to assetIds.toList().ifEmpty { null }, "description" to description))

/** Fields: albumName, description, albumThumbnailAssetId, isActivityEnabled, order */
suspend fun ImmichClient.updateAlbum(id: String, vararg fields: Pair<String, Any?>): AlbumDto =
    send("PATCH", "/albums/$id", obj(*fields))

suspend fun ImmichClient.deleteAlbum(id: String) = sendUnit("DELETE", "/albums/$id")

suspend fun ImmichClient.addToAlbum(id: String, assetIds: Collection<String>): List<BulkIdResultDto> =
    send("PUT", "/albums/$id/assets", obj("ids" to assetIds.toList()))

suspend fun ImmichClient.removeFromAlbum(id: String, assetIds: Collection<String>): List<BulkIdResultDto> =
    send("DELETE", "/albums/$id/assets", obj("ids" to assetIds.toList()))

suspend fun ImmichClient.addAlbumUsers(id: String, userIds: Collection<String>, role: String = "editor"): AlbumDto =
    send("PUT", "/albums/$id/users", obj("albumUsers" to userIds.map { mapOf("userId" to it, "role" to role) }))

/** userId "me" leaves an album shared with you. */
suspend fun ImmichClient.removeAlbumUser(id: String, userId: String) = sendUnit("DELETE", "/albums/$id/user/$userId")

suspend fun ImmichClient.setAlbumUserRole(id: String, userId: String, role: String) =
    sendUnit("PUT", "/albums/$id/user/$userId", obj("role" to role))

// ---------- Search ----------

suspend fun ImmichClient.smartSearch(query: String, page: Int = 1, size: Int = 100, extra: Map<String, Any?> = emptyMap()): SearchResponseDto =
    send("POST", "/search/smart", obj("query" to query, "page" to page, "size" to size, *extra.toList().toTypedArray()))

suspend fun ImmichClient.metadataSearch(page: Int = 1, size: Int = 100, fields: Map<String, Any?>): SearchResponseDto =
    send("POST", "/search/metadata", obj("page" to page, "size" to size, "withExif" to true, *fields.toList().toTypedArray()))

suspend fun ImmichClient.explore(): List<ExploreDto> = get("/search/explore")
suspend fun ImmichClient.searchPeople(name: String): List<PersonDto> = get("/search/person", "name" to name, "withHidden" to false)
suspend fun ImmichClient.searchPlaces(name: String): List<PlaceDto> = get("/search/places", "name" to name)

/** type: country, state, city, camera-make, camera-model */
suspend fun ImmichClient.searchSuggestions(type: String): List<String> =
    call("GET", "/search/suggestions", ListSerializer(String.serializer()), null, "type" to type)

// ---------- People ----------

suspend fun ImmichClient.people(withHidden: Boolean = true, page: Int = 1, size: Int = 500): PeopleDto =
    get("/people", "withHidden" to withHidden, "page" to page, "size" to size)

suspend fun ImmichClient.person(id: String): PersonDto = get("/people/$id")
suspend fun ImmichClient.personStats(id: String): PersonStatsDto = get("/people/$id/statistics")

/** Fields: name, isHidden, isFavorite, birthDate, featureFaceAssetId */
suspend fun ImmichClient.updatePerson(id: String, vararg fields: Pair<String, Any?>): PersonDto =
    send("PUT", "/people/$id", obj(*fields))

suspend fun ImmichClient.mergePeople(into: String, ids: Collection<String>): List<BulkIdResultDto> =
    send("POST", "/people/$into/merge", obj("ids" to ids.toList()))

// ---------- Memories, map, places ----------

suspend fun ImmichClient.memories(forIso: String): List<MemoryDto> = get("/memories", "for" to forIso)

suspend fun ImmichClient.mapMarkers(withPartners: Boolean, withSharedAlbums: Boolean, isFavorite: Boolean? = null): List<MapMarkerDto> =
    get("/map/markers", "withPartners" to withPartners, "withSharedAlbums" to withSharedAlbums, "isFavorite" to isFavorite)

// ---------- Shared links ----------

suspend fun ImmichClient.sharedLinks(): List<SharedLinkDto> = get("/shared-links")

suspend fun ImmichClient.createSharedLink(
    albumId: String? = null,
    assetIds: Collection<String>? = null,
    description: String? = null,
    expiresAt: String? = null,
    allowDownload: Boolean = true,
    allowUpload: Boolean = false,
    showMetadata: Boolean = true,
    password: String? = null,
): SharedLinkDto = send(
    "POST", "/shared-links",
    obj(
        "type" to if (albumId != null) "ALBUM" else "INDIVIDUAL",
        "albumId" to albumId,
        "assetIds" to assetIds?.toList(),
        "description" to description,
        "expiresAt" to expiresAt,
        "allowDownload" to allowDownload,
        "allowUpload" to allowUpload,
        "showMetadata" to showMetadata,
        "password" to password?.ifBlank { null },
    ),
)

suspend fun ImmichClient.deleteSharedLink(id: String) = sendUnit("DELETE", "/shared-links/$id")

// ---------- Partners ----------

/** direction: shared-by (you share with them) or shared-with (they share with you). */
suspend fun ImmichClient.partners(direction: String): List<PartnerDto> = get("/partners", "direction" to direction)
suspend fun ImmichClient.addPartner(userId: String): PartnerDto = send("POST", "/partners", obj("sharedWithId" to userId))
suspend fun ImmichClient.updatePartner(id: String, inTimeline: Boolean): PartnerDto =
    send("PUT", "/partners/$id", obj("inTimeline" to inTimeline))
suspend fun ImmichClient.removePartner(id: String) = sendUnit("DELETE", "/partners/$id")

// ---------- Activity (shared album comments and likes) ----------

suspend fun ImmichClient.activities(albumId: String, assetId: String? = null): List<ActivityDto> =
    get("/activities", "albumId" to albumId, "assetId" to assetId)

suspend fun ImmichClient.addActivity(albumId: String, assetId: String?, comment: String?): ActivityDto =
    send("POST", "/activities", obj("albumId" to albumId, "assetId" to assetId, "type" to if (comment == null) "like" else "comment", "comment" to comment))

suspend fun ImmichClient.deleteActivity(id: String) = sendUnit("DELETE", "/activities/$id")

// ---------- Tags and folders ----------

suspend fun ImmichClient.tags(): List<TagDto> = get("/tags")
suspend fun ImmichClient.createTag(name: String): TagDto = send("POST", "/tags", obj("name" to name))
suspend fun ImmichClient.tagAssets(tagIds: Collection<String>, assetIds: Collection<String>) =
    sendUnit("PUT", "/tags/assets", obj("tagIds" to tagIds.toList(), "assetIds" to assetIds.toList()))
suspend fun ImmichClient.untagAssets(tagId: String, assetIds: Collection<String>) =
    sendUnit("DELETE", "/tags/$tagId/assets", obj("ids" to assetIds.toList()))
suspend fun ImmichClient.deleteTag(id: String) = sendUnit("DELETE", "/tags/$id")

suspend fun ImmichClient.folderPaths(): List<String> =
    call("GET", "/view/folder/unique-paths", ListSerializer(String.serializer()))

suspend fun ImmichClient.folderAssets(path: String): List<AssetDto> = get("/view/folder", "path" to path)

// ---------- Admin: job queues ----------

suspend fun ImmichClient.queues(): List<QueueDto> = get("/queues")

suspend fun ImmichClient.setQueuePaused(name: String, paused: Boolean): QueueDto =
    send("PUT", "/queues/$name", obj("isPaused" to paused))

/** command: start (force = all, not only missing), clear-failed, empty */
suspend fun ImmichClient.queueCommand(name: String, command: String, force: Boolean = false) =
    sendUnit("PUT", "/jobs/$name", obj("command" to command, "force" to force))

suspend fun ImmichClient.memory(id: String): MemoryDto = get("/memories/$id")

// ---------- Editing (crop, rotate, mirror; Immich keeps the original) ----------

suspend fun ImmichClient.edits(id: String): EditsDto = get("/assets/$id/edits")

suspend fun ImmichClient.setEdits(id: String, edits: List<JsonObject>): EditsDto =
    send("PUT", "/assets/$id/edits", obj("edits" to edits))

suspend fun ImmichClient.revertEdits(id: String) = sendUnit("DELETE", "/assets/$id/edits")

// ---------- OCR ----------

suspend fun ImmichClient.ocr(id: String): List<OcrDto> = get("/assets/$id/ocr")

// ---------- Stacks ----------

suspend fun ImmichClient.stack(id: String): StackDto = get("/stacks/$id")
suspend fun ImmichClient.createStack(assetIds: List<String>): StackDto = send("POST", "/stacks", obj("assetIds" to assetIds))
suspend fun ImmichClient.setStackPrimary(id: String, assetId: String): StackDto =
    send("PUT", "/stacks/$id", obj("primaryAssetId" to assetId))
suspend fun ImmichClient.deleteStack(id: String) = sendUnit("DELETE", "/stacks/$id")
suspend fun ImmichClient.removeFromStack(id: String, assetId: String) = sendUnit("DELETE", "/stacks/$id/assets/$assetId")

// ---------- Faces ----------

suspend fun ImmichClient.faces(assetId: String): List<FaceDto> = get("/faces", "id" to assetId)

/** Moves one face to another person. */
suspend fun ImmichClient.reassignFace(faceId: String, personId: String): PersonDto =
    send("PUT", "/faces/$faceId", obj("id" to personId))

suspend fun ImmichClient.deleteFace(faceId: String) = sendUnit("DELETE", "/faces/$faceId", obj("force" to false))

suspend fun ImmichClient.createPerson(name: String): PersonDto = send("POST", "/people", obj("name" to name))

suspend fun ImmichClient.peopleShared(withHidden: Boolean = true): List<PersonFullDto> =
    get<PeopleFullDto>("/people", "withHidden" to withHidden, "page" to 1, "size" to 500).people

@kotlinx.serialization.Serializable
data class PeopleFullDto(val people: List<PersonFullDto> = emptyList())

// ---------- Duplicates ----------

suspend fun ImmichClient.duplicates(): List<DuplicateGroupDto> = get("/duplicates")

suspend fun ImmichClient.resolveDuplicates(groups: List<Triple<String, List<String>, List<String>>>): List<BulkIdResultDto> =
    try {
        send(
            "POST", "/duplicates/resolve",
            obj("groups" to groups.map { (id, keep, trash) -> mapOf("duplicateId" to id, "keepAssetIds" to keep, "trashAssetIds" to trash) }),
        )
    } catch (e: ApiException) {
        // Immich before 2.6 has no resolve endpoint: trash the others and clear the group on the kept ones.
        if (e.code != 404) throw e
        val trash = groups.flatMap { it.third }
        if (trash.isNotEmpty()) deleteAssets(trash)
        val keep = groups.flatMap { it.second }
        if (keep.isNotEmpty()) updateAssets(keep, "duplicateId" to Null)
        emptyList()
    }

suspend fun ImmichClient.dismissDuplicate(id: String) = sendUnit("DELETE", "/duplicates/$id")

// ---------- Sessions, profile, version ----------

/** A short-lived session token, used for URLs a cast device fetches without our headers. */
suspend fun ImmichClient.createSession(seconds: Int): SessionTokenDto =
    send("POST", "/sessions", obj("duration" to seconds, "deviceType" to "Chromecast", "deviceOS" to "Cast"))

suspend fun ImmichClient.versionCheck(): VersionCheckDto = get("/server/version-check")

suspend fun ImmichClient.deleteProfileImage() = sendUnit("DELETE", "/users/profile-image")

suspend fun ImmichClient.randomAssets(count: Int, favoritesOnly: Boolean = false): List<AssetDto> =
    send("POST", "/search/random", obj("size" to count, "type" to "IMAGE", "isFavorite" to if (favoritesOnly) true else null))

suspend fun ImmichClient.updateSharedLink(id: String, vararg fields: Pair<String, Any?>): SharedLinkDto =
    send("PATCH", "/shared-links/$id", obj(*fields))

suspend fun ImmichClient.mapMarkersBetween(
    withPartners: Boolean,
    isFavorite: Boolean?,
    after: String?,
    before: String?,
    archived: Boolean?,
): List<MapMarkerDto> = get(
    "/map/markers",
    "withPartners" to withPartners,
    "withSharedAlbums" to true,
    "isFavorite" to isFavorite,
    "fileCreatedAfter" to after,
    "fileCreatedBefore" to before,
    "isArchived" to archived,
)

/** Suggestions narrowed by other fields, e.g. cities in a country ("country" to "Norway"). */
suspend fun ImmichClient.searchSuggestionsFor(type: String, vararg filters: Pair<String, Any?>): List<String> =
    call("GET", "/search/suggestions", ListSerializer(String.serializer()), null, "type" to type, *filters)

/** Replaces the signed-in user's profile picture. */
suspend fun ImmichClient.setProfileImage(bytes: ByteArray, mime: String, fileName: String = "profile.jpg") {
    val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
        .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull()))
        .build()
    execute(request(url("/users/profile-image")).post(body).build()).close()
}
