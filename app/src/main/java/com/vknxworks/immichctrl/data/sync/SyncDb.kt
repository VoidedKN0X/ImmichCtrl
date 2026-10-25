package com.vknxworks.immichctrl.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.TimeBucketDto
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.parseDurationMs
import com.vknxworks.immichctrl.data.api.parseInstantMs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

/**
 * The account's timeline as Immich's sync stream describes it: assets (own, partners' and those in
 * shared albums), what's in which album, stacks and partners, plus the few EXIF fields the grid
 * shows. Only what the timeline needs is kept, never the photos themselves, and never anything from
 * the locked folder. One file per account, deleted on sign-out or "Reset sync".
 */
class SyncDb private constructor(
    context: Context,
    name: String,
    val accountKey: String,
    val userId: String,
) : SQLiteOpenHelper(context, name, null, VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    /** A full sync has finished at least once, so the timeline can be shown from here. */
    @Volatile var complete: Boolean = false
        private set

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE assets (id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, file_name TEXT, thumbhash TEXT, " +
                "taken_ms INTEGER NOT NULL, local_ms INTEGER NOT NULL, local_day TEXT NOT NULL, month TEXT NOT NULL, " +
                "is_image INTEGER NOT NULL, duration_ms INTEGER, favorite INTEGER NOT NULL, visibility TEXT NOT NULL, " +
                "live_id TEXT, stack_id TEXT, width INTEGER, height INTEGER, deleted INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX assets_owner ON assets (owner_id, visibility, month)")
        db.execSQL("CREATE INDEX assets_stack ON assets (stack_id)")
        db.execSQL(
            "CREATE TABLE exif (asset_id TEXT PRIMARY KEY, city TEXT, country TEXT, file_size INTEGER, " +
                "exif_w INTEGER, exif_h INTEGER, orientation TEXT)",
        )
        db.execSQL("CREATE TABLE stacks (id TEXT PRIMARY KEY, primary_id TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE partners (shared_by TEXT NOT NULL, shared_with TEXT NOT NULL, in_timeline INTEGER NOT NULL, " +
                "PRIMARY KEY (shared_by, shared_with))",
        )
        db.execSQL("CREATE TABLE album_assets (album_id TEXT NOT NULL, asset_id TEXT NOT NULL, PRIMARY KEY (album_id, asset_id))")
        db.execSQL("CREATE INDEX album_assets_asset ON album_assets (asset_id)")
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // It's only a copy of the server: start over (the next sync asks the server to start over too).
        listOf("assets", "exif", "stacks", "partners", "album_assets", "meta").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    // ---------------------------------------------------------------------------------------------
    // Meta
    // ---------------------------------------------------------------------------------------------

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun setMeta(key: String, value: String?) {
        val db = writableDatabase
        if (value == null) db.delete("meta", "k = ?", arrayOf(key))
        else db.insertWithOnConflict("meta", null, ContentValues().apply { put("k", key); put("v", value) }, SQLiteDatabase.CONFLICT_REPLACE)
        if (key == COMPLETE) complete = value != null
    }

    /** Empties everything (keeping which login it belongs to), so the next sync starts from scratch. */
    @Synchronized
    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("assets", "exif", "stacks", "partners", "album_assets").forEach { db.delete(it, null, null) }
            db.delete("meta", "k != ?", arrayOf(AUTH))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        complete = false
    }

    /** After a full sync: drops copies of other people's photos this account can't see any more. */
    @Synchronized
    fun prune() {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            db.execSQL(
                "DELETE FROM assets WHERE owner_id != ? AND owner_id NOT IN (SELECT shared_by FROM partners WHERE shared_with = ?) " +
                    "AND id NOT IN (SELECT asset_id FROM album_assets)",
                arrayOf(userId, userId),
            )
            db.execSQL("DELETE FROM exif WHERE asset_id NOT IN (SELECT id FROM assets)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun itemCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM assets WHERE deleted = 0", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    // ---------------------------------------------------------------------------------------------
    // Writing what the stream sends, in batches (one transaction each)
    // ---------------------------------------------------------------------------------------------

    /**
     * Applies a batch of stream items in one transaction, without suspending in between (SQLite
     * transactions belong to one thread). True when something the timeline shows changed.
     */
    @Synchronized
    fun applyBatch(items: List<Pair<String, JsonObject>>): Boolean {
        if (items.isEmpty()) return false
        val db = writableDatabase
        val w = Writer(db)
        db.beginTransactionNonExclusive()
        try {
            var changed = false
            for ((type, data) in items) if (w.apply(type, data)) changed = true
            db.setTransactionSuccessful()
            return changed
        } finally {
            db.endTransaction()
            w.close()
        }
    }

    private inner class Writer(private val db: SQLiteDatabase) {
        private var upsertAsset: SQLiteStatement? = null
        private var upsertExif: SQLiteStatement? = null

        fun close() {
            upsertAsset?.close()
            upsertExif?.close()
        }

        /** Applies one stream item; true when it changed something the timeline shows. */
        fun apply(type: String, d: JsonObject): Boolean {
            return when (type) {
                "AssetV1", "AssetV2", "PartnerAssetV1", "PartnerAssetV2", "PartnerAssetBackfillV1", "PartnerAssetBackfillV2",
                "AlbumAssetCreateV1", "AlbumAssetCreateV2", "AlbumAssetUpdateV1", "AlbumAssetUpdateV2",
                "AlbumAssetBackfillV1", "AlbumAssetBackfillV2" -> asset(d)
                "AssetDeleteV1", "PartnerAssetDeleteV1" -> d.str("assetId")?.let { deleteAsset(it); true } ?: false
                "AssetExifV1", "PartnerAssetExifV1", "PartnerAssetExifBackfillV1",
                "AlbumAssetExifCreateV1", "AlbumAssetExifUpdateV1", "AlbumAssetExifBackfillV1" -> exif(d)
                "StackV1", "PartnerStackV1", "PartnerStackBackfillV1" -> {
                    val id = d.str("id") ?: return false
                    val primary = d.str("primaryAssetId") ?: return false
                    db.insertWithOnConflict("stacks", null, ContentValues().apply { put("id", id); put("primary_id", primary) }, SQLiteDatabase.CONFLICT_REPLACE)
                    true
                }
                "StackDeleteV1", "PartnerStackDeleteV1" -> d.str("stackId")?.let { db.delete("stacks", "id = ?", arrayOf(it)); true } ?: false
                "PartnerV1" -> {
                    val by = d.str("sharedById") ?: return false
                    val with = d.str("sharedWithId") ?: return false
                    db.insertWithOnConflict(
                        "partners", null,
                        ContentValues().apply {
                            put("shared_by", by)
                            put("shared_with", with)
                            put("in_timeline", if (d.bool("inTimeline") == true) 1 else 0)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                    true
                }
                "PartnerDeleteV1" -> {
                    val by = d.str("sharedById") ?: return false
                    val with = d.str("sharedWithId") ?: return false
                    db.delete("partners", "shared_by = ? AND shared_with = ?", arrayOf(by, with))
                    // Their photos stop coming; drop the ones that aren't in an album shared with us.
                    if (with == userId && by != userId) {
                        db.execSQL(
                            "DELETE FROM assets WHERE owner_id = ? AND id NOT IN (SELECT asset_id FROM album_assets)",
                            arrayOf(by),
                        )
                    }
                    true
                }
                "AlbumToAssetV1", "AlbumToAssetBackfillV1" -> {
                    val album = d.str("albumId") ?: return false
                    val asset = d.str("assetId") ?: return false
                    db.insertWithOnConflict("album_assets", null, ContentValues().apply { put("album_id", album); put("asset_id", asset) }, SQLiteDatabase.CONFLICT_IGNORE)
                    true
                }
                "AlbumToAssetDeleteV1" -> {
                    val album = d.str("albumId") ?: return false
                    val asset = d.str("assetId") ?: return false
                    db.delete("album_assets", "album_id = ? AND asset_id = ?", arrayOf(album, asset))
                    true
                }
                "AlbumUserDeleteV1" -> {
                    // Removed from someone's shared album: its contents go with it.
                    if (d.str("userId") != userId) return false
                    val album = d.str("albumId") ?: return false
                    db.delete("album_assets", "album_id = ?", arrayOf(album))
                    true
                }
                "AlbumDeleteV1" -> d.str("albumId")?.let { db.delete("album_assets", "album_id = ?", arrayOf(it)); true } ?: false
                else -> false // albums themselves, album users and the like: only acknowledged
            }
        }

        private fun deleteAsset(id: String) {
            db.delete("assets", "id = ?", arrayOf(id))
            db.delete("exif", "asset_id = ?", arrayOf(id))
        }

        private fun asset(d: JsonObject): Boolean {
            val id = d.str("id") ?: return false
            val visibility = d.str("visibility") ?: "timeline"
            // The locked folder needs its PIN on the server; nothing of it is kept on the phone.
            if (visibility == "locked") {
                deleteAsset(id)
                return true
            }
            val owner = d.str("ownerId") ?: return false
            val fileCreated = d.str("fileCreatedAt") ?: d.str("createdAt")
            val takenMs = parseInstantMs(fileCreated) ?: 0L
            // localDateTime is the wall-clock time it was taken, written as if it were UTC.
            val localIso = d.str("localDateTime") ?: fileCreated
            val localMs = parseInstantMs(localIso) ?: takenMs
            val localDay = localIso?.takeIf { it.length >= 10 }?.take(10) ?: "1970-01-01"
            val st = upsertAsset ?: db.compileStatement(
                "INSERT OR REPLACE INTO assets (id, owner_id, file_name, thumbhash, taken_ms, local_ms, local_day, month, " +
                    "is_image, duration_ms, favorite, visibility, live_id, stack_id, width, height, deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).also { upsertAsset = it }
            st.clearBindings()
            st.bindString(1, id)
            st.bindString(2, owner)
            st.bindOpt(3, d.str("originalFileName"))
            st.bindOpt(4, d.str("thumbhash"))
            st.bindLong(5, takenMs)
            st.bindLong(6, localMs)
            st.bindString(7, localDay)
            st.bindString(8, localDay.take(7) + "-01")
            st.bindLong(9, if (d.str("type") == "IMAGE") 1 else 0)
            st.bindOpt(10, parseDurationMs(d["duration"]))
            st.bindLong(11, if (d.bool("isFavorite") == true) 1 else 0)
            st.bindString(12, visibility)
            st.bindOpt(13, d.str("livePhotoVideoId"))
            st.bindOpt(14, d.str("stackId"))
            st.bindOpt(15, d.int("width")?.toLong())
            st.bindOpt(16, d.int("height")?.toLong())
            st.bindLong(17, if (d.str("deletedAt") != null) 1 else 0)
            st.executeInsert()
            return true
        }

        private fun exif(d: JsonObject): Boolean {
            val id = d.str("assetId") ?: return false
            val st = upsertExif ?: db.compileStatement(
                "INSERT OR REPLACE INTO exif (asset_id, city, country, file_size, exif_w, exif_h, orientation) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).also { upsertExif = it }
            st.clearBindings()
            st.bindString(1, id)
            st.bindOpt(2, d.str("city"))
            st.bindOpt(3, d.str("country"))
            st.bindOpt(4, d.long("fileSizeInByte"))
            st.bindOpt(5, d.int("exifImageWidth")?.toLong())
            st.bindOpt(6, d.int("exifImageHeight")?.toLong())
            st.bindOpt(7, d.str("orientation"))
            st.executeInsert()
            return true
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reading the timeline: the same months and order the server's timeline buckets have
    // ---------------------------------------------------------------------------------------------

    private fun where(q: TimelineQuery): Pair<String, Array<String>> {
        val args = ArrayList<String>()
        val sb = StringBuilder("a.deleted = 0")
        if (q.albumId != null) {
            sb.append(" AND a.visibility IN ('timeline', 'archive') AND a.id IN (SELECT asset_id FROM album_assets WHERE album_id = ?)")
            args += q.albumId
        } else {
            sb.append(" AND a.visibility = ?")
            args += q.visibility ?: "timeline"
            if (q.withPartners == true) {
                sb.append(" AND (a.owner_id = ? OR a.owner_id IN (SELECT shared_by FROM partners WHERE shared_with = ? AND in_timeline = 1))")
                args += userId
                args += userId
            } else {
                sb.append(" AND a.owner_id = ?")
                args += userId
            }
            if (q.isFavorite == true) sb.append(" AND a.favorite = 1")
        }
        // A stack shows as its cover photo only.
        if (q.withStacked == true) sb.append(" AND NOT EXISTS (SELECT 1 FROM stacks s WHERE s.id = a.stack_id AND s.primary_id != a.id)")
        return sb.toString() to args.toTypedArray()
    }

    private fun dir(q: TimelineQuery) = if (q.order == "asc") "ASC" else "DESC"

    /** Months with their photo counts, like GET /timeline/buckets. */
    fun buckets(q: TimelineQuery): List<TimeBucketDto> {
        val (w, args) = where(q)
        val out = ArrayList<TimeBucketDto>()
        readableDatabase.rawQuery("SELECT a.month, COUNT(*) FROM assets a WHERE $w GROUP BY a.month ORDER BY a.month ${dir(q)}", args).use { c ->
            while (c.moveToNext()) out += TimeBucketDto(c.getString(0), c.getInt(1))
        }
        return out
    }

    /** One month's photos, like GET /timeline/bucket. */
    fun bucket(q: TimelineQuery, month: String): List<Asset> {
        val (w, args) = where(q)
        val stackCount = if (q.withStacked == true) {
            "CASE WHEN a.stack_id IS NULL THEN 0 ELSE (SELECT COUNT(*) FROM assets b WHERE b.stack_id = a.stack_id AND b.deleted = 0 AND b.visibility = 'timeline') END"
        } else {
            "0"
        }
        val d = dir(q)
        val sql = "SELECT a.id, a.is_image, a.width, a.height, e.exif_w, e.exif_h, e.orientation, a.favorite, a.visibility, " +
            "a.duration_ms, a.thumbhash, a.taken_ms, a.local_ms, a.live_id, a.owner_id, e.city, e.country, a.file_name, e.file_size, $stackCount " +
            "FROM assets a LEFT JOIN exif e ON e.asset_id = a.id WHERE $w AND a.month = ? " +
            "ORDER BY a.local_day $d, a.taken_ms $d, a.file_name $d"
        val out = ArrayList<Asset>()
        readableDatabase.rawQuery(sql, args + month).use { c ->
            while (c.moveToNext()) {
                val owner = c.getString(14)
                val taken = c.getLong(11)
                out += Asset(
                    id = c.getString(0),
                    isImage = c.getInt(1) == 1,
                    ratio = ratio(c.intOrNull(2), c.intOrNull(3), c.intOrNull(4), c.intOrNull(5), c.getString(6)),
                    // Like the server: a partner's favourite isn't yours.
                    isFavorite = c.getInt(7) == 1 && owner == userId,
                    isTrashed = false,
                    visibility = c.getString(8),
                    durationMs = c.longOrNull(9),
                    thumbhash = c.getString(10),
                    takenAtMs = taken,
                    localOffsetHours = (c.getLong(12) - taken) / 3_600_000.0,
                    livePhotoVideoId = c.getString(13),
                    stackCount = c.getInt(19),
                    ownerId = owner,
                    city = c.getString(15),
                    country = c.getString(16),
                    fileName = c.getString(17),
                    fileSize = c.longOrNull(18),
                )
            }
        }
        return out
    }

    companion object {
        private const val VERSION = 1
        const val COMPLETE = "complete"
        const val EPOCH = "epoch"
        const val AUTH = "auth"
        const val SYNCED_AT = "synced_at"
        /** The server has been told to forget what it sent this login before the first stream. */
        const val STARTED = "started"
        /** "2" for Immich 3.x's V2 stream types, "1" for 2.x. */
        const val PROTO = "proto"

        private fun fileName(accountKey: String) = "sync-" + sha(accountKey).take(20) + ".db"

        fun sha(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /**
         * Opens the account's copy. A different login (new session on the server, so the server has no
         * record of what this phone has) empties it first.
         */
        fun open(context: Context, accountKey: String, userId: String, secret: String): SyncDb {
            val db = SyncDb(context.applicationContext, fileName(accountKey), accountKey, userId)
            val auth = sha("immichctrl-sync|$secret").take(24)
            if (db.meta(AUTH) != auth) {
                db.clearAll()
                db.setMeta(AUTH, auth)
            }
            db.complete = db.meta(COMPLETE) != null
            return db
        }

        fun delete(context: Context, accountKey: String) {
            SQLiteDatabase.deleteDatabase(context.applicationContext.getDatabasePath(fileName(accountKey)))
        }

        /** Timelines that can be drawn from the local copy; everything else asks the server. */
        fun supports(q: TimelineQuery, proto: Int): Boolean {
            if (q.personId != null || q.tagId != null || q.userId != null || q.isTrashed == true || q.orderBy != null) return false
            // Album contents only from Immich 3's stream, which covers albums you own as well as shared ones.
            if (q.albumId != null) return proto >= 2 && q.visibility == null && q.isFavorite == null && q.withPartners != true
            return when (q.visibility) {
                "timeline" -> q.isFavorite == null || (q.isFavorite == true && q.withPartners != true)
                "archive" -> q.isFavorite == null && q.withPartners != true
                else -> false
            }
        }

        /** width / height, from the asset or else its EXIF (turned for rotated photos); 1 when unknown. */
        fun ratio(w: Int?, h: Int?, ew: Int?, eh: Int?, orientation: String?): Float {
            if (w != null && h != null && w > 0 && h > 0) return w.toFloat() / h
            if (ew != null && eh != null && ew > 0 && eh > 0) {
                val turned = orientation in setOf("5", "6", "7", "8")
                return if (turned) eh.toFloat() / ew else ew.toFloat() / eh
            }
            return 1f
        }
    }
}

internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull

private fun SQLiteStatement.bindOpt(i: Int, v: String?) = if (v == null) bindNull(i) else bindString(i, v)
private fun SQLiteStatement.bindOpt(i: Int, v: Long?) = if (v == null) bindNull(i) else bindLong(i, v)
private fun android.database.Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
private fun android.database.Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)
