package com.vknxworks.immichctrl.data.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Getting photos out of Immich: saving originals to the phone and sharing them to other apps. */
object Exporter {

    /**
     * Saves originals to Pictures/ImmichCtrl. They're fetched through the app's own HTTP client (so the
     * access token stays inside the app, is never handed to the system download manager or stored in
     * its database, never follows a redirect to another host, and the HTTPS rules apply) and written
     * straight into the phone's photo library.
     */
    suspend fun download(context: Context, client: ImmichClient, ids: Collection<String>): Int = coroutineScope {
        val gate = Semaphore(3)
        ids.map { id ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    runCatching { saveOriginal(context, client, id) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; AppLog.e("Save", "Couldn't save a photo to the phone", it) }
                        .isSuccess
                }
            }
        }.awaitAll().count { it }
    }

    private suspend fun saveOriginal(context: Context, client: ImmichClient, id: String) {
        val dto = client.asset(id)
        val name = safeFileName(dto.originalFileName, "$id.jpg")
        val mime = dto.originalMimeType?.takeIf { it.startsWith("image/") || it.startsWith("video/") }
            ?: if (dto.isImage) "image/jpeg" else "video/mp4"
        val collection = if (mime.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ImmichCtrl")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create $name")
        try {
            client.execute(client.request(client.url("/assets/$id/original")).build()).use { resp ->
                val body = resp.body ?: throw IOException("Empty response")
                (resolver.openOutputStream(uri) ?: throw IOException("Couldn't write $name")).use { out ->
                    body.byteStream().copyTo(out)
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** A file name from the server, made safe to use as one path segment. */
    fun safeFileName(name: String?, fallback: String): String {
        val cleaned = name.orEmpty()
            .replace(Regex("[\\u0000-\\u001f/\\\\:*?\"<>|]"), "_")
            .trim()
            .trimStart('.')
            .take(150)
        return cleaned.ifBlank { fallback }
    }

    /** A server id as a directory name: hashed, so it can never point outside its parent. */
    private fun safeDirName(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    /** Removes share and upload copies left behind (e.g. the app was closed mid-way) after a day. */
    fun cleanUp(context: Context) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        listOf("shared", "incoming").forEach { sub ->
            File(context.cacheDir, sub).listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.deleteRecursively()
            }
        }
    }

    /**
     * Fetches server photos into the app's cache (originals, or [large] previews for smaller files)
     * and opens the share sheet with them plus any [phoneUris] (photos only on this phone).
     */
    suspend fun shareFiles(
        context: Context,
        client: ImmichClient,
        ids: Collection<String>,
        large: Boolean = false,
        phoneUris: List<Uri> = emptyList(),
        stripLocation: Boolean = true,
    ) {
        val dir = File(context.cacheDir, "shared").apply {
            // Old shares are no longer needed once a new one starts.
            listFiles()?.forEach { it.deleteRecursively() }
            mkdirs()
        }
        val files = coroutineScope {
            val gate = Semaphore(3)
            ids.map { id ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val dto = client.asset(id)
                        val original = safeFileName(dto.originalFileName, "$id.jpg")
                        // Videos are always sent as they are; "large" photos are Immich's JPEG preview.
                        val preview = large && dto.isImage
                        val name = if (preview) original.substringBeforeLast('.') + ".jpg" else original
                        val target = File(File(dir, safeDirName(id)).apply { mkdirs() }, name)
                        val previewUrl = client.url("/assets/$id/thumbnail", "size" to "preview")
                        fetch(client, if (preview) previewUrl else client.url("/assets/$id/original"), target)
                        val mime = if (preview) "image/jpeg" else dto.originalMimeType ?: if (dto.isImage) "image/*" else "video/*"
                        if (!stripLocation || !dto.isImage || removeLocation(target, mime)) return@withPermit target to mime
                        // The location couldn't be removed for sure: never send the original then. Immich's
                        // preview is re-encoded without the photo's metadata; send that (checked too) instead.
                        target.delete()
                        if (preview) throw IOException("Couldn't remove the location from $original")
                        val fallback = File(target.parentFile, original.substringBeforeLast('.') + ".jpg")
                        fetch(client, previewUrl, fallback)
                        if (!removeLocation(fallback, "image/jpeg")) {
                            fallback.delete()
                            throw IOException("Couldn't remove the location from $original")
                        }
                        AppLog.i("Share", "Sent a preview of a photo whose location couldn't be removed from the original")
                        fallback to "image/jpeg"
                    }
                }
            }.awaitAll()
        }
        if (files.isEmpty() && phoneUris.isEmpty()) return
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it.first) } + phoneUris
        val types = files.map { it.second } + phoneUris.map { context.contentResolver.getType(it) ?: "*/*" }
        val type = types.distinct().singleOrNull() ?: if (types.all { it.startsWith("image/") }) "image/*" else "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = type
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE, ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_SATELLITES, ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_MEASURE_MODE, ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR, ExifInterface.TAG_GPS_VERSION_ID,
    )

    private suspend fun fetch(client: ImmichClient, url: okhttp3.HttpUrl, target: File) {
        client.execute(client.request(url).build()).use { resp ->
            val body = resp.body ?: throw IOException("Empty response")
            target.sink().buffer().use { it.writeAll(body.source()) }
        }
    }

    /**
     * Takes the GPS position out of a shared copy, in formats ExifInterface can rewrite (JPEG, PNG,
     * WebP; others such as HEIC are sent as they are, as the setting says). True only when the file was
     * checked afterwards and has no position left, in its EXIF or its XMP; false when that can't be
     * guaranteed (unreadable file, a failed rewrite, a position that stayed), so it isn't sent.
     */
    private fun removeLocation(file: File, mime: String): Boolean {
        val m = mime.lowercase()
        val writable = m == "image/jpeg" || m == "image/jpg" || m == "image/png" || m == "image/webp" ||
            file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        if (!writable) return true
        return runCatching {
            val exif = ExifInterface(file)
            var changed = false
            if (GPS_TAGS.any { exif.getAttribute(it) != null }) {
                GPS_TAGS.forEach { exif.setAttribute(it, null) }
                changed = true
            }
            // Many cameras and editors repeat the position in the XMP block (exif:GPSLatitude...).
            if (exif.getAttribute(ExifInterface.TAG_XMP)?.contains("GPS") == true) {
                exif.setAttribute(ExifInterface.TAG_XMP, null)
                changed = true
            }
            if (changed) exif.saveAttributes()
            hasNoLocation(file)
        }.getOrElse {
            AppLog.w("Share", "Couldn't remove the location from a shared photo: ${it.message}")
            false
        }
    }

    /** Reads the file again: no EXIF position, no GPS tags, and no GPS fields anywhere in its bytes (XMP is text). */
    private fun hasNoLocation(file: File): Boolean {
        val exif = ExifInterface(file)
        if (exif.latLong != null || GPS_TAGS.any { exif.getAttribute(it) != null }) return false
        return !containsText(file, listOf("GPSLatitude", "GPSLongitude"))
    }

    private fun containsText(file: File, needles: List<String>): Boolean = file.inputStream().use { input ->
        val overlap = needles.maxOf { it.length } - 1
        val buf = ByteArray(256 * 1024)
        var carry = 0
        var found = false
        while (!found) {
            val n = input.read(buf, carry, buf.size - carry)
            if (n <= 0) break
            val len = carry + n
            // ISO-8859-1 maps each byte to one char, so ASCII text is found wherever it is.
            val text = String(buf, 0, len, Charsets.ISO_8859_1)
            found = needles.any { text.contains(it) }
            carry = minOf(overlap, len)
            System.arraycopy(buf, len - carry, buf, 0, carry)
        }
        found
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Public address of a shared link: the server's external domain if set, else the address the app uses. */
    fun sharedLinkUrl(client: ImmichClient, externalDomain: String?, key: String, slug: String?): String {
        val base = externalDomain?.trim()?.trimEnd('/')?.ifBlank { null } ?: client.baseUrl
        return if (!slug.isNullOrBlank()) "$base/s/$slug" else "$base/share/$key"
    }
}
