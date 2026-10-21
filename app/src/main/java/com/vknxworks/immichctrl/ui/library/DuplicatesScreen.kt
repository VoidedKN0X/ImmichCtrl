package com.vknxworks.immichctrl.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AssetDto
import com.vknxworks.immichctrl.data.api.DuplicateGroupDto
import com.vknxworks.immichctrl.data.api.dismissDuplicate
import com.vknxworks.immichctrl.data.api.duplicates
import com.vknxworks.immichctrl.data.api.resolveDuplicates
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.AssetThumb
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.theme.Gutter
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/**
 * Groups of photos Immich thinks are the same picture. For each group, tap to choose what to keep;
 * the rest go to the trash. Immich's own suggestion (the largest, most detailed copy) is preselected.
 */
@Composable
fun DuplicatesScreen() {
    val c = LocalContext.current.container
    val groups = rememberLoader("duplicates") { c.api.duplicates().filter { it.assets.size > 1 } }
    // duplicateId -> asset ids to keep (absent: Immich's suggestion)
    val keep = remember { mutableStateMapOf<String, Set<String>>() }
    val done = remember { mutableStateMapOf<String, Boolean>() }
    var confirmAll by remember { mutableStateOf(false) }

    fun keepFor(g: DuplicateGroupDto): Set<String> =
        keep[g.duplicateId] ?: g.suggestedKeepAssetIds.toSet().ifEmpty { setOf(bestOf(g.assets).id) }

    fun resolve(list: List<DuplicateGroupDto>) {
        // Never send a group with nothing kept: the server would trash every copy.
        val ready = list.filter { g -> g.assets.any { it.id in keepFor(g) } }
        if (ready.isEmpty()) return
        val payload = ready.map { g ->
            val ids = g.assets.map { it.id }
            val k = keepFor(g).filter { it in ids }
            Triple(g.duplicateId, k, ids.filter { it !in k })
        }
        ready.forEach { done[it.duplicateId] = true }
        c.runTask(if (ready.size == 1) Strings.str(R.string.duplicates_resolved) else Strings.plural(R.plurals.groups_resolved, ready.size, ready.size)) {
            try {
                c.api.resolveDuplicates(payload)
                Sources.refreshAll()
            } catch (e: Throwable) {
                ready.forEach { done.remove(it.duplicateId) }
                throw e
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        when (val s = groups.state) {
            is Load.Loading -> { ScreenTopBar(stringResource(R.string.duplicates)); LoadingView() }
            is Load.Err -> { ScreenTopBar(stringResource(R.string.duplicates)); ErrorView(s.message) { groups.reload() } }
            is Load.Ok -> {
                val open = s.value.filter { done[it.duplicateId] != true }
                ScreenTopBar(stringResource(R.string.duplicates)) {
                    if (open.isNotEmpty()) IconButton(onClick = { confirmAll = true }) { Icon(Icons.Outlined.DoneAll, stringResource(R.string.resolve_all)) }
                }
                if (open.isEmpty()) {
                    MessageView(
                        Icons.Outlined.FilterNone,
                        stringResource(R.string.no_duplicates),
                        stringResource(R.string.immich_looks_for_duplicates_when_its),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        item(key = "summary") {
                            Text(
                                stringResource(R.string.tap_the_copies_to_keep, countLabel(open.size, R.plurals.count_groups), countLabel(open.sumOf { it.assets.size }, R.plurals.count_photos)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                            )
                        }
                        items(open, key = { it.duplicateId }) { g ->
                            DuplicateGroup(
                                g,
                                keepFor(g),
                                onToggle = { id ->
                                    val cur = keepFor(g)
                                    keep[g.duplicateId] = if (id in cur) cur - id else cur + id
                                },
                                onResolve = { resolve(listOf(g)) },
                                onKeepAll = {
                                    done[g.duplicateId] = true
                                    c.runTask(Strings.str(R.string.kept_all)) {
                                        try { c.api.dismissDuplicate(g.duplicateId) } catch (e: Throwable) { done.remove(g.duplicateId); throw e }
                                    }
                                },
                            )
                        }
                    }
                }
                if (confirmAll) {
                    val trash = open.sumOf { g -> g.assets.count { it.id !in keepFor(g) } }
                    ConfirmDialog(
                        stringResource(R.string.resolve_all_2, countLabel(open.size, R.plurals.count_groups)),
                        stringResource(R.string.keeps_the_selected_copy_in_each, countLabel(trash, R.plurals.count_others)),
                        stringResource(R.string.resolve_all),
                        onDismiss = { confirmAll = false },
                    ) {
                        confirmAll = false
                        resolve(open)
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroup(
    g: DuplicateGroupDto,
    keep: Set<String>,
    onToggle: (String) -> Unit,
    onResolve: () -> Unit,
    onKeepAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(g.assets, key = { it.id }) { a ->
                val kept = a.id in keep
                Column(Modifier.width(150.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                3.dp,
                                if (kept) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onToggle(a.id) },
                    ) {
                        AssetThumb(a.toAsset(), Modifier.fillMaxSize())
                        Icon(
                            if (kept) Icons.Filled.CheckCircle else Icons.Filled.Delete,
                            if (kept) stringResource(R.string.keep) else stringResource(R.string.trash),
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (kept) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer)
                                .padding(4.dp),
                            tint = if (kept) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.originalFileName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        details(a),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val trash = g.assets.size - keep.size
            Text(
                if (keep.isEmpty()) stringResource(R.string.pick_at_least_one_to_keep) else stringResource(R.string.keep_trash, keep.size, trash),
                style = MaterialTheme.typography.bodySmall,
                color = if (keep.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onKeepAll) { Text(stringResource(R.string.not_duplicates)) }
            PillButton(stringResource(R.string.resolve), onClick = onResolve, enabled = keep.isNotEmpty())
        }
    }
}

private fun details(a: AssetDto): String {
    val e = a.exifInfo
    val w = e?.exifImageWidth ?: a.width?.toLong()
    val h = e?.exifImageHeight ?: a.height?.toLong()
    return listOfNotNull(
        if (w != null && h != null && w > 0 && h > 0) "$w × $h" else null,
        e?.fileSizeInByte?.let { formatBytes(it) },
        a.fileCreatedAt.take(10).ifBlank { null },
    ).joinToString(Strings.str(R.string.list_separator_wide))
}

/** Fallback when the server gives no suggestion: the biggest file. */
private fun bestOf(assets: List<AssetDto>): AssetDto =
    assets.maxByOrNull { (it.exifInfo?.fileSizeInByte ?: 0L) } ?: assets.first()
