package com.vknxworks.immichctrl.data.backup

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.auth.SessionManager
import com.vknxworks.immichctrl.data.settings.AppSettings
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** What's on this phone, relative to the server: photos not backed up yet, and copies of server photos. */
data class LocalSnapshot(
    /** Photos only on this phone, by month, newest first. */
    val localOnly: Map<YearMonth, List<Asset>> = emptyMap(),
    /** Server asset id -> MediaStore id, for photos that were backed up from this phone. */
    val onDevice: Map<String, Long> = emptyMap(),
)

/** The phone's photos as the app sees them, refreshed when the gallery or the backup record changes. */
class LocalLibrary(
    private val context: Context,
    private val db: BackupDb,
    private val sessions: SessionManager,
    private val settings: SettingsRepository,
) {
    private val _version = MutableStateFlow(0)
    /** Bumps whenever the phone's photos (or what's backed up) change. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun changed() {
        _version.value++
    }

    /** Listens for photos being taken, deleted or edited on the phone. */
    fun watchGallery() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            private var pending = false
            override fun onChange(selfChange: Boolean) {
                if (pending) return
                pending = true
                Handler(Looper.getMainLooper()).postDelayed({ pending = false; changed() }, 2_000)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        }
    }

    private fun backupAlbums(s: AppSettings): Set<String>? = if (s.backupEverything) null else s.backupAlbums

    /** For the Photos timeline: photos from the backup albums that aren't on the server yet. */
    suspend fun snapshot(): LocalSnapshot = withContext(Dispatchers.IO) {
        val account = sessions.active.value?.account?.key ?: return@withContext LocalSnapshot()
        if (!LocalMedia.hasPermission(context)) return@withContext LocalSnapshot()
        val s = settings.current()
        val remote = db.remoteIds(account)
        val onDevice = remote.entries.associate { (local, rem) -> rem to local }
        if (!s.showLocal) return@withContext LocalSnapshot(onDevice = onDevice)
        // Nothing chosen for backup yet: show the camera roll, which is what people expect to see.
        val buckets = backupAlbums(s)?.ifEmpty { cameraBuckets() }
        val done = db.uploaded(account)
        val items = LocalMedia.items(context, buckets, s.excludedAlbums, true).filterNot { db.isDone(done, it) }
        val byMonth = items.map { it.toAsset() }
            .sortedByDescending { it.takenAtMs }
            .groupBy { YearMonth.from(it.localDate) }
        LocalSnapshot(byMonth, onDevice)
    }

    private fun cameraBuckets(): Set<String> =
        LocalMedia.albums(context).filter { it.name.equals("Camera", true) }.mapTo(HashSet()) { it.id }

    /** Every item in one phone album, with whether each one is on the server already. */
    suspend fun album(bucketId: String): List<Asset> = withContext(Dispatchers.IO) {
        val account = sessions.active.value?.account?.key
        val done = account?.let { db.uploaded(it) }.orEmpty()
        val remote = account?.let { db.remoteIds(it) }.orEmpty()
        LocalMedia.items(context, setOf(bucketId), emptySet(), true)
            .sortedByDescending { it.takenMs }
            .map { item ->
                val rem = if (db.isDone(done, item)) remote[item.id] else null
                if (rem != null) item.toAsset().copy(id = rem, isLocalOnly = false, onDevice = true) else item.toAsset()
            }
            // Identical copies on the phone all point at one server photo; show it once (grid keys must be unique).
            .distinctBy { it.id }
    }

    companion object {
        fun LocalItem.toAsset(): Asset {
            val offsetSec = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(takenMs)).totalSeconds
            return Asset(
                id = "local:$id",
                isImage = !isVideo,
                ratio = if (width > 0 && height > 0) width.toFloat() / height else 1f,
                isFavorite = isFavorite,
                isTrashed = false,
                visibility = "timeline",
                durationMs = durationMs.takeIf { it > 0 },
                thumbhash = null,
                takenAtMs = takenMs,
                localOffsetHours = offsetSec / 3600.0,
                livePhotoVideoId = null,
                stackCount = 0,
                ownerId = "",
                localUri = uri.toString(),
                isLocalOnly = true,
                localId = id,
                fileName = name,
                fileSize = size,
            )
        }

        /** MediaStore id from a local asset id ("local:123"). */
        fun localIdOf(a: Asset): Long? = a.localId ?: a.id.removePrefix("local:").toLongOrNull()
    }
}
