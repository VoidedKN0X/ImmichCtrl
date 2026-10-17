package com.vknxworks.immichctrl.ui.library

import com.vknxworks.immichctrl.data.api.peopleShared
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Null
import com.vknxworks.immichctrl.data.api.PersonDto
import com.vknxworks.immichctrl.data.api.TimelineQuery
import com.vknxworks.immichctrl.data.api.mergePeople
import com.vknxworks.immichctrl.data.api.people
import com.vknxworks.immichctrl.data.api.person
import com.vknxworks.immichctrl.data.api.personStats
import com.vknxworks.immichctrl.data.api.updatePerson
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PersonFace
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberTimeline
import com.vknxworks.immichctrl.ui.theme.Gutter
import java.time.LocalDate
import java.time.Period

@Composable
fun PeopleScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val people = rememberLoader("people") { c.api.people(withHidden = true) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    // Immich 3 lets people be shared between users; mark the ones someone else shared with us.
    val sharedBy = rememberLoader("people-shared") {
        if (c.api.serverMajor < 3) emptyMap()
        else runCatching { c.api.peopleShared().associate { p -> p.id to p.sharedBy.orEmpty().map { it.name } } }.getOrDefault(emptyMap())
    }
    val sharers = (sharedBy.state as? Load.Ok)?.value.orEmpty()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("People")
        when (val s = people.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { people.reload() }
            is Load.Ok -> {
                val all = s.value.people
                val shown = all.filter { it.isHidden == showHidden }
                    .sortedWith(compareByDescending<PersonDto> { it.isFavorite }.thenBy { it.name.isBlank() }.thenBy { it.name.lowercase() })
                if (all.isEmpty()) {
                    MessageView(Icons.Outlined.People, "No people yet", "Immich finds faces once facial recognition has run on your photos.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!showHidden, onClick = { showHidden = false }, label = { Text("Shown") })
                                FilterChip(showHidden, onClick = { showHidden = true }, label = { Text("Hidden (${s.value.hidden})") })
                            }
                        }
                        items(shown, key = { it.id }) { p ->
                            Column(
                                Modifier.clip(MaterialTheme.shapes.medium).clickable { nav.person(p.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    PersonFace(p.id, p.updatedAt, Modifier.fillMaxWidth().aspectRatio(1f))
                                    if (p.isFavorite) Icon(Icons.Filled.Favorite, "Favourite", Modifier.align(Alignment.BottomEnd).size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                                Text(
                                    p.name.ifBlank { "Add a name" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (p.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                sharers[p.id]?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { names ->
                                    Text(
                                        "From " + names.joinToString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonScreen(id: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val person = rememberLoader("person:$id") { c.api.person(id) }
    val stats = rememberLoader("person-stats:$id") { c.api.personStats(id) }
    val source = rememberTimeline("person:$id", TimelineQuery(personId = id, visibility = "timeline", withStacked = true))
    var menu by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var birthday by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    val p = person.value

    fun changed() {
        person.reload(silent = true)
        Sources.refreshAll()
    }

    SourceGrid(
        source = source,
        context = SelectionContext.TIMELINE,
        topBar = {
            ScreenTopBar(p?.name?.ifBlank { "Unnamed person" } ?: "") {
                IconButton(onClick = { menu = true }, enabled = p != null) { Icon(Icons.Filled.MoreVert, "More") }
            }
        },
        header = {
            item(key = "person-header") {
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonFace(id, p?.updatedAt, Modifier.size(72.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p?.name?.ifBlank { null } ?: "Add a name",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clickable { naming = true },
                        )
                        val age = p?.birthDate?.let { runCatching { Period.between(LocalDate.parse(it.take(10)), LocalDate.now()).years }.getOrNull() }
                        Text(
                            listOfNotNull(
                                stats.value?.let { countLabel(it.assets, "photo") },
                                age?.let { "Age $it" },
                                if (p?.isHidden == true) "Hidden" else null,
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )

    if (menu && p != null) {
        ModalBottomSheet(
            onDismissRequest = { menu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(p.name.ifBlank { "Unnamed person" })
                ActionItem(Icons.Outlined.Edit, if (p.name.isBlank()) "Add a name" else "Rename") { menu = false; naming = true }
                ActionItem(Icons.Outlined.Cake, if (p.birthDate == null) "Add birthday" else "Change birthday (${shortDate(p.birthDate)})") { menu = false; birthday = true }
                ActionItem(if (p.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (p.isFavorite) "Remove from favourites" else "Add to favourites") {
                    menu = false
                    c.runTask { c.api.updatePerson(p.id, "isFavorite" to !p.isFavorite); changed() }
                }
                ActionItem(if (p.isHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (p.isHidden) "Show this person" else "Hide this person") {
                    menu = false
                    c.runTask { c.api.updatePerson(p.id, "isHidden" to !p.isHidden); changed() }
                }
                ActionItem(Icons.AutoMirrored.Outlined.CallMerge, "Merge with another person") { menu = false; merging = true }
            }
        }
    }
    if (naming && p != null) {
        TextInputDialog("Name", "Name", "Save", initial = p.name, allowEmpty = true, onDismiss = { naming = false }) { name ->
            naming = false
            c.runTask { c.api.updatePerson(p.id, "name" to name); changed() }
        }
    }
    if (birthday && p != null) {
        TextInputDialog(
            "Birthday", "YYYY-MM-DD (leave empty to clear)", "Save",
            initial = p.birthDate?.take(10).orEmpty(), allowEmpty = true,
            onDismiss = { birthday = false },
        ) { text ->
            birthday = false
            val date = text.trim().ifEmpty { null }
            if (date != null && runCatching { LocalDate.parse(date) }.isFailure) {
                c.runTask { throw IllegalArgumentException("Use the format YYYY-MM-DD, e.g. 1990-04-21") }
            } else {
                c.runTask { c.api.updatePerson(p.id, "birthDate" to (date ?: Null)); changed() }
            }
        }
    }
    if (merging && p != null) MergeSheet(p, onDismiss = { merging = false }) { other ->
        merging = false
        c.runTask("Merged") {
            c.api.mergePeople(p.id, listOf(other.id))
            changed()
            source.refresh(silent = true)
        }
    }
}

/** Pick a person to fold into [into]: their faces become this person's. */
@Composable
private fun MergeSheet(into: PersonDto, onDismiss: () -> Unit, onPick: (PersonDto) -> Unit) {
    val c = LocalContext.current.container
    val people = rememberLoader("people-merge") { c.api.people(withHidden = false) }
    var confirm by remember { mutableStateOf<PersonDto?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SheetTitle("Merge into ${into.name.ifBlank { "this person" }}", "Pick who else these photos show")
        when (val s = people.state) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp)) { LoadingView() }
            is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            is Load.Ok -> LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(s.value.people.filter { it.id != into.id }, key = { it.id }) { p ->
                    Column(Modifier.clickable { confirm = p }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PersonFace(p.id, p.updatedAt, Modifier.size(64.dp).clip(CircleShape))
                        Text(p.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    confirm?.let { other ->
        ConfirmDialog(
            "Merge ${other.name.ifBlank { "this person" }}?",
            "Their faces will belong to ${into.name.ifBlank { "this person" }}. This can't be undone.",
            "Merge",
            destructive = false,
            onDismiss = { confirm = null },
        ) {
            confirm = null
            onPick(other)
        }
    }
}
