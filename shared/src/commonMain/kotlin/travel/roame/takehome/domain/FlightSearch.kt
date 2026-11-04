package travel.roame.takehome.domain

import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.DestinationMarker
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightQuery

/** Stable display order: soonest first, cheapest within a day, id as a deterministic tie-break. */
val flightOrder: Comparator<Flight> =
    compareBy<Flight> { it.departureDate }.thenBy { it.points }.thenBy { it.id }

/** Different filters combine with AND; within loyalty programs a flight may match any selection. */
fun filterFlights(flights: List<Flight>, query: FlightQuery): List<Flight> {
    val origin = query.originText.normalizedForSearch()
    val destination = query.destinationText.normalizedForSearch()
    return flights.filter { flight ->
        matchesPlace(origin, flight.origin, flight.originCity) &&
            matchesPlace(destination, flight.destination, flight.destinationCity) &&
            (query.dateStart == null || flight.departureDate >= query.dateStart) &&
            (query.dateEnd == null || flight.departureDate <= query.dateEnd) &&
            (query.cabin == null || flight.cabin == query.cabin) &&
            (query.maxPoints == null || flight.points <= query.maxPoints) &&
            (query.programs.isEmpty() || flight.program in query.programs)
    }
}

private fun String.normalizedForSearch(): String = trim().lowercase()

private fun matchesPlace(term: String, code: String, city: String): Boolean =
    term.isEmpty() || code.lowercase().contains(term) || city.lowercase().contains(term)

/** One marker per destination airport, summarising only the flights passed in. */
fun aggregateByDestination(flights: List<Flight>): List<DestinationMarker> =
    flights.groupBy { it.destination }
        .map { (code, group) ->
            val first = group.first()
            DestinationMarker(
                code = code,
                city = first.destinationCity,
                latitude = first.destinationLatitude,
                longitude = first.destinationLongitude,
                flightCount = group.size,
                lowestPoints = group.minOf { it.points },
            )
        }
        .sortedBy { it.code }

private val cabinRank = listOf("Economy", "Premium Economy", "Business", "First")

fun filterOptions(flights: List<Flight>): FilterOptions {
    if (flights.isEmpty()) return FilterOptions()
    return FilterOptions(
        firstDate = flights.minOf { it.departureDate },
        lastDate = flights.maxOf { it.departureDate },
        // Known cabins in travel order, anything new after them rather than dropped.
        cabins = flights.map { it.cabin }.distinct().sortedWith(
            compareBy<String> { cabinRank.indexOf(it).let { rank -> if (rank < 0) cabinRank.size else rank } }.thenBy { it },
        ),
        programs = flights.map { it.program }.distinct().sorted(),
        minPoints = flights.minOf { it.points },
        maxPoints = flights.maxOf { it.points },
    )
}

fun activeFilters(query: FlightQuery): List<ActiveFilter> = buildList {
    query.originText.trim().takeIf { it.isNotEmpty() }?.let { add(ActiveFilter(FilterKind.ORIGIN, "From $it")) }
    query.destinationText.trim().takeIf { it.isNotEmpty() }?.let { add(ActiveFilter(FilterKind.DESTINATION, "To $it")) }
    if (query.dateStart != null || query.dateEnd != null) {
        val label = when {
            query.dateStart != null && query.dateEnd != null ->
                if (query.dateStart == query.dateEnd) shortDate(query.dateStart)
                else "${shortDate(query.dateStart)} – ${shortDate(query.dateEnd)}"
            query.dateStart != null -> "From ${shortDate(query.dateStart)}"
            else -> "Until ${shortDate(query.dateEnd!!)}"
        }
        add(ActiveFilter(FilterKind.DATES, label))
    }
    query.cabin?.let { add(ActiveFilter(FilterKind.CABIN, it)) }
    query.maxPoints?.let { add(ActiveFilter(FilterKind.MAX_POINTS, "Up to ${formatPoints(it)} pts")) }
    query.programs.sorted().forEach { add(ActiveFilter(FilterKind.PROGRAM, it, it)) }
}
