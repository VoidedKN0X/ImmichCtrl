package com.vknxworks.immichctrl.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.explore
import com.vknxworks.immichctrl.data.api.metadataSearch
import com.vknxworks.immichctrl.data.api.people
import com.vknxworks.immichctrl.data.api.smartSearch
import com.vknxworks.immichctrl.data.settings.GroupBy
import com.vknxworks.immichctrl.ui.components.AssetThumb
import com.vknxworks.immichctrl.ui.components.LocalBottomClearance
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PersonFace
import com.vknxworks.immichctrl.ui.components.ProfileButton
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.nav.CollectionRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.PeopleRoute
import com.vknxworks.immichctrl.ui.nav.PlaceRoute
import com.vknxworks.immichctrl.ui.nav.PlacesRoute
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberPaged
import com.vknxworks.immichctrl.ui.theme.Gutter

private enum class Mode(val label: String) { SMART("Smart"), FILENAME("File name"), DESCRIPTION("Description"), TEXT("Text in photo") }

@Composable
fun SearchScreen() {
    val c = LocalContext.current.container
    val features by c.features.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(if (features.smartSearch) Mode.SMART else Mode.FILENAME) }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var editing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val modes = Mode.entries.filter { (it != Mode.SMART || features.smartSearch) && (it != Mode.TEXT || features.ocr) }

    fun submit(q: String = text) {
        text = q
        query = q.trim()
        focus.clearFocus()
    }

    val searchBar: @Composable () -> Unit = {
        Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(if (mode == Mode.SMART) "Search photos, e.g. \"dog on a beach\"" else "Search by ${mode.label.lowercase()}")
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        Row {
                            if (text.isNotEmpty()) IconButton(onClick = { text = ""; query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                            IconButton(onClick = { editing = true }) {
                                BadgedBox(badge = { if (filters.active > 0) Badge { Text(filters.active.toString()) } }) {
                                    Icon(Icons.Outlined.Tune, "Filters")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                ProfileButton()
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modes.forEach { m -> FilterChip(mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                filters.summary().forEach { label ->
                    InputChip(selected = true, onClick = { editing = true }, label = { Text(label) })
                }
            }
        }
    }

    if (editing) {
        FiltersSheet(filters, onDismiss = { editing = false }) { f ->
            filters = f
            editing = false
            if (query.isEmpty() && text.isNotBlank()) submit()
        }
    }

    if (query.isEmpty() && filters.active == 0) {
        Column(Modifier.fillMaxSize()) {
            searchBar()
            Explore(onSearch = { submit(it) })
        }
    } else {
        // Text searches use the chosen mode; filters on their own search by metadata.
        val smart = mode == Mode.SMART && query.isNotEmpty()
        val key = "search:${mode.name}:${filters.hashCode()}:$query"
        val source = rememberPaged(key) { page ->
            val params = filters.params(smart)
            val res = when {
                smart -> c.api.smartSearch(query, page, 100, params)
                query.isEmpty() -> c.api.metadataSearch(page, 100, params)
                mode == Mode.DESCRIPTION -> c.api.metadataSearch(page, 100, params + ("description" to query))
                mode == Mode.TEXT -> c.api.metadataSearch(page, 100, params + ("ocr" to query))
                else -> c.api.metadataSearch(page, 100, params + ("originalFileName" to query))
            }
            res.assets.items.map { it.toAsset() } to (res.assets.nextPage != null)
        }
        SourceGrid(
            source,
            SelectionContext.TIMELINE,
            topBar = searchBar,
            groupBy = if (smart) GroupBy.MONTH else null,
            empty = { MessageView(Icons.Outlined.ImageSearch, "No results", "Try different words, fewer filters, or another search type.") },
        )
    }
}

@Composable
private fun Explore(onSearch: (String) -> Unit) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val people = rememberLoader("explore-people") { c.api.people(withHidden = false, size = 30).people.filter { it.name.isNotBlank() }.take(20) }
    val places = rememberLoader("explore-places") { c.api.explore().firstOrNull { it.fieldName.contains("city") }?.items.orEmpty().take(20) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomClearance.current + 24.dp)) {
        val ppl = people.value.orEmpty()
        if (ppl.isNotEmpty()) {
            SectionHeader("People") { nav.navigate(PeopleRoute) }
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(ppl, key = { it.id }) { p ->
                    Column(Modifier.width(76.dp).clickable { nav.person(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PersonFace(p.id, p.updatedAt, Modifier.size(68.dp))
                        Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        val pl = places.value.orEmpty()
        if (pl.isNotEmpty()) {
            SectionHeader("Places") { nav.navigate(PlacesRoute) }
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(pl, key = { it.value }) { item ->
                    Box(Modifier.size(112.dp).clip(MaterialTheme.shapes.medium).clickable { nav.navigate(PlaceRoute(item.value)) }) {
                        AssetThumb(item.data.id, item.data.thumbhash, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                        Text(
                            item.value,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        )
                    }
                }
            }
        }
        SectionHeader("Browse", null)
        Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BrowseRow(Icons.Outlined.FavoriteBorder, "Favourites") { nav.navigate(CollectionRoute("favorites")) }
            BrowseRow(Icons.Outlined.VideoLibrary, "Videos") { nav.navigate(CollectionRoute("videos")) }
            BrowseRow(Icons.Outlined.NewReleases, "Recently added") { nav.navigate(CollectionRoute("recent")) }
            BrowseRow(Icons.Outlined.Screenshot, "Screenshots") { onSearch("screenshot") }
            BrowseRow(Icons.Outlined.People, "People") { nav.navigate(PeopleRoute) }
            BrowseRow(Icons.Outlined.Place, "Places") { nav.navigate(PlacesRoute) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onAll != null) TextButton(onClick = onAll) { Text("See all") }
    }
}

@Composable
private fun BrowseRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
