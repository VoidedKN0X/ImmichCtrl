package com.vknxworks.immichctrl.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import com.vknxworks.immichctrl.ui.theme.CtrlType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.settings.GroupBy
import com.vknxworks.immichctrl.data.timeline.AssetSource
import com.vknxworks.immichctrl.data.timeline.Bucket
import com.vknxworks.immichctrl.data.timeline.PagedSource
import com.vknxworks.immichctrl.data.timeline.SourceState
import com.vknxworks.immichctrl.ui.components.AssetThumb
import com.vknxworks.immichctrl.ui.components.Badge
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.LocalBottomClearance
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.dayLabel
import com.vknxworks.immichctrl.ui.components.formatClock
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.R

// ---------------------------------------------------------------------------------------------
// Selection
// ---------------------------------------------------------------------------------------------

/** Selected assets, by id. Survives scrolling; cleared by the screen. */
class Selection {
    val items = mutableStateMapOf<String, Asset>()
    val active: Boolean get() = items.isNotEmpty()
    val size: Int get() = items.size
    val ids: Set<String> get() = items.keys.toSet()
    val assets: List<Asset> get() = items.values.toList()

    operator fun contains(id: String) = items.containsKey(id)
    fun toggle(a: Asset) {
        if (items.containsKey(a.id)) items.remove(a.id) else items[a.id] = a
    }
    fun set(a: Asset, selected: Boolean) {
        if (selected) items[a.id] = a else items.remove(a.id)
    }
    fun clear() = items.clear()
}

// ---------------------------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------------------------

private sealed interface GridItem {
    val key: String
    val bucket: Int
}

private data class MonthHeader(override val key: String, override val bucket: Int, val label: String) : GridItem
private data class DayHeader(override val key: String, override val bucket: Int, val date: LocalDate, val assets: List<Asset>) : GridItem
private data class AssetRow(override val key: String, override val bucket: Int, val start: Int, val assets: List<Asset>) : GridItem
private data class PlaceholderRow(override val key: String, override val bucket: Int, val cells: Int) : GridItem

private fun buildGrid(buckets: List<Bucket>, cols: Int, groupBy: GroupBy): List<GridItem> {
    val out = ArrayList<GridItem>()
    var global = 0
    buckets.forEachIndexed { bi, b ->
        if (b.label != null) out += MonthHeader("m:${b.key}", bi, b.label)
        val a = b.assets
        if (a == null) {
            val rows = (b.count + cols - 1) / cols
            for (r in 0 until rows) {
                val cells = if (r == rows - 1 && b.count % cols != 0) b.count % cols else cols
                out += PlaceholderRow("p:${b.key}:$r", bi, cells)
            }
        } else if (groupBy == GroupBy.DAY && b.label != null) {
            var i = 0
            while (i < a.size) {
                val day = a[i].localDate
                var j = i
                while (j < a.size && a[j].localDate == day) j++
                val dayAssets = a.subList(i, j)
                out += DayHeader("d:${b.key}:$day:$i", bi, day, dayAssets)
                dayAssets.chunked(cols).forEachIndexed { r, row ->
                    out += AssetRow("r:${row.first().id}", bi, global + i + r * cols, row)
                }
                i = j
            }
        } else {
            a.chunked(cols).forEachIndexed { r, row ->
                out += AssetRow("r:${row.first().id}", bi, global + r * cols, row)
            }
        }
        global += b.size
    }
    return out
}

// ---------------------------------------------------------------------------------------------
// The grid
// ---------------------------------------------------------------------------------------------

/**
 * Photo grid for any [AssetSource]. Tap opens, long-press starts selecting (keep dragging to select
 * a run), pinch changes the number of columns, the right edge has a fast scroller.
 */
@Composable
fun AssetGrid(
    source: AssetSource,
    selection: Selection,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onOpen: (index: Int, asset: Asset) -> Unit,
    modifier: Modifier = Modifier,
    groupBy: GroupBy = GroupBy.DAY,
    state: LazyListState = rememberLazyListState(),
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    /** Keeps the fast scroller's touch strip clear of buttons in a title row above the grid. */
    scrollerTopInset: androidx.compose.ui.unit.Dp = 0.dp,
    selectable: Boolean = true,
    /** Cloud icons for photos only on this phone, or backed up from it. */
    indicator: Boolean = false,
    empty: @Composable () -> Unit = { MessageView(Icons.Outlined.PhotoLibrary, stringResource(R.string.nothing_here_yet)) },
    header: (LazyListScope.() -> Unit)? = null,
) {
    val cols = columns.coerceIn(2, 7)
    val gridItems = remember(source.buckets, cols, groupBy) { buildGrid(source.buckets, cols, groupBy) }
    val byKey = remember(gridItems) { gridItems.associateBy { it.key } }
    val headerCount = if (header != null) 1 else 0
    val clearance = LocalBottomClearance.current
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = (if (clearance > 0.dp) clearance else navBar) + 24.dp
    val haptics = LocalHapticFeedback.current

    // Load month buckets as they come near the screen.
    LaunchedEffect(source, gridItems) {
        snapshotFlow {
            val vis = state.layoutInfo.visibleItemsInfo
            if (vis.isEmpty()) null else (vis.first().index to vis.last().index)
        }.distinctUntilChanged().collect { range ->
            range ?: return@collect
            val from = (range.first - headerCount - 30).coerceAtLeast(0)
            val to = (range.second - headerCount + 30).coerceAtMost(gridItems.lastIndex)
            if (from > to) return@collect
            val wanted = (from..to).mapNotNullTo(HashSet()) { gridItems.getOrNull(it)?.takeIf { g -> g is PlaceholderRow }?.bucket }
            wanted.forEach { source.ensureLoaded(it) }
            if (source.canLoadMore && range.second >= headerCount + gridItems.size - 8) source.loadMore()
        }
    }

    // ---- drag-to-select state ----
    var gridWidth by remember { mutableIntStateOf(1) }
    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    var dragBase by remember { mutableStateOf<Map<String, Asset>>(emptyMap()) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(gridItems)
    val currentByKey by rememberUpdatedState(byKey)
    val currentCols by rememberUpdatedState(cols)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnColumns by rememberUpdatedState(onColumnsChange)
    val density = LocalDensity.current
    val edgePx = with(density) { 72.dp.toPx() }

    /** Which asset (global index) is under a point in the list. */
    fun hit(pos: Offset): Pair<Int, Asset>? {
        val info = state.layoutInfo
        val y = pos.y.toInt() + info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return null
        val row = (item.key as? String)?.let { currentByKey[it] } as? AssetRow ?: return null
        val col = (pos.x / (gridWidth.toFloat() / currentCols)).toInt().coerceIn(0, currentCols - 1)
        val a = row.assets.getOrNull(col) ?: return null
        return (row.start + col) to a
    }

    fun applyDrag(pos: Offset) {
        val anchor = dragAnchor ?: return
        val (idx, _) = hit(pos) ?: return
        val lo = minOf(anchor, idx)
        val hi = maxOf(anchor, idx)
        val sel = currentSelection
        val wanted = HashMap(dragBase)
        for (i in lo..hi) source.assetAt(i)?.let { wanted[it.id] = it }
        sel.items.keys.filterNot { it in wanted }.forEach { sel.items.remove(it) }
        wanted.forEach { (k, v) -> if (!sel.items.containsKey(k)) sel.items[k] = v }
    }

    // While dragging a selection near the top or bottom edge, keep scrolling.
    val scrollingAtEdge = autoScroll != 0f
    LaunchedEffect(scrollingAtEdge) {
        if (!scrollingAtEdge) return@LaunchedEffect
        while (isActive && autoScroll != 0f) {
            state.scrollBy(autoScroll)
            dragPos?.let { applyDrag(it) }
            delay(16)
        }
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis

    val content: @Composable () -> Unit = {
        when (val s = source.state) {
            is SourceState.Loading -> if (source.buckets.isEmpty()) LoadingView() else Unit
            is SourceState.Failed -> if (source.buckets.isEmpty()) ErrorView(s.message) { source.refresh() } else Unit
            else -> Unit
        }
        val showList = source.buckets.isNotEmpty() || (source.state is SourceState.Ready)
        if (showList) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(top = topPadding, bottom = bottom),
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { gridWidth = it.width.coerceAtLeast(1) }
                        // Pinch: two fingers change the column count (consumed before the list can scroll).
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var zoom = 1f
                                do {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    if (ev.changes.count { it.pressed } >= 2) {
                                        zoom *= ev.calculateZoom()
                                        if (zoom > 1.3f) {
                                            currentOnColumns(currentCols - 1)
                                            zoom = 1f
                                        } else if (zoom < 0.77f) {
                                            currentOnColumns(currentCols + 1)
                                            zoom = 1f
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                } while (ev.changes.any { it.pressed })
                            }
                        }
                        // Tap to open (or toggle while selecting); long-press and drag to select a run.
                        .pointerInput(selectable) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var cancelled = false
                                var tapped = false
                                val result = withTimeoutOrNull(longPressMs) {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id }
                                        if (ch == null || ev.changes.size > 1) {
                                            cancelled = true
                                            break
                                        }
                                        if (ch.changedToUp()) {
                                            tapped = !ch.isConsumed
                                            break
                                        }
                                        if (ch.isConsumed || (ch.position - down.position).getDistance() > touchSlop) {
                                            cancelled = true
                                            break
                                        }
                                    }
                                }
                                if (cancelled) return@awaitEachGesture
                                if (result != null || tapped) {
                                    if (!tapped) return@awaitEachGesture
                                    val (idx, a) = hit(down.position) ?: return@awaitEachGesture
                                    if (currentSelection.active) currentSelection.toggle(a) else currentOnOpen(idx, a)
                                    return@awaitEachGesture
                                }
                                // Long press.
                                if (!selectable) return@awaitEachGesture
                                val (idx, a) = hit(down.position) ?: return@awaitEachGesture
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentSelection.set(a, true)
                                dragBase = currentSelection.items.toMap()
                                dragAnchor = idx
                                dragPos = down.position
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        ch.consume()
                                        dragPos = ch.position
                                        applyDrag(ch.position)
                                        val h = size.height
                                        autoScroll = when {
                                            ch.position.y < edgePx -> -((edgePx - ch.position.y) / edgePx * 30f)
                                            ch.position.y > h - edgePx -> ((ch.position.y - (h - edgePx)) / edgePx * 30f)
                                            else -> 0f
                                        }
                                    }
                                } finally {
                                    autoScroll = 0f
                                    dragAnchor = null
                                    dragPos = null
                                }
                            }
                        },
                ) {
                    if (header != null) header()
                    items(gridItems, key = { it.key }, contentType = { it::class.simpleName }) { item ->
                        when (item) {
                            is MonthHeader -> MonthHeaderRow(item.label)
                            is DayHeader -> DayHeaderRow(item, selection)
                            is AssetRow -> AssetRowView(item, cols, selection, indicator)
                            is PlaceholderRow -> PlaceholderRowView(item.cells, cols)
                        }
                    }
                    if (source is PagedSource && source.loadingMore) {
                        item(key = "more") { Box(Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
                if (source.state is SourceState.Ready && source.total == 0) {
                    Box(Modifier.fillMaxSize().padding(top = topPadding)) { empty() }
                }
                GridFastScroller(
                    state = state,
                    total = gridItems.size,
                    headerItems = headerCount,
                    label = { i ->
                        val g = currentItems.getOrNull(i) ?: return@GridFastScroller null
                        source.buckets.getOrNull(g.bucket)?.label
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = topPadding + scrollerTopInset, bottom = bottom),
                )
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = source.refreshing,
        onRefresh = { source.refresh(silent = true) },
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}

@Composable
private fun MonthHeaderRow(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 22.dp, bottom = 4.dp),
    )
}

@Composable
private fun DayHeaderRow(item: DayHeader, selection: Selection) {
    val allSelected = item.assets.all { it.id in selection }
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(item.date).uppercase(),
            style = CtrlType.Label,
            color = CtrlColors.Muted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selection.active) {
            // Select or clear the whole day.
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable {
                    item.assets.forEach { selection.set(it, !allSelected) }
                },
                contentAlignment = Alignment.Center,
            ) { SelectMark(allSelected) }
        }
    }
}

@Composable
private fun AssetRowView(row: AssetRow, cols: Int, selection: Selection, indicator: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until cols) {
            val a = row.assets.getOrNull(i)
            Box(Modifier.weight(1f).aspectRatio(1f)) {
                if (a != null) AssetCell(a, a.id in selection, selection.active, cols, indicator)
            }
        }
    }
}

@Composable
private fun PlaceholderRowView(cells: Int, cols: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until cols) {
            Box(
                Modifier.weight(1f).aspectRatio(1f)
                    .then(if (i < cells) Modifier.clip(CtrlShapes.Tile).background(CtrlColors.Surface) else Modifier),
            )
        }
    }
}

@Composable
private fun AssetCell(a: Asset, selected: Boolean, selecting: Boolean, cols: Int, indicator: Boolean) {
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "sel-scale")
    Box(Modifier.fillMaxSize().clip(CtrlShapes.Tile)) {
        AssetThumb(
            a,
            Modifier.fillMaxSize().scale(scale).clip(if (selected) RoundedCornerShape(10.dp) else CtrlShapes.Tile)
                .then(if (selected) Modifier.border(2.dp, CtrlColors.Accent, RoundedCornerShape(10.dp)) else Modifier),
            size = if (cols <= 2) "preview" else "thumbnail",
        )
        if (indicator && !selecting && cols <= 5 && (a.isLocalOnly || a.onDevice)) {
            Icon(
                if (a.isLocalOnly) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                if (a.isLocalOnly) stringResource(R.string.only_on_this_phone) else stringResource(R.string.backed_up_from_this_phone),
                Modifier.align(Alignment.TopStart).padding(5.dp).size(15.dp),
                tint = Color.White,
            )
        }
        if (a.isVideo) {
            Badge(a.durationMs?.let { formatClock(it) } ?: "", Modifier.align(Alignment.TopEnd).padding(5.dp), icon = Icons.Filled.PlayArrow)
        } else if (a.livePhotoVideoId != null && cols <= 4) {
            Icon(Icons.Outlined.MotionPhotosOn, stringResource(R.string.live_photo), Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp), tint = Color.White)
        }
        if (a.stackCount > 1 && cols <= 5) {
            Badge(a.stackCount.toString(), Modifier.align(Alignment.BottomEnd).padding(5.dp), icon = Icons.Outlined.Collections)
        }
        if (a.isFavorite && cols <= 5) {
            Icon(Icons.Filled.Favorite, stringResource(R.string.favourite), Modifier.align(Alignment.BottomStart).padding(5.dp).size(16.dp), tint = Color.White)
        }
        if (selecting) {
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { SelectMark(selected) }
        }
    }
}

@Composable
fun SelectMark(selected: Boolean) {
    if (selected) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(CtrlColors.Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, stringResource(R.string.selected), Modifier.size(16.dp), tint = CtrlColors.OnAccent)
        }
    } else {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.25f)).border(2.dp, CtrlColors.Text, RoundedCornerShape(7.dp)))
    }
}

/**
 * A thumb on the right edge for jumping through years of photos. Dragging shows the month there.
 */
@Composable
private fun GridFastScroller(
    state: LazyListState,
    total: Int,
    headerItems: Int,
    label: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    if (total < 40) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val thumbH = 48.dp
    val thumbPx = with(density) { thumbH.toPx() }
    val scrolling = state.isScrollInProgress || dragging
    val listFraction = ((state.firstVisibleItemIndex - headerItems).coerceAtLeast(0).toFloat() / (total - 1)).coerceIn(0f, 1f)
    val fraction = if (dragging) dragFraction else listFraction
    val thumbY = ((trackPx - thumbPx) * fraction).roundToInt().coerceAtLeast(0)
    val index = (fraction * (total - 1)).roundToInt()
    var lastJump by remember { mutableIntStateOf(-1) }

    Box(
        modifier
            .fillMaxHeight()
            .width(40.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { trackPx = it.height }
            .pointerInput(total) {
                fun jump(y: Float) {
                    dragFraction = ((y - thumbPx / 2) / (trackPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val target = headerItems + (dragFraction * (total - 1)).roundToInt()
                    if (target != lastJump) {
                        lastJump = target
                        scope.launch { state.scrollToItem(target) }
                    }
                }
                detectVerticalDragGestures(
                    onDragStart = { o -> dragging = true; jump(o.y) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, _ -> change.consume(); jump(change.position.y) },
                )
            },
    ) {
        AnimatedVisibility(scrolling, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.offset { IntOffset(0, thumbY) }.align(Alignment.TopEnd)) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(width = 6.dp, height = thumbH)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (dragging) CtrlColors.Accent else CtrlColors.Muted.copy(alpha = 0.6f)),
            )
        }
    }
    if (dragging) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val text = label(index) ?: "…"
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { 52.dp.roundToPx() }, thumbY + with(density) { 8.dp.roundToPx() }) }
                    .clip(CtrlShapes.Button)
                    .background(CtrlColors.Accent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.uppercase(), style = CtrlType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = CtrlColors.OnAccent)
            }
        }
    }
}
