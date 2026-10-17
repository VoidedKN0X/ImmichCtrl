package com.vknxworks.immichctrl.data.backup

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.ApiException
import com.vknxworks.immichctrl.data.api.ApiJson
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.UploadResultDto
import com.vknxworks.immichctrl.data.api.addToAlbum
import com.vknxworks.immichctrl.data.api.formatUploadDuration
import com.vknxworks.immichctrl.data.api.majorVersion
import com.vknxworks.immichctrl.data.api.serverVersion
import com.vknxworks.immichctrl.data.api.uploadCheck
import com.vknxworks.immichctrl.data.timeline.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Uploads new photos and videos from the chosen phone albums, or files shared to the app.
 * Each batch is checked against the server first (by SHA-1), so nothing already in Immich is sent again.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sessions.ready.first { it }
        val session = c.sessions.active.value ?: return Result.success()
        val mode = inputData.getString(KEY_MODE) ?: MODE_AUTO
        return lock.withLock {
            try {
                runCatching {
                    val v = session.client.serverVersion().toString()
                    session.client.serverMajor = majorVersion(v) ?: session.client.serverMajor
                }
                when (mode) {
                    MODE_SHARED -> uploadShared(session.client)
                    else -> backup(session.client, session.account.key, mode)
                }
            } finally {
                if (mode == MODE_TRIGGER) c.backup.scheduleTrigger()
            }
        }
    }

    // ---------------- Phone albums ----------------

    private suspend fun backup(client: ImmichClient, accountKey: String, mode: String): Result {
        val c = applicationContext.container
        val settings = c.settings.current()
        if (mode != MODE_MANUAL && !settings.backupEnabled) return Result.success()
        if (!LocalMedia.hasPermission(applicationContext)) return Result.failure(workDataOf(KEY_ERROR to "Photo access is off"))

        val items = withContext(Dispatchers.IO) {
            LocalMedia.items(applicationContext, settings.backupAlbums, settings.excludedAlbums, settings.backupVideos)
        }
        val done = withContext(Dispatchers.IO) { c.backupDb.uploaded(accountKey) }
        val todo = items.filterNot { c.backupDb.isDone(done, it) }
        if (todo.isEmpty()) return Result.success(workDataOf(KEY_UPLOADED to 0))

        startForeground(0, todo.size, null)
        var uploaded = 0
        var skipped = 0
        var failed = 0
        var processed = 0
        for (batch in todo.chunked(BATCH)) {
            if (isStopped) break
            val hashes = withContext(Dispatchers.IO) {
                batch.associateWith { item -> c.backupDb.cachedHash(item) ?: LocalMedia.sha1(applicationContext, item.uri)?.also { c.backupDb.saveHash(item, it) } }
            }
            val known = hashes.filterValues { it != null }
            val check = try {
                client.uploadCheck(known.map { (item, sha) -> item.id.toString() to sha!! })
            } catch (e: IOException) {
                return Result.retry()
            }
            val results = check.results.associateBy { it.id }
            for (item in batch) {
                if (isStopped) break
                processed++
                val sha = hashes[item]
                if (sha == null) {
                    failed++
                    continue
                }
                val r = results[item.id.toString()]
                if (r != null && r.action == "reject") {
                    // Already on the server (maybe in the trash); remember it so it isn't checked every run.
                    c.backupDb.markUploaded(accountKey, item, r.assetId)
                    skipped++
                    continue
                }
                setProgressAsync(workDataOf(KEY_DONE to processed, KEY_TOTAL to todo.size, KEY_CURRENT to item.name))
                notifyProgress(processed, todo.size, item.name)
                try {
                    val result = upload(client, item.name, item.mime, item.size, item.takenMs, item.modifiedMs, item.isFavorite,
                        item.durationMs, sha, deviceAssetId = "${item.id}-${item.size}") { LocalMedia.open(applicationContext, item.uri) }
                    c.backupDb.markUploaded(accountKey, item, result.id)
                    uploaded++
                } catch (e: ApiException) {
                    failed++
                } catch (e: IOException) {
                    // Lost the connection: try again later, keeping what's done.
                    finish(uploaded, skipped, failed)
                    return Result.retry()
                }
            }
        }
        finish(uploaded, skipped, failed)
        return Result.success(workDataOf(KEY_UPLOADED to uploaded, KEY_FAILED to failed))
    }

    private suspend fun finish(uploaded: Int, skipped: Int, failed: Int) {
        if (uploaded > 0) withContext(Dispatchers.Main) { Sources.refreshAll() }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
        if (uploaded == 0 && failed == 0) return
        val text = buildList {
            if (uploaded > 0) add("$uploaded backed up")
            if (skipped > 0) add("$skipped already on the server")
            if (failed > 0) add("$failed couldn't be uploaded")
        }.joinToString(", ")
        nm.notify(
            DONE_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Backup finished")
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    // ---------------- Shared to the app ----------------

    private suspend fun uploadShared(client: ImmichClient): Result {
        val paths = inputData.getStringArray(KEY_FILES).orEmpty()
        val albumId = inputData.getString(KEY_ALBUM)
        if (paths.isEmpty()) return Result.success()
        startForeground(0, paths.size, null)
        val ids = ArrayList<String>()
        var failed = 0
        paths.forEachIndexed { i, path ->
            val f = File(path)
            if (!f.exists()) return@forEachIndexed
            notifyProgress(i + 1, paths.size, f.name)
            try {
                val sha = withContext(Dispatchers.IO) { sha1(f) }
                val mime = mimeFor(f.name)
                val taken = withContext(Dispatchers.IO) { exifTakenMs(f) } ?: f.lastModified()
                val res = upload(client, f.name, mime, f.length(), taken, f.lastModified(), false, 0, sha, deviceAssetId = "shared-$sha") { f.inputStream() }
                ids += res.id
                f.delete()
                f.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
            } catch (e: ApiException) {
                failed++
            } catch (e: IOException) {
                return Result.retry()
            }
        }
        if (albumId != null && ids.isNotEmpty()) runCatching { client.addToAlbum(albumId, ids) }
        finish(ids.size, 0, failed)
        return Result.success(workDataOf(KEY_UPLOADED to ids.size))
    }

    // ---------------- Upload ----------------

    private suspend fun upload(
        client: ImmichClient,
        name: String,
        mime: String,
        size: Long,
        takenMs: Long,
        modifiedMs: Long,
        favorite: Boolean,
        durationMs: Long,
        sha1: String,
        deviceAssetId: String,
        open: () -> InputStream?,
    ): UploadResultDto {
        val file = object : RequestBody() {
            override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                (open() ?: throw IOException("Can't read $name")).source().use { sink.writeAll(it) }
            }
        }
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
        if (client.serverMajor < 3) {
            // Immich 2.x still requires these; 3.x dropped them.
            form.addFormDataPart("deviceAssetId", deviceAssetId)
            form.addFormDataPart("deviceId", deviceId())
        }
        form.addFormDataPart("fileCreatedAt", iso(takenMs))
        form.addFormDataPart("fileModifiedAt", iso(modifiedMs))
        form.addFormDataPart("filename", name)
        form.addFormDataPart("isFavorite", favorite.toString())
        if (durationMs > 0) form.addFormDataPart("duration", formatUploadDuration(durationMs, client.serverMajor))
        form.addFormDataPart("assetData", name, file)
        val req = client.request(client.url("/assets"))
            .header("x-immich-checksum", sha1)
            .post(form.build())
            .build()
        val text = client.execute(req).use { it.body?.string().orEmpty() }
        return ApiJson.decodeFromString(UploadResultDto.serializer(), text)
    }

    @SuppressLint("HardwareIds")
    private fun deviceId(): String =
        Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "immichctrl"

    // ---------------- Notifications ----------------

    private suspend fun startForeground(done: Int, total: Int, name: String?) {
        runCatching { setForeground(foregroundInfo(done, total, name)) }
    }

    private fun notifyProgress(done: Int, total: Int, name: String?) {
        runCatching {
            applicationContext.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(done, total, name))
        }
    }

    private fun notification(done: Int, total: Int, name: String?) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Backing up $done of $total")
            .setContentText(name ?: "Checking what's new")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun foregroundInfo(done: Int, total: Int, name: String?) =
        ForegroundInfo(NOTIFICATION_ID, notification(done, total, name), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0, null)

    companion object {
        const val CHANNEL = "backup"
        const val NOTIFICATION_ID = 4101
        const val DONE_ID = 4102
        private const val BATCH = 30

        const val KEY_MODE = "mode"
        const val KEY_FILES = "files"
        const val KEY_ALBUM = "album"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_FAILED = "failed"
        const val KEY_ERROR = "error"

        const val MODE_AUTO = "auto"
        const val MODE_TRIGGER = "trigger"
        const val MODE_MANUAL = "manual"
        const val MODE_SHARED = "shared"

        /** One backup at a time, whichever way it was started. */
        private val lock = Mutex()

        private fun iso(ms: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms))

        fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic", "heif" -> "image/heic"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "dng" -> "image/x-adobe-dng"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }

        private fun sha1(f: File): String {
            val md = java.security.MessageDigest.getInstance("SHA-1")
            f.inputStream().use { input ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
        }

        private fun exifTakenMs(f: File): Long? = runCatching {
            val exif = ExifInterface(f)
            val s = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
            LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
