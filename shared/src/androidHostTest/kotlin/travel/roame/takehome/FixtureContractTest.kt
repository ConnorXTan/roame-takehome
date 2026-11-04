package travel.roame.takehome

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import travel.roame.takehome.domain.aggregateByDestination
import travel.roame.takehome.domain.filterFlights
import travel.roame.takehome.domain.filterOptions
import travel.roame.takehome.domain.normalizeLongitude
import travel.roame.takehome.domain.routePath
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightQuery
import travel.roame.takehome.model.FlightsEnvelope

/** Runs the real logic over the real fixture, so the rules are checked at full size and not only on toy data. */
class FixtureContractTest {
    private val flights: List<Flight> = run {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "fixtures/flights.json") }
            .first { it.exists() }
        Json.decodeFromString<FlightsEnvelope>(file.readText()).flights
    }

    @Test
    fun theWholeFixtureDecodes() {
        assertEquals(1_000, flights.size)
        assertEquals(1_000, flights.map { it.id }.toSet().size)
    }

    @Test
    fun theDefaultQueryShowsEveryFlight() {
        assertEquals(1_000, filterFlights(flights, FlightQuery()).size)
    }

    @Test
    fun optionsCoverTheDocumentedRange() {
        val options = filterOptions(flights)
        assertEquals(LocalDate.parse("2027-02-01"), options.firstDate)
        assertEquals(LocalDate.parse("2027-04-01"), options.lastDate)
    }

    @Test
    fun everyDestinationAirportGetsItsOwnMarker() {
        val markers = aggregateByDestination(flights)
        assertEquals(24, markers.size)
        assertEquals(1_000, markers.sumOf { it.flightCount })
        assertTrue(markers.count { it.city == "Tokyo" } == 2, "HND and NRT must not merge")
    }

    @Test
    fun inclusiveBoundariesHoldOnRealData() {
        val first = LocalDate.parse("2027-02-01")
        val onFirstDay = flights.count { it.departureDate == first }
        assertEquals(onFirstDay, filterFlights(flights, FlightQuery(dateStart = first, dateEnd = first)).size)

        val limit = 50_000
        val atOrUnder = flights.count { it.points <= limit }
        assertEquals(atOrUnder, filterFlights(flights, FlightQuery(maxPoints = limit)).size)
        assertTrue(flights.any { it.points == limit }, "the fixture should exercise the exact boundary")
    }

    @Test
    fun noRouteWrapsAroundTheWorld() {
        // Near-polar routes legitimately swing tens of degrees per step, so the rule is "never wraps", not "small".
        flights.forEach { flight ->
            val path = routePath(flight)
            path.zipWithNext().forEach { (a, b) ->
                assertTrue(abs(a.longitude - b.longitude) < 180, "${flight.id} wraps ${a.longitude} to ${b.longitude}")
            }
            assertEquals(flight.destinationLongitude, normalizeLongitude(path.last().longitude), 1e-6, flight.id)
        }
    }
}
