package com.vknxworks.immichctrl.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.MemoryDto
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.memories
import com.vknxworks.immichctrl.ui.components.AssetThumb
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.UserAvatar
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.yearsAgo
import com.vknxworks.immichctrl.ui.nav.BackupRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.MemoryRoute
import com.vknxworks.immichctrl.ui.nav.YouRoute
import com.vknxworks.immichctrl.ui.theme.Gutter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** The Photos tab: everything on the server, newest first, with memories on top. */
@Composable
fun PhotosScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val work by c.backup.work.collectAsStateWithLifecycle(emptyList())
    val acc = session?.account
    val source = rememberTimeline(
        "photos:${settings.withPartners}",
        TimelineQuery(visibility = "timeline", withPartners = settings.withPartners, withStacked = true, order = "desc"),
    )
    val memories = rememberLoader("memories") {
        if (!settings.showMemories) emptyList()
        else c.api.memories(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).filter { it.assets.isNotEmpty() }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backingUp = work.any { it.state == WorkInfo.State.RUNNING }

    SourceGrid(
        source = source,
        context = SelectionContext.TIMELINE,
        topPadding = top,
        empty = {
            MessageView(
                Icons.Outlined.PhotoLibrary,
                "No photos yet",
                "Turn on backup to upload this phone's photos, or upload from the Immich website.",
                actionLabel = "Set up backup",
                onAction = { nav.navigate(BackupRoute) },
            )
        },
        header = {
            item(key = "photos-header") {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Photos", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { nav.navigate(BackupRoute) }) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    backingUp -> {
                                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                        Icon(Icons.Outlined.CloudUpload, "Backing up", Modifier.size(16.dp))
                                    }
                                    settings.backupEnabled -> Icon(Icons.Outlined.CloudDone, "Backup is on")
                                    else -> Icon(Icons.Outlined.CloudOff, "Backup is off", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (acc != null) {
                            IconButton(onClick = { nav.navigate(YouRoute) }) {
                                UserAvatar(acc.userId, acc.name, true, null, null, Modifier.size(32.dp))
                            }
                        }
                    }
                    val list = memories.value.orEmpty()
                    if (settings.showMemories && list.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(list, key = { it.id }) { m -> MemoryCard(m) { nav.navigate(MemoryRoute(m.id)) } }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MemoryCard(m: MemoryDto, onClick: () -> Unit) {
    val cover = m.assets.first()
    Box(
        Modifier
            .width(112.dp)
            .height(160.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        AssetThumb(cover.id, cover.thumbhash, Modifier.fillMaxSize(), size = "preview")
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
        )
        Text(
            if (m.type == "on_this_day") yearsAgo(m.data.year) else m.data.personName ?: "Memory",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
