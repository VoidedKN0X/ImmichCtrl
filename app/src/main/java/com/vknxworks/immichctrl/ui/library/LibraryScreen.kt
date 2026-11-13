package com.vknxworks.immichctrl.ui.library

import com.vknxworks.immichctrl.ui.theme.CtrlType
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.components.CtrlLabel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import com.vknxworks.immichctrl.ui.components.ctrlFieldColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import com.vknxworks.immichctrl.data.api.partners
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.vknxworks.immichctrl.data.settings.AlbumSort
import com.vknxworks.immichctrl.ui.nav.DuplicatesRoute
import com.vknxworks.immichctrl.ui.nav.LocalAlbumsRoute
import com.vknxworks.immichctrl.ui.nav.OfflineAlbumRoute
import kotlinx.coroutines.launch
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
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

private enum class AlbumFilter(val label: String) { ALL(Strings.str(R.string.all)), MINE(Strings.str(R.string.mine)), SHARED(Strings.str(R.string.shared_with_me)) }

/** The Library tab: collections, then albums. */
@Composable
fun LibraryScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val features by c.features.collectAsStateWithLifecycle()
    val albums = rememberLoader("albums") { c.api.albums().sortedByDescending { it.lastModifiedAssetTimestamp ?: it.updatedAt } }
    var filter by rememberSaveable { mutableStateOf(AlbumFilter.ALL) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var sorting by remember { mutableStateOf(false) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val offline by c.offline.albums.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val partners = rememberLoader("partners-with") { c.api.partners("shared-with") }
    val partnersWithMe = partners.value.orEmpty()

    if (creating) {
        TextInputDialog(stringResource(R.string.new_album), stringResource(R.string.album_name), stringResource(R.string.create), onDismiss = { creating = false }) { name ->
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
            fullWidth("title") { TabTitle(stringResource(R.string.library), padded = false) }
            if (partnersWithMe.isNotEmpty()) fullWidth("partners") { PartnerRow(partnersWithMe) }
            fullWidth("tiles") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileRow(
                        Tile(Icons.Outlined.FavoriteBorder, Strings.str(R.string.favourites)) { nav.navigate(CollectionRoute("favorites")) },
                        Tile(Icons.Outlined.People, Strings.str(R.string.people)) { nav.navigate(PeopleRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Place, Strings.str(R.string.places)) { nav.navigate(PlacesRoute) },
                        Tile(Icons.Outlined.Map, Strings.str(R.string.map)) { nav.navigate(MapRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.VideoLibrary, Strings.str(R.string.videos)) { nav.navigate(CollectionRoute("videos")) },
                        Tile(Icons.Outlined.NewReleases, Strings.str(R.string.recently_added)) { nav.navigate(CollectionRoute("recent")) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Archive, Strings.str(R.string.archive)) { nav.navigate(CollectionRoute("archive")) },
                        Tile(Icons.Outlined.Lock, Strings.str(R.string.locked_folder)) { nav.navigate(LockedRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Link, Strings.str(R.string.shared_links)) { nav.navigate(SharedLinksRoute) },
                        Tile(Icons.Outlined.Group, Strings.str(R.string.partners)) { nav.navigate(PartnersRoute) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.Sell, Strings.str(R.string.tags)) { nav.navigate(TagsRoute) },
                        Tile(Icons.Outlined.Folder, Strings.str(R.string.folders)) { nav.navigate(FoldersRoute()) },
                    )
                    TileRow(
                        Tile(Icons.Outlined.PhoneAndroid, Strings.str(R.string.on_this_device)) { nav.navigate(LocalAlbumsRoute) },
                        if (features.trash) Tile(Icons.Outlined.Delete, Strings.str(R.string.trash)) { nav.navigate(CollectionRoute("trash")) } else null,
                    )
                    if (features.duplicateDetection) {
                        TileRow(Tile(Icons.Outlined.ContentCopy, Strings.str(R.string.duplicates)) { nav.navigate(DuplicatesRoute) }, null)
                    }
                }
            }
            fullWidth("albums-title") {
                Column {
                    if (offline.isNotEmpty()) {
                        CtrlLabel(stringResource(R.string.available_offline), Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp))
                        offline.forEachIndexed { i, o ->
                            if (i > 0) com.vknxworks.immichctrl.ui.components.CtrlDivider()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { nav.navigate(OfflineAlbumRoute(o.albumId)) }.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.OfflinePin, null, Modifier.size(20.dp), tint = CtrlColors.AccentText)
                                Text(o.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (o.complete) countLabel(o.count, R.plurals.count_items) else stringResource(R.string.saving)).uppercase(),
                                    style = CtrlType.Mono,
                                    color = if (o.complete) CtrlColors.AccentText else CtrlColors.Stopped,
                                )
                                Spacer(Modifier.width(10.dp))
                                com.vknxworks.immichctrl.ui.components.CtrlDot(if (o.complete) CtrlColors.AccentText else CtrlColors.Stopped)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.albums), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        albums.value?.let { Text("${it.size}", style = CtrlType.Mono, color = CtrlColors.Muted, modifier = Modifier.padding(end = 4.dp)) }
                        IconButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort)) }
                        IconButton(onClick = { scope.launch { c.settings.update { it.copy(albumGrid = !it.albumGrid) } } }) {
                            Icon(if (settings.albumGrid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, if (settings.albumGrid) stringResource(R.string.list) else stringResource(R.string.grid))
                        }
                        if (!settings.readOnly) IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.new_album)) }
                    }
                    OutlinedTextField(
                        search, { search = it },
                        placeholder = { Text(stringResource(R.string.find_an_album)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = CtrlShapes.Field, colors = ctrlFieldColors(),
                    )
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
                        } && (search.isBlank() || it.albumName.contains(search.trim(), ignoreCase = true))
                    }.let { list -> sortAlbums(list, settings.albumSort) }
                    if (shown.isEmpty()) {
                        fullWidth("empty") {
                            Text(
                                if (filter == AlbumFilter.SHARED) stringResource(R.string.no_one_has_shared_an_album_with_you) else stringResource(R.string.no_albums_yet_tap_to_make_one),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    if (settings.albumGrid) {
                        items(shown, key = { it.id }) { a -> AlbumCard(a) { nav.album(a.id) } }
                    } else {
                        items(shown, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { a -> AlbumRow(a) { nav.album(a.id) } }
                    }
                }
            }
        }
    }
    if (sorting) {
        com.vknxworks.immichctrl.ui.components.ChoiceSheet(
            stringResource(R.string.sort_albums),
            AlbumSort.entries.map { it to it.label },
            settings.albumSort,
            onDismiss = { sorting = false },
        ) { v ->
            sorting = false
            scope.launch { c.settings.update { it.copy(albumSort = v) } }
        }
    }
}

private fun sortAlbums(list: List<AlbumDto>, sort: AlbumSort): List<AlbumDto> = when (sort) {
    AlbumSort.ACTIVITY -> list.sortedByDescending { it.lastModifiedAssetTimestamp ?: it.updatedAt }
    AlbumSort.NAME -> list.sortedBy { it.albumName.lowercase() }
    AlbumSort.CREATED -> list.sortedByDescending { it.createdAt }
    AlbumSort.NEWEST -> list.sortedByDescending { it.endDate ?: "" }
    AlbumSort.OLDEST -> list.sortedBy { it.startDate ?: "9999" }
    AlbumSort.COUNT -> list.sortedByDescending { it.assetCount }
}

@Composable
private fun AlbumRow(a: AlbumDto, onClick: () -> Unit) {
    val c = LocalContext.current.container
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))) {
            RemoteImage(a.albumThumbnailAssetId?.let { c.api.thumbnailUrl(it) }, Modifier.fillMaxSize(), placeholderIcon = Icons.Outlined.PhotoAlbum)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(a.albumName.ifBlank { Strings.str(R.string.untitled) }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    countLabel(a.assetCount, R.plurals.count_items),
                    if (a.ownerUserId != c.me) a.ownerUser?.name?.let { Strings.str(R.string.by, it) } else if (a.shared) Strings.str(R.string.shared) else null,
                    shortDate(a.startDate),
                ).joinToString(Strings.str(R.string.list_separator_wide)),
                style = CtrlType.Mono,
                color = CtrlColors.Muted,
                maxLines = 1,
            )
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
        ScreenTopBar(if (shared) stringResource(R.string.shared_with_me) else stringResource(R.string.albums))
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
    Row(
        modifier
            .clip(CtrlShapes.Button)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Button)
            .clickable(onClick = t.onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(t.icon, null, Modifier.size(20.dp), tint = CtrlColors.AccentText)
        Spacer(Modifier.width(12.dp))
        Text(t.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AlbumCard(a: AlbumDto, onClick: () -> Unit) {
    val c = LocalContext.current.container
    Column(
        Modifier
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
            RemoteImage(
                a.albumThumbnailAssetId?.let { c.api.thumbnailUrl(it, "preview") },
                Modifier.fillMaxSize(),
                placeholderIcon = Icons.Outlined.PhotoAlbum,
            )
        }
        Text(
            a.albumName.ifBlank { Strings.str(R.string.untitled) },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
        )
        Text(
            listOfNotNull(
                countLabel(a.assetCount, R.plurals.count_items),
                if (a.ownerUserId != c.me) a.ownerUser?.name?.let { Strings.str(R.string.by, it) } else if (a.shared) Strings.str(R.string.shared) else null,
                if (a.ownerUserId == c.me && !a.shared) shortDate(a.startDate) else null,
            ).joinToString(Strings.str(R.string.list_separator_wide)),
            style = CtrlType.Mono,
            color = CtrlColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
        )
    }
}

/** People who share their library with you: tap one to see only their photos. */
@Composable
private fun PartnerRow(partners: List<com.vknxworks.immichctrl.data.api.PartnerDto>) {
    val nav = LocalNavigator.current
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtrlLabel(stringResource(R.string.shared_with_you), Modifier.weight(1f).padding(start = 4.dp))
            androidx.compose.material3.TextButton(onClick = { nav.navigate(PartnersRoute) }) { Text(stringResource(R.string.manage)) }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(partners.size, key = { partners[it].id }) { i ->
                val p = partners[i]
                Column(
                    Modifier.width(80.dp).clip(MaterialTheme.shapes.medium).clickable { nav.navigate(com.vknxworks.immichctrl.ui.nav.PartnerRoute(p.id, p.name.ifBlank { p.email })) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.vknxworks.immichctrl.ui.components.UserAvatar(p.id, p.name.ifBlank { p.email }, p.profileImagePath.isNotBlank(), p.profileChangedAt, null, Modifier.size(60.dp))
                    Text(
                        p.name.ifBlank { p.email },
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
