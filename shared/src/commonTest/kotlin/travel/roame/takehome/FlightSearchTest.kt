package travel.roame.takehome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import travel.roame.takehome.domain.activeFilters
import travel.roame.takehome.domain.filterFlights
import travel.roame.takehome.domain.filterOptions
import travel.roame.takehome.domain.flightOrder
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FlightQuery

class FlightSearchTest {
    private val jfkLhr = flight("a", "JFK", "LHR", originCity = "New York", destinationCity = "London")
    private val sfoNrt = flight("b", "SFO", "NRT", originCity = "San Francisco", destinationCity = "Tokyo")
    private val laxHnd = flight("c", "LAX", "HND", originCity = "Los Angeles", destinationCity = "Tokyo")
    private val places = listOf(jfkLhr, sfoNrt, laxHnd)

    private fun ids(query: FlightQuery, flights: List<travel.roame.takehome.model.Flight> = places) =
        filterFlights(flights, query).map { it.id }

    @Test
    fun emptyQueryIncludesEverything() {
        assertEquals(listOf("a", "b", "c"), ids(FlightQuery()))
    }

    @Test
    fun originMatchesAirportCode() {
        assertEquals(listOf("a"), ids(FlightQuery(originText = "JFK")))
    }

    @Test
    fun originMatchesCityName() {
        assertEquals(listOf("b"), ids(FlightQuery(originText = "San Francisco")))
    }

    @Test
    fun destinationMatchesAirportCodeAndCity() {
        assertEquals(listOf("b"), ids(FlightQuery(destinationText = "NRT")))
        assertEquals(listOf("b", "c"), ids(FlightQuery(destinationText = "Tokyo")))
    }

    @Test
    fun searchIgnoresCapitalization() {
        assertEquals(listOf("a"), ids(FlightQuery(originText = "jFk")))
        assertEquals(listOf("b", "c"), ids(FlightQuery(destinationText = "tOKYO")))
    }

    @Test
    fun searchIgnoresSurroundingWhitespace() {
        assertEquals(listOf("a"), ids(FlightQuery(originText = "  jfk\t")))
        assertEquals(listOf("b", "c"), ids(FlightQuery(destinationText = "\n tokyo  ")))
    }

    @Test
    fun whitespaceOnlySearchIsNoSearch() {
        assertEquals(listOf("a", "b", "c"), ids(FlightQuery(originText = "   ", destinationText = "\t")))
    }

    @Test
    fun searchMatchesPartialText() {
        assertEquals(listOf("a"), ids(FlightQuery(originText = "york")))
    }

    @Test
    fun originAndDestinationSearchTheirOwnSide() {
        assertEquals(emptyList(), ids(FlightQuery(originText = "Tokyo")))
        assertEquals(emptyList(), ids(FlightQuery(destinationText = "JFK")))
    }

    @Test
    fun originAndDestinationCombineWithAnd() {
        assertEquals(listOf("c"), ids(FlightQuery(originText = "lax", destinationText = "tokyo")))
        assertEquals(emptyList(), ids(FlightQuery(originText = "jfk", destinationText = "tokyo")))
    }

    private val dated = listOf(
        flight("jan31", date = "2027-01-31"),
        flight("feb01", date = "2027-02-01"),
        flight("feb15", date = "2027-02-15"),
        flight("mar01", date = "2027-03-01"),
        flight("mar02", date = "2027-03-02"),
    )

    private fun date(iso: String) = LocalDate.parse(iso)

    @Test
    fun dateRangeIncludesBothBoundaries() {
        val query = FlightQuery(dateStart = date("2027-02-01"), dateEnd = date("2027-03-01"))
        assertEquals(listOf("feb01", "feb15", "mar01"), ids(query, dated))
    }

    @Test
    fun dateRangeExcludesTheDayEitherSide() {
        val query = FlightQuery(dateStart = date("2027-02-02"), dateEnd = date("2027-02-28"))
        assertEquals(listOf("feb15"), ids(query, dated))
    }

    @Test
    fun singleDayRangeMatchesThatDay() {
        val query = FlightQuery(dateStart = date("2027-02-15"), dateEnd = date("2027-02-15"))
        assertEquals(listOf("feb15"), ids(query, dated))
    }

    @Test
    fun openEndedDateRanges() {
        assertEquals(listOf("mar01", "mar02"), ids(FlightQuery(dateStart = date("2027-03-01")), dated))
        assertEquals(listOf("jan31", "feb01"), ids(FlightQuery(dateEnd = date("2027-02-01")), dated))
    }

    private val priced = listOf(flight("low", points = 39_500), flight("limit", points = 40_000), flight("over", points = 40_001))

    @Test
    fun maximumPointsIncludesTheLimit() {
        assertEquals(listOf("low", "limit"), ids(FlightQuery(maxPoints = 40_000), priced))
    }

    @Test
    fun maximumPointsExcludesOneOverTheLimit() {
        assertEquals(listOf("low"), ids(FlightQuery(maxPoints = 39_999), priced))
    }

    @Test
    fun cabinMatchesExactly() {
        val flights = listOf(flight("e", cabin = "Economy"), flight("pe", cabin = "Premium Economy"), flight("b", cabin = "Business"))
        assertEquals(listOf("e"), ids(FlightQuery(cabin = "Economy"), flights))
    }

    private val programs = listOf(
        flight("av", program = "Avios"),
        flight("ae", program = "Aeroplan"),
        flight("fb", program = "Flying Blue"),
    )

    @Test
    fun aFlightCanMatchAnySelectedProgram() {
        assertEquals(listOf("av", "fb"), ids(FlightQuery(programs = setOf("Avios", "Flying Blue")), programs))
    }

    @Test
    fun noSelectedProgramsIncludesAll() {
        assertEquals(listOf("av", "ae", "fb"), ids(FlightQuery(programs = emptySet()), programs))
    }

    @Test
    fun mileagePlanAndMileagePlusAreDifferentPrograms() {
        val flights = listOf(flight("plan", program = "Mileage Plan"), flight("plus", program = "MileagePlus"))
        assertEquals(listOf("plan"), ids(FlightQuery(programs = setOf("Mileage Plan")), flights))
    }

    @Test
    fun differentFiltersCombineWithAnd() {
        val match = flight("match", "JFK", cabin = "Business", points = 50_000, program = "Avios", date = "2027-02-10")
        val flights = listOf(
            match,
            match.copy(id = "wrong-origin", origin = "BOS", originCity = "Boston"),
            match.copy(id = "wrong-cabin", cabin = "Economy"),
            match.copy(id = "too-expensive", points = 50_500),
            match.copy(id = "wrong-program", program = "Aeroplan"),
            match.copy(id = "too-late", departureDate = date("2027-03-10")),
        )
        val query = FlightQuery(
            originText = "jfk",
            dateEnd = date("2027-02-28"),
            cabin = "Business",
            maxPoints = 50_000,
            programs = setOf("Avios", "Flying Blue"),
        )
        assertEquals(listOf("match"), ids(query, flights))
    }

    @Test
    fun orderIsSoonestThenCheapestThenId() {
        val flights = listOf(
            flight("z", date = "2027-02-02", points = 10_000),
            flight("c", date = "2027-02-01", points = 20_000),
            flight("b", date = "2027-02-01", points = 10_000),
            flight("a", date = "2027-02-01", points = 10_000),
        )
        assertEquals(listOf("a", "b", "c", "z"), flights.sortedWith(flightOrder).map { it.id })
    }

    @Test
    fun filterOptionsComeFromTheData() {
        val options = filterOptions(
            listOf(
                flight("1", date = "2027-02-01", points = 9_000, cabin = "Business", program = "Avios"),
                flight("2", date = "2027-04-01", points = 133_000, cabin = "Economy", program = "Aeroplan"),
                flight("3", date = "2027-03-01", points = 50_000, cabin = "Premium Economy", program = "Avios"),
            ),
        )
        assertEquals(date("2027-02-01"), options.firstDate)
        assertEquals(date("2027-04-01"), options.lastDate)
        assertEquals(9_000, options.minPoints)
        assertEquals(133_000, options.maxPoints)
        assertEquals(listOf("Economy", "Premium Economy", "Business"), options.cabins)
        assertEquals(listOf("Aeroplan", "Avios"), options.programs)
    }

    @Test
    fun activeFiltersDescribeEachRestriction() {
        val filters = activeFilters(
            FlightQuery(
                originText = " bos ",
                destinationText = "Tokyo",
                dateStart = date("2027-02-03"),
                dateEnd = date("2027-03-01"),
                cabin = "Business",
                maxPoints = 50_000,
                programs = setOf("Avios", "Aeroplan"),
            ),
        )
        assertEquals(
            listOf("From bos", "To Tokyo", "Feb 3 – Mar 1", "Business", "Up to 50,000 pts", "Aeroplan", "Avios"),
            filters.map { it.label },
        )
        assertEquals(FilterKind.PROGRAM, filters.last().kind)
    }

    @Test
    fun anEmptyQueryHasNoActiveFilters() {
        assertEquals(emptyList(), activeFilters(FlightQuery(originText = "   ")))
    }
}
