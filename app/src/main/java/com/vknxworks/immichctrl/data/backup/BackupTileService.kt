package com.vknxworks.immichctrl.data.backup

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vknxworks.immichctrl.MainActivity
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings tile: tap to back up now; shows progress while a backup runs. */
class BackupTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val c = applicationContext.container
        if (c.sessions.active.value == null) {
            // Not signed in yet: open the app instead.
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        // From the lock screen, the phone has to be unlocked first.
        if (isLocked) {
            unlockAndRun { toggleBackup() }
            return
        }
        toggleBackup()
    }

    private fun toggleBackup() {
        val c = applicationContext.container
        val running = qsTile?.state == Tile.STATE_ACTIVE
        if (running) {
            c.backup.cancel()
            showState(false, "Stopped")
            return
        }
        c.appScope.launch(Dispatchers.Main) {
            // The tile doesn't say which account is in use: when backup is set up for a different
            // account than the active one, don't upload this phone's photos to the active one.
            // (Read from storage: right after the process starts, the in-memory settings are still defaults.)
            val s = c.settings.current()
            c.sessions.ready.first { it }
            val active = c.sessions.active.value?.account?.key
            if (active == null || (s.backupEnabled && s.backupAccount != null && s.backupAccount != active)) {
                showState(false, if (active == null) "Not signed in" else "Set up for another account")
                return@launch
            }
            c.backup.backupNow()
            showState(true, "Starting…")
        }
    }

    private fun showState(active: Boolean, text: String) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = text
            updateTile()
        }
    }

    private fun refresh() {
        val future = WorkManager.getInstance(applicationContext).getWorkInfosByTag(BackupManager.TAG)
        future.addListener({
            val infos = runCatching { future.get() }.getOrDefault(emptyList())
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            val tile = qsTile ?: return@addListener
            tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
            tile.label = "Back up photos"
            if (running != null) {
                val done = running.progress.getInt(BackupWorker.KEY_DONE, 0)
                val total = running.progress.getInt(BackupWorker.KEY_TOTAL, 0)
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = if (total > 0) "$done of $total" else "Checking…"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Tap to back up"
            }
            tile.updateTile()
        }, ContextCompat.getMainExecutor(this))
    }
}
