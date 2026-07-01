package com.phoebe.app.feature.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.phoebe.app.domain.RadioMapViewport
import com.phoebe.app.ui.LocalPhoebePalette
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

@Composable
internal actual fun RadioMapHost(
    items: List<RadioMapItem>,
    selectedItem: RadioMapItem?,
    startingStationIds: Set<String>,
    markerTintColor: Color,
    googleMapsApiKey: String?,
    onItemSelected: (RadioMapItem) -> Unit,
    onItemPlay: (RadioMapItem) -> Unit,
    onMapZoomChanged: (Double) -> Unit,
    onMapViewportChanged: (RadioMapViewport) -> Unit,
    modifier: Modifier,
    fallback: @Composable (Modifier) -> Unit,
) {
    if (googleMapsApiKey.isNullOrBlank()) {
        fallback(modifier)
        return
    }

    val currentItems = rememberUpdatedState(items)
    val currentOnItemSelected = rememberUpdatedState(onItemSelected)
    val currentOnMapZoomChanged = rememberUpdatedState(onMapZoomChanged)
    val currentOnMapViewportChanged = rememberUpdatedState(onMapViewportChanged)
    val markerTintArgb = remember(markerTintColor) { markerTintColor.toRadioMapArgbInt() }
    val useLightTheme = LocalPhoebePalette.current.canvasBackground.luminance() > 0.5f
    val selectedItemId = selectedItem?.id
    val controller = remember {
        AndroidRadioMapController(
            onItemSelected = { item -> currentOnItemSelected.value(item) },
            onMapZoomChanged = { zoom -> currentOnMapZoomChanged.value(zoom) },
            onMapViewportChanged = { viewport -> currentOnMapViewportChanged.value(viewport) },
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            val view = controller.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.clear()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { view ->
                controller.attachView(view)
                view.onCreate(Bundle())
                view.onStart()
                view.onResume()
                view.getMapAsync(
                    OnMapReadyCallback { googleMap ->
                        controller.bindMap(context, googleMap)
                    },
                )
            }
        },
        update = {
            controller.sync(
                sourceItems = currentItems.value,
                selectedItemId = selectedItemId,
                markerTintArgb = markerTintArgb,
                useLightTheme = useLightTheme,
            )
        },
        onRelease = { view ->
            controller.clear()
            view.onDestroy()
        },
    )
}

internal actual fun radioMapGoogleMapsApiKey(): String? =
    RadioMapBuildConfig.googleMapsAndroidApiKey.takeIf { it.isNotBlank() }
        ?: RadioMapBuildConfig.googleMapsApiKey.takeIf { it.isNotBlank() }

internal actual fun radioMapUsesExternalBrowser(): Boolean = false

internal actual fun radioMapUsesMinimalEmbeddedChrome(): Boolean = true

internal actual fun radioMapHostClustersMarkers(): Boolean = true

private class AndroidRadioMapController(
    private val onItemSelected: (RadioMapItem) -> Unit,
    private val onMapZoomChanged: (Double) -> Unit,
    private val onMapViewportChanged: (RadioMapViewport) -> Unit,
) {
    var mapView: MapView? = null
        private set
    private var map: GoogleMap? = null
    private var clusterManager: ClusterManager<RadioStationClusterItem>? = null
    private var renderer: RadioStationClusterRenderer? = null
    private var pendingSourceItems: List<RadioMapItem> = emptyList()
    private var pendingSelectedItemId: String? = null
    private var pendingMarkerTintArgb: Int = 0xFFFFFFFF.toInt()
    private var pendingUseLightTheme: Boolean = false
    private var appliedUseLightTheme: Boolean? = null
    private var lastRenderSignature: RenderSignature? = null
    private var expandedCluster: ExpandedRadioMapCluster? = null
    private val expandedMarkers = mutableMapOf<String, Marker>()

    fun attachView(view: MapView) {
        mapView = view
    }

    fun bindMap(context: Context, googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.setInfoWindowAdapter(EmptyRadioMapInfoWindowAdapter)
        applyMapThemeIfNeeded(force = true)
        googleMap.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.fromLatLngZoom(LatLng(DefaultCameraLatitude, DefaultCameraLongitude), DefaultZoom),
            ),
        )
        val nextClusterManager = ClusterManager<RadioStationClusterItem>(context, googleMap)
        val nextRenderer = RadioStationClusterRenderer(context, googleMap, nextClusterManager)
        nextClusterManager.renderer = nextRenderer
        nextClusterManager.setOnClusterItemClickListener { item ->
            onItemSelected(item.item)
            true
        }
        nextClusterManager.setOnClusterClickListener { cluster ->
            handleClusterClick(cluster)
            true
        }
        googleMap.setOnMarkerClickListener { marker ->
            val expandedItem = marker.tag as? RadioMapItem.Station
            if (expandedItem != null) {
                onItemSelected(expandedItem)
                true
            } else {
                nextClusterManager.onMarkerClick(marker)
            }
        }
        googleMap.setOnCameraIdleListener {
            val zoom = googleMap.cameraPosition.zoom.toDouble()
            clearExpandedClusterForZoomIfNeeded(zoom)
            nextClusterManager.onCameraIdle()
            onMapZoomChanged(zoom)
            val bounds = googleMap.projection.visibleRegion.latLngBounds
            onMapViewportChanged(
                RadioMapViewport(
                    north = bounds.northeast.latitude,
                    south = bounds.southwest.latitude,
                    east = bounds.northeast.longitude,
                    west = bounds.southwest.longitude,
                    zoom = zoom,
                ),
            )
        }
        clusterManager = nextClusterManager
        renderer = nextRenderer
        renderIfNeeded(force = true)
    }

    fun sync(
        sourceItems: List<RadioMapItem>,
        selectedItemId: String?,
        markerTintArgb: Int,
        useLightTheme: Boolean,
    ) {
        pendingSourceItems = sourceItems
        pendingSelectedItemId = selectedItemId
        pendingMarkerTintArgb = markerTintArgb
        pendingUseLightTheme = useLightTheme
        applyMapThemeIfNeeded()
        renderIfNeeded()
    }

    fun clear() {
        clusterManager?.clearItems()
        clearExpandedMarkers()
        map?.clear()
        clusterManager = null
        renderer = null
        map = null
        mapView = null
        appliedUseLightTheme = null
        lastRenderSignature = null
        expandedCluster = null
    }

    private fun applyMapThemeIfNeeded(force: Boolean = false) {
        val googleMap = map ?: return
        if (!force && appliedUseLightTheme == pendingUseLightTheme) return
        googleMap.setMapStyle(
            if (pendingUseLightTheme) {
                null
            } else {
                MapStyleOptions(AndroidRadioMapDarkStyleJson)
            },
        )
        appliedUseLightTheme = pendingUseLightTheme
    }

    private fun renderIfNeeded(force: Boolean = false) {
        val manager = clusterManager ?: return
        val stationItems = pendingSourceItems.flattenRadioMapStationMarkers()
        val activeExpandedCluster = expandedCluster
            ?.takeIf { cluster -> stationItems.any { it.id in cluster.stationIds } }
            .also { cluster ->
                if (cluster == null) expandedCluster = null
            }
        val expandedStationIds = activeExpandedCluster?.stationIds.orEmpty()
        val signature = RenderSignature(
            itemIds = stationItems.map { it.id },
            selectedItemId = pendingSelectedItemId,
            markerTintArgb = pendingMarkerTintArgb,
            expandedStationIds = expandedStationIds,
        )
        if (!force && signature == lastRenderSignature) return
        renderer?.updateStyle(pendingMarkerTintArgb, pendingSelectedItemId)
        manager.clearItems()
        clearExpandedMarkers()
        stationItems.filterNot { it.id in expandedStationIds }.forEach { item ->
            manager.addItem(RadioStationClusterItem(item))
        }
        manager.cluster()
        activeExpandedCluster?.let { cluster ->
            renderExpandedClusterMarkers(
                items = stationItems
                    .filter { it.id in cluster.stationIds }
                    .sortedBy { it.id },
            )
        }
        lastRenderSignature = signature
    }

    private fun handleClusterClick(cluster: Cluster<RadioStationClusterItem>) {
        val googleMap = map ?: return
        val clusterItems = cluster.items.map { it.item }.sortedBy { it.id }
        if (clusterItems.isEmpty()) return
        val singlePositionCluster = cluster.isSinglePositionRadioMapCluster()
        if (singlePositionCluster || googleMap.shouldExpandRadioMapCluster(cluster)) {
            expandedCluster = ExpandedRadioMapCluster(
                stationIds = clusterItems.map { it.id }.toSet(),
                collapseBelowZoom = (googleMap.cameraPosition.zoom - AndroidExpandedClusterZoomHysteresis)
                    .coerceAtLeast(DefaultZoom),
            )
            renderIfNeeded(force = true)
            googleMap.focusRadioMapPositions(
                positions = clusterItems.expandedRadioMapClusterPositions(),
                fallbackPosition = cluster.position,
            )
        } else {
            if (expandedCluster != null) {
                expandedCluster = null
                renderIfNeeded(force = true)
            }
            googleMap.focusRadioMapCluster(cluster)
        }
    }

    private fun clearExpandedClusterForZoomIfNeeded(zoom: Double) {
        val collapseBelowZoom = expandedCluster?.collapseBelowZoom ?: return
        if (zoom >= collapseBelowZoom) return
        expandedCluster = null
        renderIfNeeded(force = true)
    }

    private fun renderExpandedClusterMarkers(
        items: List<RadioMapItem.Station>,
    ) {
        val googleMap = map ?: return
        items.expandedRadioMapClusterPositions().forEachIndexed { index, position ->
            val item = items[index]
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(item.name)
                    .icon(
                        dotDescriptor(
                            markerTintArgb = item.markerTintArgbForItem(pendingMarkerTintArgb),
                            selected = item.id == pendingSelectedItemId,
                            count = null,
                        ),
                    ),
            ) ?: return@forEachIndexed
            marker.tag = item
            expandedMarkers[item.id] = marker
        }
    }

    private fun clearExpandedMarkers() {
        expandedMarkers.values.forEach { marker -> marker.remove() }
        expandedMarkers.clear()
    }
}

private fun GoogleMap.focusRadioMapCluster(cluster: Cluster<RadioStationClusterItem>) {
    val positions = cluster.items.map { it.position }
    focusRadioMapPositions(positions = positions, fallbackPosition = cluster.position)
}

private fun GoogleMap.focusRadioMapPositions(
    positions: List<LatLng>,
    fallbackPosition: LatLng,
) {
    val firstPosition = positions.firstOrNull()
    if (firstPosition == null || positions.size == 1 || positions.all { it == firstPosition }) {
        animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                fallbackPosition,
                (cameraPosition.zoom + 2f).coerceAtMost(18f),
            ),
        )
        return
    }
    val bounds = LatLngBounds.builder().apply {
        positions.forEach { position -> include(position) }
    }.build()
    animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72))
}

private fun GoogleMap.shouldExpandRadioMapCluster(cluster: Cluster<RadioStationClusterItem>): Boolean {
    if (cluster.size <= 1) return false
    return cameraPosition.zoom >= AndroidClusterExpansionZoom
}

private fun Cluster<RadioStationClusterItem>.isSinglePositionRadioMapCluster(): Boolean {
    val positions = items.map { it.position }
    val firstPosition = positions.firstOrNull() ?: return false
    return positions.size > 1 && positions.all { it.isApproximatelySameRadioMapPosition(firstPosition) }
}

private fun LatLng.isApproximatelySameRadioMapPosition(other: LatLng): Boolean =
    kotlin.math.abs(latitude - other.latitude) < 0.0002 &&
        radioMapLongitudeDistance(longitude, other.longitude) < 0.0002

private fun List<RadioMapItem.Station>.expandedRadioMapClusterPositions(): List<LatLng> =
    map { item -> LatLng(item.latitude, item.longitude) }
        .let { actualPositions ->
            val overlapGroups = actualPositions.indices.groupBy { index ->
                actualPositions[index].radioMapOverlapKey()
            }
            actualPositions.mapIndexed { index, position ->
                val group = overlapGroups[position.radioMapOverlapKey()].orEmpty()
                if (group.size <= 1) {
                    position
                } else {
                    val groupIndex = group.indexOf(index).coerceAtLeast(0)
                    position.androidSpiderfyLocation(groupIndex, group.size)
                }
            }
        }

private fun LatLng.radioMapOverlapKey(): Pair<Int, Int> =
    Pair(
        (latitude / AndroidExpandedMarkerOverlapDegrees).toInt(),
        (longitude / AndroidExpandedMarkerOverlapDegrees).toInt(),
    )

private fun LatLng.androidSpiderfyLocation(index: Int, count: Int): LatLng {
    if (count <= 1) return this
    val angle = index * AndroidSpiderfyGoldenAngleRadians
    val radius = minOf(
        AndroidSpiderfyMaxRadiusDegrees,
        AndroidSpiderfyBaseRadiusDegrees * kotlin.math.sqrt((index + 1).toDouble()),
    )
    val latitudeOffset = kotlin.math.sin(angle) * radius
    val longitudeScale = kotlin.math.cos(latitude * Math.PI / 180.0).coerceAtLeast(0.25)
    val longitudeOffset = (kotlin.math.cos(angle) * radius) / longitudeScale
    return LatLng(
        (latitude + latitudeOffset).coerceIn(-90.0, 90.0),
        (longitude + longitudeOffset).wrapAndroidRadioMapLongitude(),
    )
}

private fun Double.wrapAndroidRadioMapLongitude(): Double {
    var wrapped = this
    while (wrapped > 180.0) wrapped -= 360.0
    while (wrapped < -180.0) wrapped += 360.0
    return wrapped
}

private data class ExpandedRadioMapCluster(
    val stationIds: Set<String>,
    val collapseBelowZoom: Float,
)

private data class RenderSignature(
    val itemIds: List<String>,
    val selectedItemId: String?,
    val markerTintArgb: Int,
    val expandedStationIds: Set<String>,
)

private class RadioStationClusterItem(
    val item: RadioMapItem.Station,
) : ClusterItem {
    override val position: LatLng = LatLng(item.latitude, item.longitude)
    override val title: String = item.name
    override val snippet: String? = null
    override val zIndex: Float = if (item.approximate) 0f else 1f
}

private class RadioStationClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<RadioStationClusterItem>,
) : DefaultClusterRenderer<RadioStationClusterItem>(context, map, clusterManager) {
    private var markerTintArgb: Int = 0xFFFFFFFF.toInt()
    private var selectedItemId: String? = null
    private val iconCache = mutableMapOf<DotDescriptorKey, BitmapDescriptor>()

    fun updateStyle(markerTintArgb: Int, selectedItemId: String?) {
        this.markerTintArgb = markerTintArgb
        this.selectedItemId = selectedItemId
    }

    override fun onBeforeClusterItemRendered(item: RadioStationClusterItem, markerOptions: MarkerOptions) {
        markerOptions.icon(
            cachedDotDescriptor(
                markerTintArgb = item.item.markerTintArgbForItem(markerTintArgb),
                selected = item.item.id == selectedItemId,
                count = null,
            ),
        )
    }

    override fun onClusterItemUpdated(item: RadioStationClusterItem, marker: Marker) {
        marker.setIcon(
            cachedDotDescriptor(
                markerTintArgb = item.item.markerTintArgbForItem(markerTintArgb),
                selected = item.item.id == selectedItemId,
                count = null,
            ),
        )
        marker.title = null
        marker.snippet = null
    }

    override fun onBeforeClusterRendered(cluster: Cluster<RadioStationClusterItem>, markerOptions: MarkerOptions) {
        markerOptions.icon(
            cachedDotDescriptor(
                markerTintArgb = opaqueRadioMapArgb(markerTintArgb),
                selected = false,
                count = cluster.size.toString(),
            ),
        )
    }

    override fun onClusterUpdated(cluster: Cluster<RadioStationClusterItem>, marker: Marker) {
        marker.setIcon(
            cachedDotDescriptor(
                markerTintArgb = opaqueRadioMapArgb(markerTintArgb),
                selected = false,
                count = cluster.size.toString(),
            ),
        )
        marker.title = null
        marker.snippet = null
    }

    private fun cachedDotDescriptor(
        markerTintArgb: Int,
        selected: Boolean,
        count: String?,
    ): BitmapDescriptor =
        iconCache.getOrPut(DotDescriptorKey(markerTintArgb, selected, count)) {
            dotDescriptor(markerTintArgb, selected, count)
        }
}

private data class DotDescriptorKey(
    val markerTintArgb: Int,
    val selected: Boolean,
    val count: String?,
)

private object EmptyRadioMapInfoWindowAdapter : GoogleMap.InfoWindowAdapter {
    override fun getInfoWindow(marker: Marker) = null

    override fun getInfoContents(marker: Marker) = null
}

private fun dotDescriptor(
    markerTintArgb: Int,
    selected: Boolean,
    count: String?,
): BitmapDescriptor {
    val density = 3f
    val diameter = if (count == null) {
        if (selected) 10f else 8f
    } else {
        22f
    }
    val sizePx = (diameter * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    val radius = center - density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = markerTintArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radius, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = if (selected) 2.2f * density else 1f * density
    paint.color = if (selected) android.graphics.Color.WHITE else 0x73050B18
    canvas.drawCircle(center, center, radius - paint.strokeWidth / 2f, paint)
    if (count != null) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF07111E.toInt()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8.5f * density
        val baseline = center - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(count, center, baseline, paint)
    }
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun RadioMapItem.markerTintArgbForItem(markerTintArgb: Int): Int =
    if (approximate) {
        applyRadioMapMarkerAlpha(markerTintArgb, 0.7f)
    } else {
        opaqueRadioMapArgb(markerTintArgb)
    }

private fun opaqueRadioMapArgb(argb: Int): Int =
    0xFF000000.toInt() or (argb and 0x00FFFFFF)

private fun applyRadioMapMarkerAlpha(argb: Int, alphaScale: Float): Int {
    val alpha = (argb ushr 24) and 0xFF
    val scaledAlpha = (alpha * alphaScale).toInt().coerceIn(0, 255)
    return (scaledAlpha shl 24) or (argb and 0x00FFFFFF)
}

private const val DefaultCameraLatitude = 20.0
private const val DefaultCameraLongitude = 0.0
private const val DefaultZoom = 2f
private const val AndroidClusterExpansionZoom = 11f
private const val AndroidExpandedClusterZoomHysteresis = 1.5f
private const val AndroidExpandedMarkerOverlapDegrees = 0.0002
private const val AndroidSpiderfyGoldenAngleRadians = 2.399963229728653
private const val AndroidSpiderfyBaseRadiusDegrees = 0.00016
private const val AndroidSpiderfyMaxRadiusDegrees = 0.0035

private val AndroidRadioMapDarkStyleJson = """
[
  { "elementType": "geometry", "stylers": [{ "color": "#121722" }] },
  { "elementType": "labels.text.fill", "stylers": [{ "color": "#d5dae5" }] },
  { "elementType": "labels.text.stroke", "stylers": [{ "color": "#121722" }] },
  { "featureType": "administrative", "elementType": "geometry.stroke", "stylers": [{ "color": "#374151" }] },
  { "featureType": "landscape", "elementType": "geometry", "stylers": [{ "color": "#151b27" }] },
  { "featureType": "poi", "elementType": "geometry", "stylers": [{ "color": "#1d2633" }] },
  { "featureType": "poi", "elementType": "labels.text.fill", "stylers": [{ "color": "#9aa4b5" }] },
  { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#263244" }] },
  { "featureType": "road", "elementType": "geometry.stroke", "stylers": [{ "color": "#111827" }] },
  { "featureType": "road", "elementType": "labels.text.fill", "stylers": [{ "color": "#c4cad6" }] },
  { "featureType": "transit", "elementType": "geometry", "stylers": [{ "color": "#202a38" }] },
  { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#0b1020" }] },
  { "featureType": "water", "elementType": "labels.text.fill", "stylers": [{ "color": "#8b95a7" }] }
]
""".trimIndent()
