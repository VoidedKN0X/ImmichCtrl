package com.vknxworks.immichctrl.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------------------------------------------------------------------------------------------
// Durations: Immich 2.x sends "H:MM:SS.ffffff" strings, 3.x sends milliseconds.
// ---------------------------------------------------------------------------------------------

fun parseDurationMs(e: JsonElement?): Long? {
    if (e == null || e is JsonNull) return null
    val p = e as? JsonPrimitive ?: return null
    p.longOrNull?.let { return it.takeIf { ms -> ms > 0 } }
    val s = p.contentOrNull?.trim().orEmpty()
    if (s.isEmpty()) return null
    s.toDoubleOrNull()?.let { return it.toLong().takeIf { ms -> ms > 0 } }
    val parts = s.split(':')
    if (parts.size != 3) return null
    val h = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull() ?: return null
    val sec = parts[2].toDoubleOrNull() ?: return null
    val ms = h * 3_600_000 + m * 60_000 + (sec * 1000).toLong()
    return ms.takeIf { it > 0 }
}

/** The duration format the server wants on upload. */
fun formatUploadDuration(ms: Long, serverMajor: Int): String =
    if (serverMajor >= 3) ms.toString()
    else {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val frac = (ms % 1000) * 1000
        "%d:%02d:%02d.%06d".format(h, m, s, frac)
    }

fun parseInstantMs(iso: String?): Long? = iso?.let { s ->
    runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}

// ---------------------------------------------------------------------------------------------
// The app's own lightweight asset, used by grids and the viewer.
// ---------------------------------------------------------------------------------------------

data class Asset(
    val id: String,
    val isImage: Boolean,
    /** width / height */
    val ratio: Float,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val visibility: String,
    val durationMs: Long?,
    val thumbhash: String?,
    /** UTC epoch ms when the photo was taken. */
    val takenAtMs: Long,
    /** Hours to add to UTC to get the local time it was taken. */
    val localOffsetHours: Double,
    val livePhotoVideoId: String?,
    val stackCount: Int,
    val ownerId: String,
    val city: String? = null,
    val country: String? = null,
    /** content:// (photo on this phone) or file:// (offline copy); shown instead of the server's image. */
    val localUri: String? = null,
    /** Only on this phone, not on the server (id is "local:<MediaStore id>"). */
    val isLocalOnly: Boolean = false,
    /** On the server and also on this phone (it was backed up from here). */
    val onDevice: Boolean = false,
    /** MediaStore id of the copy on this phone, if there is one. */
    val localId: Long? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
) {
    val isVideo: Boolean get() = !isImage
    val isRemote: Boolean get() = !isLocalOnly
    val localDateTime: java.time.LocalDateTime
        get() = java.time.LocalDateTime.ofEpochSecond(
            Math.floorDiv(takenAtMs, 1000L) + (localOffsetHours * 3600).toLong(),
            (Math.floorMod(takenAtMs, 1000L) * 1_000_000).toInt(),
            ZoneOffset.UTC,
        )
    val localDate: LocalDate get() = localDateTime.toLocalDate()
}

@Serializable
data class TimeBucketDto(val timeBucket: String, val count: Int = 0)

/** Columnar bucket contents: one array per field, all the same length. */
@Serializable
data class TimeBucketAssetsDto(
    val id: List<String> = emptyList(),
    val isImage: List<Boolean> = emptyList(),
    val ratio: List<Double?> = emptyList(),
    val isFavorite: List<Boolean> = emptyList(),
    val isTrashed: List<Boolean> = emptyList(),
    val visibility: List<String?> = emptyList(),
    val duration: List<JsonElement?> = emptyList(),
    val thumbhash: List<String?> = emptyList(),
    val fileCreatedAt: List<String> = emptyList(),
    val localOffsetHours: List<Double?> = emptyList(),
    val livePhotoVideoId: List<String?> = emptyList(),
    val ownerId: List<String> = emptyList(),
    val stack: List<List<String>?>? = null,
    val city: List<String?>? = null,
    val country: List<String?>? = null,
) {
    fun toAssets(): List<Asset> = id.indices.map { i ->
        Asset(
            id = id[i],
            isImage = isImage.getOrElse(i) { true },
            ratio = (ratio.getOrNull(i) ?: 1.0).toFloat().takeIf { it > 0f && it.isFinite() } ?: 1f,
            isFavorite = isFavorite.getOrElse(i) { false },
            isTrashed = isTrashed.getOrElse(i) { false },
            visibility = visibility.getOrNull(i) ?: "timeline",
            durationMs = parseDurationMs(duration.getOrNull(i)),
            thumbhash = thumbhash.getOrNull(i),
            takenAtMs = parseInstantMs(fileCreatedAt.getOrNull(i)) ?: 0L,
            localOffsetHours = localOffsetHours.getOrNull(i) ?: 0.0,
            livePhotoVideoId = livePhotoVideoId.getOrNull(i),
            stackCount = stack?.getOrNull(i)?.getOrNull(1)?.toIntOrNull() ?: 0,
            ownerId = ownerId.getOrElse(i) { "" },
            city = city?.getOrNull(i),
            country = country?.getOrNull(i),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Server DTOs (only the fields the app uses; unknown keys are ignored)
// ---------------------------------------------------------------------------------------------

@Serializable
data class LoginResponse(
    val accessToken: String,
    val userId: String,
    val userEmail: String = "",
    val name: String = "",
    val isAdmin: Boolean = false,
    val shouldChangePassword: Boolean = false,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String = "",
    val email: String = "",
    val profileImagePath: String = "",
    val avatarColor: String? = null,
    val profileChangedAt: String? = null,
)

@Serializable
data class UserMeDto(
    val id: String,
    val name: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val profileImagePath: String = "",
    val profileChangedAt: String? = null,
    val avatarColor: String? = null,
    val quotaSizeInBytes: Long? = null,
    val quotaUsageInBytes: Long? = null,
    val storageLabel: String? = null,
    val oauthId: String? = null,
)

@Serializable
data class ServerFeaturesDto(
    val oauth: Boolean = false,
    val oauthAutoLaunch: Boolean = false,
    val passwordLogin: Boolean = true,
    val smartSearch: Boolean = false,
    val facialRecognition: Boolean = false,
    val map: Boolean = false,
    val trash: Boolean = true,
    val reverseGeocoding: Boolean = false,
    val search: Boolean = true,
    val duplicateDetection: Boolean = false,
    val ocr: Boolean = false,
)

@Serializable
data class ServerConfigDto(
    val externalDomain: String = "",
    val loginPageMessage: String = "",
    val oauthButtonText: String = "",
    val trashDays: Int = 30,
    val mapDarkStyleUrl: String = "",
    val mapLightStyleUrl: String = "",
    val isInitialized: Boolean = true,
    val maintenanceMode: Boolean = false,
)

@Serializable
data class ServerVersionDto(val major: Int = 0, val minor: Int = 0, val patch: Int = 0) {
    override fun toString() = "v$major.$minor.$patch"
}

@Serializable
data class ServerAboutDto(val version: String = "", val versionUrl: String = "", val licensed: Boolean = false)

@Serializable
data class ServerStorageDto(
    val diskSize: String = "",
    val diskUse: String = "",
    val diskAvailable: String = "",
    val diskSizeRaw: Long = 0,
    val diskUseRaw: Long = 0,
    val diskAvailableRaw: Long = 0,
    val diskUsagePercentage: Double = 0.0,
)

@Serializable
data class UsageByUserDto(
    val userId: String,
    val userName: String = "",
    val photos: Long = 0,
    val videos: Long = 0,
    val usage: Long = 0,
    val quotaSizeInBytes: Long? = null,
)

@Serializable
data class ServerStatsDto(
    val photos: Long = 0,
    val videos: Long = 0,
    val usage: Long = 0,
    val usagePhotos: Long = 0,
    val usageVideos: Long = 0,
    val usageByUser: List<UsageByUserDto> = emptyList(),
)

@Serializable
data class ExifDto(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val fNumber: Double? = null,
    val focalLength: Double? = null,
    val iso: Long? = null,
    val exposureTime: String? = null,
    val exifImageWidth: Long? = null,
    val exifImageHeight: Long? = null,
    val fileSizeInByte: Long? = null,
    val dateTimeOriginal: String? = null,
    val timeZone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val description: String? = null,
    val rating: Int? = null,
    val projectionType: String? = null,
    /** EXIF orientation ("1".."8"); 5 to 8 mean the stored image is turned on its side. */
    val orientation: String? = null,
)

@Serializable
data class PersonDto(
    val id: String,
    val name: String = "",
    val thumbnailPath: String = "",
    val isHidden: Boolean = false,
    val isFavorite: Boolean = false,
    val birthDate: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TagDto(val id: String, val name: String = "", val value: String = "", val color: String? = null, val parentId: String? = null)

@Serializable
data class AssetStackDto(val id: String, val primaryAssetId: String = "", val assetCount: Int = 0)

@Serializable
data class AssetDto(
    val id: String,
    val type: String = "IMAGE",
    val originalFileName: String = "",
    val originalMimeType: String? = null,
    val fileCreatedAt: String = "",
    val fileModifiedAt: String = "",
    val localDateTime: String = "",
    val updatedAt: String = "",
    val createdAt: String = "",
    val ownerId: String = "",
    val owner: UserDto? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val isOffline: Boolean = false,
    val isEdited: Boolean = false,
    val visibility: String = "timeline",
    val duration: JsonElement? = null,
    val thumbhash: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val livePhotoVideoId: String? = null,
    val exifInfo: ExifDto? = null,
    val people: List<PersonDto>? = null,
    val tags: List<TagDto>? = null,
    val stack: AssetStackDto? = null,
    val libraryId: String? = null,
    val checksum: String = "",
    val duplicateId: String? = null,
) {
    val isImage: Boolean get() = type != "VIDEO"
    val durationMs: Long? get() = parseDurationMs(duration)

    fun toAsset(): Asset {
        // Asset width/height already account for rotation; EXIF sizes are a fallback for older servers.
        val w = width ?: exifInfo?.exifImageWidth?.toDouble()
        val h = height ?: exifInfo?.exifImageHeight?.toDouble()
        // Local offset: localDateTime is the wall-clock time written as if it were UTC.
        val taken = parseInstantMs(fileCreatedAt) ?: 0L
        val local = parseInstantMs(localDateTime)
        val offsetH = if (local != null && taken != 0L) (local - taken) / 3_600_000.0 else 0.0
        return Asset(
            id = id,
            isImage = isImage,
            ratio = if (w != null && h != null && w > 0 && h > 0) (w / h).toFloat() else 1f,
            isFavorite = isFavorite,
            isTrashed = isTrashed,
            visibility = if (isArchived && visibility == "timeline") "archive" else visibility,
            durationMs = durationMs,
            thumbhash = thumbhash,
            takenAtMs = taken,
            localOffsetHours = offsetH,
            livePhotoVideoId = livePhotoVideoId,
            stackCount = stack?.assetCount ?: 0,
            ownerId = ownerId,
            city = exifInfo?.city,
            country = exifInfo?.country,
        )
    }
}

@Serializable
data class AlbumUserDto(val user: UserDto, val role: String = "viewer")

@Serializable
data class AlbumDto(
    val id: String,
    val albumName: String = "",
    val description: String = "",
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int = 0,
    val albumUsers: List<AlbumUserDto> = emptyList(),
    /** Immich 2.x only; 3.x lists the owner in [albumUsers]. */
    val owner: UserDto? = null,
    val ownerId: String? = null,
    val shared: Boolean = false,
    val hasSharedLink: Boolean = false,
    val isActivityEnabled: Boolean = true,
    val startDate: String? = null,
    val endDate: String? = null,
    val order: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val lastModifiedAssetTimestamp: String? = null,
) {
    val ownerUser: UserDto? get() = owner ?: albumUsers.firstOrNull { it.role == "owner" }?.user
    val ownerUserId: String? get() = ownerId ?: ownerUser?.id
    /** People the album is shared with (not the owner). */
    val members: List<AlbumUserDto> get() = albumUsers.filter { it.role != "owner" && it.user.id != ownerUserId }
}

@Serializable
data class SearchAssetsDto(
    val items: List<AssetDto> = emptyList(),
    val total: Int = 0,
    val count: Int = 0,
    val nextPage: String? = null,
)

@Serializable
data class SearchAlbumsDto(val items: List<AlbumDto> = emptyList(), val total: Int = 0)

@Serializable
data class SearchResponseDto(val assets: SearchAssetsDto = SearchAssetsDto(), val albums: SearchAlbumsDto = SearchAlbumsDto())

@Serializable
data class ExploreItemDto(val value: String, val data: AssetDto)

@Serializable
data class ExploreDto(val fieldName: String, val items: List<ExploreItemDto> = emptyList())

@Serializable
data class PeopleDto(
    val people: List<PersonDto> = emptyList(),
    val total: Int = 0,
    val hidden: Int = 0,
    val hasNextPage: Boolean? = null,
)

@Serializable
data class PersonStatsDto(val assets: Int = 0)

@Serializable
data class PlaceDto(
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val admin1name: String? = null,
    val admin2name: String? = null,
)

@Serializable
data class MemoryDataDto(val year: Int? = null, val personName: String? = null)

@Serializable
data class MemoryDto(
    val id: String,
    val type: String = "on_this_day",
    val data: MemoryDataDto = MemoryDataDto(),
    val assets: List<AssetDto> = emptyList(),
    val memoryAt: String = "",
    val isSaved: Boolean = false,
    val seenAt: String? = null,
)

@Serializable
data class MapMarkerDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
)

@Serializable
data class SharedLinkDto(
    val id: String,
    val key: String,
    val slug: String? = null,
    val type: String = "INDIVIDUAL",
    val description: String? = null,
    val expiresAt: String? = null,
    val createdAt: String = "",
    val allowDownload: Boolean = true,
    val allowUpload: Boolean = false,
    val showMetadata: Boolean = true,
    val password: String? = null,
    val album: AlbumDto? = null,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
data class PartnerDto(
    val id: String,
    val name: String = "",
    val email: String = "",
    val inTimeline: Boolean? = null,
    val profileImagePath: String = "",
    val profileChangedAt: String? = null,
)

@Serializable
data class ActivityDto(
    val id: String,
    val type: String,
    val createdAt: String = "",
    val comment: String? = null,
    val assetId: String? = null,
    val user: UserDto,
)

@Serializable
data class AuthStatusDto(
    val pinCode: Boolean = false,
    val password: Boolean = true,
    val isElevated: Boolean = false,
    val expiresAt: String? = null,
    val pinExpiresAt: String? = null,
)

@Serializable
data class OAuthAuthorizeDto(val url: String)

@Serializable
data class QueueStatsDto(
    val active: Long = 0,
    val completed: Long = 0,
    val failed: Long = 0,
    val delayed: Long = 0,
    val waiting: Long = 0,
    val paused: Long = 0,
)

@Serializable
data class QueueDto(val name: String, val isPaused: Boolean = false, val statistics: QueueStatsDto = QueueStatsDto())

@Serializable
data class BulkIdResultDto(val id: String, val success: Boolean = true, val error: String? = null)

@Serializable
data class UploadCheckResultDto(
    val id: String,
    val action: String,
    val reason: String? = null,
    val assetId: String? = null,
    val isTrashed: Boolean? = null,
)

@Serializable
data class UploadCheckDto(val results: List<UploadCheckResultDto> = emptyList())

@Serializable
data class UploadResultDto(val id: String, val status: String = "created")

@Serializable
data class TrashResultDto(val count: Int = 0)

@Serializable
data class PingDto(val res: String = "")

// ---------------------------------------------------------------------------------------------
// Editing, OCR, stacks, faces, duplicates, sessions
// ---------------------------------------------------------------------------------------------

@Serializable
data class EditDto(val action: String, val parameters: JsonElement)

@Serializable
data class EditsDto(val assetId: String = "", val edits: List<EditDto> = emptyList())

/** Recognised text; corners are fractions of the image (0..1) on current servers. */
@Serializable
data class OcrDto(
    val id: String,
    val text: String = "",
    val x1: Double = 0.0, val y1: Double = 0.0,
    val x2: Double = 0.0, val y2: Double = 0.0,
    val x3: Double = 0.0, val y3: Double = 0.0,
    val x4: Double = 0.0, val y4: Double = 0.0,
)

@Serializable
data class StackDto(val id: String, val primaryAssetId: String = "", val assets: List<AssetDto> = emptyList())

@Serializable
data class FaceDto(
    val id: String,
    val boundingBoxX1: Int = 0,
    val boundingBoxY1: Int = 0,
    val boundingBoxX2: Int = 0,
    val boundingBoxY2: Int = 0,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val person: PersonDto? = null,
)

@Serializable
data class DuplicateGroupDto(
    val duplicateId: String,
    val assets: List<AssetDto> = emptyList(),
    val suggestedKeepAssetIds: List<String> = emptyList(),
)

@Serializable
data class VersionCheckDto(val checkedAt: String? = null, val releaseVersion: String? = null)

@Serializable
data class SharedPersonUserDto(val id: String, val name: String = "")

@Serializable
data class PersonFullDto(
    val id: String,
    val name: String = "",
    val isHidden: Boolean = false,
    val isFavorite: Boolean = false,
    val updatedAt: String? = null,
    val sharedBy: List<SharedPersonUserDto>? = null,
)
