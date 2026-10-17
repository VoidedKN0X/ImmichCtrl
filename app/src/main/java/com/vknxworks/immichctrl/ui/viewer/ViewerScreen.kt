package com.vknxworks.immichctrl.ui.viewer

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.MotionPhotosPaused
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.api.createAlbum
import com.vknxworks.immichctrl.data.api.runAssetJob
import com.vknxworks.immichctrl.data.api.updateAlbum
import com.vknxworks.immichctrl.data.timeline.ListSource
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.photos.AlbumPickerSheet
import com.vknxworks.immichctrl.ui.photos.TagPickerSheet
import com.vknxworks.immichctrl.ui.photos.addToAlbum
import com.vknxworks.immichctrl.ui.photos.download
import com.vknxworks.immichctrl.ui.photos.removeFromAlbum
import com.vknxworks.immichctrl.ui.photos.restore
import com.vknxworks.immichctrl.ui.photos.rememberSource
import com.vknxworks.immichctrl.ui.photos.setFavorite
import com.vknxworks.immichctrl.ui.photos.setVisibility
import com.vknxworks.immichctrl.ui.photos.share
import com.vknxworks.immichctrl.ui.photos.shareLink
import com.vknxworks.immichctrl.ui.photos.trash
import java.time.format.DateTimeFormatter

private val titleDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val titleTime = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Full-screen photos and videos, swiping through the grid they were opened from.
 * Tap to show or hide the controls; swipe down to close, up for details.
 */
@Composable
fun ViewerScreen(sourceKey: String, startIndex: Int, startId: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    // After the app was killed the grid's source is gone; fall back to just this photo.
    val source = Sources[sourceKey] ?: rememberSource("single:$startId") { scope ->
        ListSource("single:$startId", scope) { listOf(c.api.asset(startId).toAsset()) }
    }
    val fallback = Sources[sourceKey] == null
    val initial = remember { if (fallback) 0 else source.indexOf(startId).takeIf { it >= 0 } ?: startIndex }
    val pager = rememberPagerState(initialPage = initial) { source.total }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var pickAlbum by remember { mutableStateOf(false) }
    var pickTag by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Boolean?>(null) }
    var playLive by remember { mutableStateOf(false) }

    val current: Asset? = source.assetAt(pager.currentPage)
    val isTrash = sourceKey == "trash"
    val isLocked = sourceKey == "locked"
    val albumId = sourceKey.removePrefix("album:").takeIf { sourceKey.startsWith("album:") }
    val mine = current != null && (current.ownerId.isEmpty() || current.ownerId == c.me)

    LaunchedEffect(pager.currentPage) { playLive = false }
    // Close when the last photo was deleted.
    LaunchedEffect(source.total) { if (source.total == 0 && !fallback) nav.back() }

    // Hide the system bars with the controls.
    val view = LocalView.current
    DisposableEffect(chrome) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) ctl?.show(WindowInsetsCompat.Type.systemBars()) else ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { page -> source.assetAt(page)?.id ?: "page-$page" },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val a = source.assetAt(page)
            if (a == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (a.isVideo) {
                VideoPlayer(
                    videoAssetId = a.id,
                    active = pager.settledPage == page,
                    autoPlay = settings.autoPlayVideos,
                    loop = settings.loopVideos,
                    showControls = chrome,
                    onTap = { chrome = !chrome },
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    ZoomableImage(
                        asset = a,
                        loadOriginal = settings.loadOriginal,
                        onTap = { chrome = !chrome },
                        onDismiss = { nav.back() },
                        onSwipeUp = { info = true },
                        onZoomChange = { zoomed = it },
                    )
                    val motion = a.livePhotoVideoId
                    if (motion != null && playLive && pager.settledPage == page) {
                        VideoPlayer(motion, active = true, autoPlay = true, loop = true, showControls = false, onTap = { playLive = false })
                    }
                }
            }
        }

        // ---- Top bar ----
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    if (current != null) {
                        val dt = current.localDateTime
                        Text(dt.format(titleDate), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            listOfNotNull(dt.format(titleTime), current.city, current.country).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (current?.livePhotoVideoId != null) {
                    IconButton(onClick = { playLive = !playLive }) {
                        Icon(if (playLive) Icons.Outlined.MotionPhotosPaused else Icons.Outlined.MotionPhotosOn, "Live photo", tint = Color.White)
                    }
                }
                IconButton(onClick = { more = true }, enabled = current != null) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
            }
        }

        // ---- Bottom bar ----
        AnimatedVisibility(chrome && current != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val a = current ?: return@AnimatedVisibility
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = if (a.isVideo) 56.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isTrash -> {
                        BottomAction(Icons.Outlined.RestoreFromTrash, "Restore") { c.restore(listOf(a)) }
                        BottomAction(Icons.Outlined.Info, "Details") { info = true }
                        BottomAction(Icons.Outlined.DeleteForever, "Delete") { confirmDelete = true }
                    }
                    isLocked -> {
                        BottomAction(Icons.Outlined.LockOpen, "Unlock") { c.setVisibility(listOf(a), "timeline", "Moved out of the locked folder") }
                        BottomAction(Icons.Outlined.Info, "Details") { info = true }
                        BottomAction(Icons.Outlined.Delete, "Delete") { confirmDelete = false }
                    }
                    else -> {
                        BottomAction(Icons.Outlined.Share, "Share") { c.share(listOf(a)) }
                        if (mine) {
                            BottomAction(if (a.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (a.isFavorite) "Unfavourite" else "Favourite") {
                                c.setFavorite(listOf(a), !a.isFavorite)
                            }
                        }
                        BottomAction(Icons.Outlined.Info, "Details") { info = true }
                        if (mine) BottomAction(Icons.Outlined.Delete, "Delete") {
                            if (settings.confirmDelete) confirmDelete = false else c.trash(listOf(a), false)
                        }
                    }
                }
            }
        }
    }

    val a = current
    if (info && a != null) InfoSheet(a, onDismiss = { info = false })

    if (more && a != null) {
        ModalBottomSheet(
            onDismissRequest = { more = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(a.localDateTime.format(titleDate))
                fun act(block: () -> Unit) { block(); more = false }
                ActionItem(Icons.Outlined.LibraryAdd, "Add to album") { more = false; pickAlbum = true }
                ActionItem(Icons.Outlined.Download, "Save to phone") { act { c.download(listOf(a)) } }
                ActionItem(Icons.Outlined.Link, "Share a link") { act { c.shareLink(listOf(a)) } }
                if (mine) {
                    ActionItem(Icons.Outlined.Sell, "Tag") { more = false; pickTag = true }
                    if (a.visibility == "archive") {
                        ActionItem(Icons.Outlined.Unarchive, "Unarchive") { act { c.setVisibility(listOf(a), "timeline", "Moved back to Photos") } }
                    } else if (!isLocked) {
                        ActionItem(Icons.Outlined.Archive, "Archive") { act { c.setVisibility(listOf(a), "archive", "Archived") } }
                    }
                    if (!isLocked) ActionItem(Icons.Outlined.Lock, "Move to locked folder") { act { c.setVisibility(listOf(a), "locked", "Moved to the locked folder") } }
                }
                if (albumId != null) {
                    ActionItem(Icons.Outlined.Image, "Use as album cover") {
                        act { c.runTask("Album cover changed") { c.api.updateAlbum(albumId, "albumThumbnailAssetId" to a.id); Sources.refreshAll() } }
                    }
                    ActionItem(Icons.Outlined.RemoveCircleOutline, "Remove from album") { act { c.removeFromAlbum(albumId, listOf(a)) } }
                }
                if (mine) {
                    ActionItem(Icons.Outlined.Refresh, "Refresh metadata") {
                        act { c.runTask("Metadata refresh queued") { c.api.runAssetJob(listOf(a.id), "refresh-metadata") } }
                    }
                    ActionItem(Icons.Outlined.Refresh, "Regenerate thumbnails") {
                        act { c.runTask("Thumbnails queued") { c.api.runAssetJob(listOf(a.id), "regenerate-thumbnail") } }
                    }
                    if (!isTrash) ActionItem(Icons.Outlined.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error) {
                        more = false
                        confirmDelete = true
                    }
                }
            }
        }
    }

    if (pickAlbum && a != null) {
        AlbumPickerSheet(onDismiss = { pickAlbum = false }) { id, name ->
            pickAlbum = false
            if (id == null) c.runTask { c.api.createAlbum(name, listOf(a.id)); c.toast("Created $name"); Sources.refreshAll() }
            else c.addToAlbum(id, name, listOf(a))
        }
    }
    if (pickTag && a != null) TagPickerSheet(listOf(a), onDismiss = { pickTag = false })

    confirmDelete?.let { permanent ->
        if (a == null) {
            confirmDelete = null
            return@let
        }
        if (permanent) {
            ConfirmDialog(
                "Delete permanently?",
                "It'll be removed from the server for good. This can't be undone.",
                "Delete",
                onDismiss = { confirmDelete = null },
            ) { c.trash(listOf(a), true); confirmDelete = null }
        } else {
            ConfirmDialog(
                "Move to the trash?",
                "You can restore it from the trash in Library for ${c.serverConfig.value.trashDays} days.",
                "Move to trash",
                onDismiss = { confirmDelete = null },
            ) { c.trash(listOf(a), false); confirmDelete = null }
        }
    }
}

@Composable
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = Color.White) }
}
