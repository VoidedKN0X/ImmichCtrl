package com.vknxworks.immichctrl.data.offline

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.ApiJson
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.album
import com.vknxworks.immichctrl.data.api.timeBucket
import com.vknxworks.immichctrl.data.api.timeBuckets
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import com.vknxworks.immichctrl.Strings

@Serializable
data class OfflineAsset(
    val id: String,
    val isImage: Boolean,
    val ratio: Float,
    val takenAtMs: Long,
    val localOffsetHours: Double,
    val durationMs: Long? = null,
    val thumbhash: String? = null,
    val isFavorite: Boolean = false,
    val ownerId: String = "",
    val file: String,
)

@Serializable
data class OfflineAlbum(
    val albumId: String,
    val name: String,
    val updatedAt: Long = 0,
    val assets: List<OfflineAsset> = emptyList(),
    val complete: Boolean = false,
) {
    val count: Int get() = assets.size
}

/**
 * Albums kept on the phone for viewing without a connection. Photos are stored at preview size
 * (sharp on a phone screen, a fraction of the original's size); videos as Immich's playback version.
 */
class OfflineStore(private val context: Context) {
    private val _albums = MutableStateFlow<List<OfflineAlbum>>(emptyList())
    val albums: StateFlow<List<OfflineAlbum>> = _albums.asStateFlow()
    private var account: String? = null

    /** Each account keeps its own offline albums. */
    fun setAccount(key: String?) {
        account = key
        reload()
    }

    private fun root(): File? = account?.let { rootFor(it) }

    private fun rootFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        return File(context.filesDir, "offline/$hash")
    }

    /** Signing out removes that account's offline albums from the phone. */
    fun deleteAccount(key: String) {
        rootFor(key).deleteRecursively()
        if (key == account) reload()
    }

    fun dir(albumId: String): File? = root()?.let { r ->
        // Album ids come from the server; never let one point outside this account's folder.
        File(r, albumId).takeIf { albumId.isNotBlank() && it.canonicalFile.parentFile == r.canonicalFile }
    }

    fun reload() {
        val r = root()
        _albums.value = r?.listFiles()?.mapNotNull { d -> read(File(d, MANIFEST)) }?.sortedBy { it.name.lowercase() }.orEmpty()
    }

    private fun read(f: File): OfflineAlbum? =
        runCatching { ApiJson.decodeFromString(OfflineAlbum.serializer(), f.readText()) }.getOrNull()

    fun get(albumId: String): OfflineAlbum? = _albums.value.firstOrNull { it.albumId == albumId }

    fun isOffline(albumId: String): Boolean = dir(albumId)?.let { File(it, MANIFEST).exists() } == true

    fun bytes(albumId: String): Long = dir(albumId)?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun totalBytes(): Long = root()?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    internal fun write(album: OfflineAlbum) {
        val d = dir(album.albumId) ?: return
        d.mkdirs()
        val tmp = File(d, "$MANIFEST.tmp")
        tmp.writeText(ApiJson.encodeToString(OfflineAlbum.serializer(), album))
        tmp.renameTo(File(d, MANIFEST))
        reload()
    }

    /** Starts keeping an album offline (or brings an offline album up to date). */
    fun sync(albumId: String, name: String) {
        if (!isOffline(albumId)) write(OfflineAlbum(albumId, name))
        val req = OneTimeWorkRequestBuilder<OfflineWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(OfflineWorker.KEY_ALBUM to albumId))
            .addTag(OfflineWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("offline-$albumId", ExistingWorkPolicy.REPLACE, req)
    }

    fun syncAll() = _albums.value.forEach { sync(it.albumId, it.name) }

    fun remove(albumId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("offline-$albumId")
        dir(albumId)?.deleteRecursively()
        reload()
    }

    /** Offline assets as grid/viewer assets, pointing at their files. */
    fun assets(albumId: String): List<Asset> {
        val a = get(albumId) ?: return emptyList()
        val d = dir(albumId) ?: return emptyList()
        return a.assets.mapNotNull { o ->
            val f = File(d, o.file)
            if (!f.exists()) return@mapNotNull null
            Asset(
                id = o.id,
                isImage = o.isImage,
                ratio = o.ratio,
                isFavorite = o.isFavorite,
                isTrashed = false,
                visibility = "timeline",
                durationMs = o.durationMs,
                thumbhash = o.thumbhash,
                takenAtMs = o.takenAtMs,
                localOffsetHours = o.localOffsetHours,
                livePhotoVideoId = null,
                stackCount = 0,
                ownerId = o.ownerId,
                localUri = Uri.fromFile(f).toString(),
            )
        }
    }

    companion object {
        const val MANIFEST = "album.json"
    }
}

/** Downloads (and tidies up) an offline album's files. */
class OfflineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sessions.ready.first { it }
        val client = c.sessions.active.value?.client ?: return Result.success()
        val albumId = inputData.getString(KEY_ALBUM) ?: return Result.success()
        val store = c.offline
        val dir = store.dir(albumId) ?: return Result.success()
        if (!store.isOffline(albumId)) return Result.success()
        return try {
            val album = client.album(albumId)
            val q = TimelineQuery(albumId = albumId)
            val assets = client.timeBuckets(q).flatMap { client.timeBucket(q, it.timeBucket) }
            runCatching { setForeground(info(0, assets.size, album.albumName)) }
            val kept = ArrayList<OfflineAsset>()
            assets.forEachIndexed { i, a ->
                if (isStopped) return Result.retry()
                val file = if (a.isImage) "${a.id}.jpg" else "${a.id}.mp4"
                val target = File(dir, file)
                if (!target.exists() || target.length() == 0L) {
                    notify(i + 1, assets.size, album.albumName)
                    val url = if (a.isImage) client.thumbnailUrl(a.id, "preview") else client.videoUrl(a.id)
                    val req = client.request(url.toHttpUrl()).build()
                    try {
                        withContext(Dispatchers.IO) {
                            client.execute(req).use { resp ->
                                val tmp = File(dir, "$file.part")
                                tmp.sink().buffer().use { it.writeAll(resp.body!!.source()) }
                                tmp.renameTo(target)
                            }
                        }
                    } catch (e: com.vknxworks.immichctrl.data.api.ApiException) {
                        // One item the server can't give (e.g. a video not transcoded yet) shouldn't stop the album.
                        AppLog.w("Offline", "Skipped ${a.id}: ${e.message}")
                        return@forEachIndexed
                    }
                }
                kept += OfflineAsset(a.id, a.isImage, a.ratio, a.takenAtMs, a.localOffsetHours, a.durationMs, a.thumbhash, a.isFavorite, a.ownerId, file)
            }
            // Remove files for photos no longer in the album.
            val keep = kept.mapTo(HashSet()) { it.file } + OfflineStore.MANIFEST
            dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
            store.write(OfflineAlbum(albumId, album.albumName, System.currentTimeMillis(), kept, complete = true))
            done()
            AppLog.i("Offline", "${album.albumName}: ${kept.size} items available offline")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLog.e("Offline", "Couldn't download album for offline use", e)
            done()
            Result.retry()
        }
    }

    private fun notification(done: Int, total: Int, name: String) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Strings.str(R.string.saving_for_offline, name))
            .setContentText(Strings.str(R.string.of, done, total))
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            // On the lock screen: no album name.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(applicationContext, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(Strings.str(R.string.saving_an_album_for_offline))
                    .setProgress(total, done, total == 0)
                    .setSilent(true)
                    .build(),
            )
            .build()

    private fun info(done: Int, total: Int, name: String) =
        ForegroundInfo(NOTIFICATION_ID, notification(done, total, name), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun notify(done: Int, total: Int, name: String) {
        runCatching { applicationContext.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(done, total, name)) }
    }

    private fun done() {
        applicationContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(0, 0, "album")

    companion object {
        const val TAG = "offline"
        const val KEY_ALBUM = "album"
        const val CHANNEL = "offline"
        private const val NOTIFICATION_ID = 4201
    }
}
