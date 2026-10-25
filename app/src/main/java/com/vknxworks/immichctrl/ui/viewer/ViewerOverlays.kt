package com.vknxworks.immichctrl.ui.viewer

import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.data.api.FaceDto
import com.vknxworks.immichctrl.data.api.OcrDto
import com.vknxworks.immichctrl.data.api.StackDto
import com.vknxworks.immichctrl.data.api.createPerson
import com.vknxworks.immichctrl.data.api.deleteFace
import com.vknxworks.immichctrl.data.api.people
import com.vknxworks.immichctrl.data.api.reassignFace
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.AssetThumb
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.PersonFace
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.toast
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/** Where a [ratio] (w/h) image sits when fitted into a box of [w] x [h] pixels. */
fun fitRect(w: Float, h: Float, ratio: Float): Rect {
    if (w <= 0 || h <= 0 || ratio <= 0) return Rect(0f, 0f, w, h)
    return if (w / h > ratio) {
        val iw = h * ratio
        Rect((w - iw) / 2, 0f, (w + iw) / 2, h)
    } else {
        val ih = w / ratio
        Rect(0f, (h - ih) / 2, w, (h + ih) / 2)
    }
}

/** A box drawn over the photo at fractional coordinates (0..1) of the displayed image. */
@Composable
private fun OverlayBox(
    image: Rect,
    l: Float, t: Float, r: Float, b: Float,
    color: Color,
    label: String?,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val x = image.left + l * image.width
    val y = image.top + t * image.height
    val w = (r - l) * image.width
    val h = (b - t) * image.height
    Box(
        Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .border(2.dp, color, RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    )
    if (label != null) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset { IntOffset(x.toInt(), (y + h + 4).toInt()) }
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Recognised text drawn over the photo; tap a box to copy it, or copy everything. */
@Composable
fun OcrOverlay(asset: Asset, lines: List<OcrDto>, onClose: () -> Unit) {
    val c = LocalContext.current.container
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val img = fitRect(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, asset.ratio)
        lines.forEach { o ->
            val xs = listOf(o.x1, o.x2, o.x3, o.x4)
            val ys = listOf(o.y1, o.y2, o.y3, o.y4)
            // Immich stores text corners as fractions (0..1) of the image.
            OverlayBox(
                img,
                xs.min().toFloat(), ys.min().toFloat(), xs.max().toFloat(), ys.max().toFloat(),
                MaterialTheme.colorScheme.primary,
                null,
            ) {
                com.vknxworks.immichctrl.data.security.copySensitive(c.context, o.text, Strings.str(R.string.text_from_photo))
                c.toast(Strings.str(R.string.copied_2, o.text.take(40)))
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 96.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                com.vknxworks.immichctrl.data.security.copySensitive(c.context, lines.joinToString("\n") { it.text }, Strings.str(R.string.text_from_photo))
                c.toast(Strings.str(R.string.copied_all_text))
            }) { Text(if (lines.isEmpty()) stringResource(R.string.no_text_found) else stringResource(R.string.copy_all_text), color = Color.White) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.lock_done), color = Color.White) }
        }
    }
}

/** Faces Immich found, with names; tap one to fix who it is. */
@Composable
fun FacesOverlay(asset: Asset, faces: List<FaceDto>, onChanged: () -> Unit, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<FaceDto?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val img = fitRect(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, asset.ratio)
        faces.forEach { f ->
            val w = f.imageWidth.coerceAtLeast(1).toFloat()
            val h = f.imageHeight.coerceAtLeast(1).toFloat()
            OverlayBox(
                img,
                f.boundingBoxX1 / w, f.boundingBoxY1 / h, f.boundingBoxX2 / w, f.boundingBoxY2 / h,
                if (f.person == null) MaterialTheme.colorScheme.tertiary else Color.White,
                f.person?.name?.ifBlank { Strings.str(R.string.unnamed) } ?: stringResource(R.string.who_is_this),
            ) { editing = f }
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 96.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (faces.isEmpty()) stringResource(R.string.no_faces_found) else stringResource(R.string.tap_a_face_to_change_who_it_is),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.lock_done), color = Color.White) }
        }
    }
    editing?.let { face -> FaceSheet(face, onDismiss = { editing = null }, onChanged = { editing = null; onChanged() }) }
}

@Composable
private fun FaceSheet(face: FaceDto, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val c = LocalContext.current.container
    val people = rememberLoader("face-people") { c.api.people(withHidden = false).people.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() } }
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        TextInputDialog(stringResource(R.string.new_person), stringResource(R.string.name), stringResource(R.string.save), onDismiss = { naming = false }) { name ->
            naming = false
            c.runTask(Strings.str(R.string.saved_as, name)) {
                val p = c.api.createPerson(name)
                c.api.reassignFace(face.id, p.id)
                Sources.refreshAll()
                onChanged()
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = CtrlShapes.Sheet) {
        Column(Modifier.navigationBarsPadding()) {
            SheetTitle(face.person?.name?.ifBlank { null }?.let { Strings.str(R.string.this_is, it) } ?: stringResource(R.string.who_is_this), stringResource(R.string.pick_the_right_person_or_add_someone))
            ActionItem(Icons.Filled.Add, stringResource(R.string.someone_new)) { naming = true }
            if (face.person != null) {
                ActionItem(Icons.Outlined.Delete, stringResource(R.string.not_a_face_remove_it), tint = MaterialTheme.colorScheme.error) {
                    c.runTask(Strings.str(R.string.face_removed)) { c.api.deleteFace(face.id); onChanged() }
                }
            }
            when (val s = people.state) {
                is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp)) { LoadingView() }
                is Load.Err -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                is Load.Ok -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.value, key = { it.id }) { p ->
                        Column(
                            Modifier.clickable {
                                c.runTask(Strings.str(R.string.changed_to, p.name)) {
                                    c.api.reassignFace(face.id, p.id)
                                    Sources.refreshAll()
                                    onChanged()
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PersonFace(p.id, p.updatedAt, Modifier.size(56.dp))
                            Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Thumbnails of the photos in this asset's stack; the one on screen is outlined. */
@Composable
fun StackStrip(stack: StackDto, showingId: String, onPick: (Asset) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stack.assets.forEach { dto ->
            val a = dto.toAsset()
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (a.id == showingId) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onPick(a) },
            ) {
                AssetThumb(a, Modifier.fillMaxSize())
            }
        }
    }
}
