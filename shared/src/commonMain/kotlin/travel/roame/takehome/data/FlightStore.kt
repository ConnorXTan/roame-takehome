package travel.roame.takehome.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import travel.roame.takehome.db.Cached_flight
import travel.roame.takehome.db.FlightDatabase
import travel.roame.takehome.db.FlightsQueries
import travel.roame.takehome.db.Saved_flight
import travel.roame.takehome.model.Flight

/** The last successful response. An empty [flights] is real data: the server said there is nothing. */
data class FlightSnapshot(val flights: List<Flight>, val fetchedAt: Instant)

interface FlightStore {
    /** Null when no fetch has ever succeeded. */
    suspend fun loadSnapshot(): FlightSnapshot?

    /** Replaces the cache in one transaction and refreshes any saved copies of the same flights. */
    suspend fun replaceSnapshot(snapshot: FlightSnapshot)

    fun observeSaved(): Flow<List<Flight>>
    suspend fun saveFlight(flight: Flight, savedAt: Instant)
    suspend fun removeFlight(id: String)
}

class SqlFlightStore(
    private val database: FlightDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : FlightStore {
    private val queries: FlightsQueries get() = database.flightsQueries

    override suspend fun loadSnapshot(): FlightSnapshot? = withContext(ioDispatcher) {
        val fetchedAt = queries.selectFetchedAt().executeAsOneOrNull() ?: return@withContext null
        FlightSnapshot(
            flights = queries.selectCached().executeAsList().map { it.toFlight() },
            fetchedAt = Instant.fromEpochMilliseconds(fetchedAt),
        )
    }

    override suspend fun replaceSnapshot(snapshot: FlightSnapshot): Unit = withContext(ioDispatcher) {
        database.transaction {
            queries.clearCached()
            snapshot.flights.forEach { flight ->
                queries.insertCached(flight.toCachedRow())
                flight.updateSavedCopy()
            }
            queries.setFetchedAt(snapshot.fetchedAt.toEpochMilliseconds())
        }
    }

    override fun observeSaved(): Flow<List<Flight>> =
        queries.selectSaved { id, origin, destination, departureDate, program, points, cabin, originCity,
                destinationCity, originLatitude, originLongitude, destinationLatitude, destinationLongitude, _ ->
            Flight(
                id, origin, destination, LocalDate.parse(departureDate), program, points.toInt(), cabin, originCity,
                destinationCity, originLatitude, originLongitude, destinationLatitude, destinationLongitude,
            )
        }.asFlow().mapToList(ioDispatcher)

    override suspend fun saveFlight(flight: Flight, savedAt: Instant): Unit = withContext(ioDispatcher) {
        // Saving again updates the details in place: no duplicate, and it keeps its original position.
        database.transaction {
            queries.insertSavedIfAbsent(flight.toSavedRow(savedAt.toEpochMilliseconds()))
            flight.updateSavedCopy()
        }
    }

    override suspend fun removeFlight(id: String) {
        withContext(ioDispatcher) { queries.deleteSaved(id) }
    }

    private fun Flight.updateSavedCopy() = queries.updateSaved(
        origin = origin,
        destination = destination,
        departureDate = departureDate.toString(),
        program = program,
        points = points.toLong(),
        cabin = cabin,
        originCity = originCity,
        destinationCity = destinationCity,
        originLatitude = originLatitude,
        originLongitude = originLongitude,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        id = id,
    )
}

private fun Cached_flight.toFlight() = Flight(
    id, origin, destination, LocalDate.parse(departure_date), program, points.toInt(), cabin, origin_city,
    destination_city, origin_latitude, origin_longitude, destination_latitude, destination_longitude,
)

private fun Flight.toCachedRow() = Cached_flight(
    id, origin, destination, departureDate.toString(), program, points.toLong(), cabin, originCity,
    destinationCity, originLatitude, originLongitude, destinationLatitude, destinationLongitude,
)

private fun Flight.toSavedRow(savedAtMs: Long) = Saved_flight(
    id, origin, destination, departureDate.toString(), program, points.toLong(), cabin, originCity,
    destinationCity, originLatitude, originLongitude, destinationLatitude, destinationLongitude, savedAtMs,
)
