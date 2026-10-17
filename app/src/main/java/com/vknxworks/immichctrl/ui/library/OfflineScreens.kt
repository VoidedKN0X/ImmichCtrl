package com.vknxworks.immichctrl.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberList
import com.vknxworks.immichctrl.ui.theme.Gutter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** An album saved on the phone; works without a connection. */
@Composable
fun OfflineAlbumScreen(albumId: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val albums by c.offline.albums.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.albumId == albumId }
    val source = rememberList("offline:$albumId") { c.offline.assets(albumId) }
    LaunchedEffect(album?.updatedAt) { source.refresh(silent = true) }
    SourceGrid(
        source,
        SelectionContext.SHARED,
        selectable = false,
        topBar = {
            ScreenTopBar(album?.name ?: "Offline album") {
                IconButton(onClick = {
                    c.offline.sync(albumId, album?.name ?: "album")
                    c.toast("Updating the offline copy")
                }) { Icon(Icons.Outlined.Sync, "Update") }
                IconButton(onClick = { c.offline.remove(albumId); nav.back() }) { Icon(Icons.Outlined.Delete, "Remove offline copy") }
            }
        },
        header = {
            item(key = "offline-header") {
                Text(
                    if (album == null) "Not saved" else listOfNotNull(
                        countLabel(album.count, "item"),
                        formatBytes(c.offline.bytes(albumId)),
                        if (album.updatedAt > 0) "Updated " + DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(album.updatedAt).atZone(ZoneId.systemDefault())) else "Saving…",
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
        },
        empty = { MessageView(Icons.Outlined.CloudOff, "Saving…", "The photos appear here as they download.") },
    )
}
