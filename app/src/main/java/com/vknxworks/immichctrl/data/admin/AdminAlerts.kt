package com.vknxworks.immichctrl.data.admin

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.immichctrl.MainActivity
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.queues
import com.vknxworks.immichctrl.data.api.serverStorage
import com.vknxworks.immichctrl.data.api.serverVersion
import com.vknxworks.immichctrl.data.api.versionCheck
import com.vknxworks.immichctrl.data.appDataStore
import com.vknxworks.immichctrl.data.log.AppLog
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import com.vknxworks.immichctrl.Strings

/**
 * For admins: checks the server every hour and notifies about failed background jobs, a disk
 * that's nearly full, and new Immich releases. Never runs for accounts that aren't admins.
 */
class AdminAlertsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.sessions.ready.first { it }
        val s = c.sessions.active.value ?: return Result.success()
        val settings = c.settings.current()
        if (!s.account.isAdmin || !settings.adminAlerts) return Result.success()
        val key = s.account.key
        val prefs = applicationContext.appDataStore
        val stored = prefs.data.first()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)

        // Failed jobs: alert when the number of failures goes up.
        runCatching {
            val failed = c.api.queues().associate { it.name to it.statistics.failed }
            val total = failed.values.sum()
            val lastKey = longPreferencesKey("admin_failed:$key")
            val last = stored[lastKey] ?: 0L
            if (total > last) {
                val which = failed.filterValues { it > 0 }.keys.joinToString(", ")
                notify(nm, 1, Strings.plural(R.plurals.background_jobs_failed, (total - last).toInt(), total - last), Strings.str(R.string.on, s.account.serverName, which))
            }
            prefs.edit { it[lastKey] = total }
        }.onFailure { AppLog.w("Admin", "Couldn't check job queues: ${it.message}") }

        // Disk space: alert once each time usage crosses into a new 5% step above the threshold.
        runCatching {
            val disk = c.api.serverStorage()
            val pct = disk.diskUsagePercentage.toInt()
            val stepKey = longPreferencesKey("admin_disk_step:$key")
            val lastStep = stored[stepKey] ?: 0L
            val step = if (pct >= settings.adminDiskPercent) (pct / 5).toLong() else 0L
            if (step > lastStep) notify(nm, 2, Strings.str(R.string.server_disk_is_full, pct), Strings.str(R.string.left_of_on, disk.diskAvailable, disk.diskSize, s.account.serverName))
            prefs.edit { it[stepKey] = step }
        }

        // New Immich release.
        runCatching {
            val latest = c.api.versionCheck().releaseVersion?.removePrefix("v") ?: return@runCatching
            val current = c.api.serverVersion().toString().removePrefix("v")
            val seenKey = stringPreferencesKey("admin_release_seen:$key")
            if (newer(latest, current) && stored[seenKey] != latest) {
                notify(nm, 3, Strings.str(R.string.immich_is_available, latest), Strings.str(R.string.runs, s.account.serverName, current))
                prefs.edit { it[seenKey] = latest }
            }
        }
        return Result.success()
    }

    private fun newer(a: String, b: String): Boolean {
        val pa = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun notify(nm: NotificationManager, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "admin"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            5000 + id,
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                // The server's name and details stay off the lock screen.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(applicationContext, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(Strings.str(R.string.immich_server_alert))
                        .build(),
                )
                .build(),
        )
        AppLog.i("Admin", title)
    }

    companion object {
        const val CHANNEL = "admin"
        private const val WORK = "admin-alerts"

        /** Schedules (or cancels) the hourly check for the active account. */
        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<AdminAlertsWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
