package com.vknxworks.immichctrl.data.backup

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.security.MessageDigest
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/** A folder of photos on the phone (MediaStore bucket): Camera, Screenshots, WhatsApp Images... */
data class LocalAlbum(val id: String, val name: String, val count: Int, val coverUri: Uri?)

data class LocalItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val takenMs: Long,
    val modifiedMs: Long,
    val durationMs: Long,
    val isVideo: Boolean,
    val isFavorite: Boolean,
    val bucketId: String,
    val bucketName: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

object LocalMedia {
    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

    /** Only some photos were shared with the app (Android 14 "select photos"). */
    fun isPartialAccess(context: Context): Boolean =
        !hasPermission(context) && android.os.Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED

    private const val MEDIA_SELECTION =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    fun albums(context: Context): List<LocalAlbum> {
        val counts = LinkedHashMap<String, Triple<String, Int, Uri?>>()
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        context.contentResolver.query(filesUri, proj, MEDIA_SELECTION, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (c.moveToNext()) {
                val bucket = c.getString(bucketCol) ?: continue
                val prev = counts[bucket]
                if (prev == null) {
                    val video = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    counts[bucket] = Triple(c.getString(nameCol) ?: Strings.str(R.string.unnamed), 1, contentUri(c.getLong(idCol), video))
                } else {
                    counts[bucket] = prev.copy(second = prev.second + 1)
                }
            }
        }
        return counts.map { (id, t) -> LocalAlbum(id, t.first, t.second, t.third) }
            .sortedWith(compareByDescending<LocalAlbum> { it.name.equals("Camera", true) }.thenByDescending { it.count })
    }

    /** Photos and videos in [bucketIds] (null = every album on the phone), oldest first. */
    fun items(context: Context, bucketIds: Set<String>?, excluded: Set<String>, videos: Boolean): List<LocalItem> {
        if (bucketIds != null && bucketIds.isEmpty()) return emptyList()
        return query(context, null, null) { it.bucketId !in excluded && (bucketIds == null || it.bucketId in bucketIds) && (videos || !it.isVideo) }
    }

    /** Specific items by MediaStore id. */
    fun itemsByIds(context: Context, ids: Collection<Long>): List<LocalItem> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toHashSet()
        return query(context, null, null) { it.id in wanted }
    }

    private fun query(context: Context, extraSelection: String?, args: Array<String>?, keep: (LocalItem) -> Boolean): List<LocalItem> {
        val out = ArrayList<LocalItem>()
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
        )
        val selection = if (extraSelection != null) "$MEDIA_SELECTION AND $extraSelection" else MEDIA_SELECTION
        context.contentResolver.query(filesUri, proj, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val favCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val oCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            while (c.moveToNext()) {
                val bucket = c.getString(bucketCol) ?: continue
                val video = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val size = c.getLong(sizeCol)
                if (size <= 0) continue
                val id = c.getLong(idCol)
                val modified = c.getLong(modCol) * 1000
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: modified.takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                val rotated = c.getInt(oCol) % 180 != 0
                val item = LocalItem(
                    id = id,
                    uri = contentUri(id, video),
                    name = c.getString(nameCol) ?: "$id",
                    mime = c.getString(mimeCol) ?: if (video) "video/mp4" else "image/jpeg",
                    size = size,
                    takenMs = taken,
                    modifiedMs = if (modified > 0) modified else taken,
                    durationMs = if (video) c.getLong(durCol) else 0L,
                    isVideo = video,
                    isFavorite = c.getInt(favCol) == 1,
                    bucketId = bucket,
                    bucketName = c.getString(bucketNameCol) ?: "",
                    width = if (rotated) c.getInt(hCol) else c.getInt(wCol),
                    height = if (rotated) c.getInt(wCol) else c.getInt(hCol),
                )
                if (keep(item)) out += item
            }
        }
        return out
    }

    fun contentUri(id: Long, video: Boolean): Uri = ContentUris.withAppendedId(
        if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        id,
    )

    /**
     * Opens the file as stored, location included. Android strips GPS from photos unless the app
     * holds "access media location" and asks for the original.
     */
    fun open(context: Context, uri: Uri): InputStream? {
        val original = runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
            } else null
        }.getOrNull()
        return original ?: context.contentResolver.openInputStream(uri)
    }

    /** Base64 SHA-1 of the file, which is how Immich spots photos it already has. */
    fun sha1(context: Context, uri: Uri): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-1")
        open(context, uri)?.use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        } ?: return null
        Base64.encodeToString(md.digest(), Base64.NO_WRAP)
    }.getOrNull()
}
