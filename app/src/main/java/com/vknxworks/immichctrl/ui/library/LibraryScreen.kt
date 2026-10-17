package com.vknxworks.immichctrl.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AlbumDto
import com.vknxworks.immichctrl.data.api.albums
import com.vknxworks.immichctrl.data.api.createAlbum
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.LocalBottomClearance
import com.vknxworks.immichctrl.ui.components.RemoteImage
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.TabTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.nav.CollectionRoute
import com.vknxworks.immichctrl.ui.nav.FoldersRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.LockedRoute
import com.vknxworks.immichctrl.ui.nav.MapRoute
import com.vknxworks.immichctrl.ui.nav.PartnersRoute
import com.vknxworks.immichctrl.ui.nav.PeopleRoute
import com.vknxworks.immichctrl.ui.nav.PlacesRoute
import com.vknxworks.immichctrl.ui.nav.SharedLinksRoute
import com.vknxworks.immichctrl.ui.nav.TagsRoute
import com.vknxworks.immichctrl.ui.theme.Gutter

private enum class AlbumFilter(val label: String) { ALL("All"), MINE("Mine"), SHARED("Shared with me") }

/** The Library tab: collections, then albums. */
@Composable
fun LibraryScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val features by c.features.collectAsStateWithLifecycle()
    val albums = rememberLoader("albums") { c.api.albums().sortedByDescending { it.lastModifiedAssetTimestamp ?: it.updatedAt } }
    var filter by rememberSaveable { mutableStateOf(AlbumFilter.ALL) }
    var creating by rememberSaveable { mutableStateOf(false) }

    if (creating) {
        TextInputDialog("New album", "Album name", "Create", onDismiss = { creating = false }) { name ->
            creating = false
            c.runTask {
                val a = c.api.createAlbum(name)
                albums.reload(silent = true)
                nav.album(a.id)
            }
        }
    }

    PullToRefreshBox(isRefreshing = albums.refreshing, onRefresh = { albums.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = LocalBottomClearance.current + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            fullWidth("title") { TabTitle("Library", padded = false) }
            fullWidth("tiles") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileRow(
                        Tile(Icons.Outlined.FavoriteBorder, "Favourites") { nav.navigate(CollectionRoute("favorites")) },
                        Tile(Icons.Outlined.People, "People") { nav.navigate(PeopleRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Place, "Places") { nav.navigate(PlacesRoute) },
                        Tile(Icons.Outlined.Map, "Map") { nav.navigate(MapRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.VideoLibrary, "Videos") { nav.navigate(CollectionRoute("videos")) },
                        Tile(Icons.Outlined.NewReleases, "Recently added") { nav.navigate(CollectionRoute("recent")) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Archive, "Archive") { nav.navigate(CollectionRoute("archive")) },
                        Tile(Icons.Outlined.Lock, "Locked folder") { nav.navigate(LockedRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Link, "Shared links") { nav.navigate(SharedLinksRoute) },
                        Tile(Icons.Outlined.Group, "Partners") { nav.navigate(PartnersRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Sell, "Tags") { nav.navigate(TagsRoute) },
                        Tile(Icons.Outlined.Folder, "Folders") { nav.navigate(FoldersRoute()) },
                    )
                    if (features.trash) {
                        TileRow(Tile(Icons.Outlined.Delete, "Trash") { nav.navigate(CollectionRoute("trash")) }, null)
                    }
                }
            }
            fullWidth("albums-title") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Albums", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New album") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlbumFilter.entries.forEach { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
            }
            when (val s = albums.state) {
                is Load.Loading -> fullWidth("loading") { Box(Modifier.height(200.dp)) { LoadingView() } }
                is Load.Err -> fullWidth("error") { Box(Modifier.height(300.dp)) { ErrorView(s.message) { albums.reload() } } }
                is Load.Ok -> {
                    val shown = s.value.filter {
                        when (filter) {
                            AlbumFilter.ALL -> true
                            AlbumFilter.MINE -> it.ownerUserId == c.me
                            AlbumFilter.SHARED -> it.ownerUserId != c.me
                        }
                    }
                    if (shown.isEmpty()) {
                        fullWidth("empty") {
                            Text(
                                if (filter == AlbumFilter.SHARED) "No one has shared an album with you yet." else "No albums yet. Tap + to make one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(shown, key = { it.id }) { a -> AlbumCard(a) { nav.album(a.id) } }
                }
            }
        }
    }
}

/** All albums, or only the ones shared with you. */
@Composable
fun AlbumsScreen(shared: Boolean) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val albums = rememberLoader("albums-$shared") { c.api.albums().filter { if (shared) it.ownerUserId != c.me else true } }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(if (shared) "Shared with me" else "Albums")
        when (val s = albums.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { albums.reload() }
            is Load.Ok -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.value, key = { it.id }) { a -> AlbumCard(a) { nav.album(a.id) } }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private class Tile(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun TileRow(a: Tile, b: Tile?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileView(a, Modifier.weight(1f))
        if (b != null) TileView(b, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = t.onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(t.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Text(t.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AlbumCard(a: AlbumDto, onClick: () -> Unit) {
    val c = LocalContext.current.container
    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
            RemoteImage(
                a.albumThumbnailAssetId?.let { c.api.thumbnailUrl(it, "preview") },
                Modifier.fillMaxSize(),
                placeholderIcon = Icons.Outlined.PhotoAlbum,
            )
        }
        Text(
            a.albumName.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
        Text(
            listOfNotNull(
                countLabel(a.assetCount, "item"),
                if (a.ownerUserId != c.me) a.ownerUser?.name?.let { "by $it" } else if (a.shared) "Shared" else null,
                if (a.ownerUserId == c.me && !a.shared) shortDate(a.startDate) else null,
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
        )
    }
}
