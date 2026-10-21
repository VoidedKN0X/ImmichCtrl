package com.vknxworks.immichctrl.ui.viewer

import com.vknxworks.immichctrl.data.api.users
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.UserAvatar
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.MotionPhotosPaused
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.vknxworks.immichctrl.data.api.FaceDto
import com.vknxworks.immichctrl.data.api.OcrDto
import com.vknxworks.immichctrl.data.api.StackDto
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.api.createAlbum
import com.vknxworks.immichctrl.data.api.deleteStack
import com.vknxworks.immichctrl.data.api.faces
import com.vknxworks.immichctrl.data.api.ocr
import com.vknxworks.immichctrl.data.api.removeFromStack
import com.vknxworks.immichctrl.data.api.runAssetJob
import com.vknxworks.immichctrl.data.api.setStackPrimary
import com.vknxworks.immichctrl.data.api.stack
import com.vknxworks.immichctrl.data.api.updateAlbum
import com.vknxworks.immichctrl.data.timeline.ListSource
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.LocalHazeState
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.glass
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.EditorRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.SlideshowRoute
import com.vknxworks.immichctrl.ui.photos.AlbumPickerSheet
import com.vknxworks.immichctrl.ui.photos.DateTimeDialog
import com.vknxworks.immichctrl.ui.photos.LocationDialog
import com.vknxworks.immichctrl.ui.photos.ShareFlow
import com.vknxworks.immichctrl.ui.photos.SharedLinkDialog
import com.vknxworks.immichctrl.ui.photos.TagPickerSheet
import com.vknxworks.immichctrl.ui.photos.addToAlbum
import com.vknxworks.immichctrl.ui.photos.deleteFromPhone
import com.vknxworks.immichctrl.ui.photos.download
import com.vknxworks.immichctrl.ui.photos.rememberSource
import com.vknxworks.immichctrl.ui.photos.removeFromAlbum
import com.vknxworks.immichctrl.ui.photos.restore
import com.vknxworks.immichctrl.ui.photos.setDate
import com.vknxworks.immichctrl.ui.photos.setFavorite
import com.vknxworks.immichctrl.ui.photos.setLocation
import com.vknxworks.immichctrl.ui.photos.setVisibility
import com.vknxworks.immichctrl.ui.photos.shareLink
import com.vknxworks.immichctrl.ui.photos.trash
import com.vknxworks.immichctrl.ui.photos.upload
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

private val titleDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val titleTime = DateTimeFormatter.ofPattern("HH:mm")

private enum class Overlay { NONE, TEXT, FACES }

/**
 * Full-screen photos and videos, swiping through the grid they were opened from.
 * Tap to show or hide the controls; swipe down to close, up for details.
 */
@Composable
fun ViewerScreen(sourceKey: String, startIndex: Int, startId: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val features by c.features.collectAsStateWithLifecycle()
    // After the app was killed the grid's source is gone; fall back to just this photo.
    val source = Sources[sourceKey] ?: rememberSource("single:$startId") { scope ->
        ListSource("single:$startId", scope) { listOf(c.api.asset(startId).toAsset()) }
    }
    val fallback = Sources[sourceKey] == null
    val initial = remember { if (fallback) 0 else source.indexOf(startId).takeIf { it >= 0 } ?: startIndex }
    val pager = rememberPagerState(initialPage = initial) { source.total }
    val haze = rememberHazeState()
    val shareFlow = remember { ShareFlow() }
    // The player of the video on screen, for the seekbar above the island.
    var videoPlayer by remember { mutableStateOf<androidx.media3.common.Player?>(null) }
    // Names of the other users on the server, to say whose photo this is.
    val ownerNames = rememberLoader("user-names") {
        runCatching { c.api.users().associate { it.id to it.name.ifBlank { it.email } } }.getOrDefault(emptyMap())
    }.value.orEmpty()
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var pickAlbum by remember { mutableStateOf(false) }
    var pickTag by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickPlace by remember { mutableStateOf(false) }
    var linkOptions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Boolean?>(null) }
    var playLive by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var ocrLines by remember { mutableStateOf<List<OcrDto>?>(null) }
    var faces by remember { mutableStateOf<List<FaceDto>?>(null) }
    var stack by remember { mutableStateOf<StackDto?>(null) }
    var stackShown by remember { mutableStateOf<Asset?>(null) }

    val pageAsset: Asset? = source.assetAt(pager.currentPage)
    // A photo picked from the stack strip takes the place of the stack's cover.
    val current: Asset? = stackShown ?: pageAsset
    val isTrash = sourceKey == "trash"
    val isLocked = sourceKey == "locked"
    val albumId = sourceKey.removePrefix("album:").takeIf { sourceKey.startsWith("album:") }
    val readOnly = settings.readOnly
    val mine = current != null && current.isRemote && (current.ownerId.isEmpty() || current.ownerId == c.me)

    LaunchedEffect(pager.currentPage) {
        playLive = false
        overlay = Overlay.NONE
        stackShown = null
        stack = null
    }
    // Load the stack for photos that have one.
    LaunchedEffect(pageAsset?.id) {
        val a = pageAsset ?: return@LaunchedEffect
        if (a.stackCount > 1 && a.isRemote) {
            stack = runCatching { c.api.asset(a.id).stack?.id?.let { c.api.stack(it) } }.getOrNull()
        }
    }
    LaunchedEffect(overlay, current?.id) {
        val a = current ?: return@LaunchedEffect
        when (overlay) {
            Overlay.TEXT -> ocrLines = runCatching { c.api.ocr(a.id) }.getOrDefault(emptyList())
            Overlay.FACES -> faces = runCatching { c.api.faces(a.id) }.getOrDefault(emptyList())
            Overlay.NONE -> Unit
        }
    }
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
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed && overlay == Overlay.NONE,
                beyondViewportPageCount = 1,
                key = { page -> source.assetAt(page)?.id ?: "page-$page" },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val base = source.assetAt(page)
                val a = if (page == pager.currentPage && stackShown != null) stackShown else base
                if (a == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (a.isVideo) {
                    val localFile = a.localUri?.takeIf { a.isLocalOnly || it.startsWith("file:") }
                    VideoPlayer(
                        videoAssetId = localFile ?: a.id,
                        isUri = localFile != null,
                        active = pager.settledPage == page,
                        autoPlay = settings.autoPlayVideos,
                        loop = settings.loopVideos,
                        showControls = chrome,
                        onTap = { chrome = !chrome },
                        onPlayer = { p, on -> if (on) videoPlayer = p else if (videoPlayer === p) videoPlayer = null },
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
        }

        // ---- Text and faces drawn over the photo ----
        val shown = current
        if (shown != null && overlay == Overlay.TEXT && ocrLines != null) {
            OcrOverlay(shown, ocrLines.orEmpty(), onClose = { overlay = Overlay.NONE })
        }
        if (shown != null && overlay == Overlay.FACES && faces != null) {
            FacesOverlay(shown, faces.orEmpty(), onChanged = { faces = null; overlay = Overlay.FACES }, onClose = { overlay = Overlay.NONE })
        }

        // ---- Top bar ----
        AnimatedVisibility(chrome && overlay == Overlay.NONE, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    if (current != null) {
                        val dt = current.localDateTime
                        Text(dt.format(titleDate), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            listOfNotNull(dt.format(titleTime), current.city, current.country, if (current.isLocalOnly) Strings.str(R.string.only_on_this_phone) else null).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Someone else's photo (a partner's, or from a shared album): say whose.
                        if (current.isRemote && current.ownerId.isNotBlank() && current.ownerId != c.me) {
                            val owner = ownerNames[current.ownerId]
                            Row(
                                Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(current.ownerId, owner ?: "?", true, null, null, Modifier.size(18.dp))
                                Text(
                                    if (owner != null) stringResource(R.string.s_photo, owner) else stringResource(R.string.shared_with_you),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 6.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (current?.livePhotoVideoId != null) {
                    IconButton(onClick = { playLive = !playLive }) {
                        Icon(if (playLive) Icons.Outlined.MotionPhotosPaused else Icons.Outlined.MotionPhotosOn, stringResource(R.string.live_photo), tint = Color.White)
                    }
                }
            }
        }

        // ---- Bottom: stack strip and the glass island ----
        AnimatedVisibility(
            chrome && current != null && overlay == Overlay.NONE,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val a = current ?: return@AnimatedVisibility
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                stack?.let { s ->
                    StackStrip(s, a.id, onPick = { picked -> stackShown = if (picked.id == pageAsset?.id) null else picked })
                    Spacer(Modifier.height(10.dp))
                }
                // Videos: the seekbar sits on its own glass bar above the island.
                val vp = videoPlayer
                if (a.isVideo && vp != null) {
                    CompositionLocalProvider(LocalHazeState provides haze) {
                        VideoControls(
                            vp,
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).glass(RoundedCornerShape(28.dp)).padding(horizontal = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                CompositionLocalProvider(LocalHazeState provides haze) {
                    Row(
                        Modifier.glass(CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            readOnly -> {
                                IslandAction(Icons.Outlined.Info, stringResource(R.string.details)) { info = true }
                            }
                            a.isLocalOnly -> {
                                IslandAction(Icons.Outlined.CloudUpload, stringResource(R.string.upload)) { c.upload(listOf(a)) }
                                IslandAction(Icons.Outlined.Share, stringResource(R.string.share)) { shareFlow.start(c, listOf(a)) }
                                IslandAction(Icons.Outlined.Info, stringResource(R.string.details)) { info = true }
                                IslandAction(Icons.Outlined.Delete, stringResource(R.string.lock_delete)) { confirmDelete = false }
                            }
                            isTrash -> {
                                IslandAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.restore)) { c.restore(listOf(a)) }
                                IslandAction(Icons.Outlined.Info, stringResource(R.string.details)) { info = true }
                                IslandAction(Icons.Outlined.DeleteForever, stringResource(R.string.lock_delete)) { confirmDelete = true }
                            }
                            isLocked -> {
                                IslandAction(Icons.Outlined.LockOpen, stringResource(R.string.lock_unlock)) { c.setVisibility(listOf(a), "timeline", Strings.str(R.string.moved_out_of_the_locked_folder)) }
                                IslandAction(Icons.Outlined.Info, stringResource(R.string.details)) { info = true }
                                IslandAction(Icons.Outlined.Delete, stringResource(R.string.lock_delete)) { confirmDelete = false }
                                IslandAction(Icons.Filled.MoreHoriz, stringResource(R.string.more)) { more = true }
                            }
                            else -> {
                                IslandAction(Icons.Outlined.Share, stringResource(R.string.share)) { shareFlow.start(c, listOf(a)) }
                                if (mine) {
                                    IslandAction(if (a.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (a.isFavorite) stringResource(R.string.unfavourite) else stringResource(R.string.favourite)) {
                                        c.setFavorite(listOf(a), !a.isFavorite)
                                    }
                                    if (a.isImage) IslandAction(Icons.Outlined.Crop, stringResource(R.string.edit)) { nav.navigate(EditorRoute(a.id)) }
                                }
                                IslandAction(Icons.Outlined.Info, stringResource(R.string.details)) { info = true }
                                if (mine) IslandAction(Icons.Outlined.Delete, stringResource(R.string.lock_delete)) {
                                    if (settings.confirmDelete) confirmDelete = false else c.trash(listOf(a), false)
                                }
                                IslandAction(Icons.Filled.MoreHoriz, stringResource(R.string.more)) { more = true }
                            }
                        }
                    }
                }
            }
        }
    }

    shareFlow.Dialog()

    val a = current
    if (info && a != null) {
        InfoSheet(
            a,
            onDismiss = { info = false },
            onEditDate = { info = false; pickDate = true },
            onEditLocation = { info = false; pickPlace = true },
        )
    }

    if (more && a != null) {
        ModalBottomSheet(
            onDismissRequest = { more = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(a.localDateTime.format(titleDate))
                fun act(block: () -> Unit) { block(); more = false }
                ActionItem(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_album)) { more = false; pickAlbum = true }
                ActionItem(Icons.Outlined.Download, stringResource(R.string.save_to_phone)) { act { c.download(listOf(a)) } }
                ActionItem(Icons.Outlined.Link, stringResource(R.string.share_a_link)) { more = false; linkOptions = true }
                ActionItem(Icons.Outlined.SmartDisplay, stringResource(R.string.slideshow)) { act { nav.navigate(SlideshowRoute(source.key, pager.currentPage)) } }
                if (a.isImage && features.ocr) ActionItem(Icons.Outlined.TextFields, stringResource(R.string.text_in_this_photo)) { act { overlay = Overlay.TEXT; ocrLines = null } }
                if (a.isImage) ActionItem(Icons.Outlined.Face, stringResource(R.string.people_in_this_photo)) { act { overlay = Overlay.FACES; faces = null } }
                if (mine) {
                    ActionItem(Icons.Outlined.Sell, stringResource(R.string.tag)) { more = false; pickTag = true }
                    ActionItem(Icons.Outlined.CalendarMonth, stringResource(R.string.change_date_and_time)) { more = false; pickDate = true }
                    ActionItem(Icons.Outlined.Place, stringResource(R.string.change_location)) { more = false; pickPlace = true }
                    if (a.visibility == "archive") {
                        ActionItem(Icons.Outlined.Unarchive, stringResource(R.string.unarchive)) { act { c.setVisibility(listOf(a), "timeline", Strings.str(R.string.moved_back_to_photos)) } }
                    } else if (!isLocked) {
                        ActionItem(Icons.Outlined.Archive, stringResource(R.string.archive)) { act { c.setVisibility(listOf(a), "archive", Strings.str(R.string.archived_2)) } }
                    }
                    if (!isLocked) ActionItem(Icons.Outlined.Lock, stringResource(R.string.move_to_locked_folder)) { act { c.setVisibility(listOf(a), "locked", Strings.str(R.string.moved_to_the_locked_folder)) } }
                }
                stack?.let { s ->
                    if (mine) {
                        if (s.primaryAssetId != a.id) ActionItem(Icons.Outlined.ViewCarousel, stringResource(R.string.show_this_on_top_of_the_stack)) {
                            act { c.runTask(Strings.str(R.string.stack_cover_changed)) { c.api.setStackPrimary(s.id, a.id); Sources.refreshAll() } }
                        }
                        ActionItem(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.take_out_of_the_stack)) {
                            act { c.runTask(Strings.str(R.string.removed_from_the_stack)) { c.api.removeFromStack(s.id, a.id); Sources.refreshAll() } }
                        }
                        ActionItem(Icons.Outlined.Collections, stringResource(R.string.unstack_all)) {
                            act { c.runTask(Strings.str(R.string.unstacked)) { c.api.deleteStack(s.id); Sources.refreshAll() } }
                        }
                    }
                }
                if (albumId != null) {
                    ActionItem(Icons.Outlined.Image, stringResource(R.string.use_as_album_cover)) {
                        act { c.runTask(Strings.str(R.string.album_cover_changed)) { c.api.updateAlbum(albumId, "albumThumbnailAssetId" to a.id); Sources.refreshAll() } }
                    }
                    ActionItem(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.remove_from_album)) { act { c.removeFromAlbum(albumId, listOf(a)) } }
                }
                if (a.onDevice) ActionItem(Icons.Outlined.PhoneAndroid, stringResource(R.string.delete_from_this_phone_only)) { act { c.deleteFromPhone(listOf(a)) } }
                if (mine) {
                    ActionItem(Icons.Outlined.Refresh, stringResource(R.string.refresh_metadata)) {
                        act { c.runTask(Strings.str(R.string.metadata_refresh_queued)) { c.api.runAssetJob(listOf(a.id), "refresh-metadata") } }
                    }
                    ActionItem(Icons.Outlined.Refresh, stringResource(R.string.regenerate_thumbnails)) {
                        act { c.runTask(Strings.str(R.string.thumbnails_queued)) { c.api.runAssetJob(listOf(a.id), "regenerate-thumbnail") } }
                    }
                    if (!isTrash) ActionItem(Icons.Outlined.DeleteForever, stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error) {
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
            if (id == null) {
                c.runTask {
                    val album = c.api.createAlbum(name, if (a.isRemote) listOf(a.id) else emptyList())
                    if (a.isLocalOnly) c.addToAlbum(album.id, name, listOf(a))
                    c.toast(Strings.str(R.string.created, name))
                    Sources.refreshAll()
                }
            } else c.addToAlbum(id, name, listOf(a))
        }
    }
    if (pickTag && a != null) TagPickerSheet(listOf(a), onDismiss = { pickTag = false })
    if (pickDate && a != null) {
        DateTimeDialog(a.localDateTime, onDismiss = { pickDate = false }) { iso ->
            pickDate = false
            c.setDate(listOf(a), iso)
        }
    }
    if (pickPlace && a != null) {
        LocationDialog(onDismiss = { pickPlace = false }) { lat, lon, _ ->
            pickPlace = false
            c.setLocation(listOf(a), lat, lon)
        }
    }
    if (linkOptions && a != null) {
        SharedLinkDialog(stringResource(R.string.share_a_link), allowUploadOption = false, onDismiss = { linkOptions = false }) { o ->
            linkOptions = false
            c.shareLink(listOf(a), o)
        }
    }

    confirmDelete?.let { permanent ->
        if (a == null) {
            confirmDelete = null
            return@let
        }
        if (permanent) {
            ConfirmDialog(
                stringResource(R.string.delete_permanently_3),
                stringResource(R.string.it_ll_be_removed_from_the_server_for),
                stringResource(R.string.lock_delete),
                onDismiss = { confirmDelete = null },
            ) { c.trash(listOf(a), true); confirmDelete = null }
        } else if (a.isLocalOnly) {
            ConfirmDialog(
                stringResource(R.string.delete_from_this_phone_2),
                stringResource(R.string.it_isn_t_backed_up_so_it_s_gone_for),
                stringResource(R.string.lock_delete),
                onDismiss = { confirmDelete = null },
            ) { c.trash(listOf(a), false); confirmDelete = null }
        } else {
            ConfirmDialog(
                stringResource(R.string.move_to_the_trash_2),
                stringResource(R.string.you_can_restore_it_from_the_trash_in, c.serverConfig.value.trashDays),
                stringResource(R.string.move_to_trash),
                onDismiss = { confirmDelete = null },
            ) { c.trash(listOf(a), false); confirmDelete = null }
        }
    }
}

@Composable
private fun IslandAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label) }
}
