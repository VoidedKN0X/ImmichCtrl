package com.vknxworks.immichctrl.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.ui.photos.SharedLinkDialog
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.ActivityDto
import com.vknxworks.immichctrl.data.api.AlbumDto
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.activities
import com.vknxworks.immichctrl.data.api.addActivity
import com.vknxworks.immichctrl.data.api.addAlbumUsers
import com.vknxworks.immichctrl.data.api.album
import com.vknxworks.immichctrl.data.api.createSharedLink
import com.vknxworks.immichctrl.data.api.deleteActivity
import com.vknxworks.immichctrl.data.api.deleteAlbum
import com.vknxworks.immichctrl.data.api.removeAlbumUser
import com.vknxworks.immichctrl.data.api.setAlbumUserRole
import com.vknxworks.immichctrl.data.api.updateAlbum
import com.vknxworks.immichctrl.data.api.users
import com.vknxworks.immichctrl.data.share.Exporter
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.UserAvatar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.PickAssetsRoute
import com.vknxworks.immichctrl.ui.photos.AssetGrid
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.Selection
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.addToAlbum
import com.vknxworks.immichctrl.ui.photos.rememberTimeline
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.theme.Gutter
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

@Composable
fun AlbumScreen(id: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val album = rememberLoader("album:$id") { c.api.album(id) }
    when (val s = album.state) {
        is Load.Loading -> Column(Modifier.fillMaxSize()) { ScreenTopBar(""); LoadingView() }
        is Load.Err -> if (c.offline.isOffline(id)) {
            // No connection, but it's saved on the phone.
            OfflineAlbumScreen(id)
        } else {
            Column(Modifier.fillMaxSize()) { ScreenTopBar(stringResource(R.string.album)); ErrorView(s.message) { album.reload() } }
        }
        is Load.Ok -> AlbumContent(s.value, onChanged = { album.reload(silent = true) }, onGone = { nav.back() })
    }
}

@Composable
private fun AlbumContent(a: AlbumDto, onChanged: () -> Unit, onGone: () -> Unit) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val isOwner = a.ownerUserId == c.me
    val canEdit = isOwner || a.albumUsers.any { it.user.id == c.me && it.role == "editor" }
    val source = rememberTimeline("album:${a.id}", TimelineQuery(albumId = a.id, order = a.order))
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var describing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var linkOptions by remember { mutableStateOf(false) }
    val offlineAlbums by c.offline.albums.collectAsStateWithLifecycle()
    val isOffline = offlineAlbums.any { it.albumId == a.id }
    val readOnly = c.settings.settings.value.readOnly

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) c.runTask {
            val n = c.backup.uploadShared(uris, a.id)
            c.toast(Strings.str(R.string.uploading_to, countLabel(n, R.plurals.count_items), a.albumName))
        }
    }

    SourceGrid(
        source = source,
        context = SelectionContext.ALBUM,
        albumId = a.id,
        canEditAlbum = canEdit,
        topBar = {
            ScreenTopBar(a.albumName.ifBlank { Strings.str(R.string.untitled) }) {
                if (canEdit && !readOnly) IconButton(onClick = { addMenu = true }) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.add_photos)) }
                if (isOwner && !readOnly) IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.PersonAddAlt, stringResource(R.string.share)) }
                if (a.shared && a.isActivityEnabled) IconButton(onClick = { activity = true }) { Icon(Icons.AutoMirrored.Outlined.Comment, stringResource(R.string.activity)) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
            }
        },
        empty = {
            com.vknxworks.immichctrl.ui.components.MessageView(
                Icons.Outlined.PhotoLibrary,
                stringResource(R.string.this_album_is_empty),
                if (canEdit) stringResource(R.string.add_photos_from_your_library_or_this) else null,
                actionLabel = if (canEdit) stringResource(R.string.add_photos) else null,
                onAction = { addMenu = true },
            )
        },
        header = {
            item(key = "album-header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                    val range = listOfNotNull(shortDate(a.startDate), shortDate(a.endDate)).distinct().joinToString(" – ")
                    Text(
                        listOfNotNull(range.ifBlank { null }, countLabel(a.assetCount, R.plurals.count_items)).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (a.description.isNotBlank()) {
                        Text(a.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (a.members.isNotEmpty()) {
                        LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val people = listOfNotNull(a.ownerUser) + a.members.map { it.user }
                            items(people, key = { it.id }) { u ->
                                UserAvatar(u.id, u.name, u.profileImagePath.isNotBlank(), u.profileChangedAt, u.avatarColor, Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        },
    )

    if (addMenu) {
        ModalBottomSheet(onDismissRequest = { addMenu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(stringResource(R.string.add_to, a.albumName))
                ActionItem(Icons.Outlined.PhotoLibrary, stringResource(R.string.from_your_immich_library)) { addMenu = false; nav.navigate(PickAssetsRoute(a.id)) }
                ActionItem(Icons.Outlined.Smartphone, stringResource(R.string.upload_from_this_phone)) {
                    addMenu = false
                    com.vknxworks.immichctrl.data.security.expectAppReturn(c.context)
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
            }
        }
    }

    if (menu) {
        ModalBottomSheet(
            onDismissRequest = { menu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(a.albumName, a.ownerUser?.name?.let { if (isOwner) null else Strings.str(R.string.shared_by, it) })
                ActionItem(Icons.Outlined.OfflinePin, if (isOffline) stringResource(R.string.remove_offline_copy) else stringResource(R.string.make_available_offline)) {
                    menu = false
                    if (isOffline) c.offline.remove(a.id) else {
                        c.offline.sync(a.id, a.albumName)
                        c.toast(Strings.str(R.string.saving_for_offline_viewing, a.albumName))
                    }
                }
                if (canEdit && !isOwner && !readOnly) {
                    ActionItem(Icons.Outlined.Edit, stringResource(R.string.rename)) { menu = false; renaming = true }
                    ActionItem(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.edit_description)) { menu = false; describing = true }
                }
                if (isOwner && !readOnly) {
                    ActionItem(Icons.Outlined.Edit, stringResource(R.string.rename)) { menu = false; renaming = true }
                    ActionItem(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.edit_description)) { menu = false; describing = true }
                    ActionItem(Icons.Outlined.SwapVert, if (a.order == "asc") stringResource(R.string.show_newest_first) else stringResource(R.string.show_oldest_first)) {
                        menu = false
                        c.runTask {
                            c.api.updateAlbum(a.id, "order" to if (a.order == "asc") "desc" else "asc")
                            onChanged()
                        }
                    }
                    ActionItem(Icons.Outlined.Link, stringResource(R.string.share_a_link)) {
                        menu = false
                        linkOptions = true
                    }
                    ActionItem(Icons.AutoMirrored.Outlined.Comment, if (a.isActivityEnabled) stringResource(R.string.turn_off_comments_and_likes) else stringResource(R.string.turn_on_comments_and_likes)) {
                        menu = false
                        c.runTask { c.api.updateAlbum(a.id, "isActivityEnabled" to !a.isActivityEnabled); onChanged() }
                    }
                    ActionItem(Icons.Outlined.Delete, stringResource(R.string.delete_album), tint = MaterialTheme.colorScheme.error) { menu = false; confirmDelete = true }
                } else if (!isOwner && !readOnly) {
                    ActionItem(Icons.AutoMirrored.Outlined.ExitToApp, stringResource(R.string.leave_album), tint = MaterialTheme.colorScheme.error) { menu = false; confirmLeave = true }
                }
                ActionItem(Icons.Outlined.SmartDisplay, stringResource(R.string.play_as_slideshow)) {
                    menu = false
                    if (source.total > 0) source.assetAt(0)?.let { nav.viewer(source.key, 0, it.id) }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(stringResource(R.string.rename_album), stringResource(R.string.name), stringResource(R.string.save), initial = a.albumName, onDismiss = { renaming = false }) { name ->
            renaming = false
            c.runTask { c.api.updateAlbum(a.id, "albumName" to name); onChanged() }
        }
    }
    if (describing) {
        TextInputDialog(stringResource(R.string.description), stringResource(R.string.description), stringResource(R.string.save), initial = a.description, allowEmpty = true, singleLine = false, onDismiss = { describing = false }) { text ->
            describing = false
            c.runTask { c.api.updateAlbum(a.id, "description" to text); onChanged() }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete, a.albumName),
            stringResource(R.string.the_album_goes_away_for_everyone_it),
            stringResource(R.string.lock_delete),
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            c.runTask(Strings.str(R.string.album_deleted)) { c.api.deleteAlbum(a.id); onGone() }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(stringResource(R.string.leave_2, a.albumName), stringResource(R.string.you_ll_no_longer_see_this_album), stringResource(R.string.leave), onDismiss = { confirmLeave = false }) {
            confirmLeave = false
            c.runTask(Strings.str(R.string.left_the_album)) { c.api.removeAlbumUser(a.id, "me"); onGone() }
        }
    }
    if (linkOptions) {
        SharedLinkDialog(stringResource(R.string.share_2, a.albumName), allowUploadOption = true, onDismiss = { linkOptions = false }) { o ->
            linkOptions = false
            c.runTask {
                val link = c.api.createSharedLink(
                    albumId = a.id,
                    description = o.description.ifBlank { null },
                    expiresAt = o.expiresAt(),
                    allowDownload = o.allowDownload,
                    allowUpload = o.allowUpload,
                    showMetadata = o.showMetadata,
                    password = o.password,
                )
                Exporter.shareText(c.context, Exporter.sharedLinkUrl(c.api, c.serverConfig.value.externalDomain, link.key, link.slug))
            }
        }
    }
    if (sharing) ShareAlbumSheet(a, onDismiss = { sharing = false }, onChanged = onChanged)
    if (activity) ActivitySheet(a, onDismiss = { activity = false })
}

/** Who the album is shared with, their roles, and adding more people. */
@Composable
private fun ShareAlbumSheet(a: AlbumDto, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val c = LocalContext.current.container
    var adding by remember { mutableStateOf(false) }
    val users = rememberLoader("users") { c.api.users() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(stringResource(R.string.shared_with), if (a.members.isEmpty()) stringResource(R.string.only_you_can_see_this_album) else null)
            a.members.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(m.user.id, m.user.name, m.user.profileImagePath.isNotBlank(), m.user.profileChangedAt, m.user.avatarColor, Modifier.size(40.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.user.name, style = MaterialTheme.typography.bodyLarge)
                        Text(if (m.role == "editor") stringResource(R.string.can_add_and_remove_photos) else stringResource(R.string.can_view), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        c.runTask { c.api.setAlbumUserRole(a.id, m.user.id, if (m.role == "editor") "viewer" else "editor"); onChanged() }
                    }) { Text(if (m.role == "editor") stringResource(R.string.make_viewer) else stringResource(R.string.make_editor)) }
                    IconButton(onClick = { c.runTask { c.api.removeAlbumUser(a.id, m.user.id); onChanged() } }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.remove, m.user.name))
                    }
                }
            }
            ActionItem(Icons.Outlined.PersonAdd, stringResource(R.string.add_people)) { adding = true }
            if (adding) {
                val existing = a.albumUsers.map { it.user.id }.toSet() + c.me
                when (val s = users.state) {
                    is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                    is Load.Ok -> {
                        val others = s.value.filter { it.id !in existing }
                        if (others.isEmpty()) {
                            Text(stringResource(R.string.everyone_on_this_server_already_has), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        others.forEach { u ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    c.runTask(Strings.str(R.string.shared_with_2, u.name)) { c.api.addAlbumUsers(a.id, listOf(u.id)); onChanged() }
                                    onDismiss()
                                }.padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(u.id, u.name, u.profileImagePath.isNotBlank(), u.profileChangedAt, u.avatarColor, Modifier.size(40.dp))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(u.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Comments and likes on a shared album. */
@Composable
private fun ActivitySheet(a: AlbumDto, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val list = rememberLoader("activity:${a.id}") { c.api.activities(a.id).sortedBy { it.createdAt } }
    var text by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<ActivityDto?>(null) }
    val myLike = list.value?.firstOrNull { it.type == "like" && it.assetId == null && it.user.id == c.me }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    c.runTask {
                        if (myLike != null) c.api.deleteActivity(myLike.id) else c.api.addActivity(a.id, null, null)
                        list.reload(silent = true)
                    }
                }) {
                    Icon(if (myLike != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, stringResource(R.string.like), tint = if (myLike != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (val s = list.state) {
                is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                is Load.Ok -> LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (s.value.isEmpty()) item { Text(stringResource(R.string.no_comments_yet), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(s.value, key = { it.id }) { act ->
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(onClick = {}, onLongClick = { if (act.user.id == c.me) removing = act })
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            UserAvatar(act.user.id, act.user.name, act.user.profileImagePath.isNotBlank(), act.user.profileChangedAt, act.user.avatarColor, Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    listOfNotNull(act.user.name, shortDate(act.createdAt)).joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (act.type == "like") stringResource(if (act.assetId == null) R.string.liked_the_album else R.string.liked_a_photo) else act.comment.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(R.string.say_something)) }, modifier = Modifier.weight(1f), maxLines = 4)
                IconButton(onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        text = ""
                        c.runTask { c.api.addActivity(a.id, null, t); list.reload(silent = true) }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
            }
        }
    }
    removing?.let { act ->
        ConfirmDialog(stringResource(R.string.delete_this), stringResource(if (act.type == "like") R.string.your_like_will_be_removed else R.string.your_comment_will_be_removed), stringResource(R.string.lock_delete), onDismiss = { removing = null }) {
            removing = null
            c.runTask { c.api.deleteActivity(act.id); list.reload(silent = true) }
        }
    }
}

/** Choose photos from the library to add to an album. */
@Composable
fun PickAssetsScreen(albumId: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val settings = c.settings.settings.value
    val album = rememberLoader("album:$albumId") { c.api.album(albumId) }
    val source = rememberTimeline("pick:$albumId", TimelineQuery(visibility = "timeline", withPartners = false, withStacked = true, order = "desc"))
    val selection = remember { Selection() }
    var columns by remember { mutableStateOf(settings.columns) }
    BackHandler(enabled = selection.active) { selection.clear() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(if (selection.active) stringResource(R.string.selected_2, selection.size) else stringResource(R.string.add_to, album.value?.albumName ?: "album"))
            AssetGrid(
                source = source,
                selection = selection,
                columns = columns,
                onColumnsChange = { columns = it.coerceIn(2, 7) },
                onOpen = { _, a -> selection.toggle(a) },
            )
        }
        if (selection.active) {
            PillButton(
                stringResource(R.string.add, countLabel(selection.size, R.plurals.count_items)),
                onClick = {
                    c.addToAlbum(albumId, album.value?.albumName ?: Strings.str(R.string.the_album), selection.assets)
                    Sources["album:$albumId"]?.refresh(silent = true)
                    nav.back()
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}
