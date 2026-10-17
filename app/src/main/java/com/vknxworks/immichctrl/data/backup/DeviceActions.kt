package com.vknxworks.immichctrl.data.backup

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.uploadCheck
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Changes to photos stored on the phone. Android shows its own confirmation for each, so requests
 * go to the activity (which owns the system dialog) and the result comes back here.
 */
class DeviceActions(private val context: Context) {
    class Request(val sender: IntentSender, val onResult: (Boolean) -> Unit)

    private val _requests = MutableSharedFlow<Request>(extraBufferCapacity = 4)
    val requests: SharedFlow<Request> = _requests.asSharedFlow()

    /** Moves photos to the phone's own trash (recoverable there for about 30 days). */
    suspend fun trash(uris: List<Uri>): Boolean = ask { MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender }

    /** Deletes photos from the phone for good. */
    suspend fun delete(uris: List<Uri>): Boolean = ask { MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender }

    private suspend fun ask(make: () -> IntentSender): Boolean {
        val sender = runCatching { make() }.getOrElse {
            AppLog.e("Device", "Couldn't ask to change photos on the phone", it)
            return false
        }
        return suspendCancellableCoroutine { cont ->
            if (!_requests.tryEmit(Request(sender) { ok -> if (cont.isActive) cont.resume(ok) })) cont.resume(false)
        }
    }
}

/** Options for Free Up Space. */
data class FreeUpOptions(
    /** Only photos taken before this (epoch ms); null for any age. */
    val before: Long? = null,
    val keepFavorites: Boolean = true,
    val keepAlbums: Set<String> = emptySet(),
    val keepPhotos: Boolean = false,
    val keepVideos: Boolean = false,
)

data class FreeUpPlan(val items: List<LocalItem>, val bytes: Long)

/** Finds phone copies that are safely on the server, so they can be removed from the phone. */
object FreeUpSpace {
    /**
     * Candidates are photos backed up from this phone that the server still has outside its trash
     * (checked again now, by checksum), filtered by [options].
     */
    suspend fun plan(
        context: Context,
        db: BackupDb,
        client: ImmichClient,
        account: String,
        options: FreeUpOptions,
        onProgress: (String) -> Unit,
    ): FreeUpPlan = withContext(Dispatchers.IO) {
        val done = db.uploaded(account)
        val candidates = LocalMedia.items(context, null, options.keepAlbums, true).filter { item ->
            db.isDone(done, item) &&
                (options.before == null || item.takenMs < options.before) &&
                !(options.keepFavorites && item.isFavorite) &&
                !(options.keepPhotos && !item.isVideo) &&
                !(options.keepVideos && item.isVideo)
        }
        val safe = ArrayList<LocalItem>()
        candidates.chunked(100).forEachIndexed { i, batch ->
            onProgress("Checking ${minOf((i + 1) * 100, candidates.size)} of ${candidates.size} with the server…")
            val hashes = batch.associateWith { item -> db.cachedHash(item) ?: LocalMedia.sha1(context, item.uri)?.also { db.saveHash(item, it) } }
            val check = client.uploadCheck(hashes.mapNotNull { (item, sha) -> sha?.let { item.id.toString() to it } })
            val onServer = check.results
                .filter { it.action == "reject" && it.reason == "duplicate" && it.isTrashed != true }
                .mapTo(HashSet()) { it.id }
            safe += batch.filter { it.id.toString() in onServer }
        }
        AppLog.i("FreeUp", "${safe.size} of ${candidates.size} candidates confirmed on the server")
        FreeUpPlan(safe, safe.sumOf { it.size })
    }

    fun cutoff(days: Int?): Long? = days?.let { Instant.now().minusSeconds(it * 86_400L).toEpochMilli() }
}
