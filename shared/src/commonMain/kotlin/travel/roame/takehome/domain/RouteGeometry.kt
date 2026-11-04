package travel.roame.takehome.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import travel.roame.takehome.model.DestinationMarker
import travel.roame.takehome.model.Flight

data class GeoPoint(val latitude: Double, val longitude: Double)

/** A camera framing: centre plus span, because a bounding box cannot describe a route over the antimeridian. */
data class RouteFrame(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val latitudeSpan: Double,
    val longitudeSpan: Double,
)

private fun Double.toRadians() = this * PI / 180.0
private fun Double.toDegrees() = this * 180.0 / PI

/**
 * Great-circle path between two points. Longitudes are unwrapped, so they may leave [-180, 180]
 * but each step stays continuous. Over a third of the fixture's routes cross the antimeridian,
 * and clamped longitudes would draw those the long way around the world.
 */
fun greatCirclePath(from: GeoPoint, to: GeoPoint, segments: Int = 64): List<GeoPoint> {
    val lat1 = from.latitude.toRadians()
    val lon1 = from.longitude.toRadians()
    val lat2 = to.latitude.toRadians()
    val lon2 = to.longitude.toRadians()

    val halfChord = sin((lat2 - lat1) / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).let { it * it }
    val angle = 2 * asin(min(1.0, sqrt(halfChord)))
    if (angle < 1e-9) return listOf(from, to)

    val path = ArrayList<GeoPoint>(segments + 1)
    var previousLongitude = from.longitude
    for (step in 0..segments) {
        val fraction = step.toDouble() / segments
        val a = sin((1 - fraction) * angle) / sin(angle)
        val b = sin(fraction * angle) / sin(angle)
        val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
        val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
        val z = a * sin(lat1) + b * sin(lat2)
        val latitude = atan2(z, sqrt(x * x + y * y)).toDegrees()
        var longitude = atan2(y, x).toDegrees()
        while (longitude - previousLongitude > 180) longitude -= 360
        while (longitude - previousLongitude < -180) longitude += 360
        path += GeoPoint(latitude, longitude)
        previousLongitude = longitude
    }
    return path
}

fun routePath(flight: Flight): List<GeoPoint> = greatCirclePath(
    GeoPoint(flight.originLatitude, flight.originLongitude),
    GeoPoint(flight.destinationLatitude, flight.destinationLongitude),
)

/** Frames an unwrapped path with some margin. Spans are capped to what a map can show. */
fun routeFrame(path: List<GeoPoint>, margin: Double = 1.35): RouteFrame {
    val south = path.minOf { it.latitude }
    val north = path.maxOf { it.latitude }
    val west = path.minOf { it.longitude }
    val east = path.maxOf { it.longitude }
    return RouteFrame(
        centerLatitude = (south + north) / 2,
        centerLongitude = normalizeLongitude((west + east) / 2),
        latitudeSpan = min(150.0, max(4.0, abs(north - south) * margin)),
        longitudeSpan = min(340.0, max(4.0, abs(east - west) * margin)),
    )
}

fun routeFrame(flight: Flight): RouteFrame = routeFrame(routePath(flight))

/**
 * Frames a set of airports. The largest empty stretch of longitude is the part left out of view, so
 * a set spanning the Pacific comes out as one tight group rather than the whole world the wrong way.
 * Used when results change, because otherwise a narrow search can leave the map looking empty.
 */
fun markersFrame(markers: List<DestinationMarker>, margin: Double = 1.5): RouteFrame? {
    if (markers.isEmpty()) return null
    val longitudes = markers.map { it.longitude }.sorted()
    var west = longitudes.first()
    var largestGap = longitudes.first() + 360.0 - longitudes.last()
    longitudes.zipWithNext().forEach { (a, b) ->
        if (b - a > largestGap) {
            largestGap = b - a
            west = b
        }
    }
    val longitudeExtent = 360.0 - largestGap
    val south = markers.minOf { it.latitude }
    val north = markers.maxOf { it.latitude }
    return RouteFrame(
        centerLatitude = (south + north) / 2,
        centerLongitude = normalizeLongitude(west + longitudeExtent / 2),
        latitudeSpan = min(150.0, max(MINIMUM_MARKER_SPAN, (north - south) * margin)),
        longitudeSpan = min(340.0, max(MINIMUM_MARKER_SPAN, longitudeExtent * margin)),
    )
}

/** Wide enough that a single airport still shows where in the world it is. */
private const val MINIMUM_MARKER_SPAN = 30.0

fun normalizeLongitude(longitude: Double): Double {
    var value = longitude
    while (value > 180) value -= 360
    while (value < -180) value += 360
    return value
}
