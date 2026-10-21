package com.vknxworks.immichctrl.ui.photos

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.AppContainer
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AlbumDto
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.addToAlbum
import com.vknxworks.immichctrl.data.api.albums
import com.vknxworks.immichctrl.data.api.createAlbum
import com.vknxworks.immichctrl.data.api.createSharedLink
import com.vknxworks.immichctrl.data.api.createStack
import com.vknxworks.immichctrl.data.api.createTag
import com.vknxworks.immichctrl.data.api.deleteAssets
import com.vknxworks.immichctrl.data.api.removeFromAlbum
import com.vknxworks.immichctrl.data.api.restoreAssets
import com.vknxworks.immichctrl.data.api.tagAssets
import com.vknxworks.immichctrl.data.api.tags
import com.vknxworks.immichctrl.data.api.updateAssets
import com.vknxworks.immichctrl.data.backup.LocalLibrary
import com.vknxworks.immichctrl.data.backup.LocalMedia
import com.vknxworks.immichctrl.data.settings.ShareSize
import com.vknxworks.immichctrl.data.share.Exporter
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.RemoteImage
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.glass
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.toast
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

// ---------------------------------------------------------------------------------------------
// Actions (shared by grids and the viewer). Each updates the server, then every open grid.
// ---------------------------------------------------------------------------------------------

/**
 * Server assets the signed-in user owns. Partner and shared-album photos are skipped with a note;
 * photos only on this phone are left out (they have their own actions).
 */
private fun AppContainer.own(assets: Collection<Asset>): Set<String> {
    val remote = assets.filter { it.isRemote }
    val mine = remote.filter { it.ownerId.isEmpty() || it.ownerId == me }.mapTo(HashSet()) { it.id }
    val skipped = remote.size - mine.size
    if (skipped > 0) toast(Strings.str(R.string.belong_to_someone_else_and_were_left, countLabel(skipped, R.plurals.count_items)))
    if (remote.size < assets.size && mine.isEmpty()) toast(Strings.str(R.string.photos_only_on_this_phone_need))
    return mine
}

/** content:// addresses of the phone's copies of these assets (phone-only or backed up from here). */
private fun phoneUris(assets: Collection<Asset>): List<Uri> = assets.mapNotNull { a ->
    if (a.isLocalOnly) a.localUri?.let(Uri::parse)
    else a.localId?.let { LocalMedia.contentUri(it, a.isVideo) }
}

fun AppContainer.setFavorite(assets: Collection<Asset>, favorite: Boolean) {
    val ids = own(assets).ifEmpty { return }
    Sources.updateEverywhere(ids) { it.copy(isFavorite = favorite) }
    runTask {
        api.updateAssets(ids, "isFavorite" to favorite)
        Sources.refreshAll()
    }
}

/** visibility: timeline, archive, locked. Moving to the locked folder also removes the phone's copies. */
fun AppContainer.setVisibility(assets: Collection<Asset>, visibility: String, done: String) {
    val ids = own(assets).ifEmpty { return }
    val onPhone = if (visibility == "locked") phoneUris(assets.filter { it.id in ids && it.onDevice }) else emptyList()
    runTask(done) {
        api.updateAssets(ids, "visibility" to visibility)
        Sources.removeEverywhere(ids)
        Sources.refreshAll()
        // The phone's trash, not a permanent delete: the phone copy may have been edited since it was backed up.
        if (onPhone.isNotEmpty() && device.trash(onPhone)) local.changed()
    }
}

/** Server photos go to Immich's trash (or are deleted); phone-only photos go to the phone's trash. */
fun AppContainer.trash(assets: Collection<Asset>, permanently: Boolean) {
    val localOnly = assets.filter { it.isLocalOnly }
    val ids = if (localOnly.size == assets.size) emptySet() else own(assets)
    runTask {
        if (ids.isNotEmpty()) {
            api.deleteAssets(ids, force = permanently || !features.value.trash)
            Sources.removeEverywhere(ids)
            Sources.refreshAll()
            toast(if (permanently) Strings.str(R.string.deleted, countLabel(ids.size, R.plurals.count_items)) else Strings.str(R.string.moved_to_the_trash, countLabel(ids.size, R.plurals.count_items)))
        }
        if (localOnly.isNotEmpty()) {
            val uris = phoneUris(localOnly)
            // Not backed up: always the phone's trash, even from "Delete permanently" (which is about the server).
            val ok = device.trash(uris)
            if (ok) {
                Sources.removeEverywhere(localOnly.mapTo(HashSet()) { it.id })
                local.changed()
            }
        }
    }
}

/** Removes the phone's copy of photos that are backed up (Free Up Space for a few photos). */
fun AppContainer.deleteFromPhone(assets: Collection<Asset>) {
    val backedUp = assets.filter { it.onDevice && it.localId != null }
    if (backedUp.isEmpty()) {
        toast(Strings.str(R.string.these_aren_t_backed_up_from_this))
        return
    }
    runTask {
        if (device.trash(phoneUris(backedUp))) {
            toast(Strings.str(R.string.removed_from_this_phone, countLabel(backedUp.size, R.plurals.count_items)))
            local.changed()
            Sources.refreshAll()
        }
    }
}

/** Uploads photos that are only on this phone. */
fun AppContainer.upload(assets: Collection<Asset>) {
    val ids = assets.filter { it.isLocalOnly }.mapNotNull { LocalLibrary.localIdOf(it) }
    if (ids.isEmpty()) return
    backup.uploadLocal(ids)
    toast(Strings.str(R.string.uploading, countLabel(ids.size, R.plurals.count_items)))
}

fun AppContainer.restore(assets: Collection<Asset>) {
    val ids = assets.mapTo(HashSet()) { it.id }
    runTask(Strings.str(R.string.restored_2, countLabel(ids.size, R.plurals.count_items))) {
        api.restoreAssets(ids)
        Sources.removeEverywhere(ids)
        Sources.refreshAll()
    }
}

/** Server photos are added; photos only on this phone are uploaded straight into the album. */
fun AppContainer.addToAlbum(albumId: String, albumName: String, assets: Collection<Asset>) {
    val remote = assets.filter { it.isRemote }.map { it.id }
    val phone = phoneUris(assets.filter { it.isLocalOnly })
    runTask {
        if (remote.isNotEmpty()) {
            val res = api.addToAlbum(albumId, remote)
            val added = res.count { it.success }
            toast(if (added == 0) Strings.str(R.string.already_in, albumName) else Strings.str(R.string.added_to, countLabel(added, R.plurals.count_items), albumName))
        }
        if (phone.isNotEmpty()) {
            val n = backup.uploadShared(phone, albumId)
            toast(Strings.str(R.string.uploading_to, countLabel(n, R.plurals.count_items), albumName))
        }
        Sources.refreshAll()
    }
}

fun AppContainer.removeFromAlbum(albumId: String, assets: Collection<Asset>) {
    val ids = assets.mapTo(HashSet()) { it.id }
    runTask(Strings.str(R.string.removed_from_the_album)) {
        api.removeFromAlbum(albumId, ids)
        Sources["album:$albumId"]?.removeIds(ids)
    }
}

fun AppContainer.share(assets: Collection<Asset>, large: Boolean) {
    val remote = assets.filter { it.isRemote }.map { it.id }
    val phone = phoneUris(assets.filter { it.isLocalOnly })
    if (remote.isNotEmpty()) toast(if (assets.size == 1) Strings.str(R.string.preparing_to_share) else Strings.str(R.string.preparing_items_to_share, assets.size))
    val strip = settings.settings.value.shareStripLocation
    runTask { Exporter.shareFiles(context, api, remote, large, phone, stripLocation = strip) }
}

fun AppContainer.download(assets: Collection<Asset>) {
    val remote = assets.filter { it.isRemote }
    if (remote.isEmpty()) return
    toast(Strings.str(R.string.saving_to_pictures_immichctrl, countLabel(remote.size, R.plurals.count_items)))
    runTask {
        val n = Exporter.download(context, api, remote.map { it.id })
        toast(if (n == 0) Strings.str(R.string.couldn_t_save_to_the_phone) else Strings.str(R.string.saved_to_pictures_immichctrl, countLabel(n, R.plurals.count_items)))
    }
}

fun AppContainer.shareLink(assets: Collection<Asset>, options: LinkOptions = LinkOptions()) {
    val remote = assets.filter { it.isRemote }
    if (remote.isEmpty()) return
    runTask {
        val link = api.createSharedLink(
            assetIds = remote.map { it.id },
            description = options.description.ifBlank { null },
            expiresAt = options.expiresAt(),
            allowDownload = options.allowDownload,
            showMetadata = options.showMetadata,
            password = options.password,
        )
        Exporter.shareText(context, Exporter.sharedLinkUrl(api, serverConfig.value.externalDomain, link.key, link.slug))
    }
}

/** Groups photos into a stack; the first one shows on top. */
fun AppContainer.stack(assets: List<Asset>) {
    val ids = own(assets)
    if (ids.size < 2) {
        toast(Strings.str(R.string.pick_at_least_two_of_your_photos_to))
        return
    }
    runTask(Strings.str(R.string.stacked, countLabel(ids.size, R.plurals.count_photos))) {
        api.createStack(assets.map { it.id }.filter { it in ids })
        Sources.refreshAll()
    }
}

fun AppContainer.setDate(assets: Collection<Asset>, iso: String) {
    val ids = own(assets).ifEmpty { return }
    runTask(Strings.str(R.string.date_changed)) {
        api.updateAssets(ids, "dateTimeOriginal" to iso)
        Sources.refreshAll()
    }
}

fun AppContainer.setLocation(assets: Collection<Asset>, lat: Double, lon: Double) {
    val ids = own(assets).ifEmpty { return }
    runTask(Strings.str(R.string.location_changed)) {
        api.updateAssets(ids, "latitude" to lat, "longitude" to lon)
        Sources.refreshAll()
    }
}

/** Sharing, asking original-or-smaller first when that's the setting. Show [Dialog] in composition. */
class ShareFlow {
    var pending by mutableStateOf<List<Asset>?>(null)

    fun start(c: AppContainer, assets: List<Asset>) {
        when (c.settings.settings.value.shareSize) {
            ShareSize.ASK -> if (assets.any { it.isRemote && it.isImage }) pending = assets else c.share(assets, false)
            ShareSize.ORIGINAL -> c.share(assets, false)
            ShareSize.LARGE -> c.share(assets, true)
        }
    }

    @Composable
    fun Dialog(onDone: () -> Unit = {}) {
        val c = LocalContext.current.container
        val list = pending ?: return
        ShareSizeDialog(onDismiss = { pending = null }) { large, keep ->
            pending = null
            if (keep) c.appScope.launch { c.settings.update { it.copy(shareSize = if (large) ShareSize.LARGE else ShareSize.ORIGINAL) } }
            c.share(list, large)
            onDone()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Selection bar
// ---------------------------------------------------------------------------------------------

/** Where a selection was made, which decides the actions offered. */
enum class SelectionContext { TIMELINE, ARCHIVE, TRASH, LOCKED, ALBUM, SHARED, LOCAL }

/** Floating glass bar shown while photos are selected. */
@Composable
fun SelectionBar(
    selection: Selection,
    context: SelectionContext,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    canEditAlbum: Boolean = false,
) {
    val c = LocalContext.current.container
    var more by remember { mutableStateOf(false) }
    var pickAlbum by remember { mutableStateOf(false) }
    var pickTag by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickPlace by remember { mutableStateOf(false) }
    var linkOptions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Boolean?>(null) }
    val shareFlow = remember { ShareFlow() }
    val assets = selection.assets
    val anyLocal = assets.any { it.isLocalOnly }
    val anyRemote = assets.any { it.isRemote }
    val anyOnPhone = assets.any { it.onDevice }
    fun done() = selection.clear()
    fun askDelete() {
        if (c.settings.settings.value.confirmDelete || (anyLocal && !anyRemote)) confirmDelete = false
        else {
            c.trash(assets, false)
            done()
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .glass(MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::done) { Icon(Icons.Filled.Close, stringResource(R.string.clear_selection)) }
            Text(
                stringResource(R.string.selected_2, selection.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            when (context) {
                SelectionContext.TRASH -> {
                    BarAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.restore)) { c.restore(assets); done() }
                    BarAction(Icons.Outlined.DeleteForever, stringResource(R.string.lock_delete)) { confirmDelete = true }
                }
                SelectionContext.LOCKED -> {
                    BarAction(Icons.Outlined.LockOpen, stringResource(R.string.lock_unlock)) { c.setVisibility(assets, "timeline", Strings.str(R.string.moved_out_of_the_locked_folder)); done() }
                    BarAction(Icons.Outlined.Download, stringResource(R.string.save)) { c.download(assets); done() }
                    BarAction(Icons.Outlined.Delete, stringResource(R.string.lock_delete)) { askDelete() }
                }
                else -> {
                    if (anyLocal) BarAction(Icons.Outlined.CloudUpload, stringResource(R.string.upload)) { c.upload(assets); done() }
                    BarAction(Icons.Outlined.Share, stringResource(R.string.share)) { shareFlow.start(c, assets) }
                    if (anyRemote) {
                        val allFav = assets.all { it.isFavorite }
                        BarAction(if (allFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (allFav) stringResource(R.string.unfavourite) else stringResource(R.string.favourite)) {
                            c.setFavorite(assets, !allFav); done()
                        }
                    }
                    BarAction(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_album)) { pickAlbum = true }
                    BarAction(Icons.Outlined.Delete, stringResource(R.string.lock_delete)) { askDelete() }
                    if (anyRemote || anyOnPhone) IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                }
            }
        }
    }

    shareFlow.Dialog(onDone = ::done)

    if (more) {
        ModalBottomSheet(
            onDismissRequest = { more = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(stringResource(R.string.count_selected, countLabel(assets.size, R.plurals.count_items)))
                fun act(block: () -> Unit) { block(); more = false; done() }
                if (anyRemote) {
                    if (context == SelectionContext.ARCHIVE) {
                        ActionItem(Icons.Outlined.Unarchive, stringResource(R.string.unarchive)) { act { c.setVisibility(assets, "timeline", Strings.str(R.string.moved_back_to_photos)) } }
                    } else {
                        ActionItem(Icons.Outlined.Archive, stringResource(R.string.archive)) { act { c.setVisibility(assets, "archive", Strings.str(R.string.archived, countLabel(assets.size, R.plurals.count_items))) } }
                    }
                    ActionItem(Icons.Outlined.Download, stringResource(R.string.save_to_phone)) { act { c.download(assets) } }
                    ActionItem(Icons.Outlined.Link, stringResource(R.string.share_a_link)) { more = false; linkOptions = true }
                    ActionItem(Icons.Outlined.Sell, stringResource(R.string.tag)) { more = false; pickTag = true }
                    ActionItem(Icons.Outlined.Collections, stringResource(R.string.stack)) { act { c.stack(assets) } }
                    ActionItem(Icons.Outlined.CalendarMonth, stringResource(R.string.change_date_and_time)) { more = false; pickDate = true }
                    ActionItem(Icons.Outlined.Place, stringResource(R.string.change_location)) { more = false; pickPlace = true }
                    ActionItem(Icons.Outlined.Lock, stringResource(R.string.move_to_locked_folder)) {
                        act { c.setVisibility(assets, "locked", Strings.str(R.string.moved_to_the_locked_folder)) }
                    }
                }
                if (anyOnPhone) {
                    ActionItem(Icons.Outlined.PhoneAndroid, stringResource(R.string.delete_from_this_phone_only)) { act { c.deleteFromPhone(assets) } }
                }
                if (context == SelectionContext.ALBUM && albumId != null && canEditAlbum) {
                    ActionItem(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.remove_from_album)) { act { c.removeFromAlbum(albumId, assets) } }
                }
                if (anyRemote) {
                    ActionItem(Icons.Outlined.DeleteForever, stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error) {
                        more = false
                        confirmDelete = true
                    }
                }
            }
        }
    }

    if (pickAlbum) {
        AlbumPickerSheet(onDismiss = { pickAlbum = false }) { id, name ->
            pickAlbum = false
            val list = assets
            if (id == null) {
                c.runTask {
                    val remote = list.filter { it.isRemote }.map { it.id }
                    val album = c.api.createAlbum(name, remote)
                    c.toast(Strings.str(R.string.created, name))
                    if (list.any { it.isLocalOnly }) c.addToAlbum(album.id, name, list.filter { it.isLocalOnly })
                    Sources.refreshAll()
                }
            } else {
                c.addToAlbum(id, name, list)
            }
            done()
        }
    }

    if (pickTag) TagPickerSheet(assets.filter { it.isRemote }, onDismiss = { pickTag = false; done() })

    if (pickDate) {
        val first = assets.firstOrNull()?.localDateTime ?: LocalDateTime.now()
        DateTimeDialog(first, onDismiss = { pickDate = false }) { iso ->
            pickDate = false
            c.setDate(assets, iso)
            done()
        }
    }
    if (pickPlace) {
        LocationDialog(onDismiss = { pickPlace = false }) { lat, lon, _ ->
            pickPlace = false
            c.setLocation(assets, lat, lon)
            done()
        }
    }
    if (linkOptions) {
        SharedLinkDialog(stringResource(R.string.share_a_link), allowUploadOption = false, onDismiss = { linkOptions = false }) { o ->
            linkOptions = false
            c.shareLink(assets, o)
            done()
        }
    }

    confirmDelete?.let { permanent ->
        val n = countLabel(assets.size, R.plurals.count_items)
        if (permanent) {
            ConfirmDialog(
                stringResource(R.string.delete_permanently_2, n),
                stringResource(R.string.they_ll_be_removed_from_the_server),
                stringResource(R.string.lock_delete),
                onDismiss = { confirmDelete = null },
            ) { c.trash(assets, true); confirmDelete = null; done() }
        } else {
            val onlyPhone = anyLocal && !anyRemote
            ConfirmDialog(
                if (onlyPhone) stringResource(R.string.delete_from_this_phone, n) else stringResource(R.string.move_to_the_trash, n),
                if (onlyPhone) stringResource(R.string.they_aren_t_backed_up_so_this)
                else stringResource(if (anyLocal) R.string.trash_restore_days_local else R.string.trash_restore_days, c.serverConfig.value.trashDays),
                if (onlyPhone) stringResource(R.string.lock_delete) else stringResource(R.string.move_to_trash),
                onDismiss = { confirmDelete = null },
            ) { c.trash(assets, false); confirmDelete = null; done() }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label) }
}

// ---------------------------------------------------------------------------------------------
// Album and tag pickers
// ---------------------------------------------------------------------------------------------

/** Pick an album you can add to, or make a new one (album id null, the typed name). */
@Composable
fun AlbumPickerSheet(onDismiss: () -> Unit, onPick: (id: String?, name: String) -> Unit) {
    val c = LocalContext.current.container
    val albums = rememberLoader("album-picker") {
        c.api.albums().filter { a -> a.ownerUserId == c.me || a.albumUsers.any { it.user.id == c.me && it.role == "editor" } }
            .sortedByDescending { it.lastModifiedAssetTimestamp ?: it.updatedAt }
    }
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TextInputDialog(stringResource(R.string.new_album), stringResource(R.string.album_name), stringResource(R.string.create), onDismiss = { naming = false }) { name ->
            naming = false
            onPick(null, name)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SheetTitle(stringResource(R.string.add_to_album))
        ActionItem(Icons.Filled.Add, stringResource(R.string.new_album)) { naming = true }
        when (val s = albums.state) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            is Load.Ok -> LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(s.value, key = { it.id }) { a -> AlbumPickRow(a) { onPick(a.id, a.albumName) } }
            }
        }
    }
}

@Composable
private fun AlbumPickRow(a: AlbumDto, onClick: () -> Unit) {
    val c = LocalContext.current.container
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) {
            RemoteImage(a.albumThumbnailAssetId?.let { c.api.thumbnailUrl(it) }, Modifier.fillMaxSize(), placeholderIcon = Icons.Outlined.PhotoAlbum)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.albumName.ifBlank { Strings.str(R.string.untitled) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (a.shared) stringResource(R.string.count_shared, countLabel(a.assetCount, R.plurals.count_items)) else countLabel(a.assetCount, R.plurals.count_items),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TagPickerSheet(assets: List<Asset>, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val tags = rememberLoader("tag-picker") { c.api.tags().sortedBy { it.value.lowercase() } }
    var naming by remember { mutableStateOf(false) }
    val ids = assets.map { it.id }
    if (naming) {
        TextInputDialog(stringResource(R.string.new_tag), stringResource(R.string.tag_use_for_nested_tags), stringResource(R.string.create), onDismiss = { naming = false }) { name ->
            naming = false
            onDismiss()
            c.runTask(Strings.str(R.string.tagged, countLabel(ids.size, R.plurals.count_items), name)) {
                val tag = c.api.createTag(name)
                c.api.tagAssets(listOf(tag.id), ids)
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SheetTitle(stringResource(R.string.tag_2, countLabel(ids.size, R.plurals.count_items)))
        ActionItem(Icons.Filled.Add, stringResource(R.string.new_tag)) { naming = true }
        when (val s = tags.state) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            is Load.Ok -> LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(s.value, key = { it.id }) { t ->
                    ActionItem(Icons.Outlined.Sell, t.value.ifBlank { t.name }) {
                        onDismiss()
                        c.runTask(Strings.str(R.string.tagged, countLabel(ids.size, R.plurals.count_items), t.name)) { c.api.tagAssets(listOf(t.id), ids) }
                    }
                }
            }
        }
    }
}
