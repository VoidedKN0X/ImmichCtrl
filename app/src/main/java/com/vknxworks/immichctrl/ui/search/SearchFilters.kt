package com.vknxworks.immichctrl.ui.search

import com.vknxworks.immichctrl.ui.components.CtrlSwitch
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.people
import com.vknxworks.immichctrl.data.api.searchSuggestions
import com.vknxworks.immichctrl.data.api.searchSuggestionsFor
import com.vknxworks.immichctrl.ui.components.PersonFace
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.rememberLoader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/** Everything a search can be narrowed by; sent to both smart and metadata search. */
data class SearchFilters(
    val people: Map<String, String> = emptyMap(), // id -> name
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val make: String? = null,
    val model: String? = null,
    val takenAfter: LocalDate? = null,
    val takenBefore: LocalDate? = null,
    /** IMAGE, VIDEO or null for both. */
    val type: String? = null,
    val favorites: Boolean = false,
    val includeArchived: Boolean = false,
    val notInAlbum: Boolean = false,
    val oldestFirst: Boolean = false,
) {
    val active: Int
        get() = listOfNotNull(
            people.takeIf { it.isNotEmpty() }, country, state, city, make, model, takenAfter ?: takenBefore, type,
            favorites.takeIf { it }, includeArchived.takeIf { it }, notInAlbum.takeIf { it }, oldestFirst.takeIf { it },
        ).size

    fun params(smart: Boolean): Map<String, Any?> = buildMap {
        if (people.isNotEmpty()) put("personIds", people.keys.toList())
        country?.let { put("country", it) }
        state?.let { put("state", it) }
        city?.let { put("city", it) }
        make?.let { put("make", it) }
        model?.let { put("model", it) }
        takenAfter?.let { put("takenAfter", it.atStartOfDay(ZoneOffset.UTC).toInstant().toString()) }
        takenBefore?.let { put("takenBefore", it.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString()) }
        type?.let { put("type", it) }
        if (favorites) put("isFavorite", true)
        if (!includeArchived) put("visibility", "timeline")
        if (notInAlbum) put("isNotInAlbum", true)
        // Smart search is ordered by how well photos match, so only date searches can be sorted.
        if (!smart) put("order", if (oldestFirst) "asc" else "desc")
    }

    fun summary(): List<String> = buildList {
        if (people.isNotEmpty()) add(people.values.joinToString(", ").ifBlank { Strings.str(R.string.people_2, people.size) })
        listOfNotNull(city, state, country).firstOrNull()?.let { add(it) }
        listOfNotNull(make, model).joinToString(" ").takeIf { it.isNotBlank() }?.let { add(it) }
        if (takenAfter != null || takenBefore != null) add(listOfNotNull(takenAfter, takenBefore).joinToString(" – ") { it.format(DateTimeFormatter.ofPattern("d MMM yyyy")) })
        type?.let { add(if (it == "VIDEO") Strings.str(R.string.videos) else Strings.str(R.string.photos)) }
        if (favorites) add(Strings.str(R.string.favourites))
        if (includeArchived) add(Strings.str(R.string.with_archived))
        if (notInAlbum) add(Strings.str(R.string.not_in_an_album))
        if (oldestFirst) add(Strings.str(R.string.oldest_first))
    }
}

@Composable
fun FiltersSheet(initial: SearchFilters, onDismiss: () -> Unit, onApply: (SearchFilters) -> Unit) {
    val c = LocalContext.current.container
    var f by remember { mutableStateOf(initial) }
    var dates by remember { mutableStateOf(false) }
    val people = rememberLoader("filter-people") { c.api.people(withHidden = false).people.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() } }
    val countries = rememberLoader("filter-country") { runCatching { c.api.searchSuggestions("country") }.getOrDefault(emptyList()).sorted() }
    val makes = rememberLoader("filter-make") { runCatching { c.api.searchSuggestions("camera-make") }.getOrDefault(emptyList()).sorted() }
    // Cities and models narrow with the country and make chosen.
    val cities = rememberLoader("filter-city:${f.country}") {
        runCatching { c.api.searchSuggestionsFor("city", "country" to f.country) }.getOrDefault(emptyList()).sorted()
    }
    val models = rememberLoader("filter-model:${f.make}") {
        runCatching { c.api.searchSuggestionsFor("camera-model", "make" to f.make) }.getOrDefault(emptyList()).sorted()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = CtrlShapes.Sheet,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { f = SearchFilters() }) { Text(stringResource(R.string.clear_all)) }
            }

            Section(stringResource(R.string.people))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                people.value.orEmpty().take(60).forEach { p ->
                    val on = p.id in f.people
                    Column(
                        Modifier.width(64.dp).clickable {
                            f = f.copy(people = if (on) f.people - p.id else f.people + (p.id to p.name))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PersonFace(p.id, p.updatedAt, Modifier.size(if (on) 56.dp else 48.dp))
                        Text(
                            p.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Section(stringResource(R.string.place))
            ChipList(countries.value.orEmpty(), f.country) { f = f.copy(country = it, city = null) }
            if (f.country != null) ChipList(cities.value.orEmpty(), f.city) { f = f.copy(city = it) }

            Section(stringResource(R.string.camera))
            ChipList(makes.value.orEmpty(), f.make) { f = f.copy(make = it, model = null) }
            if (f.make != null) ChipList(models.value.orEmpty(), f.model) { f = f.copy(model = it) }

            Section(stringResource(R.string.date_taken))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(f.takenAfter == null && f.takenBefore == null, onClick = { f = f.copy(takenAfter = null, takenBefore = null) }, label = { Text(stringResource(R.string.any_time)) })
                FilterChip(f.takenAfter != null || f.takenBefore != null, onClick = { dates = true }, label = {
                    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
                    Text(
                        if (f.takenAfter != null || f.takenBefore != null) listOfNotNull(f.takenAfter, f.takenBefore).joinToString(" – ") { it.format(fmt) }
                        else stringResource(R.string.choose_dates),
                    )
                })
            }

            Section(stringResource(R.string.type))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(f.type == null, onClick = { f = f.copy(type = null) }, label = { Text(stringResource(R.string.all)) })
                FilterChip(f.type == "IMAGE", onClick = { f = f.copy(type = "IMAGE") }, label = { Text(stringResource(R.string.photos)) })
                FilterChip(f.type == "VIDEO", onClick = { f = f.copy(type = "VIDEO") }, label = { Text(stringResource(R.string.videos)) })
            }

            Section(stringResource(R.string.show))
            Toggle(stringResource(R.string.only_favourites), f.favorites) { f = f.copy(favorites = it) }
            Toggle(stringResource(R.string.include_archived), f.includeArchived) { f = f.copy(includeArchived = it) }
            Toggle(stringResource(R.string.only_photos_not_in_any_album), f.notInAlbum) { f = f.copy(notInAlbum = it) }
            Toggle(stringResource(R.string.oldest_first_not_for_smart_search), f.oldestFirst) { f = f.copy(oldestFirst = it) }

            Spacer(Modifier.height(12.dp))
            PillButton(stringResource(R.string.show_results), onClick = { onApply(f) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }
    }

    if (dates) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = f.takenAfter?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = f.takenBefore?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dates = false },
            confirmButton = {
                TextButton(onClick = {
                    fun d(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    f = f.copy(takenAfter = d(state.selectedStartDateMillis), takenBefore = d(state.selectedEndDateMillis) ?: d(state.selectedStartDateMillis))
                    dates = false
                }) { Text(stringResource(R.string.lock_ok)) }
            },
            dismissButton = { TextButton(onClick = { dates = false }) { Text(stringResource(R.string.lock_cancel)) } },
        ) {
            DateRangePicker(state, modifier = Modifier.height(520.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ChipList(values: List<String>, selected: String?, onPick: (String?) -> Unit) {
    if (values.isEmpty()) {
        Text(stringResource(R.string.nothing_to_choose_from_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
        return
    }
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.take(40).forEach { v ->
            FilterChip(selected == v, onClick = { onPick(if (selected == v) null else v) }, label = { Text(v) })
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        CtrlSwitch(on, onChange)
    }
}
