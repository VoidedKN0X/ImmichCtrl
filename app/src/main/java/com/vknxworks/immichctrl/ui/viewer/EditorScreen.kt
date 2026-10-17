package com.vknxworks.immichctrl.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.AssetDto
import com.vknxworks.immichctrl.data.api.EditsDto
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.api.edits
import com.vknxworks.immichctrl.data.api.obj
import com.vknxworks.immichctrl.data.api.revertEdits
import com.vknxworks.immichctrl.data.api.setEdits
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Crop, rotate and mirror. Immich keeps the original and applies the edits when it makes thumbnails
 * and previews, so this can always be undone ("Revert").
 *
 * The edit list matches Immich's own editor: crop first, in the original's (EXIF-rotated) pixels;
 * then mirror ("horizontal" flips left to right); then a clockwise rotation in steps of 90°.
 */
@Composable
fun EditorScreen(assetId: String) {
    val c = LocalContext.current.container
    val data = rememberLoader("editor:$assetId") { c.api.asset(assetId) to runCatching { c.api.edits(assetId) }.getOrNull() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = data.state) {
            is Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Err -> ErrorView(s.message) { data.reload() }
            is Load.Ok -> Editor(s.value.first, s.value.second)
        }
    }
}

/** Normalised crop rectangle (0..1) in the space the user sees (after mirror and rotation). */
private data class Crop(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f)

@Composable
private fun Editor(asset: AssetDto, existing: EditsDto?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    // The original's size after EXIF orientation, which is what crop coordinates refer to.
    val exifW = asset.exifInfo?.exifImageWidth?.toFloat() ?: asset.width?.toFloat() ?: 1000f
    val exifH = asset.exifInfo?.exifImageHeight?.toFloat() ?: asset.height?.toFloat() ?: 1000f
    val sideways = asset.exifInfo?.orientation?.toIntOrNull()?.let { it in 5..8 } == true
    val origW = if (sideways) exifH else exifW
    val origH = if (sideways) exifW else exifH

    // Start from the edits the photo already has.
    val start = remember(existing) { readEdits(existing, origW, origH) }
    var rotation by remember { mutableIntStateOf(start.rotation) }
    var flipX by remember { mutableStateOf(start.flipX) }
    var flipY by remember { mutableStateOf(start.flipY) }
    var crop by remember { mutableStateOf(start.crop) }
    var aspect by remember { mutableStateOf<Float?>(null) }

    val turned = rotation % 180 != 0
    val shownRatio = if (turned) origH / origW else origW / origH

    fun rotate(by: Int) {
        // Turn the crop with the picture so it keeps covering the same part.
        val k = crop
        crop = if (by > 0) Crop(1 - k.b, k.l, 1 - k.t, k.r) else Crop(k.t, 1 - k.r, k.b, 1 - k.l)
        rotation = ((rotation + by) % 360 + 360) % 360
    }

    fun flip() {
        // A left-right flip on screen; on a turned photo that's the other axis of the original.
        if (turned) flipY = !flipY else flipX = !flipX
        crop = Crop(1 - crop.r, crop.t, 1 - crop.l, crop.b)
    }

    fun applyAspect(a: Float?) {
        aspect = a
        if (a == null) return
        // Largest centred rectangle with that shape, in the space of the photo as shown.
        val target = a / shownRatio // aspect in normalised units
        var w = 1f
        var h = w / target
        if (h > 1f) {
            h = 1f
            w = target
        }
        crop = Crop((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
    }

    fun save() {
        val list = toEdits(rotation, flipX, flipY, crop, origW, origH)
        c.runTask(if (list.isEmpty()) "Back to the original" else "Saved") {
            if (list.isEmpty()) c.api.revertEdits(asset.id) else c.api.setEdits(asset.id, list)
            Sources.refreshAll()
            nav.back()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = Color.White) }
            Text("Edit", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                rotation = 0; flipX = false; flipY = false; crop = Crop(); aspect = null
            }) { Text("Revert", color = Color.White) }
            TextButton(onClick = ::save) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val area = fitRect(boxW, boxH, shownRatio)
            val wDp = with(density) { area.width.toDp() }
            val hDp = with(density) { area.height.toDp() }
            Box(Modifier.size(wDp, hDp), contentAlignment = Alignment.Center) {
                // The unedited photo, drawn at its own shape, mirrored then turned to match.
                AsyncImage(
                    model = c.api.url("/assets/${asset.id}/thumbnail", "size" to "preview", "edited" to false).toString(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .requiredSize(if (turned) hDp else wDp, if (turned) wDp else hDp)
                        .graphicsLayer {
                            scaleX = if (flipX) -1f else 1f
                            scaleY = if (flipY) -1f else 1f
                            rotationZ = rotation.toFloat()
                        },
                )
                CropFrame(crop, aspect, shownRatio) { crop = it }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf<Pair<Float?, String>>(null to "Free", (origW / origH) to "Original", 1f to "Square", (4f / 3f) to "4:3", (3f / 4f) to "3:4", (16f / 9f) to "16:9", (9f / 16f) to "9:16")
                .forEach { (a, label) -> FilterChip(aspect == a, onClick = { applyAspect(a) }, label = { Text(label) }) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolButton(Icons.Outlined.RotateLeft, "Turn left") { rotate(-90) }
            ToolButton(Icons.Outlined.RotateRight, "Turn right") { rotate(90) }
            ToolButton(Icons.Outlined.Flip, "Mirror") { flip() }
            ToolButton(Icons.Outlined.RestartAlt, "Reset crop") { crop = Crop(); aspect = null }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape)) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 4.dp))
    }
}

/** The crop rectangle with corner handles; drag a corner to resize, inside to move. */
@Composable
private fun CropFrame(crop: Crop, aspect: Float?, shownRatio: Float, onChange: (Crop) -> Unit) {
    val current by rememberUpdatedState(crop)
    val change by rememberUpdatedState(onChange)
    val keep by rememberUpdatedState(aspect?.let { it / shownRatio })
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var corner = -1 // 0 tl, 1 tr, 2 bl, 3 br, 4 move
            detectDragGestures(
                onDragStart = { p ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val k = current
                    val pts = listOf(Offset(k.l * w, k.t * h), Offset(k.r * w, k.t * h), Offset(k.l * w, k.b * h), Offset(k.r * w, k.b * h))
                    val near = pts.indexOfFirst { (it - p).getDistance() < 64f }
                    corner = if (near >= 0) near else if (p.x in k.l * w..k.r * w && p.y in k.t * h..k.b * h) 4 else -1
                },
                onDrag = { ch, d ->
                    ch.consume()
                    val dx = d.x / size.width
                    val dy = d.y / size.height
                    val k = current
                    val minS = 0.08f
                    var n = when (corner) {
                        0 -> k.copy(l = min(k.l + dx, k.r - minS).coerceAtLeast(0f), t = min(k.t + dy, k.b - minS).coerceAtLeast(0f))
                        1 -> k.copy(r = max(k.r + dx, k.l + minS).coerceAtMost(1f), t = min(k.t + dy, k.b - minS).coerceAtLeast(0f))
                        2 -> k.copy(l = min(k.l + dx, k.r - minS).coerceAtLeast(0f), b = max(k.b + dy, k.t + minS).coerceAtMost(1f))
                        3 -> k.copy(r = max(k.r + dx, k.l + minS).coerceAtMost(1f), b = max(k.b + dy, k.t + minS).coerceAtMost(1f))
                        4 -> {
                            val mx = dx.coerceIn(-k.l, 1 - k.r)
                            val my = dy.coerceIn(-k.t, 1 - k.b)
                            Crop(k.l + mx, k.t + my, k.r + mx, k.b + my)
                        }
                        else -> return@detectDragGestures
                    }
                    // Keep a fixed shape by adjusting the height from the width.
                    val ar = keep
                    if (ar != null && corner in 0..3) {
                        val h = ((n.r - n.l) / ar).coerceAtMost(1f)
                        n = if (corner <= 1) n.copy(t = (n.b - h).coerceAtLeast(0f)) else n.copy(b = (n.t + h).coerceAtMost(1f))
                    }
                    change(n)
                },
                onDragEnd = { corner = -1 },
            )
        },
    ) {
        val w = size.width
        val h = size.height
        val r = Rect(crop.l * w, crop.t * h, crop.r * w, crop.b * h)
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, Offset(0f, 0f), Size(w, r.top))
        drawRect(shade, Offset(0f, r.bottom), Size(w, h - r.bottom))
        drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(shade, Offset(r.right, r.top), Size(w - r.right, r.height))
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
        // Rule of thirds.
        val third = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (i in 1..2) {
            val x = r.left + r.width * i / 3
            val y = r.top + r.height * i / 3
            drawLine(Color.White.copy(alpha = 0.5f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx(), pathEffect = third)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx(), pathEffect = third)
        }
        val handle = 10.dp.toPx()
        listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { drawCircle(Color.White, handle / 1.4f, it) }
    }
}

private data class EditState(val rotation: Int, val flipX: Boolean, val flipY: Boolean, val crop: Crop)

/** A point (fractions) in the original, through mirror then rotation, to where it shows on screen. */
private fun forward(x: Float, y: Float, flipX: Boolean, flipY: Boolean, rotation: Int): Pair<Float, Float> {
    val mx = if (flipX) 1 - x else x
    val my = if (flipY) 1 - y else y
    return when (rotation) {
        90 -> (1 - my) to mx
        180 -> (1 - mx) to (1 - my)
        270 -> my to (1 - mx)
        else -> mx to my
    }
}

/** The reverse of [forward]: from screen fractions back to the original's fractions. */
private fun backward(u: Float, v: Float, flipX: Boolean, flipY: Boolean, rotation: Int): Pair<Float, Float> {
    val (mx, my) = when (rotation) {
        90 -> v to (1 - u)
        180 -> (1 - u) to (1 - v)
        270 -> (1 - v) to u
        else -> u to v
    }
    return (if (flipX) 1 - mx else mx) to (if (flipY) 1 - my else my)
}

private fun readEdits(existing: EditsDto?, w: Float, h: Float): EditState {
    var rotation = 0
    var flipX = false
    var flipY = false
    var cropPx: List<Float>? = null
    existing?.edits?.forEach { e ->
        val p = e.parameters as? JsonObject ?: return@forEach
        fun num(k: String) = p[k]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
        when (e.action) {
            "rotate" -> rotation = (rotation + num("angle").toInt()) % 360
            "mirror" -> if (p["axis"]?.jsonPrimitive?.contentOrNull == "horizontal") flipX = !flipX else flipY = !flipY
            "crop" -> cropPx = listOf(num("x"), num("y"), num("width"), num("height"))
        }
    }
    val crop = cropPx?.let { (x, y, cw, ch) ->
        val a = forward(x / w, y / h, flipX, flipY, rotation)
        val b = forward((x + cw) / w, (y + ch) / h, flipX, flipY, rotation)
        Crop(min(a.first, b.first), min(a.second, b.second), max(a.first, b.first), max(a.second, b.second))
    } ?: Crop()
    return EditState(rotation, flipX, flipY, crop)
}

private fun toEdits(rotation: Int, flipX: Boolean, flipY: Boolean, crop: Crop, w: Float, h: Float): List<JsonObject> {
    val out = ArrayList<JsonObject>()
    val cropped = abs(crop.l) > 0.002f || abs(crop.t) > 0.002f || abs(1 - crop.r) > 0.002f || abs(1 - crop.b) > 0.002f
    if (cropped) {
        val a = backward(crop.l, crop.t, flipX, flipY, rotation)
        val b = backward(crop.r, crop.b, flipX, flipY, rotation)
        val x = (min(a.first, b.first) * w).toInt().coerceIn(0, w.toInt() - 1)
        val y = (min(a.second, b.second) * h).toInt().coerceIn(0, h.toInt() - 1)
        val cw = (abs(a.first - b.first) * w).toInt().coerceIn(1, w.toInt() - x)
        val ch = (abs(a.second - b.second) * h).toInt().coerceIn(1, h.toInt() - y)
        out += obj("action" to "crop", "parameters" to mapOf("x" to x, "y" to y, "width" to cw, "height" to ch))
    }
    if (flipX) out += obj("action" to "mirror", "parameters" to mapOf("axis" to "horizontal"))
    if (flipY) out += obj("action" to "mirror", "parameters" to mapOf("axis" to "vertical"))
    if (rotation != 0) out += obj("action" to "rotate", "parameters" to mapOf("angle" to rotation))
    return out
}
