package travel.roame.takehome.presentation

import travel.roame.takehome.data.RefreshFailure
import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.DestinationMarker
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightQuery

/** What the discovery screens should show. Decided here so both apps can never disagree about it. */
enum class ContentKind {
    /** First load, nothing cached yet. */
    LOADING,

    /** Nothing cached and the request failed: show [ExplorerState.failureMessage] with a retry. */
    ERROR,

    /** The server answered successfully with no flights at all. */
    NO_FLIGHTS,

    /** There are flights, but the current search and filters exclude them all. */
    NO_MATCHES,

    RESULTS,
}

/** A failure the user needs to hear about, shown once and then dismissed. */
data class UserMessage(val id: Int, val text: String, val canRetry: Boolean)

data class ExplorerState(
    val content: ContentKind = ContentKind.LOADING,
    val isRefreshing: Boolean = false,
    /** Set when a refresh failed. With data on screen it drives the stale banner, without it the error state. */
    val failureMessage: String? = null,
    /** True when the data on screen is older than a refresh we just failed to complete. */
    val isStale: Boolean = false,
    val lastUpdatedLabel: String? = null,
    val totalFlightCount: Int = 0,

    val query: FlightQuery = FlightQuery(),
    val options: FilterOptions = FilterOptions(),
    val activeFilters: List<ActiveFilter> = emptyList(),

    val results: List<Flight> = emptyList(),
    /** Bumped whenever [results] changes, so a client can skip re-mapping an unchanged list. */
    val resultsRevision: Int = 0,
    val markers: List<DestinationMarker> = emptyList(),

    val selectedFlight: Flight? = null,
    val selectedDestination: DestinationMarker? = null,
    /** The flights behind [selectedDestination], cheapest first. */
    val destinationFlights: List<Flight> = emptyList(),

    val saved: List<Flight> = emptyList(),
    val savedRevision: Int = 0,
    val savedIds: Set<String> = emptySet(),
    /** Flights with a save or remove in flight, so the button can wait for the database to answer. */
    val pendingSaveIds: Set<String> = emptySet(),

    val message: UserMessage? = null,
) {
    fun isSaved(flightId: String): Boolean = flightId in savedIds
    fun isPending(flightId: String): Boolean = flightId in pendingSaveIds
}
