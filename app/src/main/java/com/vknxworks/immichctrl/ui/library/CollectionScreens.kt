package com.vknxworks.immichctrl.ui.library

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.ui.components.findActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Edit
import com.vknxworks.immichctrl.data.api.updateSharedLink
import com.vknxworks.immichctrl.ui.photos.LinkOptions
import com.vknxworks.immichctrl.ui.photos.SharedLinkDialog
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.SharedLinkDto
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.addPartner
import com.vknxworks.immichctrl.data.api.authStatus
import com.vknxworks.immichctrl.data.api.deleteSharedLink
import com.vknxworks.immichctrl.data.api.deleteTag
import com.vknxworks.immichctrl.data.api.emptyTrash
import com.vknxworks.immichctrl.data.api.folderAssets
import com.vknxworks.immichctrl.data.api.folderPaths
import com.vknxworks.immichctrl.data.api.lockSession
import com.vknxworks.immichctrl.data.api.metadataSearch
import com.vknxworks.immichctrl.data.api.partners
import com.vknxworks.immichctrl.data.api.removePartner
import com.vknxworks.immichctrl.data.api.restoreAllTrash
import com.vknxworks.immichctrl.data.api.setupPin
import com.vknxworks.immichctrl.data.api.sharedLinks
import com.vknxworks.immichctrl.data.api.tags
import com.vknxworks.immichctrl.data.api.unlockSession
import com.vknxworks.immichctrl.data.api.updatePartner
import com.vknxworks.immichctrl.data.api.users
import com.vknxworks.immichctrl.data.share.Exporter
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.UserAvatar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.daysUntil
import com.vknxworks.immichctrl.ui.components.friendlyMessage
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.FoldersRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.PartnerRoute
import com.vknxworks.immichctrl.ui.nav.TagRoute
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberList
import com.vknxworks.immichctrl.ui.photos.rememberPaged
import com.vknxworks.immichctrl.ui.photos.rememberTimeline
import com.vknxworks.immichctrl.data.settings.GroupBy
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------------------------------------
// Favourites, archive, trash, videos, recently added
// ---------------------------------------------------------------------------------------------

@Composable
fun CollectionScreen(kind: String) {
    val c = LocalContext.current.container
    when (kind) {
        "favorites" -> SourceGrid(
            rememberTimeline("favorites", TimelineQuery(isFavorite = true, visibility = "timeline", withStacked = true)),
            SelectionContext.TIMELINE,
            topBar = { ScreenTopBar("Favourites") },
            empty = { MessageView(Icons.Outlined.Sell, "No favourites yet", "Tap the heart on a photo to add it here.") },
        )
        "archive" -> SourceGrid(
            rememberTimeline("archive", TimelineQuery(visibility = "archive", withStacked = true)),
            SelectionContext.ARCHIVE,
            topBar = { ScreenTopBar("Archive") },
            empty = { MessageView(Icons.Outlined.Folder, "Nothing archived", "Archived photos are kept but hidden from Photos.") },
        )
        "trash" -> TrashScreen()
        "videos" -> SourceGrid(
            rememberPaged("videos") { page ->
                val r = c.api.metadataSearch(page, 200, mapOf("type" to "VIDEO", "order" to "desc", "visibility" to "timeline"))
                r.assets.items.map { it.toAsset() } to (r.assets.nextPage != null)
            },
            SelectionContext.TIMELINE,
            topBar = { ScreenTopBar("Videos") },
        )
        else -> SourceGrid(
            // Immich 3 can sort the timeline by import date; older servers get the last 30 days of uploads.
            if (c.api.serverMajor >= 3) rememberTimeline(
                "recent-v3",
                TimelineQuery(visibility = "timeline", orderBy = "createdAt", order = "desc"),
            ) else rememberPaged("recent") { page ->
                val since = Instant.now().minus(30, ChronoUnit.DAYS).toString()
                val r = c.api.metadataSearch(page, 200, mapOf("createdAfter" to since, "order" to "desc", "visibility" to "timeline"))
                r.assets.items.map { it.toAsset() } to (r.assets.nextPage != null)
            },
            SelectionContext.TIMELINE,
            topBar = { ScreenTopBar("Recently added") },
            groupBy = if (c.api.serverMajor >= 3) null else GroupBy.DAY,
            empty = { MessageView(Icons.Outlined.Folder, "Nothing added recently") },
        )
    }
}

@Composable
private fun TrashScreen() {
    val c = LocalContext.current.container
    val source = rememberTimeline("trash", TimelineQuery(isTrashed = true))
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    SourceGrid(
        source,
        SelectionContext.TRASH,
        topBar = {
            ScreenTopBar("Trash") {
                IconButton(onClick = { confirmRestore = true }, enabled = source.total > 0) { Icon(Icons.Outlined.RestoreFromTrash, "Restore all") }
                IconButton(onClick = { confirmEmpty = true }, enabled = source.total > 0) { Icon(Icons.Outlined.DeleteForever, "Empty trash") }
            }
        },
        header = {
            item(key = "trash-note") {
                Text(
                    "Items here are deleted for good after ${c.serverConfig.value.trashDays} days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
        empty = { MessageView(Icons.Outlined.Delete, "The trash is empty") },
    )
    if (confirmEmpty) {
        ConfirmDialog("Empty the trash?", "Everything in the trash is deleted from the server for good.", "Empty trash", onDismiss = { confirmEmpty = false }) {
            confirmEmpty = false
            c.runTask("Emptying the trash") { c.api.emptyTrash(); source.refresh() }
        }
    }
    if (confirmRestore) {
        ConfirmDialog("Restore everything?", "All items in the trash go back to where they were.", "Restore", destructive = false, onDismiss = { confirmRestore = false }) {
            confirmRestore = false
            c.runTask("Restored") { c.api.restoreAllTrash(); Sources.refreshAll() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Locked folder: PIN protected, hidden from everything else
// ---------------------------------------------------------------------------------------------

/**
 * Lives as long as the locked folder's back stack entry, so opening a photo from it (which disposes
 * the grid) keeps the session unlocked; it locks again only when the folder is really left.
 */
class LockedFolderGuard : androidx.lifecycle.ViewModel() {
    var unlocked by mutableStateOf(false)
    var lock: (() -> Unit)? = null

    override fun onCleared() {
        if (unlocked) lock?.invoke()
    }
}

@Composable
fun LockedScreen() {
    val c = LocalContext.current.container
    val status = rememberLoader("auth-status") { c.api.authStatus() }
    val guard: LockedFolderGuard = androidx.lifecycle.viewmodel.compose.viewModel()
    guard.lock = { c.appScope.launch { runCatching { c.api.lockSession() } } }
    var unlocked by guard::unlocked
    var error by remember { mutableStateOf<String?>(null) }
    var settingUp by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val accountKey = c.sessions.active.collectAsStateWithLifecycle().value?.account?.key
    val bio = settings.lockedFolderBiometric && c.appLock.canLock()
    fun rememberPin(pin: String) { if (bio && accountKey != null) c.appScope.launch { c.appLock.savePin(accountKey, pin) } }

    if (unlocked) {
        SourceGrid(
            rememberTimeline("locked", TimelineQuery(visibility = "locked")),
            SelectionContext.LOCKED,
            topBar = { ScreenTopBar("Locked folder") },
            empty = { MessageView(Icons.Outlined.Lock, "Nothing locked yet", "Move photos here from a photo's menu. They're hidden from Photos, search and albums.") },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Locked folder")
        when (val s = status.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { status.reload() }
            is Load.Ok -> {
                androidx.compose.runtime.LaunchedEffect(s.value.isElevated) { if (s.value.isElevated) unlocked = true }
                MessageView(
                    Icons.Outlined.Lock,
                    if (s.value.pinCode) "Enter your PIN" else "Set up a PIN",
                    error ?: if (s.value.pinCode) "Photos in the locked folder need your PIN to see." else "Choose a PIN to protect your locked folder. It's the same PIN as on the Immich website.",
                    actionLabel = if (s.value.pinCode) "Unlock" else "Set PIN",
                    onAction = {
                        if (!s.value.pinCode) settingUp = true
                        else if (bio && activity != null && accountKey != null) c.appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            // Fingerprint first when a PIN has been remembered; fall back to typing it.
                            val saved = c.appLock.savedPin(accountKey)
                            if (saved == null || !c.appLock.promptAsync(activity, "Unlock the locked folder")) { entering = true; return@launch }
                            runCatching { c.api.unlockSession(saved) }
                                .onSuccess { error = null; unlocked = true }
                                .onFailure { c.appLock.forgetPin(accountKey); error = it.friendlyMessage(); entering = true }
                        } else entering = true
                    },
                )
            }
        }
    }
    if (entering) {
        TextInputDialog("Unlock", "PIN", "Unlock", secret = true, numeric = true, onDismiss = { entering = false }) { pin ->
            entering = false
            c.appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                runCatching { c.api.unlockSession(pin) }
                    .onSuccess { error = null; unlocked = true; rememberPin(pin) }
                    .onFailure { error = it.friendlyMessage() }
            }
        }
    }
    if (settingUp) {
        TextInputDialog("New PIN", "6-digit PIN", "Save", secret = true, numeric = true, onDismiss = { settingUp = false }) { pin ->
            settingUp = false
            if (pin.length != 6 || pin.any { !it.isDigit() }) {
                error = "The PIN needs to be 6 digits"
                return@TextInputDialog
            }
            c.appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                runCatching {
                    c.api.setupPin(pin)
                    c.api.unlockSession(pin)
                }.onSuccess { error = null; unlocked = true; rememberPin(pin) }
                    .onFailure { error = it.friendlyMessage() }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared links
// ---------------------------------------------------------------------------------------------

@Composable
fun SharedLinksScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val clipboard = LocalClipboardManager.current
    val links = rememberLoader("shared-links") { c.api.sharedLinks().sortedByDescending { it.createdAt } }
    var open by remember { mutableStateOf<SharedLinkDto?>(null) }
    var editing by remember { mutableStateOf<SharedLinkDto?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Shared links")
        when (val s = links.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { links.reload() }
            is Load.Ok -> if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.Link, "No shared links", "Share a link from an album or from selected photos. Anyone with the link can see them.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.navigationBarsPadding()) {
                    item {
                        Group {
                            s.value.forEach { l ->
                                val expires = daysUntil(l.expiresAt)
                                GroupRow(
                                    l.album?.albumName ?: l.description?.ifBlank { null } ?: countLabel(l.assets.size, "photo"),
                                    listOfNotNull(
                                        "Created ${shortDate(l.createdAt)}",
                                        when {
                                            l.expiresAt == null -> null
                                            expires != null && expires < 0 -> "Expired"
                                            else -> "Expires ${shortDate(l.expiresAt)}"
                                        },
                                        if (l.password != null) "Password" else null,
                                        if (l.allowUpload) "Uploads allowed" else null,
                                    ).joinToString("  ·  "),
                                    icon = Icons.Outlined.Link,
                                    onClick = { open = l },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    open?.let { l ->
        val url = Exporter.sharedLinkUrl(c.api, c.serverConfig.value.externalDomain, l.key, l.slug)
        ModalBottomSheet(onDismissRequest = { open = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(l.album?.albumName ?: countLabel(l.assets.size, "photo"), url)
                ActionItem(Icons.Outlined.ContentCopy, "Copy link") {
                    clipboard.setText(AnnotatedString(url))
                    c.toast("Link copied")
                    open = null
                }
                ActionItem(Icons.Outlined.Share, "Share") { Exporter.shareText(c.context, url); open = null }
                ActionItem(Icons.Outlined.Edit, "Change options") { editing = l; open = null }
                l.album?.let { a -> ActionItem(Icons.Outlined.SmartDisplay, "Open album") { open = null; nav.album(a.id) } }
                ActionItem(Icons.Outlined.Delete, "Delete link", tint = MaterialTheme.colorScheme.error) {
                    open = null
                    c.runTask("Link deleted") { c.api.deleteSharedLink(l.id); links.reload(silent = true) }
                }
            }
        }
    }
    editing?.let { l -> EditLinkDialog(l) { editing = null; links.reload(silent = true) } }
}

@Composable
private fun EditLinkDialog(l: SharedLinkDto, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val days = daysUntil(l.expiresAt)?.let { d -> listOf(1, 7, 30).minByOrNull { kotlin.math.abs(it - d) } }
    SharedLinkDialog(
        "Link options",
        initial = LinkOptions(
            description = l.description.orEmpty(),
            expiresInDays = if (l.expiresAt == null) null else days,
            password = l.password.orEmpty(),
            allowDownload = l.allowDownload,
            allowUpload = l.allowUpload,
            showMetadata = l.showMetadata,
        ),
        allowUploadOption = l.album != null,
        confirm = "Save",
        onDismiss = onDone,
    ) { o ->
        onDone()
        c.runTask("Link updated") {
            val legacy = if (c.api.serverMajor < 3) arrayOf<Pair<String, Any?>>("changeExpiryTime" to true) else emptyArray()
            c.api.updateSharedLink(
                l.id,
                *legacy,
                "description" to o.description,
                "expiresAt" to (o.expiresAt() ?: com.vknxworks.immichctrl.data.api.Null),
                "password" to o.password,
                "allowDownload" to o.allowDownload,
                "allowUpload" to o.allowUpload,
                "showMetadata" to o.showMetadata,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Partners
// ---------------------------------------------------------------------------------------------

@Composable
fun PartnersScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val withMe = rememberLoader("partners-with") { c.api.partners("shared-with") }
    val byMe = rememberLoader("partners-by") { c.api.partners("shared-by") }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        ScreenTopBar("Partners")
        Text(
            "Partners see all of each other's photos (not archived or locked ones). Shared photos can also show up in your Photos tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Group("Sharing with you") {
            val list = withMe.value.orEmpty()
            if (list.isEmpty()) GroupRow(if (withMe.state is Load.Loading) "Loading…" else "No one yet")
            list.forEach { p ->
                GroupRow(
                    p.name,
                    p.email,
                    onClick = { nav.navigate(PartnerRoute(p.id, p.name)) },
                    trailing = {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Switch(p.inTimeline == true, onCheckedChange = { on ->
                                c.runTask { c.api.updatePartner(p.id, on); withMe.reload(silent = true); Sources.refreshAll() }
                            })
                            Text("In Photos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
        }
        Group("You share with") {
            byMe.value.orEmpty().forEach { p ->
                GroupRow(p.name, p.email, trailing = {
                    IconButton(onClick = { removing = p.id to p.name }) { Icon(Icons.Outlined.Delete, "Stop sharing with ${p.name}") }
                })
            }
            GroupRow("Add a partner", icon = Icons.Outlined.PersonAdd, onClick = { adding = true })
        }
        Spacer(Modifier.height(32.dp))
    }

    if (adding) {
        val users = rememberLoader("users") { c.api.users() }
        val existing = byMe.value.orEmpty().map { it.id }.toSet() + c.me
        ModalBottomSheet(onDismissRequest = { adding = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            SheetTitle("Share your photos with")
            when (val s = users.state) {
                is Load.Loading -> LoadingView(Modifier.height(160.dp))
                is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                is Load.Ok -> LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(s.value.filter { it.id !in existing }, key = { it.id }) { u ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable {
                                adding = false
                                c.runTask("Now sharing with ${u.name}") { c.api.addPartner(u.id); byMe.reload(silent = true) }
                            }.padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            UserAvatar(u.id, u.name, u.profileImagePath.isNotBlank(), u.profileChangedAt, u.avatarColor, Modifier.size(40.dp))
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(u.name, style = MaterialTheme.typography.bodyLarge)
                                Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    removing?.let { (id, name) ->
        ConfirmDialog("Stop sharing with $name?", "$name won't see your photos any more.", "Stop sharing", onDismiss = { removing = null }) {
            removing = null
            c.runTask { c.api.removePartner(id); byMe.reload(silent = true) }
        }
    }
}

@Composable
fun PartnerScreen(userId: String, name: String) {
    SourceGrid(
        rememberTimeline("partner:$userId", TimelineQuery(userId = userId, visibility = "timeline", withStacked = true)),
        SelectionContext.SHARED,
        topBar = { ScreenTopBar(name) },
        empty = { MessageView(Icons.Outlined.People, "$name hasn't shared any photos yet") },
    )
}

// ---------------------------------------------------------------------------------------------
// Tags and folders
// ---------------------------------------------------------------------------------------------

@Composable
fun TagsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val tags = rememberLoader("tags") { c.api.tags().sortedBy { it.value.lowercase() } }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Tags")
        when (val s = tags.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { tags.reload() }
            is Load.Ok -> if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.Sell, "No tags yet", "Tag photos from their menu, or turn on tags in Immich's settings.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.navigationBarsPadding()) {
                    item {
                        Group {
                            s.value.forEach { t ->
                                GroupRow(t.value.ifBlank { t.name }, icon = Icons.Outlined.Sell, showChevron = true, onClick = { nav.navigate(TagRoute(t.id, t.value)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagScreen(id: String, name: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    SourceGrid(
        rememberTimeline("tag:$id", TimelineQuery(tagId = id, visibility = "timeline", withStacked = true)),
        SelectionContext.TIMELINE,
        topBar = {
            ScreenTopBar(name) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            }
        },
    )
    if (menu) {
        ModalBottomSheet(onDismissRequest = { menu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(name)
                ActionItem(Icons.Outlined.Delete, "Delete tag", tint = MaterialTheme.colorScheme.error) { menu = false; confirm = true }
            }
        }
    }
    if (confirm) {
        ConfirmDialog("Delete the tag \"$name\"?", "The photos stay; only the tag is removed.", "Delete", onDismiss = { confirm = false }) {
            confirm = false
            c.runTask("Tag deleted") { c.api.deleteTag(id); nav.back() }
        }
    }
}

/** Browse the server's library by its folders on disk. */
@Composable
fun FoldersScreen(path: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val all = rememberLoader("folders") { c.api.folderPaths().map { it.trimEnd('/') }.sorted() }
    val paths = all.value
    // Immediate subfolders of this path.
    val children = remember(paths, path) {
        paths.orEmpty().mapNotNull { p ->
            val rest = if (path.isEmpty()) p.trimStart('/') else if (p.startsWith("$path/")) p.removePrefix("$path/") else null
            rest?.substringBefore('/')?.takeIf { it.isNotBlank() }
        }.distinct().sortedBy { it.lowercase() }
    }
    val hasAssets = paths?.contains(path) == true
    val title = path.substringAfterLast('/').ifBlank { "Folders" }

    when (val s = all.state) {
        is Load.Loading -> Column(Modifier.fillMaxSize()) { ScreenTopBar(title); LoadingView() }
        is Load.Err -> Column(Modifier.fillMaxSize()) { ScreenTopBar(title); ErrorView(s.message) { all.reload() } }
        is Load.Ok -> {
            val folderList: @Composable () -> Unit = {
                if (children.isNotEmpty()) {
                    Group {
                        children.forEach { name ->
                            val next = if (path.isEmpty()) (if (s.value.any { it.startsWith("/") }) "/$name" else name) else "$path/$name"
                            GroupRow(name, icon = Icons.Outlined.Folder, showChevron = true, onClick = { nav.navigate(FoldersRoute(next)) })
                        }
                    }
                }
            }
            if (hasAssets) {
                SourceGrid(
                    rememberList("folder:$path") { c.api.folderAssets(path).map { it.toAsset() } },
                    SelectionContext.TIMELINE,
                    topBar = { ScreenTopBar(title) },
                    groupBy = GroupBy.MONTH,
                    header = { item(key = "subfolders") { folderList() } },
                )
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                    ScreenTopBar(title)
                    if (children.isEmpty()) MessageView(Icons.Outlined.Folder, "Empty folder") else folderList()
                }
            }
        }
    }
}
