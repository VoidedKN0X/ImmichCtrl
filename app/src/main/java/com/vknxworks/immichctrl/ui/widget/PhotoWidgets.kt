package com.vknxworks.immichctrl.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.immichctrl.MainActivity
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AssetDto
import com.vknxworks.immichctrl.data.api.await
import com.vknxworks.immichctrl.data.api.memories
import com.vknxworks.immichctrl.data.api.randomAssets
import com.vknxworks.immichctrl.data.log.AppLog
import com.vknxworks.immichctrl.ui.components.yearsAgo
import kotlinx.coroutines.flow.first
import okhttp3.Request
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Home screen widget: a photo from today's memories. */
class MemoriesWidget : PhotoWidget(KIND_MEMORIES)

/** Home screen widget: a random photo from the library. */
class RandomWidget : PhotoWidget(KIND_RANDOM)

const val KIND_MEMORIES = "memories"
const val KIND_RANDOM = "random"

abstract class PhotoWidget(private val kind: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetWorker.refresh(context, kind)
    }

    override fun onEnabled(context: Context) {
        WidgetWorker.schedule(context, kind)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("widget-periodic-$kind")
    }
}

/** Fetches a photo for the widgets of one kind and puts it on screen. */
class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.success()
        val provider = if (kind == KIND_MEMORIES) MemoriesWidget::class.java else RandomWidget::class.java
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(applicationContext, provider))
        if (ids.isEmpty()) return Result.success()
        val c = applicationContext.container
        c.sessions.ready.first { it }
        // With the app lock on, the home screen doesn't show your photos.
        if (c.appLock.enabled(c.settings.current())) {
            show(manager, ids, null, "ImmichCtrl is locked", null)
            return Result.success()
        }
        val client = c.sessions.active.value?.client
        if (client == null) {
            show(manager, ids, null, "Sign in to ImmichCtrl", null)
            return Result.success()
        }
        return try {
            var title: String
            var open: String
            val asset: AssetDto?
            if (kind == KIND_MEMORIES) {
                val memories = client.memories(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).filter { it.assets.isNotEmpty() }
                val m = memories.randomOrNull()
                asset = m?.assets?.filter { it.isImage }?.randomOrNull() ?: m?.assets?.firstOrNull()
                title = m?.let { if (it.type == "on_this_day") yearsAgo(it.data.year) else it.data.personName ?: "Memory" } ?: "No memories today"
                open = m?.let { "memory:${it.id}" } ?: ""
            } else {
                asset = client.randomAssets(1).firstOrNull()
                title = asset?.exifInfo?.city ?: asset?.fileCreatedAt?.take(4).orEmpty()
                open = asset?.let { "asset:${it.id}" } ?: ""
            }
            val bitmap = asset?.let { load(client.thumbnailUrl(it.id, "preview")) }
            show(manager, ids, bitmap, title, open.ifEmpty { null })
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLog.w("Widget", "Couldn't refresh the $kind widget: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun load(url: String): Bitmap? {
        val http = applicationContext.container.http
        return http.newCall(Request.Builder().url(url).build()).await().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            // Keep it small: widgets are drawn by the launcher and big bitmaps are rejected.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun show(manager: AppWidgetManager, ids: IntArray, bitmap: Bitmap?, title: String, open: String?) {
        val views = RemoteViews(applicationContext.packageName, R.layout.widget_photo)
        if (bitmap != null) views.setImageViewBitmap(R.id.widget_image, bitmap) else views.setImageViewResource(R.id.widget_image, R.drawable.ic_launcher_fg)
        views.setTextViewText(R.id.widget_title, title)
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (open != null) putExtra(MainActivity.EXTRA_OPEN, open) }
        val pi = PendingIntent.getActivity(applicationContext, open.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        const val KEY_KIND = "kind"

        fun refresh(context: Context, kind: String) {
            val req = OneTimeWorkRequestBuilder<WidgetWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_KIND to kind))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("widget-$kind", ExistingWorkPolicy.REPLACE, req)
        }

        fun schedule(context: Context, kind: String) {
            val hours = if (kind == KIND_MEMORIES) 6L else 2L
            val req = PeriodicWorkRequestBuilder<WidgetWorker>(hours, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_KIND to kind))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("widget-periodic-$kind", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshAll(context: Context) {
            refresh(context, KIND_MEMORIES)
            refresh(context, KIND_RANDOM)
        }
    }
}
