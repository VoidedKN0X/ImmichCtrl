package com.vknxworks.immichctrl.ui.viewer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.vknxworks.immichctrl.AppContainer
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AssetDto
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.api.parseInstantMs
import com.vknxworks.immichctrl.data.api.updateAsset
import com.vknxworks.immichctrl.data.api.uploadFile
import com.vknxworks.immichctrl.data.media.VideoTrimmer
import com.vknxworks.immichctrl.data.share.Exporter
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.CtrlProgress
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.friendlyMessage
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.theme.CtrlType
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** What the trim screen is busy with; [progress] is 0..1, or negative when it can't be measured. */
private data class TrimWork(val label: String, val progress: Float)

/**
 * Cut a piece out of a video and save it as a new video; the original is never changed. [assetId] is the
 * server asset (empty for a video that is only on this phone), [localUri] a copy on the phone when there is one.
 */
@Composable
fun TrimScreen(assetId: String, localUri: String?, name: String?, takenMs: Long) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    // Name, date and place of the original, for the copy. Not needed to trim, so a failure is not an error.
    val details = rememberLoader("trim:$assetId") { if (assetId.isEmpty()) null else runCatching { c.api.asset(assetId) }.getOrNull() }

    var player by remember { mutableStateOf<Player?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var work by remember { mutableStateOf<TrimWork?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    // Learn the length from the player, and keep "play" inside the chosen piece.
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            val d = p.duration
            if (durationMs <= 0L && d > 0L) {
                durationMs = d
                startMs = 0f
                endMs = d.toFloat()
            }
            playing = p.isPlaying
            if (durationMs > 0L && p.isPlaying && p.currentPosition >= endMs.toLong()) {
                p.pause()
                p.seekTo(startMs.toLong())
            }
            delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    // While a copy is being made, Back stops it instead of leaving half-way.
    BackHandler(enabled = work != null) {
        job?.cancel()
        work = null
    }

    fun save(toServer: Boolean) {
        if (work != null || durationMs <= 0L) return
        player?.pause()
        val from = startMs.toLong()
        val to = endMs.toLong()
        val dto = details.value
        job = scope.launch {
            work = TrimWork(Strings.str(R.string.trim_preparing), -1f)
            try {
                val saved = trimAndSave(
                    context, c, assetId, localUri,
                    originalName = dto?.originalFileName?.takeIf { it.isNotBlank() } ?: name,
                    originalTakenMs = dto?.let { parseInstantMs(it.fileCreatedAt) } ?: takenMs,
                    original = dto, fromMs = from, toMs = to, toServer = toServer,
                ) { label, progress -> work = TrimWork(label, progress) }
                work = null
                c.toast(saved)
                nav.back()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                work = null
                c.toast(
                    if (e is VideoTrimmer.UnsupportedVideo) Strings.str(R.string.trim_unsupported)
                    else Strings.str(R.string.trim_failed, e.friendlyMessage()),
                )
            }
        }
    }

    // Like the viewer, this screen is dark in light mode too: say so to the text and icons.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (work != null) { job?.cancel(); work = null } else nav.back() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
            Text(stringResource(R.string.trim_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            VideoPlayer(
                videoAssetId = localUri ?: assetId,
                active = true,
                autoPlay = false,
                loop = false,
                showControls = false,
                isUri = localUri != null,
                onPlayer = { p, on -> player = if (on) p else null },
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val busy = work
            if (busy != null) {
                Text(busy.label, style = MaterialTheme.typography.bodyLarge)
                if (busy.progress >= 0f) CtrlProgress(progress = { busy.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else CtrlProgress(Modifier.fillMaxWidth())
                TextButton(onClick = { job?.cancel(); work = null }) { Text(stringResource(R.string.lock_cancel)) }
            } else if (durationMs <= 0L) {
                Text(stringResource(R.string.trim_loading), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                CtrlProgress(Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val p = player ?: return@IconButton
                        if (p.isPlaying) p.pause()
                        else {
                            if (p.currentPosition < startMs.toLong() || p.currentPosition >= endMs.toLong() - 50) p.seekTo(startMs.toLong())
                            p.play()
                        }
                    }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play))
                    }
                    Text(
                        stringResource(R.string.trim_range, trimClock(startMs.toLong()), trimClock(endMs.toLong()), trimClock((endMs - startMs).toLong())),
                        style = CtrlType.Mono,
                        modifier = Modifier.weight(1f),
                    )
                }
                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        val movedStart = range.start != startMs
                        val (s, e) = VideoTrimmer.clampRange(range.start.toLong(), range.endInclusive.toLong(), durationMs, movedStart)
                        startMs = s.toFloat()
                        endMs = e.toFloat()
                        // Show the frame under the handle being dragged.
                        player?.let { p ->
                            p.pause()
                            p.seekTo(if (movedStart) s else e)
                        }
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.trim_note), style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                Spacer(Modifier.height(4.dp))
                val whole = startMs <= 0f && endMs >= durationMs.toFloat()
                if (session != null) {
                    PillButton(stringResource(R.string.trim_save_immich), onClick = { save(toServer = true) }, icon = Icons.Outlined.CloudUpload, enabled = !whole, modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (session != null) {
                        TextButton(onClick = { save(toServer = false) }, enabled = !whole) {
                            Icon(Icons.Outlined.SaveAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.trim_save_phone))
                        }
                    } else {
                        PillButton(stringResource(R.string.trim_save_phone), onClick = { save(toServer = false) }, icon = Icons.Outlined.SaveAlt, enabled = !whole, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    }
}

private fun trimClock(ms: Long): String {
    val tenths = (ms / 100).coerceAtLeast(0)
    val total = tenths / 10
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d.%d".format(h, m, s, tenths % 10) else "%d:%02d.%d".format(m, s, tenths % 10)
}

/**
 * Gets the original (the copy on the phone, or a download from the server), copies the chosen piece into a new
 * MP4 in the app's cache, and sends that to Immich or into the phone's Movies/ImmichCtrl folder. The cache
 * copies are removed afterwards, whatever happens. Returns the message to show.
 */
private suspend fun trimAndSave(
    context: Context,
    c: AppContainer,
    assetId: String,
    localUri: String?,
    originalName: String?,
    originalTakenMs: Long,
    original: AssetDto?,
    fromMs: Long,
    toMs: Long,
    toServer: Boolean,
    report: (String, Float) -> Unit,
): String {
    val dir = File(context.cacheDir, "trim/${UUID.randomUUID()}")
    try {
        withContext(Dispatchers.IO) { if (!dir.mkdirs()) throw IOException(Strings.str(R.string.trim_no_space)) }
        val outName = Exporter.safeFileName(VideoTrimmer.outputName(originalName), "video_trim.mp4")
        val out = File(dir, outName)

        suspend fun cut(source: Uri): VideoTrimmer.Plan = withContext(Dispatchers.IO) {
            report(Strings.str(R.string.trim_cutting), 0f)
            VideoTrimmer.trim(context, source, out, fromMs, toMs) { report(Strings.str(R.string.trim_cutting), it) }
        }

        // The copy on the phone first (no download); the server's original when there's none or it can't be read.
        val plan: VideoTrimmer.Plan = run {
            if (localUri != null) {
                try {
                    return@run cut(Uri.parse(localUri))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (assetId.isEmpty()) throw e
                }
            }
            val downloaded = File(dir, "original")
            c.api.execute(c.api.request(c.api.url("/assets/$assetId/original")).build()).use { resp ->
                val body = resp.body ?: throw IOException(Strings.str(R.string.empty_response))
                val total = body.contentLength()
                withContext(Dispatchers.IO) {
                    downloaded.sink().buffer().use { sink ->
                        val src = body.source()
                        var done = 0L
                        while (true) {
                            val n = src.read(sink.buffer, 256 * 1024L)
                            if (n < 0) break
                            sink.emitCompleteSegments()
                            done += n
                            report(Strings.str(R.string.trim_downloading), if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
            cut(Uri.fromFile(downloaded)).also { downloaded.delete() }
        }

        // The piece was "taken" when its first frame was.
        val takenMs = (if (originalTakenMs > 0) originalTakenMs else System.currentTimeMillis()) + plan.startUs / 1000
        val lengthMs = plan.lengthUs / 1000

        if (toServer) {
            report(Strings.str(R.string.trim_uploading), -1f)
            val sha1 = withContext(Dispatchers.IO) { sha1Of(out) }
            val result = c.api.uploadFile(out, outName, "video/mp4", takenMs, lengthMs, sha1)
            // The new file's own header says "made just now"; put it next to the original in the timeline,
            // and at the same place when the original has one.
            runCatching {
                val exif = original?.exifInfo
                c.api.updateAsset(
                    result.id,
                    "dateTimeOriginal" to Instant.ofEpochMilli(takenMs).toString(),
                    "latitude" to exif?.latitude,
                    "longitude" to exif?.longitude,
                )
            }
            withContext(Dispatchers.Main) { Sources.refreshAll() }
            return Strings.str(if (result.status == "duplicate") R.string.trim_already_there else R.string.trim_saved_immich)
        } else {
            report(Strings.str(R.string.trim_saving), -1f)
            withContext(Dispatchers.IO) { saveToMovies(context, out, outName, takenMs) }
            c.local.changed()
            return Strings.str(R.string.trim_saved_phone)
        }
    } finally {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { dir.deleteRecursively() }
    }
}

private fun sha1Of(f: File): String {
    val md = MessageDigest.getInstance("SHA-1")
    f.inputStream().use { input ->
        val buf = ByteArray(256 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
}

/** Adds the file to the phone's video library as Movies/ImmichCtrl/[name]. */
private fun saveToMovies(context: Context, file: File, name: String, takenMs: Long) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ImmichCtrl")
        put(MediaStore.MediaColumns.DATE_TAKEN, takenMs)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: throw IOException(Strings.str(R.string.couldn_t_create, name))
    try {
        (resolver.openOutputStream(uri) ?: throw IOException(Strings.str(R.string.couldn_t_write, name))).use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (e: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}
