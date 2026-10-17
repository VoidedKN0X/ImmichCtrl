package com.vknxworks.immichctrl.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.AssetDto
import com.vknxworks.immichctrl.data.api.Null
import com.vknxworks.immichctrl.data.api.albums
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.api.updateAsset
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.PersonFace
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.longDate
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.TagRoute
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything Immich knows about a photo, with the description and rating editable by the owner. */
@Composable
fun InfoSheet(asset: Asset, onDismiss: () -> Unit, onEditDate: () -> Unit = {}, onEditLocation: () -> Unit = {}) {
    val c = LocalContext.current.container
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (asset.isLocalOnly || asset.id.startsWith("external:")) {
            LocalInfo(asset)
            return@ModalBottomSheet
        }
        val details = rememberLoader("info:${asset.id}") { c.api.asset(asset.id) }
        val albums = rememberLoader("info-albums:${asset.id}") { c.api.albums(assetId = asset.id) }
        when (val s = details.state) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Err -> ErrorView(s.message) { details.reload() }
            is Load.Ok -> InfoContent(asset, s.value, albums.value.orEmpty().map { it.id to it.albumName }, onDismiss, onEditDate, onEditLocation)
        }
    }
}

/** What's known about a photo that's only on this phone. */
@Composable
private fun LocalInfo(asset: Asset) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        SheetTitle("Details", "Only on this phone, not backed up")
        val local = asset.localDateTime
        InfoRow(Icons.Outlined.CalendarToday, longDate(local.toLocalDate()), local.format(DateTimeFormatter.ofPattern("HH:mm")))
        InfoRow(
            Icons.Outlined.Image,
            asset.fileName ?: "Photo",
            listOfNotNull(asset.fileSize?.let { formatBytes(it) }, asset.durationMs?.let { com.vknxworks.immichctrl.ui.components.formatClock(it) }).joinToString("  ·  "),
        )
    }
}

@Composable
private fun InfoContent(
    asset: Asset,
    d: AssetDto,
    albums: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onEditDate: () -> Unit,
    onEditLocation: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    val mine = d.ownerId == c.me
    // Read-only mode: show details, offer no edits.
    val canEdit = mine && !c.settings.settings.value.readOnly
    val exif = d.exifInfo
    var description by remember(d.id) { mutableStateOf(exif?.description.orEmpty()) }
    var rating by remember(d.id) { mutableStateOf(exif?.rating ?: 0) }
    val original = exif?.description.orEmpty()

    fun saveDescription() {
        if (description == original) return
        c.runTask("Description saved") { c.api.updateAsset(d.id, "description" to description) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        SheetTitle("Details", if (!mine) d.owner?.name?.let { "Shared by $it" } else null)

        if (canEdit) {
            OutlinedTextField(
                description, { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveDescription(); focus.clearFocus() }),
                maxLines = 4,
            )
            if (description != original) {
                Text(
                    "Save",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp).clickable { saveDescription(); focus.clearFocus() },
                )
            }
            // Star rating, tap the same star again to clear.
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                for (i in 1..5) {
                    Icon(
                        if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        "$i stars",
                        tint = if (i <= rating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp).padding(4.dp).clickable {
                            val next = if (rating == i) 0 else i
                            rating = next
                            c.runTask { c.api.updateAsset(d.id, "rating" to if (next != 0) next else if (c.api.serverMajor < 3) 0 else Null) }
                        },
                    )
                }
            }
        } else if (!exif?.description.isNullOrBlank()) {
            Text(exif?.description.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        }

        val local = asset.localDateTime
        InfoRow(
            Icons.Outlined.CalendarToday,
            longDate(local.toLocalDate()),
            listOfNotNull(local.format(DateTimeFormatter.ofPattern("HH:mm")), exif?.timeZone, if (canEdit) "Tap to change" else null).joinToString("  ·  "),
            onClick = if (canEdit) onEditDate else null,
        )
        InfoRow(
            Icons.Outlined.Image,
            d.originalFileName,
            listOfNotNull(
                exif?.fileSizeInByte?.let { formatBytes(it) },
                if (exif?.exifImageWidth != null && exif.exifImageHeight != null) "${exif.exifImageWidth} × ${exif.exifImageHeight}" else null,
                exif?.exifImageWidth?.let { w -> exif.exifImageHeight?.let { h -> "%.1f MP".format(Locale.US, w * h / 1_000_000.0) } },
            ).joinToString("  ·  "),
        )
        if (exif != null && (exif.make != null || exif.model != null)) {
            InfoRow(
                Icons.Outlined.CameraAlt,
                listOfNotNull(exif.make, exif.model).joinToString(" "),
                listOfNotNull(
                    exif.lensModel,
                    exif.fNumber?.let { "ƒ/%.1f".format(Locale.US, it) },
                    exif.exposureTime?.let { "${it}s" },
                    exif.focalLength?.let { "%.0f mm".format(Locale.US, it) },
                    exif.iso?.let { "ISO $it" },
                ).joinToString("  ·  "),
            )
        }
        val lat = exif?.latitude
        val lon = exif?.longitude
        if (canEdit && lat == null) {
            InfoRow(Icons.Outlined.Place, "No location", "Tap to add one", onClick = onEditLocation)
        }
        if (lat != null && lon != null) {
            MiniMap(lat, lon, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium))
        }
        if (canEdit && lat != null) {
            Text(
                "Change location",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 2.dp).clickable(onClick = onEditLocation),
            )
        }
        if (exif?.city != null || lat != null) {
            InfoRow(
                Icons.Outlined.Place,
                listOfNotNull(exif?.city, exif?.state, exif?.country).joinToString(", ").ifBlank { "Location" },
                if (lat != null && lon != null) "%.5f, %.5f  ·  Open in maps".format(Locale.US, lat, lon) else null,
                onClick = if (lat != null && lon != null) {
                    {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
                        }
                    }
                } else null,
            )
        }

        val people = d.people.orEmpty().filter { !it.isHidden }
        if (people.isNotEmpty()) {
            Section("People")
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { p ->
                    Column(
                        Modifier.width(72.dp).clickable { onDismiss(); nav.person(p.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PersonFace(p.id, p.updatedAt, Modifier.size(56.dp))
                        Text(p.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
        if (albums.isNotEmpty()) {
            Section("In albums")
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                albums.forEach { (id, name) ->
                    AssistChip(onClick = { onDismiss(); nav.album(id) }, label = { Text(name) }, leadingIcon = { Icon(Icons.Outlined.PhotoAlbum, null, Modifier.size(18.dp)) })
                }
            }
        }
        val tags = d.tags.orEmpty()
        if (tags.isNotEmpty()) {
            Section("Tags")
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    AssistChip(onClick = { onDismiss(); nav.navigate(TagRoute(t.id, t.value)) }, label = { Text(t.value) }, leadingIcon = { Icon(Icons.Outlined.Sell, null, Modifier.size(18.dp)) })
                }
            }
        }
        if (d.owner != null || !mine) {
            InfoRow(Icons.Outlined.Person, if (mine) "You" else d.owner?.name ?: "Someone else", if (mine) "Owner" else listOfNotNull("Owner", d.owner?.email).joinToString("  ·  "))
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
private fun InfoRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
