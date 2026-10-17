package com.vknxworks.immichctrl.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.Asset
import com.vknxworks.immichctrl.ui.components.ThumbHash
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A photo that fills the screen: blur, then preview, then (when zoomed in, if enabled) the original.
 * Pinch or double-tap to zoom, drag to pan. At normal size, horizontal swipes go to the pager and a
 * vertical swipe down closes the viewer ([onDismiss]); swipe up opens the details ([onSwipeUp]).
 */
@Composable
fun ZoomableImage(
    asset: Asset,
    loadOriginal: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onSwipeUp: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dismissY = remember { Animatable(0f) }
    var wantOriginal by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dismissPx = with(density) { 140.dp.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    val onZoom by rememberUpdatedState(onZoomChange)
    val dismiss by rememberUpdatedState(onDismiss)
    val swipeUp by rememberUpdatedState(onSwipeUp)

    LaunchedEffect(scale > 1.01f) { onZoom(scale > 1.01f) }
    LaunchedEffect(scale) { if (scale > 1.6f && loadOriginal) wantOriginal = true }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun zoomTo(target: Float, focus: Offset) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val newOffset = (focus - center) - (focus - center - offset) * (target / scale)
        scale = target
        offset = clamp(newOffset, target)
    }

    val blur = remember(asset.thumbhash) { ThumbHash.bitmap(asset.thumbhash)?.asImageBitmap() }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(asset.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { p -> if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else zoomTo(2.5f, p) },
                )
            }
            .pointerInput(asset.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var vertical = false
                    var total = Offset.Zero
                    do {
                        val ev = awaitPointerEvent()
                        val pointers = ev.changes.count { it.pressed }
                        if (pointers >= 2 || scale > 1.01f) {
                            val zoom = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val centroid = ev.calculateCentroid(useCurrent = true)
                            val target = (scale * zoom).coerceIn(1f, 8f)
                            if (centroid != Offset.Unspecified) zoomTo(target, centroid)
                            offset = clamp(offset + pan, scale)
                            if (scale <= 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            // Not zoomed, one finger: vertical drags close or open details; horizontal ones page.
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            total += ch.position - ch.previousPosition
                            if (!vertical && abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.5f) vertical = true
                            if (vertical) {
                                ch.consume()
                                scope.launch { dismissY.snapTo((dismissY.value + (ch.position.y - ch.previousPosition.y))) }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (vertical) {
                        val dy = dismissY.value
                        when {
                            dy > dismissPx -> dismiss()
                            dy < -dismissPx / 2 -> {
                                swipeUp()
                                scope.launch { dismissY.animateTo(0f, spring()) }
                            }
                            else -> scope.launch { dismissY.animateTo(0f, spring()) }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y + dismissY.value
                val fade = (abs(dismissY.value) / (dismissPx * 3)).coerceIn(0f, 0.5f)
                alpha = 1f - fade
            },
    ) {
        if (blur != null) Image(blur, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        // Photos on this phone (or saved offline) are shown from the file; the rest from the server.
        val localFile = asset.localUri?.takeIf { asset.isLocalOnly || it.startsWith("file:") }
        val isPrivate = asset.visibility == "locked"
        AsyncImage(
            model = localFile ?: com.vknxworks.immichctrl.ui.components.privateImageModel(c.api.thumbnailUrl(asset.id, "preview", asset.thumbhash?.take(12)), isPrivate),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        if (wantOriginal && localFile == null) {
            AsyncImage(
                model = com.vknxworks.immichctrl.ui.components.privateImageModel(c.api.originalUrl(asset.id), isPrivate),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
