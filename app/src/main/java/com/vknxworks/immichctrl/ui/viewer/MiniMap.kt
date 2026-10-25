package com.vknxworks.immichctrl.ui.viewer

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

/**
 * The map style to load, or null when no map should be shown. Map tiles come from whoever hosts the style
 * (Immich's own tile server unless the server's admin picked another), and that host sees the phone's IP
 * address and which area is shown. So maps only load when the server's admin left the map on, the user
 * left "Show maps" on, and the style address is https.
 */
@Composable
fun rememberMapStyleUrl(): String? {
    val c = LocalContext.current.container
    val features by c.features.collectAsStateWithLifecycle()
    val config by c.serverConfig.collectAsStateWithLifecycle()
    // The stored value, not the start-up placeholder, so a map never loads before the setting is known.
    val stored by c.settings.stored.collectAsStateWithLifecycle(initialValue = null)
    val dark = com.vknxworks.immichctrl.ui.theme.LocalCtrlColors.current.isDark
    if (!features.map || stored?.showMaps != true) return null
    val url = (if (dark) config.mapDarkStyleUrl else config.mapLightStyleUrl).ifBlank {
        if (dark) "https://tiles.immich.cloud/v1/style/dark.json" else "https://tiles.immich.cloud/v1/style/light.json"
    }
    return url.takeIf { it.startsWith("https://", ignoreCase = true) }
}

/** A small, still map with a dot where the photo was taken (uses the server's map style). */
@Composable
fun MiniMap(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val styleUrl = rememberMapStyleUrl() ?: return
    val accent = com.vknxworks.immichctrl.ui.theme.CtrlColors.Accent.toArgb()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView, lat, lon, styleUrl) {
        mapView.getMapAsync { m ->
            m.uiSettings.setAllGesturesEnabled(false)
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.cameraPosition = CameraPosition.Builder().target(LatLng(lat, lon)).zoom(12.0).build()
            m.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                style.addSource(GeoJsonSource("here", Point.fromLngLat(lon, lat)))
                style.addLayer(
                    CircleLayer("here-dot", "here").withProperties(
                        PropertyFactory.circleColor(accent),
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                        PropertyFactory.circleStrokeWidth(2f),
                    ),
                )
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
