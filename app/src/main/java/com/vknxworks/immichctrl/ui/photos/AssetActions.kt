package com.vknxworks.immichctrl.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Sell
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
import com.vknxworks.immichctrl.data.api.createTag
import com.vknxworks.immichctrl.data.api.deleteAssets
import com.vknxworks.immichctrl.data.api.removeFromAlbum
import com.vknxworks.immichctrl.data.api.restoreAssets
import com.vknxworks.immichctrl.data.api.tagAssets
import com.vknxworks.immichctrl.data.api.tags
import com.vknxworks.immichctrl.data.api.updateAssets
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

// ---------------------------------------------------------------------------------------------
// Actions (shared by grids and the viewer). Each updates the server, then every open grid.
// ---------------------------------------------------------------------------------------------

/** Only the owner can change an asset; partner and shared-album photos are skipped with a note. */
private fun AppContainer.own(assets: Collection<Asset>): Set<String> {
    val mine = assets.filter { it.ownerId.isEmpty() || it.ownerId == me }.mapTo(HashSet()) { it.id }
    val skipped = assets.size - mine.size
    if (skipped > 0) toast("${countLabel(skipped, "item")} belong to someone else and were left as they are")
    return mine
}

fun AppContainer.setFavorite(assets: Collection<Asset>, favorite: Boolean) {
    val ids = own(assets).ifEmpty { return }
    Sources.updateEverywhere(ids) { it.copy(isFavorite = favorite) }
    runTask {
        api.updateAssets(ids, "isFavorite" to favorite)
        Sources.refreshAll()
    }
}

/** visibility: timeline, archive, locked */
fun AppContainer.setVisibility(assets: Collection<Asset>, visibility: String, done: String) {
    val ids = own(assets).ifEmpty { return }
    runTask(done) {
        api.updateAssets(ids, "visibility" to visibility)
        Sources.removeEverywhere(ids)
        Sources.refreshAll()
    }
}

fun AppContainer.trash(assets: Collection<Asset>, permanently: Boolean) {
    val ids = own(assets).ifEmpty { return }
    runTask(if (permanently) "Deleted ${countLabel(ids.size, "item")}" else "Moved ${countLabel(ids.size, "item")} to the trash") {
        api.deleteAssets(ids, force = permanently || !features.value.trash)
        Sources.removeEverywhere(ids)
        Sources.refreshAll()
    }
}

fun AppContainer.restore(assets: Collection<Asset>) {
    val ids = assets.mapTo(HashSet()) { it.id }
    runTask("Restored ${countLabel(ids.size, "item")}") {
        api.restoreAssets(ids)
        Sources.removeEverywhere(ids)
        Sources.refreshAll()
    }
}

fun AppContainer.addToAlbum(albumId: String, albumName: String, assets: Collection<Asset>) {
    val ids = assets.map { it.id }
    runTask {
        val res = api.addToAlbum(albumId, ids)
        val added = res.count { it.success }
        toast(if (added == 0) "Already in $albumName" else "Added ${countLabel(added, "item")} to $albumName")
        Sources.refreshAll()
    }
}

fun AppContainer.removeFromAlbum(albumId: String, assets: Collection<Asset>) {
    val ids = assets.mapTo(HashSet()) { it.id }
    runTask("Removed from the album") {
        api.removeFromAlbum(albumId, ids)
        Sources["album:$albumId"]?.removeIds(ids)
    }
}

fun AppContainer.share(assets: Collection<Asset>) {
    toast(if (assets.size == 1) "Preparing to share…" else "Preparing ${assets.size} items to share…")
    runTask { Exporter.shareFiles(context, api, assets.map { it.id }) }
}

fun AppContainer.download(assets: Collection<Asset>) {
    runTask { val n = Exporter.download(context, api, assets.map { it.id }); toast("Saving ${countLabel(n, "item")} to Pictures/ImmichCtrl") }
}

fun AppContainer.shareLink(assets: Collection<Asset>) {
    runTask {
        val link = api.createSharedLink(assetIds = assets.map { it.id })
        Exporter.shareText(context, Exporter.sharedLinkUrl(api, serverConfig.value.externalDomain, link.key, link.slug))
    }
}

// ---------------------------------------------------------------------------------------------
// Selection bar
// ---------------------------------------------------------------------------------------------

/** Where a selection was made, which decides the actions offered. */
enum class SelectionContext { TIMELINE, ARCHIVE, TRASH, LOCKED, ALBUM, SHARED }

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
    var confirmDelete by remember { mutableStateOf<Boolean?>(null) }
    val assets = selection.assets
    fun done() = selection.clear()
    fun askDelete() {
        if (c.settings.settings.value.confirmDelete) confirmDelete = false
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
            IconButton(onClick = ::done) { Icon(Icons.Filled.Close, "Clear selection") }
            Text(
                "${selection.size} selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            when (context) {
                SelectionContext.TRASH -> {
                    BarAction(Icons.Outlined.RestoreFromTrash, "Restore") { c.restore(assets); done() }
                    BarAction(Icons.Outlined.DeleteForever, "Delete") { confirmDelete = true }
                }
                SelectionContext.LOCKED -> {
                    BarAction(Icons.Outlined.LockOpen, "Unlock") { c.setVisibility(assets, "timeline", "Moved out of the locked folder"); done() }
                    BarAction(Icons.Outlined.Download, "Save") { c.download(assets); done() }
                    BarAction(Icons.Outlined.Delete, "Delete") { askDelete() }
                }
                else -> {
                    BarAction(Icons.Outlined.Share, "Share") { c.share(assets); done() }
                    val allFav = assets.isNotEmpty() && assets.all { it.isFavorite }
                    BarAction(if (allFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (allFav) "Unfavourite" else "Favourite") {
                        c.setFavorite(assets, !allFav); done()
                    }
                    BarAction(Icons.Outlined.LibraryAdd, "Add to album") { pickAlbum = true }
                    BarAction(Icons.Outlined.Delete, "Delete") { askDelete() }
                    IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More") }
                }
            }
        }
    }

    if (more) {
        ModalBottomSheet(
            onDismissRequest = { more = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(countLabel(assets.size, "item") + " selected")
                fun act(block: () -> Unit) { block(); more = false; done() }
                if (context == SelectionContext.ARCHIVE) {
                    ActionItem(Icons.Outlined.Unarchive, "Unarchive") { act { c.setVisibility(assets, "timeline", "Moved back to Photos") } }
                } else {
                    ActionItem(Icons.Outlined.Archive, "Archive") { act { c.setVisibility(assets, "archive", "Archived ${countLabel(assets.size, "item")}") } }
                }
                ActionItem(Icons.Outlined.Download, "Save to phone") { act { c.download(assets) } }
                ActionItem(Icons.Outlined.Link, "Share a link") { act { c.shareLink(assets) } }
                ActionItem(Icons.Outlined.Sell, "Tag") { more = false; pickTag = true }
                ActionItem(Icons.Outlined.Lock, "Move to locked folder") {
                    act { c.setVisibility(assets, "locked", "Moved to the locked folder") }
                }
                if (context == SelectionContext.ALBUM && albumId != null && canEditAlbum) {
                    ActionItem(Icons.Outlined.RemoveCircleOutline, "Remove from album") { act { c.removeFromAlbum(albumId, assets) } }
                }
                ActionItem(Icons.Outlined.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error) {
                    more = false
                    confirmDelete = true
                }
            }
        }
    }

    if (pickAlbum) {
        AlbumPickerSheet(onDismiss = { pickAlbum = false }) { id, name ->
            pickAlbum = false
            if (id == null) c.runTask { c.api.createAlbum(name, assets.map { it.id }); c.toast("Created $name"); Sources.refreshAll() }
            else c.addToAlbum(id, name, assets)
            done()
        }
    }

    if (pickTag) {
        TagPickerSheet(assets, onDismiss = { pickTag = false; done() })
    }

    confirmDelete?.let { permanent ->
        val n = countLabel(assets.size, "item")
        if (permanent) {
            ConfirmDialog(
                "Delete $n permanently?",
                "They'll be removed from the server for good. This can't be undone.",
                "Delete",
                onDismiss = { confirmDelete = null },
            ) { c.trash(assets, true); confirmDelete = null; done() }
        } else {
            ConfirmDialog(
                "Move $n to the trash?",
                "You can restore them from the trash in Library for ${c.serverConfig.value.trashDays} days.",
                "Move to trash",
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
        TextInputDialog("New album", "Album name", "Create", onDismiss = { naming = false }) { name ->
            naming = false
            onPick(null, name)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SheetTitle("Add to album")
        ActionItem(Icons.Filled.Add, "New album") { naming = true }
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
            Text(a.albumName.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                countLabel(a.assetCount, "item") + if (a.shared) "  ·  Shared" else "",
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
        TextInputDialog("New tag", "Tag (use / for nested tags)", "Create", onDismiss = { naming = false }) { name ->
            naming = false
            onDismiss()
            c.runTask("Tagged ${countLabel(ids.size, "item")} $name") {
                val tag = c.api.createTag(name)
                c.api.tagAssets(listOf(tag.id), ids)
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SheetTitle("Tag ${countLabel(ids.size, "item")}")
        ActionItem(Icons.Filled.Add, "New tag") { naming = true }
        when (val s = tags.state) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            is Load.Ok -> LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(s.value, key = { it.id }) { t ->
                    ActionItem(Icons.Outlined.Sell, t.value.ifBlank { t.name }) {
                        onDismiss()
                        c.runTask("Tagged ${countLabel(ids.size, "item")} ${t.name}") { c.api.tagAssets(listOf(t.id), ids) }
                    }
                }
            }
        }
    }
}
