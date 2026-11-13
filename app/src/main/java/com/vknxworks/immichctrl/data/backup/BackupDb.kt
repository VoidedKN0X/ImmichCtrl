package com.vknxworks.immichctrl.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * What has been backed up, per account, and a cache of file checksums so unchanged files aren't
 * read again. A file counts as backed up while its size and modified time match what was uploaded.
 */
class BackupDb(context: Context) : SQLiteOpenHelper(context, "backup.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE uploaded (account TEXT NOT NULL, local_id INTEGER NOT NULL, size INTEGER NOT NULL, " +
                "modified INTEGER NOT NULL, remote_id TEXT, at INTEGER NOT NULL, PRIMARY KEY (account, local_id))",
        )
        db.execSQL("CREATE TABLE hashes (local_id INTEGER PRIMARY KEY, size INTEGER NOT NULL, modified INTEGER NOT NULL, sha1 TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** local id -> (size, modified) of what was uploaded for [account]. */
    @Synchronized
    fun uploaded(account: String): Map<Long, Pair<Long, Long>> {
        val out = HashMap<Long, Pair<Long, Long>>()
        readableDatabase.rawQuery("SELECT local_id, size, modified FROM uploaded WHERE account = ?", arrayOf(account)).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1) to c.getLong(2)
        }
        return out
    }

    /** local id -> server asset id, for what was backed up from this phone to [account]. */
    @Synchronized
    fun remoteIds(account: String): Map<Long, String> {
        val out = HashMap<Long, String>()
        readableDatabase.rawQuery("SELECT local_id, remote_id FROM uploaded WHERE account = ? AND remote_id IS NOT NULL", arrayOf(account)).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getString(1)
        }
        return out
    }

    @Synchronized
    fun forget(account: String, localIds: Collection<Long>) {
        val db = writableDatabase
        localIds.forEach { db.delete("uploaded", "account = ? AND local_id = ?", arrayOf(account, it.toString())) }
    }

    fun isDone(uploaded: Map<Long, Pair<Long, Long>>, item: LocalItem): Boolean =
        uploaded[item.id]?.let { it.first == item.size && it.second == item.modifiedMs } == true

    @Synchronized
    fun markUploaded(account: String, item: LocalItem, remoteId: String?) {
        writableDatabase.insertWithOnConflict(
            "uploaded", null,
            ContentValues().apply {
                put("account", account)
                put("local_id", item.id)
                put("size", item.size)
                put("modified", item.modifiedMs)
                put("remote_id", remoteId)
                put("at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun cachedHash(item: LocalItem): String? =
        readableDatabase.rawQuery(
            "SELECT sha1 FROM hashes WHERE local_id = ? AND size = ? AND modified = ?",
            arrayOf(item.id.toString(), item.size.toString(), item.modifiedMs.toString()),
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    @Synchronized
    fun saveHash(item: LocalItem, sha1: String) {
        writableDatabase.insertWithOnConflict(
            "hashes", null,
            ContentValues().apply {
                put("local_id", item.id)
                put("size", item.size)
                put("modified", item.modifiedMs)
                put("sha1", sha1)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** Forget what was backed up for [account] so everything is checked again. */
    @Synchronized
    fun reset(account: String) {
        writableDatabase.delete("uploaded", "account = ?", arrayOf(account))
    }
}
