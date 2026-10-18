package com.vknxworks.immichctrl.data.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vknxworks.immichctrl.data.auth.SessionManager
import com.vknxworks.immichctrl.data.settings.AppSettings
import com.vknxworks.immichctrl.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

data class BackupCounts(val total: Int, val done: Int) {
    val remaining: Int get() = (total - done).coerceAtLeast(0)
}

/** Schedules backups and answers "how much is backed up". */
class BackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val sessions: SessionManager,
    private val db: BackupDb,
    scope: CoroutineScope,
) {
    private val wm get() = WorkManager.getInstance(context)

    /** Running and recent backup work, for the backup screen. */
    val work: Flow<List<WorkInfo>> get() = wm.getWorkInfosByTagFlow(TAG)

    init {
        // Keep the schedule in step with the backup settings.
        scope.launch {
            settings.settings
                .map { Triple(it.backupEnabled, it.backupWifiOnly, it.backupChargingOnly) }
                .distinctUntilChanged()
                .collect { reschedule(settings.current()) }
        }
    }

    private fun constraints(s: AppSettings) = Constraints.Builder()
        .setRequiredNetworkType(if (s.backupWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(s.backupChargingOnly)
        .setRequiresBatteryNotLow(true)
        .build()

    private fun reschedule(s: AppSettings) {
        if (!s.backupEnabled) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(TRIGGER)
            return
        }
        val periodic = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints(s))
            .setInputData(workDataOf(BackupWorker.KEY_MODE to BackupWorker.MODE_AUTO))
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        enqueueTrigger(s, ExistingWorkPolicy.REPLACE)
    }

    /** Runs a backup soon after new photos appear (re-armed after each run). */
    suspend fun scheduleTrigger() {
        val s = settings.current()
        if (s.backupEnabled) enqueueTrigger(s, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueTrigger(s: AppSettings, policy: ExistingWorkPolicy) {
        val base = constraints(s)
        val c = Constraints.Builder()
            .setRequiredNetworkType(base.requiredNetworkType)
            .setRequiresCharging(base.requiresCharging())
            .setRequiresBatteryNotLow(true)
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(20))
            .setTriggerContentMaxDelay(Duration.ofMinutes(2))
            .build()
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(c)
            .setInputData(workDataOf(BackupWorker.KEY_MODE to BackupWorker.MODE_TRIGGER))
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(TRIGGER, policy, req)
    }

    /** "Back up now": runs whatever the settings, as soon as there's a connection. */
    fun backupNow() {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(BackupWorker.KEY_MODE to BackupWorker.MODE_MANUAL, BackupWorker.KEY_ACCOUNT to activeKey()))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, req)
    }

    /** Uploads specific photos from this phone (picked in the app), whatever the backup settings. */
    fun uploadLocal(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        // WorkManager input is capped at 10 KB, so big selections go as several jobs.
        ids.chunked(500).forEach { chunk ->
            val req = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(
                    workDataOf(
                        BackupWorker.KEY_MODE to BackupWorker.MODE_ITEMS,
                        BackupWorker.KEY_IDS to chunk.toLongArray(),
                        BackupWorker.KEY_ACCOUNT to activeKey(),
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()
            wm.enqueue(req)
        }
    }

    /** Files everything backed up from this phone into server albums named like the phone's albums. */
    fun syncAlbumsNow() {
        val req = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(BackupWorker.KEY_MODE to BackupWorker.MODE_SYNC_ALBUMS, BackupWorker.KEY_ACCOUNT to activeKey()))
            .addTag(TAG)
            .build()
        wm.enqueue(req)
    }

    /**
     * The account a job is queued for. Jobs can wait (e.g. for a connection) while the user switches
     * account or signs out; the worker then refuses to send them anywhere else.
     */
    private fun activeKey(): String? = sessions.active.value?.account?.key

    fun cancel() {
        wm.cancelUniqueWork(MANUAL)
        wm.cancelUniqueWork(TRIGGER)
    }

    /**
     * Uploads files another app shared to ImmichCtrl. They're copied into the app first (the share
     * grant doesn't outlast the screen that received it), then uploaded in the background.
     */
    suspend fun uploadShared(uris: List<Uri>, albumId: String? = null): Int = withContext(Dispatchers.IO) {
        val account = activeKey() ?: return@withContext 0
        val dir = File(context.cacheDir, "incoming/${UUID.randomUUID()}").apply { mkdirs() }
        val paths = uris.mapIndexedNotNull { i, uri ->
            runCatching {
                // The name comes from the other app: make it one safe path segment, and unique.
                val name = com.vknxworks.immichctrl.data.share.Exporter.safeFileName(displayName(uri), "shared-${System.currentTimeMillis()}")
                val target = File(File(dir, "$i").apply { mkdirs() }, name)
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
                target.absolutePath
            }.getOrNull()
        }
        if (paths.isEmpty()) return@withContext 0
        // WorkManager input is capped at 10 KB, so many files go as several jobs.
        paths.chunked(40).forEach { chunk ->
            val req = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(
                    workDataOf(
                        BackupWorker.KEY_MODE to BackupWorker.MODE_SHARED,
                        BackupWorker.KEY_FILES to chunk.toTypedArray(),
                        BackupWorker.KEY_ALBUM to albumId,
                        // Confirmed for this account: never uploaded to whichever account is active later.
                        BackupWorker.KEY_ACCOUNT to account,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()
            wm.enqueue(req)
        }
        paths.size
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment

    /** How much of the selected albums is on the server, for the active account. */
    suspend fun counts(): BackupCounts = withContext(Dispatchers.IO) {
        val s = settings.current()
        val account = sessions.active.value?.account?.key ?: return@withContext BackupCounts(0, 0)
        if (!LocalMedia.hasPermission(context)) return@withContext BackupCounts(0, 0)
        val items = LocalMedia.items(context, if (s.backupEverything) null else s.backupAlbums, s.excludedAlbums, s.backupVideos)
        val done = db.uploaded(account)
        BackupCounts(items.size, items.count { db.isDone(done, it) })
    }

    suspend fun resetRecord() = withContext(Dispatchers.IO) {
        sessions.active.value?.account?.key?.let { db.reset(it) }
    }

    companion object {
        const val TAG = "backup"
        private const val PERIODIC = "backup-periodic"
        private const val TRIGGER = "backup-trigger"
        private const val MANUAL = "backup-now"
    }
}
