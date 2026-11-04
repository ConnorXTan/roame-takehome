package travel.roame.takehome

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.FlightApi
import travel.roame.takehome.data.FlightApiException
import travel.roame.takehome.data.FlightSnapshot
import travel.roame.takehome.data.FlightStore
import travel.roame.takehome.model.Flight

fun flight(
    id: String = "f1",
    origin: String = "JFK",
    destination: String = "LHR",
    date: String = "2027-02-10",
    program: String = "Avios",
    points: Int = 40_000,
    cabin: String = "Economy",
    originCity: String = "New York",
    destinationCity: String = "London",
    destinationLatitude: Double = 51.47,
    destinationLongitude: Double = -0.46,
) = Flight(
    id, origin, destination, LocalDate.parse(date), program, points, cabin, originCity, destinationCity,
    40.64, -73.78, destinationLatitude, destinationLongitude,
)

object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
}

class FakeApi(var flights: List<Flight> = emptyList()) : FlightApi {
    var failure: Exception? = null
    var calls = 0

    /** When set, the request hangs until this completes, to hold a refresh in flight. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun fetchFlights(): List<Flight> {
        calls++
        gate?.await()
        failure?.let { throw it }
        return flights
    }

    fun failUnreachable() {
        failure = FlightApiException.Unreachable(RuntimeException("connection refused"))
    }
}

/** In-memory store that can fail on demand. The real SQL store is covered by SqlFlightStoreTest. */
class FakeStore(var snapshot: FlightSnapshot? = null) : FlightStore {
    val saved = MutableStateFlow<List<Flight>>(emptyList())
    var failReads = false
    var failSnapshotWrites = false
    var failSavedWrites = false
    var saveCalls = 0

    /** When set, saved-flight writes hang until this completes. */
    var writeGate: CompletableDeferred<Unit>? = null

    override suspend fun loadSnapshot(): FlightSnapshot? {
        if (failReads) throw IllegalStateException("disk error")
        return snapshot
    }

    override suspend fun replaceSnapshot(snapshot: FlightSnapshot) {
        if (failSnapshotWrites) throw IllegalStateException("disk full")
        this.snapshot = snapshot
    }

    override fun observeSaved(): Flow<List<Flight>> = saved

    override suspend fun saveFlight(flight: Flight, savedAt: Instant) {
        saveCalls++
        writeGate?.await()
        if (failSavedWrites) throw IllegalStateException("disk full")
        saved.value = saved.value.filterNot { it.id == flight.id } + flight
    }

    override suspend fun removeFlight(id: String) {
        writeGate?.await()
        if (failSavedWrites) throw IllegalStateException("disk full")
        saved.value = saved.value.filterNot { it.id == id }
    }
}
