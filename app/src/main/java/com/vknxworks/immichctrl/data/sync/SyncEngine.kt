package com.vknxworks.immichctrl.data.sync

import android.content.Context
import com.vknxworks.immichctrl.data.api.ApiException
import com.vknxworks.immichctrl.data.api.ApiJson
import com.vknxworks.immichctrl.data.api.AuthType
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.obj
import com.vknxworks.immichctrl.data.api.serverVersion
import com.vknxworks.immichctrl.data.auth.ActiveSession
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps a copy of the signed-in account's timeline on the phone with Immich's sync stream
 * (POST /sync/stream, then POST /sync/ack for what was stored). After the first full sync only
 * changes come down, so the timeline opens from the phone and stays right without reloading every
 * month. Servers without the sync stream (or API-key sign-ins, which the server doesn't allow it for)
 * keep using the timeline buckets.
 */
class SyncEngine(private val context: Context, private val scope: CoroutineScope) {

    sealed interface Status {
        /** No account, or not checked yet. */
        data object Off : Status
        /** The server or sign-in can't use the sync stream; timelines load month by month instead. */
        data class Unsupported(val reason: Reason) : Status
        data class Syncing(val first: Boolean, val received: Long) : Status
        data class Ready(val items: Long, val syncedAt: Long) : Status
        data class Failed(val message: String, val items: Long) : Status
    }

    enum class Reason { API_KEY, OLD_SERVER, REFUSED }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Called (on the main thread) after the local copy changed, to redraw open timelines. */
    var onChanged: (() -> Unit)? = null

    @Volatile private var session: ActiveSession? = null
    @Volatile private var db: SyncDb? = null
    @Volatile private var proto: Int = 0
    private var startJob: Job? = null
    @Volatile private var job: Job? = null
    @Volatile private var again = false
    /** Opening or closing an account's copy, by account; deleting its file waits for these. */
    private val busy = HashMap<String, MutableList<Job>>()

    private fun track(accountKey: String, j: Job) {
        busy.getOrPut(accountKey) { mutableListOf() }.apply {
            removeAll { it.isCompleted }
            add(j)
        }
    }

    /** Status from a sync of [d], unless that copy has been replaced or closed in the meantime. */
    private fun post(d: SyncDb, st: Status) {
        if (db === d) _status.value = st
    }

    /** The local copy to draw [q] from, or null to ask the server (not synced yet, not supported, other filters). */
    fun dbFor(q: TimelineQuery): SyncDb? {
        val d = db ?: return null
        if (!d.complete || !SyncDb.supports(q, proto)) return null
        return d
    }

    /** Whether timelines are currently drawn from the local copy (so a refresh means "sync"). */
    val active: Boolean get() = db?.complete == true

    /** The account changed (or signed out / locked). */
    @Synchronized
    fun setSession(s: ActiveSession?) {
        if (s?.account?.key == session?.account?.key && s?.client === session?.client) return
        startJob?.cancel()
        job?.cancel()
        job = null
        val old = db
        db = null
        if (old != null) {
            // Timelines drawn from it go back to the server until the new copy is ready.
            notifyChanged()
            track(old.accountKey, scope.launch(Dispatchers.IO) { runCatching { old.close() } })
        }
        session = s
        proto = 0
        _status.value = Status.Off
        if (s == null) return
        startJob = scope.launch {
            try {
                start(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A copy that can't be opened or read (storage full, damaged file): timelines load from the server.
                AppLog.w("Sync", "Couldn't open the timeline copy: ${e.message ?: e::class.simpleName}")
            }
        }.also { track(s.account.key, it) }
    }

    private suspend fun start(s: ActiveSession) {
        val client = s.client
        if (client.authType == AuthType.API_KEY) {
            _status.value = Status.Unsupported(Reason.API_KEY)
            return
        }
        val major = runCatching { client.serverVersion().major }.getOrNull()
            ?: (s.account.serverVersion?.removePrefix("v")?.substringBefore('.')?.toIntOrNull())
            ?: client.serverMajor
        if (major < 2) {
            _status.value = Status.Unsupported(Reason.OLD_SERVER)
            return
        }
        // Not cancellable, and handed over (or closed again) inside the same block: a copy that was opened is
        // never left open, even when the account changes while it opens.
        val ready: Status.Ready? = withContext(Dispatchers.IO + NonCancellable) {
            val d = runCatching { SyncDb.open(context, s.account.key, s.account.userId, client.secret) }.getOrNull()
                ?: return@withContext null
            val r = runCatching {
                if (d.complete) Status.Ready(d.itemCount(), d.meta(SyncDb.SYNCED_AT)?.toLongOrNull() ?: 0L) else null
            }
            val current = r.isSuccess && synchronized(this@SyncEngine) {
                if (session === s) {
                    proto = if (major >= 3) 2 else 1
                    db = d
                    r.getOrNull()?.let { _status.value = it }
                    true
                } else {
                    false
                }
            }
            if (!current) {
                runCatching { d.close() }
                null
            } else {
                r.getOrNull()
            }
        }
        if (db == null || session !== s) return
        if (ready != null) notifyChanged()
        requestSync()
    }

    /** Starts a sync, or runs one more once the current one ends. */
    @Synchronized
    fun requestSync() {
        if (db == null) return
        if (job?.isActive == true) {
            again = true
            return
        }
        job = scope.launch {
            do {
                again = false
                runOnce()
            } while (again && db != null)
        }
    }

    /** Syncs and waits for it to finish (pull to refresh). */
    suspend fun syncNow() {
        if (db == null) return
        requestSync()
        job?.join()
    }

    /** "Reset sync": forget the local copy and what the server thinks this phone has, then start over. */
    @Synchronized
    fun reset() {
        val d = db ?: return
        val running = job
        job = scope.launch {
            // The sync that was running stops (and stops writing) before the copy is emptied.
            running?.cancelAndJoin()
            try {
                withContext(Dispatchers.IO) {
                    d.clearAll()
                    d.setMeta(SyncDb.STARTED, null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Closed meanwhile (account switch, sign-out): nothing left to reset.
                return@launch
            }
            AppLog.i("Sync", "Sync reset; downloading the timeline again")
            notifyChanged()
            do {
                again = false
                runOnce()
            } while (again && db === d)
        }
    }

    /** Signing out: the account's copy is deleted (once it's closed). */
    fun forget(accountKey: String) {
        val pending = synchronized(this) {
            if (session?.account?.key == accountKey) setSession(null)
            // A copy still being opened or closed for this account finishes first, so it can't outlive the delete.
            busy.remove(accountKey).orEmpty()
        }
        scope.launch(Dispatchers.IO) {
            pending.forEach { runCatching { it.join() } }
            runCatching { SyncDb.delete(context, accountKey) }
        }
    }

    private fun notifyChanged() {
        scope.launch(Dispatchers.Main) { onChanged?.invoke() }
    }

    private fun requestTypes(p: Int): List<String> = if (p >= 2) {
        listOf(
            "AssetsV2", "AssetExifsV1", "StacksV1", "PartnersV1", "PartnerAssetsV2", "PartnerAssetExifsV1", "PartnerStacksV1",
            "AlbumsV2", "AlbumUsersV1", "AlbumAssetsV2", "AlbumAssetExifsV1", "AlbumToAssetsV1",
        )
    } else {
        listOf(
            "AssetsV1", "AssetExifsV1", "StacksV1", "PartnersV1", "PartnerAssetsV1", "PartnerAssetExifsV1", "PartnerStacksV1",
            "AlbumsV1", "AlbumUsersV1", "AlbumAssetsV1", "AlbumAssetExifsV1", "AlbumToAssetsV1",
        )
    }

    private suspend fun runOnce() {
        val d = db ?: return
        val s = session ?: return
        val p = proto
        val wasComplete = d.complete
        var items = 0L
        try {
            items = withContext(Dispatchers.IO) { d.itemCount() }
            post(d, Status.Syncing(first = !wasComplete, received = 0))
            var reset = false
            withContext(Dispatchers.IO) {
                // A new copy, or the server moved to other stream types: start over on both sides.
                if (d.meta(SyncDb.STARTED) == null || d.meta(SyncDb.PROTO) != p.toString()) {
                    d.clearAll()
                    reset = true
                } else {
                    // The server forgot what it sent this login (restored, or its session was reset).
                    val acks = runCatching { s.client.raw("GET", "/sync/ack") }.getOrNull()
                        ?.let { runCatching { ApiJson.parseToJsonElement(it) as? JsonArray }.getOrNull() }
                    if (acks != null && acks.isEmpty()) {
                        d.clearAll()
                        reset = true
                    }
                }
            }
            if (reset) notifyChanged()
            var result = stream(s.client, d, p, reset)
            if (result.serverReset) {
                // The server asked for a fresh start (after a long time away): it already sent the ack
                // that resets its side, now download everything again.
                withContext(Dispatchers.IO) { d.clearAll() }
                notifyChanged()
                result = stream(s.client, d, p, reset = true)
            }
            val total = withContext(Dispatchers.IO) { d.itemCount() }
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) { d.setMeta(SyncDb.SYNCED_AT, now.toString()) }
            post(d, Status.Ready(total, now))
            if (!wasComplete) AppLog.i("Sync", "Timeline synced: $total items")
            // Always redraw (it's read from the phone): settings such as "show photos from this phone" wait for this too.
            notifyChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.code in setOf(400, 403, 404, 405, 501)) {
                AppLog.w("Sync", "The server refused the sync stream (${e.code}); loading the timeline month by month")
                val refused = synchronized(this) {
                    if (db === d) {
                        db = null
                        _status.value = Status.Unsupported(Reason.REFUSED)
                        true
                    } else {
                        false
                    }
                }
                if (refused) {
                    withContext(Dispatchers.IO) { runCatching { d.close() } }
                    notifyChanged()
                }
            } else {
                AppLog.w("Sync", "Sync failed: ${e.message}")
                post(d, Status.Failed(e.message ?: "", items))
            }
        } catch (e: Throwable) {
            // Stopped on purpose (account switch, sign-out, app locked): the closed stream isn't a failure.
            currentCoroutineContext().ensureActive()
            if (db !== d) return
            AppLog.w("Sync", "Sync failed: ${e.message ?: e::class.simpleName}")
            post(d, Status.Failed(e.message ?: e::class.simpleName.orEmpty(), items))
        }
    }

    private class StreamResult(val serverReset: Boolean)

    /**
     * Reads the newline-delimited JSON one line at a time and stores it in batches: each batch is
     * written in one transaction, then acknowledged, so an interrupted sync carries on from there.
     */
    private suspend fun stream(client: ImmichClient, d: SyncDb, p: Int, reset: Boolean): StreamResult = withContext(Dispatchers.IO) {
        // Starting over: the server forgets what it sent this login. Older servers without the
        // stream's "reset" field still have DELETE /sync/ack, so that's tried first.
        var resetFlag = false
        if (reset) {
            val cleared = runCatching { client.raw("DELETE", "/sync/ack", obj()) }.isSuccess
            resetFlag = !cleared
        }
        val body = obj("types" to requestTypes(p), "reset" to if (resetFlag) true else null)
        val req = client.request(client.url("/sync/stream"))
            .header("Accept", "application/jsonlines+json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        var serverReset = false
        var received = 0L
        val call = client.http.newCall(req)
        val resp = client.execute(call)
        // Reading the stream blocks; when the sync is stopped (account switch, sign-out), cancelling the call
        // ends the read straight away instead of holding on to the connection until it times out.
        val finished = AtomicBoolean(false)
        val closer = launch {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) call.cancel()
            }
        }
        try {
            if (reset) {
                d.setMeta(SyncDb.STARTED, System.currentTimeMillis().toString())
                d.setMeta(SyncDb.PROTO, p.toString())
            }
            val source = resp.body!!.source()
            val batch = ArrayList<Pair<String, JsonObject>>(BATCH)
            // Last ack per type in this batch: acknowledging the latest one covers everything before it.
            val acks = LinkedHashMap<String, String>()
            var complete = false

            val flush: suspend () -> Unit = {
                if (batch.isNotEmpty() || acks.isNotEmpty()) {
                    d.applyBatch(batch)
                    batch.clear()
                    if (acks.isNotEmpty()) {
                        client.raw("POST", "/sync/ack", obj("acks" to acks.values.toList()))
                        acks.clear()
                    }
                    post(d, Status.Syncing(first = !d.complete, received = received))
                }
            }

            while (true) {
                ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val o = runCatching { ApiJson.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                val type = (o["type"] as? JsonPrimitive)?.contentOrNull ?: continue
                val ack = (o["ack"] as? JsonPrimitive)?.contentOrNull
                val data = o["data"] as? JsonObject ?: JsonObject(emptyMap())
                when (type) {
                    "SyncResetV1" -> {
                        flush()
                        // Telling the server we got it resets its side for this login.
                        if (ack != null) client.raw("POST", "/sync/ack", obj("acks" to listOf(ack)))
                        serverReset = true
                        break
                    }
                    "SyncCompleteV1" -> {
                        if (ack != null) acks[ack.substringBefore('|')] = ack
                        complete = true
                    }
                    "SyncAckV1" -> if (ack != null) acks[ack.substringBefore('|')] = ack
                    else -> {
                        batch += type to data
                        received++
                        if (ack != null) acks[ack.substringBefore('|')] = ack
                    }
                }
                if (batch.size >= BATCH) flush()
            }
            flush()
            if (complete) {
                // Copies of other people's photos we can no longer see (left a shared album, partner stopped sharing).
                d.prune()
                d.setMeta(SyncDb.COMPLETE, System.currentTimeMillis().toString())
            }
        } finally {
            finished.set(true)
            closer.cancel()
            resp.close()
        }
        StreamResult(serverReset)
    }

    companion object {
        private const val BATCH = 1000
    }
}
