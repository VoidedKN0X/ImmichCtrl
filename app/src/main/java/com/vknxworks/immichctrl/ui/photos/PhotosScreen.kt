package com.vknxworks.immichctrl.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import com.vknxworks.immichctrl.ui.components.CtrlAvatarButton
import com.vknxworks.immichctrl.ui.components.CtrlLabel
import com.vknxworks.immichctrl.ui.components.CtrlSquareButton
import com.vknxworks.immichctrl.ui.components.CtrlTopBar
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import kotlinx.coroutines.launch
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
import com.vknxworks.immichctrl.ui.components.ProfileAvatar
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.yearsAgo
import com.vknxworks.immichctrl.ui.nav.BackupRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.MemoryRoute
import com.vknxworks.immichctrl.ui.nav.YouRoute
import com.vknxworks.immichctrl.ui.theme.Gutter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

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
        withLocal = true,
    )
    // New photos on the phone, a finished backup or the "show phone photos" setting: merge again.
    val localVersion by c.local.version.collectAsStateWithLifecycle()
    var seenVersion by remember { mutableIntStateOf(localVersion) }
    LaunchedEffect(localVersion, settings.showLocal) {
        if (localVersion != seenVersion) {
            seenVersion = localVersion
            source.refresh(silent = true)
        }
    }
    var toggleReadOnly by remember { mutableStateOf(false) }
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
        // The title row's backup and profile buttons sit where the fast scroller's strip would be.
        scrollerTopInset = 64.dp,
        empty = {
            MessageView(
                Icons.Outlined.PhotoLibrary,
                stringResource(R.string.no_photos_yet),
                stringResource(R.string.turn_on_backup_to_upload_this_phone),
                actionLabel = stringResource(R.string.set_up_backup),
                onAction = { nav.navigate(BackupRoute) },
            )
        },
        header = {
            item(key = "photos-header") {
                Column {
                    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 4.dp)) {
                        CtrlTopBar(trailing = {
                            if (settings.readOnly) {
                                Icon(Icons.Outlined.Lock, stringResource(R.string.read_only_mode), tint = CtrlColors.AccentText, modifier = Modifier.padding(end = 4.dp).size(20.dp))
                            } else {
                                val backupLabel = when {
                                    backingUp -> stringResource(R.string.backing_up)
                                    settings.backupEnabled -> stringResource(R.string.backup_is_on)
                                    else -> stringResource(R.string.backup_is_off)
                                }
                                CtrlSquareButton(
                                    when {
                                        backingUp -> Icons.Outlined.CloudUpload
                                        settings.backupEnabled -> Icons.Outlined.CloudDone
                                        else -> Icons.Outlined.CloudOff
                                    },
                                    backupLabel,
                                    onClick = { nav.navigate(BackupRoute) },
                                    tint = if (settings.backupEnabled || backingUp) CtrlColors.AccentText else CtrlColors.Muted,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (backingUp) CircularProgressIndicator(Modifier.size(28.dp), color = CtrlColors.AccentText, trackColor = CtrlColors.ProgressTrack, strokeWidth = 2.dp)
                                        Icon(
                                            when {
                                                backingUp -> Icons.Outlined.CloudUpload
                                                settings.backupEnabled -> Icons.Outlined.CloudDone
                                                else -> Icons.Outlined.CloudOff
                                            },
                                            null,
                                            Modifier.size(if (backingUp) 14.dp else 20.dp),
                                            tint = if (settings.backupEnabled || backingUp) CtrlColors.AccentText else CtrlColors.Muted,
                                        )
                                    }
                                }
                            }
                            if (acc != null) {
                                // Long-press the avatar for read-only mode (handy before handing the phone to a child).
                                CtrlAvatarButton(
                                    stringResource(R.string.you),
                                    onClick = { if (!settings.readOnly) nav.navigate(YouRoute) },
                                    onLongClick = { toggleReadOnly = true },
                                ) { ProfileAvatar(acc.userId, acc.name) }
                            }
                        })
                        Spacer(Modifier.height(18.dp))
                        CtrlLabel(
                            if (settings.readOnly) stringResource(R.string.read_only_mode)
                            else if (backingUp) stringResource(R.string.backing_up)
                            else if (settings.backupEnabled) stringResource(R.string.backup_is_on)
                            else stringResource(R.string.backup_is_off),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.photos), style = MaterialTheme.typography.headlineLarge)
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

    if (toggleReadOnly) {
        ReadOnlyDialog(settings.readOnly, onDismiss = { toggleReadOnly = false })
    }
}

/** Turning read-only mode on is a tap; turning it off asks for the phone's lock when there is one. */
@Composable
private fun ReadOnlyDialog(on: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    fun set(v: Boolean) = scope.launch { c.settings.update { it.copy(readOnly = v) } }
    ConfirmDialog(
        if (on) stringResource(R.string.leave_read_only_mode) else stringResource(R.string.turn_on_read_only_mode),
        if (on) stringResource(R.string.everything_can_be_changed_and) else stringResource(R.string.only_photos_can_be_browsed_and),
        if (on) stringResource(R.string.turn_off) else stringResource(R.string.turn_on),
        destructive = false,
        onDismiss = onDismiss,
    ) {
        onDismiss()
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (on && activity != null && c.appLock.canLock()) {
            c.appLock.prompt(activity, Strings.str(R.string.leave_read_only_mode_2), allowCredential = true) { ok -> if (ok) set(false) }
        } else {
            set(!on)
        }
    }
}

@Composable
private fun MemoryCard(m: MemoryDto, onClick: () -> Unit) {
    val cover = m.assets.first()
    Box(
        Modifier
            .width(112.dp)
            .height(160.dp)
            .clip(CtrlShapes.Card)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card)
            .clickable(onClick = onClick),
    ) {
        AssetThumb(cover.id, cover.thumbhash, Modifier.fillMaxSize(), size = "preview")
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
        )
        Text(
            if (m.type == "on_this_day") yearsAgo(m.data.year) else m.data.personName ?: stringResource(R.string.memory),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
