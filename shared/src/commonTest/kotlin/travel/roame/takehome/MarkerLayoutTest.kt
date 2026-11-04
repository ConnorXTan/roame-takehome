package travel.roame.takehome

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import travel.roame.takehome.domain.layoutMarkers
import travel.roame.takehome.domain.mercatorX
import travel.roame.takehome.domain.mercatorY
import travel.roame.takehome.model.DestinationMarker

class MarkerLayoutTest {
    private fun marker(code: String, latitude: Double, longitude: Double) =
        DestinationMarker(code, code, latitude, longitude, flightCount = 40, lowestPoints = 20_000)

    private val tokyo = listOf(marker("HND", 35.55, 139.79), marker("NRT", 35.77, 140.39))

    @Test
    fun farApartMarkersStayExactlyOnTheirAirports() {
        val placements = layoutMarkers(listOf(marker("LHR", 51.47, -0.46), marker("SYD", -33.95, 151.18)), worldWidth = 800.0)
        assertFalse(placements.any { it.isDisplaced })
        assertEquals(51.47, placements.first { it.code == "LHR" }.latitude)
        assertEquals(151.18, placements.first { it.code == "SYD" }.longitude)
    }

    @Test
    fun overlappingMarkersMoveApart() {
        val placements = layoutMarkers(tokyo, worldWidth = 800.0)
        assertTrue(placements.all { it.isDisplaced })
        val (a, b) = placements
        assertNotEquals(a.latitude, b.latitude)
    }

    @Test
    fun zoomingInReleasesMarkersBackToTheirAirports() {
        val zoomedIn = layoutMarkers(tokyo, worldWidth = 512.0 * 1024)
        assertFalse(zoomedIn.any { it.isDisplaced })
    }

    @Test
    fun identicalCoordinatesStillSeparate() {
        val placements = layoutMarkers(listOf(marker("AAA", 10.0, 10.0), marker("BBB", 10.0, 10.0)), worldWidth = 800.0)
        assertNotEquals(placements[0].latitude, placements[1].latitude)
    }

    @Test
    fun aLabelPushedOverTheAntimeridianKeepsItsLeaderLineShort() {
        val placements = layoutMarkers(listOf(marker("AAA", -37.0, 179.9), marker("BBB", -37.0, 179.5)), worldWidth = 800.0)
        val pushedOver = placements.first { it.code == "AAA" }
        assertTrue(pushedOver.isDisplaced)
        assertTrue(pushedOver.longitude < 0, "the label wraps to the far side, got ${pushedOver.longitude}")
        assertTrue(abs(pushedOver.leaderLongitude - 179.9) < 30, "leader runs ${pushedOver.leaderLongitude} from 179.9")
    }

    @Test
    fun theSameInputAlwaysGivesTheSameLayout() {
        val markers = tokyo + marker("ICN", 37.47, 126.45)
        assertEquals(layoutMarkers(markers, 700.0), layoutMarkers(markers.reversed(), 700.0))
    }

    @Test
    fun everyMarkerGetsAPlacement() {
        assertEquals(setOf("HND", "NRT"), layoutMarkers(tokyo, 800.0).map { it.code }.toSet())
        assertEquals(emptyList(), layoutMarkers(emptyList(), 800.0))
    }

    @Test
    fun mercatorIsCentredAndClamped() {
        assertEquals(0.5, mercatorX(0.0), 1e-9)
        assertEquals(0.5, mercatorY(0.0), 1e-9)
        assertTrue(abs(mercatorY(90.0)) < 0.01, "the pole clamps to the top of the map instead of infinity")
    }
}
