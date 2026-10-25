package com.vknxworks.immichctrl.data.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.timeBucket
import com.vknxworks.immichctrl.data.api.timeBuckets
import com.vknxworks.immichctrl.data.backup.LocalSnapshot
import com.vknxworks.immichctrl.data.sync.SyncDb
import com.vknxworks.immichctrl.data.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/**
 * A run of assets with a heading. Timelines have one per month whose contents load when scrolled
 * near; lists (search results, folders) have a single bucket that's always loaded.
 */
data class Bucket(
    val key: String,
    /** Month heading, or null for a plain list. */
    val label: String?,
    /** Count from the server; the real size once [assets] has loaded. */
    val count: Int,
    val assets: List<Asset>?,
    /** The server has this month; false for months that only have photos on this phone. */
    val remote: Boolean = true,
) {
    val size: Int get() = assets?.size ?: count
}

sealed interface SourceState {
    data object Loading : SourceState
    data object Ready : SourceState
    data class Failed(val message: String) : SourceState
}

/** Everything a grid and the full-screen viewer need from a set of assets. */
abstract class AssetSource(val key: String, protected val scope: CoroutineScope) {
    var buckets by mutableStateOf<List<Bucket>>(emptyList())
        protected set
    var state by mutableStateOf<SourceState>(SourceState.Loading)
        protected set
    var refreshing by mutableStateOf(false)
        protected set

    val total: Int get() = buckets.sumOf { it.size }

    /** Pages of a search, for sources that have more to fetch. */
    open val canLoadMore: Boolean get() = false
    open fun loadMore() {}

    abstract fun refresh(silent: Boolean = false)

    /** Drawn from the phone's synced copy of the timeline (so refreshing means syncing first). */
    open val fromLocalCopy: Boolean get() = false

    /** Should redraw after the synced copy changed: drawn from it now, or it just became usable / unusable. */
    open val followsSync: Boolean get() = false

    /** Pull to refresh. */
    open fun pullRefresh() = refresh(silent = true)

    /** Ask for a bucket's contents (no-op when loaded or loading). */
    open fun ensureLoaded(index: Int) {}

    fun assetAt(index: Int): Asset? {
        var r = index
        buckets.forEachIndexed { bi, b ->
            val n = b.size
            if (r < n) {
                val a = b.assets
                if (a == null) {
                    ensureLoaded(bi)
                    return null
                }
                return a.getOrNull(r)
            }
            r -= n
        }
        return null
    }

    fun indexOf(id: String): Int {
        var base = 0
        buckets.forEach { b ->
            val i = b.assets?.indexOfFirst { it.id == id } ?: -1
            if (i >= 0) return base + i
            base += b.size
        }
        return -1
    }

    /** Global index of the first asset in bucket [bucketIndex]. */
    fun startOf(bucketIndex: Int): Int = buckets.take(bucketIndex).sumOf { it.size }

    fun removeIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        buckets = buckets.mapNotNull { b ->
            val a = b.assets ?: return@mapNotNull b
            val kept = a.filterNot { it.id in ids }
            when {
                kept.size == a.size -> b
                kept.isEmpty() && b.label != null -> null
                else -> b.copy(count = kept.size, assets = kept)
            }
        }
    }

    fun updateIds(ids: Set<String>, change: (Asset) -> Asset) {
        if (ids.isEmpty()) return
        buckets = buckets.map { b ->
            val a = b.assets ?: return@map b
            if (a.none { it.id in ids }) b else b.copy(assets = a.map { if (it.id in ids) change(it) else it })
        }
    }

    protected fun Throwable.readable(): String = message ?: this::class.simpleName ?: Strings.str(R.string.couldn_t_load)
}

/**
 * The server's timeline for a filter (all photos, an album, a person, favourites, archive, trash...).
 * With [local], photos on this phone that aren't backed up yet are merged into their months, and
 * server photos that came from this phone are marked as such.
 */
class TimelineSource(
    key: String,
    private val client: ImmichClient,
    private val query: TimelineQuery,
    scope: CoroutineScope,
    private val local: (suspend () -> LocalSnapshot)? = null,
    private val sync: SyncEngine? = null,
) : AssetSource(key, scope) {
    /** The synced copy this timeline is drawn from right now; null while it comes from the server's buckets. */
    @Volatile private var db: SyncDb? = null

    override val fromLocalCopy: Boolean get() = db != null

    override val followsSync: Boolean get() = db != null || (sync?.dbFor(query) != null)

    override fun pullRefresh() {
        val engine = sync
        if (engine == null || db == null) {
            refresh(silent = true)
            return
        }
        refreshing = true
        scope.launch {
            try {
                engine.syncNow()
            } finally {
                refresh(silent = true)
            }
        }
    }

    private suspend fun fetchBuckets(d: SyncDb?): List<com.vknxworks.immichctrl.data.api.TimeBucketDto> =
        if (d != null) withContext(Dispatchers.IO) { d.buckets(query) } else client.timeBuckets(query)

    private suspend fun fetchBucket(d: SyncDb?, bucket: String): List<Asset> =
        if (d != null) withContext(Dispatchers.IO) { d.bucket(query, bucket) } else gate.withPermit { client.timeBucket(query, bucket) }

    private var snapshot = LocalSnapshot()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val gate = Semaphore(3)
    private var listJob: Job? = null

    init {
        refresh()
    }

    override fun refresh(silent: Boolean) {
        listJob?.cancel()
        if (!silent || state !is SourceState.Ready) state = SourceState.Loading
        refreshing = silent
        listJob = scope.launch {
            try {
                var d = sync?.dbFor(query)
                val fresh = try {
                    fetchBuckets(d)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // The copy couldn't be read (account switch, reset): ask the server.
                    if (d == null) throw e
                    d = null
                    client.timeBuckets(query)
                }
                val switched = (db != null) != (d != null)
                db = d
                snapshot = local?.invoke() ?: LocalSnapshot()
                val old = buckets.associateBy { it.key }
                val remoteMonths = HashSet<YearMonth>()
                val next = fresh.filter { it.count > 0 }.map { dto ->
                    val prev = old[dto.timeBucket]
                    val month = monthOf(dto.timeBucket)
                    month?.let { remoteMonths += it }
                    val localCount = month?.let { snapshot.localOnly[it]?.size } ?: 0
                    Bucket(dto.timeBucket, monthLabel(dto.timeBucket), dto.count + localCount, if (silent && !switched) prev?.assets else null)
                }.toMutableList()
                // Months with photos only on this phone.
                snapshot.localOnly.filterKeys { it !in remoteMonths }.forEach { (month, items) ->
                    val key = "local-$month"
                    next += Bucket(key, monthLabel("$month-01"), items.size, items, remote = false)
                }
                if (snapshot.localOnly.isNotEmpty()) {
                    val desc = query.order != "asc"
                    next.sortWith(compareBy<Bucket> { monthOf(it.key.removePrefix("local-")) ?: YearMonth.of(1970, 1) }.let { if (desc) it.reversed() else it })
                }
                loading.clear()
                failedAt.clear()
                buckets = next
                state = SourceState.Ready
                // Reload what was already on screen so changes show without a jump.
                if (silent && !switched) next.forEachIndexed { i, b -> if (b.assets != null) load(i, force = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!silent || state !is SourceState.Ready) state = SourceState.Failed(e.readable())
            } finally {
                refreshing = false
            }
        }
    }

    override fun ensureLoaded(index: Int) {
        val b = buckets.getOrNull(index) ?: return
        if (b.assets != null) return
        val failed = failedAt[b.key]
        if (failed != null && System.currentTimeMillis() - failed < 5_000) return
        load(index, force = false)
    }

    private fun load(index: Int, force: Boolean) {
        val b = buckets.getOrNull(index) ?: return
        if (!loading.add(b.key)) return
        if (!force && b.assets != null) {
            loading.remove(b.key)
            return
        }
        if (!b.remote) {
            loading.remove(b.key)
            return
        }
        scope.launch {
            try {
                val assets = merge(b.key, fetchBucket(db, b.key))
                buckets = buckets.map { if (it.key == b.key) it.copy(count = assets.size, assets = assets) else it }
                failedAt.remove(b.key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failedAt[b.key] = System.currentTimeMillis()
            } finally {
                loading.remove(b.key)
            }
        }
    }

    /** Marks server photos that are also on this phone, and mixes in this month's phone-only photos. */
    private fun merge(bucketKey: String, remote: List<Asset>): List<Asset> {
        val snap = snapshot
        if (snap.onDevice.isEmpty() && snap.localOnly.isEmpty()) return remote
        val marked = if (snap.onDevice.isEmpty()) remote else remote.map { a ->
            snap.onDevice[a.id]?.let { a.copy(onDevice = true, localId = it) } ?: a
        }
        val extra = monthOf(bucketKey)?.let { snap.localOnly[it] }.orEmpty()
        if (extra.isEmpty()) return marked
        val desc = query.order != "asc"
        return (marked + extra).sortedWith(compareBy<Asset> { it.takenAtMs }.let { if (desc) it.reversed() else it })
    }

    companion object {
        private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

        fun monthOf(timeBucket: String): YearMonth? = runCatching { YearMonth.parse(timeBucket.take(7)) }.getOrNull()
        private val thisYearFmt = DateTimeFormatter.ofPattern("MMMM")

        fun monthLabel(timeBucket: String): String {
            val date = runCatching { LocalDate.parse(timeBucket.take(10)) }.getOrNull() ?: return timeBucket
            return if (date.year == LocalDate.now().year) date.format(thisYearFmt) else date.format(monthFmt)
        }
    }
}

/** A plain list of assets fetched in one go (folder contents, memories, a person search...). */
class ListSource(
    key: String,
    scope: CoroutineScope,
    private val fetch: suspend () -> List<Asset>,
) : AssetSource(key, scope) {
    private var job: Job? = null

    init {
        refresh()
    }

    override fun refresh(silent: Boolean) {
        job?.cancel()
        if (!silent || state !is SourceState.Ready) state = SourceState.Loading
        refreshing = silent
        job = scope.launch {
            try {
                val list = fetch()
                buckets = listOf(Bucket("all", null, list.size, list))
                state = SourceState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!silent || state !is SourceState.Ready) state = SourceState.Failed(e.readable())
            } finally {
                refreshing = false
            }
        }
    }
}

/** Search results, fetched a page at a time as the grid scrolls. */
class PagedSource(
    key: String,
    scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> Pair<List<Asset>, Boolean>,
) : AssetSource(key, scope) {
    private var page = 1
    private var hasMore = true
    private var job: Job? = null
    var loadingMore by mutableStateOf(false)
        private set

    override val canLoadMore: Boolean get() = hasMore && state is SourceState.Ready

    init {
        refresh()
    }

    override fun refresh(silent: Boolean) {
        job?.cancel()
        page = 1
        hasMore = true
        if (!silent || state !is SourceState.Ready) state = SourceState.Loading
        refreshing = silent
        job = scope.launch {
            try {
                val (items, more) = fetch(1)
                hasMore = more
                buckets = listOf(Bucket("all", null, items.size, items))
                state = SourceState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!silent || state !is SourceState.Ready) state = SourceState.Failed(e.readable())
            } finally {
                refreshing = false
            }
        }
    }

    override fun loadMore() {
        if (!hasMore || loadingMore || state !is SourceState.Ready) return
        loadingMore = true
        job = scope.launch {
            try {
                val (items, more) = fetch(page + 1)
                page++
                hasMore = more && items.isNotEmpty()
                val current = buckets.firstOrNull()?.assets.orEmpty()
                val seen = current.mapTo(HashSet()) { it.id }
                val merged = current + items.filterNot { it.id in seen }
                buckets = listOf(Bucket("all", null, merged.size, merged))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                hasMore = false
            } finally {
                loadingMore = false
            }
        }
    }
}

/**
 * Sources currently open, by key, so the full-screen viewer can page through the grid it was opened
 * from, and so a change made anywhere (favourite, archive, delete) shows everywhere.
 */
object Sources {
    private val map = ConcurrentHashMap<String, AssetSource>()

    fun register(source: AssetSource) {
        map[source.key] = source
    }

    /** Only removes [source] itself, not a newer one registered under the same key. */
    fun unregister(source: AssetSource) {
        map.remove(source.key, source)
    }

    operator fun get(key: String): AssetSource? = map[key]

    fun clear() = map.clear()

    fun updateEverywhere(ids: Set<String>, change: (Asset) -> Asset) = map.values.forEach { it.updateIds(ids, change) }

    fun removeEverywhere(ids: Set<String>) = map.values.forEach { it.removeIds(ids) }

    /**
     * Asks for a sync of the phone's timeline copy; true when one is kept (its timelines then redraw
     * by themselves once the sync has stored the change).
     */
    @Volatile var syncHook: (() -> Boolean)? = null

    /** After something moved between views (archived, restored, added to an album), refetch quietly. */
    fun refreshAll(except: String? = null) {
        val synced = syncHook?.invoke() == true
        map.values.forEach { if (it.key != except && !(synced && it.fromLocalCopy)) it.refresh(silent = true) }
    }

    /** The synced copy changed (or a sync finished): redraw the timelines drawn from it, straight from the phone. */
    fun reloadAll() = map.values.forEach { if (it.followsSync) it.refresh(silent = true) }
}
