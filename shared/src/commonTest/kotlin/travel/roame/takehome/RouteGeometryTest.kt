package travel.roame.takehome

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import travel.roame.takehome.domain.GeoPoint
import travel.roame.takehome.domain.greatCirclePath
import travel.roame.takehome.domain.markersFrame
import travel.roame.takehome.domain.normalizeLongitude
import travel.roame.takehome.domain.routeFrame
import travel.roame.takehome.model.DestinationMarker

class RouteGeometryTest {
    private val vancouver = GeoPoint(49.19, -123.18)
    private val sydney = GeoPoint(-33.95, 151.18)
    private val newYork = GeoPoint(40.64, -73.78)
    private val london = GeoPoint(51.47, -0.46)

    @Test
    fun pathStartsAndEndsAtTheAirports() {
        val path = greatCirclePath(newYork, london)
        assertEquals(newYork.latitude, path.first().latitude, 1e-6)
        assertEquals(newYork.longitude, path.first().longitude, 1e-6)
        assertEquals(london.latitude, path.last().latitude, 1e-6)
        assertEquals(london.longitude, path.last().longitude, 1e-6)
    }

    @Test
    fun pathAcrossTheAntimeridianHasNoJump() {
        val path = greatCirclePath(vancouver, sydney)
        path.zipWithNext().forEach { (a, b) ->
            assertTrue(abs(a.longitude - b.longitude) < 20, "jump between ${a.longitude} and ${b.longitude}")
        }
        // West across the Pacific would mean heading through 0 degrees; the short way leaves -180 behind.
        assertTrue(path.last().longitude < -180, "expected an unwrapped longitude, got ${path.last().longitude}")
        assertEquals(sydney.longitude, normalizeLongitude(path.last().longitude), 1e-6)
    }

    @Test
    fun longHaulRoutesBendTowardThePole() {
        val midpoint = greatCirclePath(newYork, GeoPoint(35.76, 140.39)).let { it[it.size / 2] }
        assertTrue(midpoint.latitude > 60, "expected a polar arc, got ${midpoint.latitude}")
    }

    @Test
    fun identicalPointsDoNotDivideByZero() {
        assertEquals(2, greatCirclePath(london, london).size)
    }

    @Test
    fun frameCentresAntimeridianRoutesOnThePacific() {
        val frame = routeFrame(greatCirclePath(vancouver, sydney))
        assertTrue(abs(frame.centerLongitude) > 120, "centred on ${frame.centerLongitude}, the wrong side of the world")
        assertTrue(frame.longitudeSpan < 180, "span ${frame.longitudeSpan} should be the short way across")
    }

    private fun marker(code: String, latitude: Double, longitude: Double) =
        DestinationMarker(code, code, latitude, longitude, flightCount = 1, lowestPoints = 1_000)

    @Test
    fun noMarkersHaveNoFrame() {
        assertNull(markersFrame(emptyList()))
    }

    @Test
    fun aSingleMarkerGetsARegionalFrameCentredOnIt() {
        val frame = assertNotNull(markersFrame(listOf(marker("HND", 35.55, 139.79))))
        assertEquals(35.55, frame.centerLatitude, 1e-6)
        assertEquals(139.79, frame.centerLongitude, 1e-6)
        assertEquals(30.0, frame.longitudeSpan, 1e-6)
    }

    @Test
    fun markersEitherSideOfThePacificFrameTheShortWay() {
        val frame = assertNotNull(markersFrame(listOf(marker("AKL", -37.0, 174.8), marker("HNL", 21.3, -157.9))))
        assertTrue(abs(frame.centerLongitude) > 150, "centred on ${frame.centerLongitude}, the wrong side of the world")
        assertTrue(frame.longitudeSpan < 60, "span ${frame.longitudeSpan} should cross the Pacific, not the Atlantic")
    }

    @Test
    fun longitudesNormalizeIntoRange() {
        assertEquals(-170.0, normalizeLongitude(190.0), 1e-9)
        assertEquals(170.0, normalizeLongitude(-190.0), 1e-9)
        assertEquals(10.0, normalizeLongitude(10.0), 1e-9)
    }
}
