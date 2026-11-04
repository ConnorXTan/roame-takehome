package travel.roame.takehome

import kotlin.test.Test
import kotlin.test.assertEquals
import travel.roame.takehome.domain.aggregateByDestination
import travel.roame.takehome.domain.filterFlights
import travel.roame.takehome.model.FlightQuery

class MapAggregationTest {
    @Test
    fun groupsByDestinationAirport() {
        val markers = aggregateByDestination(
            listOf(flight("1", destination = "LHR"), flight("2", destination = "LHR"), flight("3", destination = "CDG", destinationCity = "Paris")),
        )
        assertEquals(listOf("CDG", "LHR"), markers.map { it.code })
        assertEquals(listOf(1, 2), markers.map { it.flightCount })
    }

    @Test
    fun twoAirportsInOneCityStaySeparate() {
        val markers = aggregateByDestination(
            listOf(
                flight("1", destination = "HND", destinationCity = "Tokyo", destinationLatitude = 35.55, destinationLongitude = 139.79),
                flight("2", destination = "NRT", destinationCity = "Tokyo", destinationLatitude = 35.77, destinationLongitude = 140.39),
            ),
        )
        assertEquals(listOf("HND", "NRT"), markers.map { it.code })
        assertEquals(listOf(139.79, 140.39), markers.map { it.longitude })
    }

    @Test
    fun markerShowsLowestPointsNotFirst() {
        val marker = aggregateByDestination(
            listOf(flight("1", points = 60_000), flight("2", points = 35_000), flight("3", points = 90_000)),
        ).single()
        assertEquals(35_000, marker.lowestPoints)
        assertEquals(3, marker.flightCount)
    }

    @Test
    fun markersOnlySummariseTheFilteredFlights() {
        val flights = listOf(
            flight("cheap-economy", points = 30_000, cabin = "Economy"),
            flight("business", points = 80_000, cabin = "Business"),
            flight("other", destination = "CDG", points = 50_000, cabin = "Economy"),
        )
        val marker = aggregateByDestination(filterFlights(flights, FlightQuery(cabin = "Business"))).single()
        assertEquals("LHR", marker.code)
        assertEquals(1, marker.flightCount)
        assertEquals(80_000, marker.lowestPoints)
    }

    @Test
    fun markerCountsAddUpToTheResultCount() {
        val flights = (1..50).map { flight("f$it", destination = "D${it % 7}", points = 1_000 * it) }
        assertEquals(flights.size, aggregateByDestination(flights).sumOf { it.flightCount })
    }

    @Test
    fun noFlightsMeansNoMarkers() {
        assertEquals(emptyList(), aggregateByDestination(emptyList()))
    }
}
