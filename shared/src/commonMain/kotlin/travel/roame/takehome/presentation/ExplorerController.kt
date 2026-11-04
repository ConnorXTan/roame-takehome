package travel.roame.takehome.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.FlightSnapshot
import travel.roame.takehome.data.RefreshFailure
import travel.roame.takehome.data.RefreshStatus
import travel.roame.takehome.domain.activeFilters
import travel.roame.takehome.domain.aggregateByDestination
import travel.roame.takehome.domain.filterFlights
import travel.roame.takehome.domain.filterOptions
import travel.roame.takehome.domain.lastUpdatedLabel
import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.DestinationMarker
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightQuery

/** Handle returned by [ExplorerController.watch]. */
fun interface Subscription {
    fun cancel()
}

/**
 * The one state holder behind both apps. Each native UI renders [state] and calls these methods;
 * search, filtering, selection and saving all live here.
 *
 * Every method must be called on [main]. Filtering runs on [compute] and storage on the store's
 * own dispatcher, so none of it blocks the UI thread.
 */
class ExplorerController(
    private val repository: FlightRepository,
    main: CoroutineDispatcher,
    private val compute: CoroutineDispatcher,
    private val onClose: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val query = MutableStateFlow(FlightQuery())
    private val _state = MutableStateFlow(ExplorerState())

    private var selectedFlightId: String? = null
    private var selectedDestinationCode: String? = null
    private val cacheLoad = scope.launch { repository.loadCache() }
    private var started = false
    private var hasSnapshot = false
    private var refreshFailed = false
    private var retryAction: (() -> Unit)? = null
    private var nextMessageId = 0

    val state: StateFlow<ExplorerState> = _state.asStateFlow()

    init {
        observeResults()
        observeSnapshot()
        observeSaved()
    }

    /**
     * Fetches for the first time. Separate from construction so a platform can settle anything the
     * request depends on first, such as Android's local network permission, without racing it.
     * The cache is already loading by now, so cached flights show whenever this is called.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            cacheLoad.join()
            repository.refresh()
        }
    }

    /** For Swift, which cannot collect a Flow directly. The callback always arrives on the main thread. */
    fun watch(onChange: (ExplorerState) -> Unit): Subscription {
        val job = state.onEach(onChange).launchIn(scope)
        return Subscription { job.cancel() }
    }

    fun refresh() {
        scope.launch { repository.refresh() }
    }

    // Search and filters

    fun setOriginText(text: String) = updateQuery { it.copy(originText = text) }

    fun setDestinationText(text: String) = updateQuery { it.copy(destinationText = text) }

    /** Moving the start past the end drags the end along, so the range can never invert. */
    fun setDateStart(date: LocalDate?) = updateQuery { current ->
        val end = current.dateEnd?.takeUnless { date != null && it < date }
        current.copy(dateStart = date, dateEnd = end ?: date?.takeIf { current.dateEnd != null })
    }

    fun setDateEnd(date: LocalDate?) = updateQuery { current ->
        val start = current.dateStart?.takeUnless { date != null && it > date }
        current.copy(dateEnd = date, dateStart = start ?: date?.takeIf { current.dateStart != null })
    }

    fun setDateStartIso(iso: String?) = setDateStart(iso?.let(LocalDate::parse))

    fun setDateEndIso(iso: String?) = setDateEnd(iso?.let(LocalDate::parse))

    fun setCabin(cabin: String?) = updateQuery { it.copy(cabin = cabin) }

    fun setMaxPoints(points: Int?) = updateQuery { it.copy(maxPoints = points) }

    fun toggleProgram(program: String) = updateQuery { current ->
        val programs = if (program in current.programs) current.programs - program else current.programs + program
        current.copy(programs = programs)
    }

    fun removeFilter(filter: ActiveFilter) = updateQuery { current ->
        when (filter.kind) {
            FilterKind.ORIGIN -> current.copy(originText = "")
            FilterKind.DESTINATION -> current.copy(destinationText = "")
            FilterKind.DATES -> current.copy(dateStart = null, dateEnd = null)
            FilterKind.CABIN -> current.copy(cabin = null)
            FilterKind.MAX_POINTS -> current.copy(maxPoints = null)
            FilterKind.PROGRAM -> current.copy(programs = current.programs - filter.value)
        }
    }

    fun resetFilters() = updateQuery { FlightQuery() }

    // Selection

    /** Null clears the flight but keeps an open destination, so closing a card returns to its list. */
    fun selectFlight(id: String?) {
        selectedFlightId = id
        _state.update { it.withSelection() }
    }

    fun selectDestination(code: String?) {
        selectedDestinationCode = code
        selectedFlightId = null
        _state.update { it.withSelection() }
    }

    fun clearSelection() {
        selectedDestinationCode = null
        selectedFlightId = null
        _state.update { it.withSelection() }
    }

    // Saved flights

    fun save(flight: Flight) {
        writeSaved(flight.id, "Couldn't save ${flight.routeLabel}.", retry = { save(flight) }) {
            repository.save(flight)
        }
    }

    fun remove(flight: Flight) {
        writeSaved(flight.id, "Couldn't remove ${flight.routeLabel}.", retry = { remove(flight) }) {
            repository.remove(flight.id)
        }
    }

    fun retryMessage() {
        val action = retryAction
        dismissMessage()
        action?.invoke()
    }

    fun dismissMessage() {
        retryAction = null
        _state.update { it.copy(message = null) }
    }

    fun close() {
        scope.cancel()
        onClose()
    }

    // Internals

    private val Flight.routeLabel: String get() = "$origin → $destination"

    private fun updateQuery(change: (FlightQuery) -> FlightQuery) {
        val next = normalized(change(query.value))
        query.value = next
        // The query itself updates synchronously so chips and controls never lag the user's input;
        // only the results below arrive asynchronously.
        _state.update { it.copy(query = next, activeFilters = activeFilters(next)) }
    }

    /** A control pushed back to the edge of the data's range is no restriction at all. */
    private fun normalized(query: FlightQuery): FlightQuery {
        val options = _state.value.options
        return query.copy(
            dateStart = query.dateStart?.takeUnless { options.firstDate != null && it <= options.firstDate },
            dateEnd = query.dateEnd?.takeUnless { options.lastDate != null && it >= options.lastDate },
            maxPoints = query.maxPoints?.takeUnless { options.maxPoints > 0 && it >= options.maxPoints },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeResults() {
        combine(repository.snapshot, query) { snapshot, query -> snapshot to query }
            // mapLatest drops any evaluation that a newer input has overtaken, so a slow filter
            // pass can never overwrite the results of the user's latest change.
            .mapLatest { (snapshot, query) ->
                withContext(compute) {
                    val results = filterFlights(snapshot?.flights.orEmpty(), query)
                    Evaluation(results, aggregateByDestination(results))
                }
            }
            .onEach { evaluation ->
                _state.update { current ->
                    val changed = evaluation.results != current.results
                    current.copy(
                        results = evaluation.results,
                        resultsRevision = current.resultsRevision + if (changed) 1 else 0,
                        markers = evaluation.markers,
                    ).withSelection().withContent()
                }
            }
            .launchIn(scope)
    }

    private fun observeSnapshot() {
        combine(repository.snapshot, repository.status) { snapshot, status -> snapshot to status }
            .mapLatest { (snapshot, status) ->
                val options = withContext(compute) { filterOptions(snapshot?.flights.orEmpty()) }
                Triple(snapshot, status, options)
            }
            .onEach { (snapshot, status, options) ->
                hasSnapshot = snapshot != null
                refreshFailed = status is RefreshStatus.Failed
                _state.update { it.withData(snapshot, status, options) }
                // Bounds may have moved under an existing filter, so re-check it against the new data.
                updateQuery { current -> current }
            }
            .launchIn(scope)
    }

    private fun observeSaved() {
        repository.saved
            .catch { showMessage("Couldn't load your saved flights.", canRetry = false) }
            .onEach { saved ->
                _state.update { current ->
                    current.copy(
                        saved = saved,
                        savedRevision = current.savedRevision + 1,
                        savedIds = saved.mapTo(mutableSetOf()) { it.id },
                    )
                }
            }
            .launchIn(scope)
    }

    /**
     * Saved state is only ever read back from the database, never set here, so the UI cannot show a
     * write that didn't happen. A failure just leaves the old state in place and tells the user.
     */
    private fun writeSaved(id: String, failureText: String, retry: () -> Unit, write: suspend () -> Unit) {
        if (id in _state.value.pendingSaveIds) return
        _state.update { it.copy(pendingSaveIds = it.pendingSaveIds + id) }
        scope.launch {
            try {
                write()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                retryAction = retry
                showMessage("$failureText Your change wasn't kept.", canRetry = true)
            } finally {
                _state.update { it.copy(pendingSaveIds = it.pendingSaveIds - id) }
            }
        }
    }

    private fun showMessage(text: String, canRetry: Boolean) {
        val message = UserMessage(id = ++nextMessageId, text = text, canRetry = canRetry)
        _state.update { it.copy(message = message) }
    }

    /** Resolves ids against current results. A selection the filters no longer include is dropped here. */
    private fun ExplorerState.withSelection(): ExplorerState {
        val flight = selectedFlightId?.let { id -> results.firstOrNull { it.id == id } }
        val destination: DestinationMarker? = selectedDestinationCode?.let { code -> markers.firstOrNull { it.code == code } }
        selectedFlightId = flight?.id
        selectedDestinationCode = destination?.code
        return copy(
            selectedFlight = flight,
            selectedDestination = destination,
            destinationFlights = destination
                ?.let { marker -> results.filter { it.destination == marker.code }.sortedBy { it.points } }
                .orEmpty(),
        )
    }

    private fun ExplorerState.withData(
        snapshot: FlightSnapshot?,
        status: RefreshStatus,
        options: FilterOptions,
    ): ExplorerState {
        val failure = (status as? RefreshStatus.Failed)?.reason
        return copy(
            isRefreshing = status == RefreshStatus.Refreshing,
            failureMessage = failure?.let(::describe),
            isStale = snapshot != null && failure != null,
            lastUpdatedLabel = snapshot?.let { lastUpdatedLabel(it.fetchedAt) },
            totalFlightCount = snapshot?.flights?.size ?: 0,
            options = options,
        ).withContent()
    }

    private fun ExplorerState.withContent(): ExplorerState {
        val content = when {
            !hasSnapshot -> if (refreshFailed) ContentKind.ERROR else ContentKind.LOADING
            totalFlightCount == 0 -> ContentKind.NO_FLIGHTS
            results.isEmpty() -> ContentKind.NO_MATCHES
            else -> ContentKind.RESULTS
        }
        return copy(content = content)
    }

    private fun describe(failure: RefreshFailure): String = when (failure) {
        RefreshFailure.UNREACHABLE -> "Can't reach the flight service. Check your connection and try again."
        RefreshFailure.SERVER -> "The flight service is having trouble. Please try again."
        RefreshFailure.INVALID_RESPONSE -> "The flight service sent data we couldn't read. Please try again."
        RefreshFailure.STORAGE -> "Couldn't store flights on this device. Please try again."
    }

    private class Evaluation(val results: List<Flight>, val markers: List<DestinationMarker>)
}
