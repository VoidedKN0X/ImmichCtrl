package com.vknxworks.immichctrl.ui.viewer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.immichctrl.MainActivity
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.MemoryDto
import com.vknxworks.immichctrl.data.api.createAlbum
import com.vknxworks.immichctrl.data.api.memories
import com.vknxworks.immichctrl.data.api.memory
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LocalHazeState
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.glass
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.components.yearsAgo
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.photos.AlbumPickerSheet
import com.vknxworks.immichctrl.ui.photos.ShareFlow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** One photo or video filling the screen, without zoom (slideshow and memories). */
@Composable
private fun Slide(asset: Asset, playing: Boolean, onVideoEnd: () -> Unit) {
    val c = LocalContext.current.container
    val localFile = asset.localUri?.takeIf { asset.isLocalOnly || it.startsWith("file:") }
    if (asset.isVideo) {
        VideoPlayer(
            videoAssetId = localFile ?: asset.id,
            isUri = localFile != null,
            active = playing,
            autoPlay = true,
            loop = false,
            showControls = false,
            onEnded = onVideoEnd,
        )
    } else {
        AsyncImage(
            model = localFile ?: c.api.thumbnailUrl(asset.id, "preview", asset.thumbhash?.take(12)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Photos and videos one after another, full screen. Tap for controls and timing. */
@Composable
fun SlideshowScreen(sourceKey: String, startIndex: Int) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val source = Sources[sourceKey]
    if (source == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val total = source.total
    val order = remember(settings.slideShuffle, total) {
        val all = (0 until total).toMutableList()
        if (settings.slideShuffle) {
            all.remove(startIndex)
            all.shuffle()
            listOf(startIndex) + all
        } else {
            all.drop(startIndex) + all.take(startIndex)
        }
    }
    var pos by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(false) }
    val haze = rememberHazeState()
    KeepScreenOn()

    fun next() {
        if (pos + 1 < order.size) pos++ else if (settings.slideLoop) pos = 0 else nav.back()
    }
    fun prev() {
        if (pos > 0) pos-- else if (settings.slideLoop) pos = order.lastIndex
    }

    val asset = order.getOrNull(pos)?.let { source.assetAt(it) }
    LaunchedEffect(pos, playing, asset?.id) {
        val a = asset ?: run {
            // Not loaded yet: wait for its month to arrive.
            delay(400)
            return@LaunchedEffect
        }
        if (!playing || a.isVideo) return@LaunchedEffect
        delay(settings.slideSeconds * 1000L)
        next()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(onTap = { controls = !controls })
        },
    ) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Crossfade(asset, animationSpec = tween(600), label = "slide") { a ->
                if (a != null) Slide(a, playing) { if (playing) next() } else LoadingView()
            }
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            CompositionLocalProvider(LocalHazeState provides haze) {
                Column(
                    Modifier.navigationBarsPadding().padding(16.dp).glass(RoundedCornerShape(28.dp)).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.Close, "Close") }
                        IconButton(onClick = ::prev) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                        IconButton(onClick = { playing = !playing }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }
                        IconButton(onClick = ::next) { Icon(Icons.Filled.SkipNext, "Next") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 10, 20).forEach { s ->
                            FilterChip(settings.slideSeconds == s, onClick = { scope.launch { c.settings.update { it.copy(slideSeconds = s) } } }, label = { Text("${s}s") })
                        }
                        FilterChip(settings.slideShuffle, onClick = { scope.launch { c.settings.update { it.copy(slideShuffle = !it.slideShuffle) } } }, label = { Text("Shuffle") }, leadingIcon = { Icon(Icons.Outlined.Shuffle, null) })
                        FilterChip(settings.slideLoop, onClick = { scope.launch { c.settings.update { it.copy(slideLoop = !it.slideLoop) } } }, label = { Text("Loop") }, leadingIcon = { Icon(Icons.Outlined.Repeat, null) })
                    }
                    Text("${pos + 1} of ${order.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/**
 * Memories as full-screen stories: each photo for a few seconds (videos to the end), with progress
 * bars. Tap the right side for next, left for back, hold to pause; it moves on to the next memory.
 */
@Composable
fun MemoryScreen(id: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val all = rememberLoader("memory-story:$id") {
        val list = runCatching { c.api.memories(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }.getOrDefault(emptyList())
            .filter { it.assets.isNotEmpty() }
        if (list.any { it.id == id }) list else listOf(c.api.memory(id)) + list
    }
    when (val s = all.state) {
        is Load.Loading -> Box(Modifier.fillMaxSize().background(Color.Black)) { LoadingView() }
        is Load.Err -> ErrorView(s.message) { all.reload() }
        is Load.Ok -> Story(s.value, s.value.indexOfFirst { it.id == id }.coerceAtLeast(0), onDone = { nav.back() })
    }
}

@Composable
private fun Story(memories: List<MemoryDto>, start: Int, onDone: () -> Unit) {
    val c = LocalContext.current.container
    var m by remember { mutableIntStateOf(start) }
    var i by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val shareFlow = remember { ShareFlow() }
    val haze = rememberHazeState()
    KeepScreenOn()
    val memory = memories.getOrNull(m) ?: return
    val assets = remember(m) { memory.assets.map { it.toAsset() } }
    val asset = assets.getOrNull(i)

    fun next() {
        if (i + 1 < assets.size) i++
        else if (m + 1 < memories.size) { m++; i = 0 } else onDone()
    }
    fun prev() {
        if (i > 0) i--
        else if (m > 0) { m--; i = 0 }
    }

    LaunchedEffect(m, i, paused) {
        progress = 0f
        val a = asset ?: return@LaunchedEffect
        if (a.isVideo || paused) return@LaunchedEffect
        val steps = 100
        repeat(steps) {
            delay(50)
            progress = (it + 1) / steps.toFloat()
        }
        next()
    }

    BackHandler { onDone() }
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(m) {
            detectTapGestures(
                onTap = { o -> if (o.x > size.width / 3) next() else prev() },
                onPress = {
                    paused = true
                    tryAwaitRelease()
                    paused = false
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Crossfade(asset, animationSpec = tween(400), label = "story") { a -> if (a != null) Slide(a, !paused) { next() } }
        }
        Column(
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                assets.indices.forEach { k ->
                    LinearProgressIndicator(
                        progress = { if (k < i) 1f else if (k == i) progress else 0f },
                        modifier = Modifier.weight(1f).height(3.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (memory.type == "on_this_day") yearsAgo(memory.data.year) else memory.data.personName ?: "Memory",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    asset?.let { Text(shortDate(java.time.Instant.ofEpochMilli(it.takenAtMs).toString()).orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f)) }
                }
            }
        }
        asset?.let { a ->
            CompositionLocalProvider(LocalHazeState provides haze) {
                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).glass(CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = { paused = !paused }) { Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "Play" else "Pause") }
                    IconButton(onClick = { paused = true; shareFlow.start(c, listOf(a)) }) { Icon(Icons.Outlined.Share, "Share") }
                }
            }
        }
    }
    shareFlow.Dialog()
}

/** A photo or video another app opened with ImmichCtrl: look, then upload or share. */
@Composable
fun ExternalViewScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val uris by c.externalUris.collectAsStateWithLifecycle()
    val uri = uris?.firstOrNull()
    var pickAlbum by remember { mutableStateOf(false) }
    val haze = rememberHazeState()
    if (uri == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val mime = remember(uri) { context.contentResolver.getType(uri).orEmpty() }
    val asset = remember(uri) {
        Asset(
            id = "external:$uri", isImage = !mime.startsWith("video/"), ratio = 1f, isFavorite = false, isTrashed = false,
            visibility = "timeline", durationMs = null, thumbhash = null, takenAtMs = System.currentTimeMillis(),
            localOffsetHours = 0.0, livePhotoVideoId = null, stackCount = 0, ownerId = "", localUri = uri.toString(), isLocalOnly = true,
        )
    }
    fun close() {
        c.externalUris.value = null
        nav.back()
    }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            if (asset.isVideo) {
                VideoPlayer(uri.toString(), active = true, autoPlay = true, loop = true, showControls = true, isUri = true)
            } else {
                ZoomableImage(asset, loadOriginal = false, onTap = {}, onDismiss = ::close, onSwipeUp = {}, onZoomChange = {})
            }
        }
        IconButton(onClick = ::close, modifier = Modifier.statusBarsPadding().padding(4.dp)) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
        CompositionLocalProvider(LocalHazeState provides haze) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (asset.isVideo) 72.dp else 20.dp)
                    .glass(CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!c.settings.settings.value.readOnly) {
                    IconButton(onClick = { (context as? MainActivity)?.uploadShared(listOf(uri)); close() }) { Icon(Icons.Outlined.CloudUpload, "Upload") }
                    IconButton(onClick = { pickAlbum = true }) { Icon(Icons.Outlined.LibraryAdd, "Upload to an album") }
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType(mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Outlined.Share, "Share") }
            }
        }
    }
    if (pickAlbum) {
        AlbumPickerSheet(onDismiss = { pickAlbum = false }) { id, name ->
            pickAlbum = false
            val activity = context as? MainActivity
            if (id != null) {
                activity?.uploadShared(listOf(uri), id)
                close()
            } else {
                c.appScope.launch {
                    runCatching { c.api.createAlbum(name) }.onSuccess { album ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { activity?.uploadShared(listOf(uri), album.id) }
                    }
                }
                close()
            }
        }
    }
}
