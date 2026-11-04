package travel.roame.takehome.android.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import travel.roame.takehome.domain.RouteFrame
import travel.roame.takehome.domain.compactPoints
import travel.roame.takehome.domain.layoutMarkers
import travel.roame.takehome.domain.routeFrame
import travel.roame.takehome.domain.routePath
import travel.roame.takehome.model.DestinationMarker
import travel.roame.takehome.model.Flight
import travel.roame.takehome.presentation.ExplorerState

private const val MARKER_SOURCE = "destinations"
private const val MARKER_LAYER = "destination-markers"
private const val ROUTE_SOURCE = "route"
private const val ROUTE_LAYER = "route-line"
private const val ENDPOINT_SOURCE = "route-endpoints"
private const val ENDPOINT_LAYER = "route-endpoint-dots"
private const val ANCHOR_SOURCE = "marker-anchors"
private const val ANCHOR_LAYER = "marker-anchor-dots"
private const val LEADER_SOURCE = "marker-leaders"
private const val LEADER_LAYER = "marker-leader-lines"

/** MapLibre draws one world this many points wide at zoom 0. */
private const val WORLD_WIDTH_AT_ZOOM_ZERO = 512.0

@Composable
fun FlightMap(
    state: ExplorerState,
    onMarkerTap: (String) -> Unit,
    onMapTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = Explorer.colors
    val tapMarker = rememberUpdatedState(onMarkerTap)
    val tapMap = rememberUpdatedState(onMapTap)
    val accent = colors.accent.toArgb()
    val ink = colors.ink.toArgb()
    val surface = colors.surface.toArgb()

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val renderer = remember {
        MapRenderer(
            context = context,
            palette = MarkerPalette(accent, ink, surface),
            onMarkerTap = { tapMarker.value(it) },
            onMapTap = { tapMap.value() },
        )
    }

    // MapView owns native resources: it has to hear every lifecycle event, and its destroy, or it leaks.
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
        onDispose {
            lifecycle.removeObserver(observer)
            renderer.detach()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) { mapView.getMapAsync(renderer::attach) }
    LaunchedEffect(state.markers, state.selectedDestination?.code, state.selectedFlight?.destination) {
        renderer.showMarkers(state.markers, state.selectedDestination?.code ?: state.selectedFlight?.destination)
    }
    LaunchedEffect(state.selectedFlight?.id) { renderer.showRoute(state.selectedFlight) }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private data class MarkerPalette(val accent: Int, val ink: Int, val surface: Int)

/** Everything that touches the imperative MapLibre API. Calls made before the style loads are replayed after. */
private class MapRenderer(
    private val context: Context,
    private val palette: MarkerPalette,
    private val onMarkerTap: (String) -> Unit,
    private val onMapTap: () -> Unit,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var markers: List<DestinationMarker> = emptyList()
    private var highlighted: String? = null
    private var route: Flight? = null
    private var laidOutZoom = -1.0
    private val addedIcons = mutableSetOf<String>()
    private val density = context.resources.displayMetrics.density

    private val isDark: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun attach(map: MapLibreMap) {
        this.map = map
        map.uiSettings.apply {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            setAttributionMargins((8 * density).toInt(), (8 * density).toInt(), 0, 0)
            attributionGravity = android.view.Gravity.TOP or android.view.Gravity.START
            setLogoMargins((8 * density).toInt(), (8 * density).toInt(), 0, 0)
            logoGravity = android.view.Gravity.TOP or android.view.Gravity.END
        }
        map.cameraPosition = CameraPosition.Builder().target(LatLng(20.0, 45.0)).zoom(0.2).build()
        map.setMinZoomPreference(0.0)
        map.setMaxZoomPreference(10.0)
        // Labels re-settle once the camera stops, not on every frame of a pinch.
        map.addOnCameraIdleListener {
            if (abs(map.cameraPosition.zoom - laidOutZoom) > 0.05) renderMarkers()
        }
        map.addOnMapClickListener { point ->
            val screen = map.projection.toScreenLocation(point)
            val code = map.queryRenderedFeatures(screen, MARKER_LAYER).firstOrNull()?.getStringProperty("code")
            if (code != null) onMarkerTap(code) else onMapTap()
            true
        }
        // The style ships in the APK, so markers and routes still draw offline even if tiles can't load.
        val styleAsset = if (isDark) "asset://map_dark.json" else "asset://map_light.json"
        map.setStyle(Style.Builder().fromUri(styleAsset)) { loaded ->
            style = loaded
            loaded.addSource(GeoJsonSource(ROUTE_SOURCE))
            loaded.addSource(GeoJsonSource(ENDPOINT_SOURCE))
            loaded.addSource(GeoJsonSource(MARKER_SOURCE))
            loaded.addSource(GeoJsonSource(ANCHOR_SOURCE))
            loaded.addSource(GeoJsonSource(LEADER_SOURCE))
            loaded.addLayer(
                LineLayer(LEADER_LAYER, LEADER_SOURCE).withProperties(
                    PropertyFactory.lineColor(palette.ink),
                    PropertyFactory.lineOpacity(0.45f),
                    PropertyFactory.lineWidth(1f),
                ),
            )
            loaded.addLayer(
                CircleLayer(ANCHOR_LAYER, ANCHOR_SOURCE).withProperties(
                    PropertyFactory.circleRadius(2.5f),
                    PropertyFactory.circleColor(palette.ink),
                ),
            )
            loaded.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor(palette.accent),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            loaded.addLayer(
                CircleLayer(ENDPOINT_LAYER, ENDPOINT_SOURCE).withProperties(
                    PropertyFactory.circleRadius(5f),
                    PropertyFactory.circleColor(palette.accent),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
            )
            loaded.addLayer(
                SymbolLayer(MARKER_LAYER, MARKER_SOURCE).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    // layoutMarkers already keeps labels apart; a collision pass here would hide airports.
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                    PropertyFactory.symbolSortKey(Expression.get("rank")),
                ),
            )
            renderMarkers()
            renderRoute(animate = false)
        }
    }

    fun detach() {
        map = null
        style = null
    }

    fun showMarkers(next: List<DestinationMarker>, highlight: String?) {
        markers = next
        highlighted = highlight
        renderMarkers()
    }

    fun showRoute(flight: Flight?) {
        route = flight
        renderRoute(animate = true)
    }

    private fun renderMarkers() {
        val style = style ?: return
        val zoom = map?.cameraPosition?.zoom ?: return
        laidOutZoom = zoom
        val placements = layoutMarkers(markers, WORLD_WIDTH_AT_ZOOM_ZERO * 2.0.pow(zoom)).associateBy { it.code }
        val labels = ArrayList<Feature>(markers.size)
        val anchors = ArrayList<Feature>()
        val leaders = ArrayList<Feature>()
        markers.forEach { marker ->
            val placement = placements.getValue(marker.code)
            val selected = marker.code == highlighted
            val position = Point.fromLngLat(placement.longitude, placement.latitude)
            labels += Feature.fromGeometry(position).apply {
                addStringProperty("code", marker.code)
                addStringProperty("icon", iconFor(style, marker, selected))
                // Higher draws on top: the selected marker first, then the cheapest.
                addNumberProperty("rank", if (selected) 2_000_000 else 1_000_000 - marker.lowestPoints)
            }
            if (placement.isDisplaced) {
                val airport = Point.fromLngLat(marker.longitude, marker.latitude)
                anchors += Feature.fromGeometry(airport)
                val labelEnd = Point.fromLngLat(placement.leaderLongitude, placement.latitude)
                leaders += Feature.fromGeometry(LineString.fromLngLats(listOf(airport, labelEnd)))
            }
        }
        style.getSourceAs<GeoJsonSource>(MARKER_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(labels))
        style.getSourceAs<GeoJsonSource>(ANCHOR_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(anchors))
        style.getSourceAs<GeoJsonSource>(LEADER_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(leaders))
    }

    private fun iconFor(style: Style, marker: DestinationMarker, selected: Boolean): String {
        val name = "${marker.flightCount}|${marker.lowestPoints}|$selected"
        if (addedIcons.add(name)) style.addImage(name, drawPill(marker, selected))
        return name
    }

    /** A bitmap rather than a text layer, so a marker doesn't depend on glyphs downloaded from the tile server. */
    private fun drawPill(marker: DestinationMarker, selected: Boolean): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val count = marker.flightCount.toString()
        val price = compactPoints(marker.lowestPoints)
        val countWidth = paint.measureText(count)
        val priceWidth = paint.measureText(price)
        val gap = 6 * density
        val padding = 9 * density
        val height = (25 * density)
        val width = padding * 2 + countWidth + gap + priceWidth
        val bitmap = Bitmap.createBitmap(width.toInt() + 2, height.toInt() + 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = if (selected) palette.accent else palette.surface
        val foreground = if (selected) AndroidColor.WHITE else palette.ink
        val body = RectF(1f, 1f, width + 1, height + 1)
        val radius = height / 2
        canvas.drawRoundRect(body, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        canvas.drawRoundRect(
            body, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = if (selected) palette.accent else (palette.ink and 0x33FFFFFF)
            },
        )
        val baseline = body.centerY() - (paint.descent() + paint.ascent()) / 2
        paint.color = if (selected) foreground else palette.accent
        canvas.drawText(count, padding + 1, baseline, paint)
        paint.color = foreground
        canvas.drawText(price, padding + 1 + countWidth + gap, baseline, paint)
        return bitmap
    }

    private fun renderRoute(animate: Boolean) {
        val style = style ?: return
        val flight = route
        if (flight == null) {
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            style.getSourceAs<GeoJsonSource>(ENDPOINT_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val path = routePath(flight)
        val line = LineString.fromLngLats(path.map { Point.fromLngLat(it.longitude, it.latitude) })
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(Feature.fromGeometry(line))
        style.getSourceAs<GeoJsonSource>(ENDPOINT_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOf(
                    Feature.fromGeometry(Point.fromLngLat(flight.originLongitude, flight.originLatitude)),
                    Feature.fromGeometry(Point.fromLngLat(flight.destinationLongitude, flight.destinationLatitude)),
                ),
            ),
        )
        frame(routeFrame(path), animate)
    }

    private fun frame(frame: RouteFrame, animate: Boolean) {
        val map = map ?: return
        // A centre and zoom instead of LatLngBounds, which can't express a route over the antimeridian.
        val widthDp = map.width / density
        val heightDp = map.height / density
        val byWidth = ln(widthDp * 360.0 / (frame.longitudeSpan * 512.0)) / ln(2.0)
        val byHeight = ln(heightDp * 170.0 / (frame.latitudeSpan * 512.0)) / ln(2.0)
        val zoom = min(byWidth, byHeight).coerceIn(0.2, 6.0)
        // Nudged south so the route sits in the part of the map the bottom card doesn't cover.
        val target = LatLng((frame.centerLatitude - frame.latitudeSpan * 0.18).coerceIn(-80.0, 80.0), frame.centerLongitude)
        val update = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).build())
        if (animate) map.easeCamera(update, 450) else map.moveCamera(update)
    }
}
