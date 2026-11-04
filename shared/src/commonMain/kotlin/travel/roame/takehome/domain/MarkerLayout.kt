package travel.roame.takehome.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import travel.roame.takehome.model.DestinationMarker

/**
 * Where to draw a marker's label. [latitude] and [longitude] may differ from the airport's when the
 * label had to move aside; [isDisplaced] says when a leader line back to the airport is needed.
 *
 * [leaderLongitude] is the label's longitude unwrapped relative to the airport. A label pushed over
 * the antimeridian has a [longitude] on the far side of the world, and a line drawn to that would
 * cross the whole map. It can leave [-180, 180].
 */
data class MarkerPlacement(
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val leaderLongitude: Double,
    val isDisplaced: Boolean,
)

private const val MARKER_HEIGHT = 25.0
private const val MARKER_GAP = 2.0
private const val MAX_PASSES = 400

/** Labels are drawn at the same size on both platforms, so one estimate of their width serves both. */
private fun labelWidth(marker: DestinationMarker): Double {
    val characters = marker.flightCount.toString().length + compactPoints(marker.lowestPoints).length
    return 24.0 + characters * 7.5
}

/**
 * Nudges overlapping labels apart for the current zoom, so every airport stays readable and tappable.
 * Ten European airports share a few points of screen at world zoom. Clustering them would merge
 * airports, and hiding them would lose their numbers, and each marker has to show both.
 *
 * Works on a Web Mercator plane, where one world is [worldWidth] screen points across, and returns
 * coordinates rather than screen offsets: on iOS an offset moves the drawing but not the tap target.
 *
 * Deterministic for the same input, so a marker can't jitter between two camera-idle events.
 */
fun layoutMarkers(markers: List<DestinationMarker>, worldWidth: Double): List<MarkerPlacement> {
    val boxes = markers.sortedBy { it.code }.map { marker ->
        val width = labelWidth(marker)
        LabelBox(marker, mercatorX(marker.longitude) * worldWidth, mercatorY(marker.latitude) * worldWidth, width)
    }

    var pass = 0
    var moved = true
    while (moved && pass++ < MAX_PASSES) {
        moved = false
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                if (separate(boxes[i], boxes[j])) moved = true
            }
        }
    }

    return boxes.map { box ->
        val displaced = abs(box.x - box.anchorX) > 0.5 || abs(box.y - box.anchorY) > 0.5
        val unwrapped = if (displaced) box.x / worldWidth * 360.0 - 180.0 else box.marker.longitude
        MarkerPlacement(
            code = box.marker.code,
            latitude = if (displaced) latitudeAt(box.y / worldWidth) else box.marker.latitude,
            longitude = normalizeLongitude(unwrapped),
            leaderLongitude = unwrapped,
            isDisplaced = displaced,
        )
    }
}

private class LabelBox(val marker: DestinationMarker, val anchorX: Double, val anchorY: Double, val width: Double) {
    var x = anchorX
    var y = anchorY
}

/**
 * Pushes two overlapping boxes apart. The axis is chosen by overlap as a share of the box's size, not
 * in points: labels are far wider than tall, so comparing points would always move them vertically
 * and stack a whole region into one tall column instead of a compact group.
 */
private fun separate(a: LabelBox, b: LabelBox): Boolean {
    val spanX = (a.width + b.width) / 2 + MARKER_GAP
    val spanY = MARKER_HEIGHT + MARKER_GAP
    val overlapX = spanX - abs(a.x - b.x)
    val overlapY = spanY - abs(a.y - b.y)
    if (overlapX <= 0 || overlapY <= 0) return false

    if (overlapY / spanY <= overlapX / spanX) {
        // Identical positions have no direction of their own, so fall back to a stable one.
        val direction = if (a.y <= b.y) 1.0 else -1.0
        a.y -= direction * overlapY / 2
        b.y += direction * overlapY / 2
    } else {
        val direction = if (a.x <= b.x) 1.0 else -1.0
        a.x -= direction * overlapX / 2
        b.x += direction * overlapX / 2
    }
    return true
}

// Web Mercator on a unit square.

private const val MAX_MERCATOR_LATITUDE = 85.05

internal fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

internal fun mercatorY(latitude: Double): Double {
    val clamped = max(-MAX_MERCATOR_LATITUDE, min(MAX_MERCATOR_LATITUDE, latitude))
    val radians = clamped * PI / 180.0
    return 0.5 - ln(tan(PI / 4 + radians / 2)) / (2 * PI)
}

private fun latitudeAt(y: Double): Double =
    (2 * atan(exp((0.5 - y) * 2 * PI)) - PI / 2) * 180.0 / PI
