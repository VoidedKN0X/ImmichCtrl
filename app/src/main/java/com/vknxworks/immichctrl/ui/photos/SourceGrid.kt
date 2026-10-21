package com.vknxworks.immichctrl.ui.photos

import androidx.compose.runtime.CompositionLocalProvider
import com.vknxworks.immichctrl.ui.components.LocalHazeState
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.settings.GroupBy
import com.vknxworks.immichctrl.data.timeline.AssetSource
import com.vknxworks.immichctrl.data.timeline.ListSource
import com.vknxworks.immichctrl.data.timeline.PagedSource
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.data.timeline.TimelineSource
import com.vknxworks.immichctrl.ui.components.LoaderStore
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.nav.LocalHideTabs
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.R

/** Keeps a source alive for the screen's back stack entry and makes it reachable by key (for the viewer). */
@Composable
fun <S : AssetSource> rememberSource(key: String, create: (CoroutineScope) -> S): S {
    val store: LoaderStore = viewModel()
    @Suppress("UNCHECKED_CAST")
    return remember(key) {
        val s = store.map.getOrPut("S:$key") { create(store.viewModelScope) } as S
        Sources.register(s)
        s
    }
}

@Composable
fun rememberTimeline(key: String, query: TimelineQuery, withLocal: Boolean = false): TimelineSource {
    val c = LocalContext.current.container
    return rememberSource(key) { scope ->
        TimelineSource(key, c.api, query, scope, local = if (withLocal) suspend { c.local.snapshot() } else null)
    }
}

@Composable
fun rememberList(key: String, fetch: suspend () -> List<Asset>): ListSource =
    rememberSource(key) { scope -> ListSource(key, scope, fetch) }

@Composable
fun rememberPaged(key: String, fetch: suspend (Int) -> Pair<List<Asset>, Boolean>): PagedSource =
    rememberSource(key) { scope -> PagedSource(key, scope, fetch) }

/**
 * A source's grid with everything around it: selecting (and the bar that comes with it), the back
 * button clearing a selection, pinch-to-zoom remembered in settings, and opening the viewer.
 */
@Composable
fun SourceGrid(
    source: AssetSource,
    context: SelectionContext,
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    topPadding: Dp = 0.dp,
    scrollerTopInset: Dp = 0.dp,
    albumId: String? = null,
    canEditAlbum: Boolean = false,
    selectable: Boolean = true,
    groupBy: GroupBy? = null,
    state: LazyListState = rememberLazyListState(),
    empty: @Composable () -> Unit = { MessageView(Icons.Outlined.PhotoLibrary, stringResource(R.string.nothing_here_yet)) },
    header: (LazyListScope.() -> Unit)? = null,
) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val hideTabs = LocalHideTabs.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val selection = remember { Selection() }

    BackHandler(enabled = selection.active) { selection.clear() }
    DisposableEffect(selection.active) {
        hideTabs.value = selection.active
        onDispose { hideTabs.value = false }
    }

    // The selection bar blurs the grid behind it. It needs its own haze source: the app-wide one
    // wraps this whole screen, and a glass surface inside its own source draws nothing (see-through).
    val haze = rememberHazeState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().hazeSource(haze)) {
            topBar?.invoke()
            AssetGrid(
                source = source,
                selection = selection,
                columns = settings.columns,
                onColumnsChange = { n -> scope.launch { c.settings.update { it.copy(columns = n) } } },
                onOpen = { i, a -> nav.viewer(source.key, i, a.id) },
                groupBy = groupBy ?: settings.groupBy,
                state = state,
                topPadding = topPadding,
                scrollerTopInset = scrollerTopInset,
                // Read-only mode: look, don't touch.
                selectable = selectable && !settings.readOnly,
                indicator = settings.storageIndicator,
                empty = empty,
                header = header,
            )
        }
        if (selection.active) {
            CompositionLocalProvider(LocalHazeState provides haze) {
                SelectionBar(
                    selection,
                    context,
                    Modifier.align(Alignment.BottomCenter),
                    albumId = albumId,
                    canEditAlbum = canEditAlbum,
                )
            }
        }
    }
}
