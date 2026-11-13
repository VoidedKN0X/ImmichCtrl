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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import com.vknxworks.immichctrl.Strings

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
        if (kind == KIND_MEMORIES) {
            WidgetSchedule.cancelMidnight(context)
            WidgetCache.clear(context)
        }
    }
}

/** The extra refresh just after midnight, when the day's memories change. */
object WidgetSchedule {
    private const val TAG = "widget-midnight"

    /**
     * One refresh just after the coming midnight. The work is named after that date and kept if it's already
     * queued, so calling this on every refresh (including the midnight one itself, which queues the next day's)
     * never stacks up or cancels a running refresh.
     */
    fun scheduleMidnight(context: Context) {
        val now = java.time.ZonedDateTime.now()
        val delay = WidgetRules.delayToNextDay(now)
        val req = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(WidgetWorker.KEY_KIND to KIND_MEMORIES))
            .addTag(TAG)
            .build()
        val name = "$TAG-${now.toLocalDate().plusDays(1)}"
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, req) }
    }

    fun cancelMidnight(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(TAG) }
    }
}

/**
 * The "On this day" widget's photo of the day, kept as a small JPEG in the app's private cache (at most
 * [WidgetRules.CACHE_SIDE] pixels), with the account and photo it came from. Removed when the app lock is on,
 * when signed out and when the last widget is removed.
 */
object WidgetCache {
    private fun dir(context: Context) = java.io.File(context.cacheDir, "widget")
    private fun image(context: Context) = java.io.File(dir(context), "memories.jpg")
    private fun meta(context: Context) = java.io.File(dir(context), "memories.key")

    fun read(context: Context, key: WidgetRules.CacheKey): Bitmap? = runCatching {
        if (WidgetRules.parseKey(meta(context).takeIf { it.isFile }?.readText()) != key) return null
        BitmapFactory.decodeFile(image(context).path)
    }.getOrNull()

    fun write(context: Context, key: WidgetRules.CacheKey, bitmap: Bitmap) {
        runCatching {
            val d = dir(context).apply { mkdirs() }
            val tmp = java.io.File(d, "memories.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (!tmp.renameTo(image(context))) { image(context).delete(); tmp.renameTo(image(context)) }
            meta(context).writeText(WidgetRules.formatKey(key))
        }
    }

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
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
        // With the app lock on, the home screen doesn't show your photos, and no copy of one is kept for it.
        if (c.appLock.enabled(c.settings.current())) {
            WidgetCache.clear(applicationContext)
            show(manager, ids, null, Strings.str(R.string.lock_title), null)
            return Result.success()
        }
        val session = c.sessions.active.value
        val client = session?.client
        if (client == null) {
            WidgetCache.clear(applicationContext)
            show(manager, ids, null, Strings.str(R.string.sign_in_to_immichctrl), null)
            return Result.success()
        }
        return try {
            var title: String
            var open: String
            val asset: AssetDto?
            if (kind == KIND_MEMORIES) {
                // The next day's memories appear a few minutes after midnight.
                WidgetSchedule.scheduleMidnight(applicationContext)
                val memories = client.memories(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).filter { it.assets.isNotEmpty() }
                // One photo per day: stills when the memory has any, else its videos' frames.
                val photos = memories.map { m -> m.assets.filter { it.isImage }.ifEmpty { m.assets } }
                val pick = WidgetRules.pickOfDay(photos.map { list -> list.map { it.id } }, LocalDate.now())
                val m = pick?.let { memories[it.first] }
                asset = pick?.let { photos[it.first][it.second] }
                title = m?.let { if (it.type == "on_this_day") yearsAgo(it.data.year) else it.data.personName ?: Strings.str(R.string.memory) } ?: Strings.str(R.string.no_memories_today)
                open = m?.let { "memory:${it.id}" } ?: ""
            } else {
                asset = client.randomAssets(1).firstOrNull()
                title = asset?.exifInfo?.city ?: asset?.fileCreatedAt?.take(4).orEmpty()
                open = asset?.let { "asset:${it.id}" } ?: ""
            }
            val bitmap = asset?.let { a ->
                if (kind == KIND_MEMORIES) {
                    // The day's photo is kept small on the phone, so later refreshes that day don't download it again.
                    val key = WidgetRules.CacheKey(session.account.key, a.id)
                    WidgetCache.read(applicationContext, key)
                        ?: load(client.thumbnailUrl(a.id, "preview"))?.also { WidgetCache.write(applicationContext, key, it) }
                } else load(client.thumbnailUrl(a.id, "preview"))
            }
            if (kind == KIND_MEMORIES && asset == null) WidgetCache.clear(applicationContext)
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
            val body = resp.body ?: return null
            // A preview is a few hundred KB: refuse anything implausibly large instead of reading it into memory.
            if (body.contentLength() > MAX_PREVIEW_BYTES) return null
            val source = body.source()
            if (source.request(MAX_PREVIEW_BYTES + 1)) return null
            val bytes = source.buffer.readByteArray()
            // Keep it small: widgets are drawn by the launcher and big bitmaps are rejected.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = WidgetRules.sampleSize(bounds.outWidth, bounds.outHeight, WidgetRules.CACHE_SIDE)
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
        private const val MAX_PREVIEW_BYTES = 20L * 1024 * 1024

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
