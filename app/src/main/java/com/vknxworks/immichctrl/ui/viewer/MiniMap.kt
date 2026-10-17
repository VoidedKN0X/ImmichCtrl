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

/** A small, still map with a dot where the photo was taken (uses the server's map style). */
@Composable
fun MiniMap(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.container
    val config by c.serverConfig.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val styleUrl = (if (dark) config.mapDarkStyleUrl else config.mapLightStyleUrl).ifBlank {
        if (dark) "https://tiles.immich.cloud/v1/style/dark.json" else "https://tiles.immich.cloud/v1/style/light.json"
    }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
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
