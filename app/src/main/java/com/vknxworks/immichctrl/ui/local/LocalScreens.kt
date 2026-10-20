package com.vknxworks.immichctrl.ui.local

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.backup.FreeUpOptions
import com.vknxworks.immichctrl.data.backup.FreeUpPlan
import com.vknxworks.immichctrl.data.backup.FreeUpSpace
import com.vknxworks.immichctrl.data.backup.LocalMedia
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupSwitch
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.RemoteImage
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.friendlyMessage
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.nav.FreeUpSpaceRoute
import com.vknxworks.immichctrl.ui.nav.LocalAlbumRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberList
import com.vknxworks.immichctrl.ui.photos.upload
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The phone's own albums (Camera, Screenshots, WhatsApp Images...). */
@Composable
fun LocalAlbumsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    var tick by remember { mutableStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val hasAccess = remember(tick) { LocalMedia.hasPermission(context) }
    val albums = rememberLoader("local-albums-$tick") { withContext(Dispatchers.IO) { if (hasAccess) LocalMedia.albums(context) else emptyList() } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("On this device") {
            IconButton(onClick = { nav.navigate(FreeUpSpaceRoute) }) { Icon(Icons.Outlined.CleaningServices, "Free up space") }
        }
        if (!hasAccess) {
            MessageView(
                Icons.Outlined.PhoneAndroid,
                "Allow access to your photos",
                "ImmichCtrl needs to see the photos on this phone to show and upload them.",
                actionLabel = "Allow",
                onAction = { com.vknxworks.immichctrl.data.security.expectAppReturn(context); permission.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION)) },
            )
            return@Column
        }
        when (val s = albums.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { albums.reload() }
            is Load.Ok -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(s.value, key = { it.id }) { a ->
                    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable { nav.navigate(LocalAlbumRoute(a.id, a.name)) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
                            RemoteImage(a.coverUri?.toString(), Modifier.fillMaxSize(), placeholderIcon = Icons.Outlined.PhotoLibrary)
                        }
                        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                        Text(
                            countLabel(a.count, "item") + if (a.id in c.settings.settings.value.backupAlbums || c.settings.settings.value.backupEverything) "  ·  Backed up" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One phone album: what's backed up (cloud icon) and what isn't, with Upload for the rest. */
@Composable
fun LocalAlbumScreen(bucketId: String, name: String) {
    val c = LocalContext.current.container
    val source = rememberList("local:$bucketId") { c.local.album(bucketId) }
    val version by c.local.version.collectAsStateWithLifecycle()
    LaunchedEffect(version) { source.refresh(silent = true) }
    val notBackedUp = source.buckets.firstOrNull()?.assets?.filter { it.isLocalOnly }.orEmpty()
    SourceGrid(
        source,
        SelectionContext.LOCAL,
        topBar = {
            ScreenTopBar(name) {
                if (notBackedUp.isNotEmpty() && !c.settings.settings.value.readOnly) {
                    IconButton(onClick = { c.upload(notBackedUp) }) { Icon(Icons.Outlined.CloudUpload, "Upload all ${notBackedUp.size} not backed up") }
                }
            }
        },
        header = {
            item(key = "local-header") {
                Text(
                    if (notBackedUp.isEmpty()) "Everything here is backed up." else "${countLabel(notBackedUp.size, "item")} not backed up yet (no cloud icon). Select them to upload, or tap the cloud above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
        },
    )
}

/**
 * Free Up Space: removes the phone's copies of photos that are safely on the server (checked with the
 * server right before), into the phone's trash.
 */
@Composable
fun FreeUpSpaceScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val nav = LocalNavigator.current
    var olderThan by remember { mutableStateOf<Int?>(30) }
    var keepFavorites by remember { mutableStateOf(true) }
    var keepPhotos by remember { mutableStateOf(false) }
    var keepVideos by remember { mutableStateOf(false) }
    var keepAlbums by remember { mutableStateOf(emptySet<String>()) }
    var plan by remember { mutableStateOf<FreeUpPlan?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val albums = rememberLoader("freeup-albums") { withContext(Dispatchers.IO) { LocalMedia.albums(context) } }

    fun review() {
        val session = c.sessions.active.value ?: return
        plan = null
        error = null
        progress = "Looking for backed-up photos…"
        scope.launch {
            try {
                plan = FreeUpSpace.plan(
                    context, c.backupDb, session.client, session.account.key,
                    FreeUpOptions(FreeUpSpace.cutoff(olderThan), keepFavorites, keepAlbums, keepPhotos, keepVideos),
                ) { progress = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                progress = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Free up space")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                "Removes photos and videos from this phone that are backed up to Immich and not in its trash. They stay on the server. " +
                    "Removed items go to the phone's trash first, so empty it there to get the space back. " +
                    "Photos other apps use (like WhatsApp chats) may look blurry or missing there afterwards; keep those albums below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
            )
            Group("Only items taken") {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "Over a month ago", 90 to "Over 3 months ago", 365 to "Over a year ago", null to "Any time").forEach { (d, label) ->
                        FilterChip(olderThan == d, onClick = { olderThan = d; plan = null }, label = { Text(label) })
                    }
                }
            }
            Group("Keep on this phone") {
                GroupSwitch("Favourites", null, keepFavorites) { keepFavorites = it; plan = null }
                GroupSwitch("All photos", null, keepPhotos) { keepPhotos = it; plan = null }
                GroupSwitch("All videos", null, keepVideos) { keepVideos = it; plan = null }
            }
            Group("Keep these albums") {
                albums.value.orEmpty().forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            keepAlbums = if (a.id in keepAlbums) keepAlbums - a.id else keepAlbums + a.id
                            plan = null
                        }.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(a.id in keepAlbums, onCheckedChange = { on ->
                            keepAlbums = if (on) keepAlbums + a.id else keepAlbums - a.id
                            plan = null
                        })
                        Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(countLabel(a.count, "item"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val p = plan
                    when {
                        progress != null -> {
                            Text(progress.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        p == null -> Text("Review what would be removed before anything happens.", style = MaterialTheme.typography.bodyMedium)
                        p.items.isEmpty() -> Text("Nothing to remove with these options.", style = MaterialTheme.typography.titleMedium)
                        else -> {
                            Text("${countLabel(p.items.size, "item")}  ·  ${formatBytes(p.bytes)}", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${p.items.count { !it.isVideo }} photos and ${p.items.count { it.isVideo }} videos, all confirmed on the server just now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (p != null && p.items.isNotEmpty() && progress == null) {
                        PillButton("Remove from this phone", onClick = { confirm = true }, modifier = Modifier.fillMaxWidth())
                    } else if (progress == null) {
                        PillButton("Review", onClick = ::review, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    val p = plan
    if (confirm && p != null) {
        ConfirmDialog(
            "Remove ${countLabel(p.items.size, "item")} from this phone?",
            "They stay in Immich. Android will ask you to confirm, and they go to the phone's trash.",
            "Remove",
            onDismiss = { confirm = false },
        ) {
            confirm = false
            scope.launch {
                var removed = 0
                // Android's confirmation can't take thousands at once; go in batches.
                for (batch in p.items.chunked(500)) {
                    if (!c.device.trash(batch.map { it.uri })) break
                    removed += batch.size
                }
                c.local.changed()
                Sources.refreshAll()
                plan = null
                if (removed > 0) {
                    android.widget.Toast.makeText(context, "Removed ${countLabel(removed, "item")} (${formatBytes(p.items.take(removed).sumOf { it.size })})", android.widget.Toast.LENGTH_LONG).show()
                    nav.back()
                }
            }
        }
    }
}
