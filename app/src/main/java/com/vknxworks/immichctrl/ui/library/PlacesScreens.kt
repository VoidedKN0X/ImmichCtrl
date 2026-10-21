package com.vknxworks.immichctrl.ui.library

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.MapMarkerDto
import com.vknxworks.immichctrl.data.api.asset
import com.vknxworks.immichctrl.data.api.explore
import com.vknxworks.immichctrl.data.api.mapMarkersBetween
import com.vknxworks.immichctrl.data.api.metadataSearch
import com.vknxworks.immichctrl.data.timeline.ListSource
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.AssetThumb
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.MapRoute
import com.vknxworks.immichctrl.ui.viewer.rememberMapStyleUrl
import com.vknxworks.immichctrl.ui.nav.PlaceRoute
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberPaged
import com.vknxworks.immichctrl.ui.theme.Gutter
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/** Cities your photos were taken in, from Immich's explore data. */
@Composable
fun PlacesScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val places = rememberLoader("places") {
        c.api.explore().firstOrNull { it.fieldName.contains("city") }?.items.orEmpty().sortedBy { it.value.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.places)) {
            IconButton(onClick = { nav.navigate(MapRoute) }) { Icon(Icons.Outlined.Map, stringResource(R.string.map)) }
        }
        when (val s = places.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { places.reload() }
            is Load.Ok -> if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.Place, stringResource(R.string.no_places_yet), stringResource(R.string.places_appear_once_immich_has_read))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    items(s.value, key = { it.value }) { item ->
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                                .clickable { nav.navigate(PlaceRoute(item.value)) },
                        ) {
                            AssetThumb(item.data.id, item.data.thumbhash, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                            Text(
                                item.value,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Photos taken in one city. */
@Composable
fun PlaceScreen(city: String) {
    val c = LocalContext.current.container
    val source = rememberPaged("place:$city") { page ->
        val res = c.api.metadataSearch(page, 200, mapOf("city" to city, "order" to "desc"))
        res.assets.items.map { it.toAsset() } to (res.assets.nextPage != null)
    }
    SourceGrid(source, SelectionContext.TIMELINE, topBar = { ScreenTopBar(city) })
}

/** Every geotagged photo on a map. Tap a cluster to zoom in, a dot to open the photo. */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var years by rememberSaveable { mutableStateOf<Int?>(null) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var inView by remember { mutableStateOf<Int?>(null) }
    val markers = rememberLoader("map:$favoritesOnly:$years:$archived") {
        val after = years?.let { java.time.Instant.now().minusSeconds(it * 365L * 86_400L).toString() }
        c.api.mapMarkersBetween(settings.withPartners, if (favoritesOnly) true else null, after, null, if (archived) null else false)
    }
    val styleUrl = rememberMapStyleUrl()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val onAccent = MaterialTheme.colorScheme.onPrimary.toArgb()

    fun openAsset(id: String) {
        // The map only knows ids; fetch this one so the viewer has what it needs.
        c.runTask {
            val asset = c.api.asset(id).toAsset()
            val key = "map:$id"
            Sources.register(ListSource(key, c.appScope) { listOf(asset) })
            nav.viewer(key, 0, id)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.map))
        Row(
            Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(stringResource(R.string.favourites)) })
            FilterChip(archived, onClick = { archived = !archived }, label = { Text(stringResource(R.string.include_archived)) })
            listOf<Pair<Int?, String>>(null to Strings.str(R.string.any_time), 1 to Strings.str(R.string.past_year), 5 to Strings.str(R.string.past_5_years)).forEach { (y, label) ->
                FilterChip(years == y, onClick = { years = y }, label = { Text(label) })
            }
        }
        markers.value?.let { all ->
            Text(
                inView?.let { Strings.str(R.string.in_view_of, countLabel(it, R.plurals.count_photos), "%,d".format(all.size)) } ?: countLabel(all.size, R.plurals.count_photos),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 2.dp),
            )
        }
        if (styleUrl == null) {
            MessageView(
                Icons.Outlined.Map,
                stringResource(R.string.maps_are_off),
                stringResource(R.string.turn_on_show_maps_in_settings_or_ask),
            )
        } else when (val s = markers.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { markers.reload() }
            is Load.Ok -> {
                if (s.value.isEmpty()) {
                    MessageView(Icons.Outlined.Map, stringResource(R.string.nothing_on_the_map), stringResource(R.string.photos_show_up_here_once_they_have_a))
                } else {
                    MapLibreView(s.value, styleUrl, accent, onAccent, onOpen = ::openAsset, onVisible = { inView = it }, modifier = Modifier.fillMaxSize().navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun MapLibreView(
    markers: List<MapMarkerDto>,
    styleUrl: String,
    accent: Int,
    onAccent: Int,
    onOpen: (String) -> Unit,
    onVisible: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val visible by androidx.compose.runtime.rememberUpdatedState(onVisible)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val clickListener = remember { arrayOfNulls<MapLibreMap.OnMapClickListener>(1) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
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

    LaunchedEffect(mapView, styleUrl, markers) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                val features = markers.map { mk ->
                    Feature.fromGeometry(Point.fromLngLat(mk.lon, mk.lat)).apply { addStringProperty("id", mk.id) }
                }
                style.removeLayer("cluster-count")
                style.removeLayer("clusters")
                style.removeLayer("points")
                style.removeSource("photos")
                style.addSource(
                    GeoJsonSource(
                        "photos",
                        FeatureCollection.fromFeatures(features),
                        GeoJsonOptions().withCluster(true).withClusterRadius(48).withClusterMaxZoom(15),
                    ),
                )
                style.addLayer(
                    CircleLayer("clusters", "photos")
                        .withFilter(Expression.has("point_count"))
                        .withProperties(
                            PropertyFactory.circleColor(accent),
                            PropertyFactory.circleOpacity(0.9f),
                            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleRadius(
                                Expression.step(
                                    Expression.get("point_count"), Expression.literal(16f),
                                    Expression.stop(10, 20f), Expression.stop(100, 26f), Expression.stop(1000, 32f),
                                ),
                            ),
                        ),
                )
                style.addLayer(
                    SymbolLayer("cluster-count", "photos")
                        .withFilter(Expression.has("point_count"))
                        .withProperties(
                            PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                            PropertyFactory.textSize(12f),
                            PropertyFactory.textColor(onAccent),
                            PropertyFactory.textAllowOverlap(true),
                        ),
                )
                style.addLayer(
                    CircleLayer("points", "photos")
                        .withFilter(Expression.not(Expression.has("point_count")))
                        .withProperties(
                            PropertyFactory.circleColor(accent),
                            PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                            PropertyFactory.circleStrokeWidth(2f),
                        ),
                )
                runCatching {
                    if (markers.size == 1) {
                        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(markers[0].lat, markers[0].lon), 12.0))
                    } else {
                        val bounds = LatLngBounds.Builder().apply { markers.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                    }
                }
            }
            clickListener[0]?.let { m.removeOnMapClickListener(it) }
            val listener = MapLibreMap.OnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val cluster = m.queryRenderedFeatures(screen, "clusters").firstOrNull()
                if (cluster != null) {
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(point, m.cameraPosition.zoom + 2))
                    return@OnMapClickListener true
                }
                val hit = m.queryRenderedFeatures(screen, "points").firstOrNull()
                val id = hit?.getStringProperty("id")
                if (id != null) {
                    onOpen(id)
                    true
                } else {
                    false
                }
            }
            clickListener[0] = listener
            m.addOnMapClickListener(listener)
            // How many photos are in the visible part of the map.
            m.addOnCameraIdleListener {
                val bounds = m.projection.visibleRegion.latLngBounds
                visible(markers.count { bounds.contains(LatLng(it.lat, it.lon)) })
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
