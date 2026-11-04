package travel.roame.takehome.data

import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import travel.roame.takehome.domain.flightOrder
import travel.roame.takehome.model.Flight

enum class RefreshFailure { UNREACHABLE, SERVER, INVALID_RESPONSE, STORAGE }

sealed interface RefreshStatus {
    data object Idle : RefreshStatus
    data object Refreshing : RefreshStatus
    data class Failed(val reason: RefreshFailure) : RefreshStatus
}

/** The single source of truth: the network only ever reaches the UI after it has been persisted. */
class FlightRepository(
    private val api: FlightApi,
    private val store: FlightStore,
    private val clock: Clock = Clock.System,
) {
    private val _snapshot = MutableStateFlow<FlightSnapshot?>(null)
    private val _status = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
    private val refreshLock = Mutex()

    val snapshot: StateFlow<FlightSnapshot?> = _snapshot.asStateFlow()
    val status: StateFlow<RefreshStatus> = _status.asStateFlow()
    val saved: Flow<List<Flight>> get() = store.observeSaved()

    suspend fun loadCache() {
        try {
            store.loadSnapshot()?.let { _snapshot.value = it.sorted() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // An unreadable cache is the same as no cache: the refresh that follows decides what to show.
            println("Flight cache unreadable: $error")
            _status.value = RefreshStatus.Failed(RefreshFailure.STORAGE)
        }
    }

    /** Overlapping calls collapse into the one already running, so Retry can be tapped freely. */
    suspend fun refresh() {
        if (!refreshLock.tryLock()) return
        try {
            _status.value = RefreshStatus.Refreshing
            _status.value = try {
                val flights = api.fetchFlights().distinctBy { it.id }
                val snapshot = FlightSnapshot(flights, clock.now())
                store.replaceSnapshot(snapshot)
                _snapshot.value = snapshot.sorted()
                RefreshStatus.Idle
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The UI gets a friendly category; the real cause stays visible to whoever is debugging.
                println("Flight refresh failed: $error")
                RefreshStatus.Failed(error.toFailure())
            }
        } finally {
            refreshLock.unlock()
        }
    }

    suspend fun save(flight: Flight) = store.saveFlight(flight, clock.now())

    suspend fun remove(id: String) = store.removeFlight(id)

    private fun FlightSnapshot.sorted() = copy(flights = flights.sortedWith(flightOrder))

    private fun Exception.toFailure(): RefreshFailure = when (this) {
        is FlightApiException.Unreachable -> RefreshFailure.UNREACHABLE
        is FlightApiException.Server -> RefreshFailure.SERVER
        is FlightApiException.InvalidResponse -> RefreshFailure.INVALID_RESPONSE
        else -> RefreshFailure.STORAGE
    }
}
