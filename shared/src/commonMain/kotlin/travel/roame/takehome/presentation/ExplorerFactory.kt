package travel.roame.takehome.presentation

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Instant
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.FlightStore
import travel.roame.takehome.data.KtorFlightApi
import travel.roame.takehome.data.SqlFlightStore
import travel.roame.takehome.db.FlightDatabase
import travel.roame.takehome.model.Flight

/** Wires the real graph. Platforms supply only the things that differ: the HTTP engine and the SQLite driver. */
internal fun createController(
    client: HttpClient,
    driver: SqlDriver,
    baseUrl: String,
    ioDispatcher: CoroutineDispatcher,
    failSavedWrites: Boolean,
): ExplorerController {
    val store = SqlFlightStore(FlightDatabase(driver), ioDispatcher)
    val repository = FlightRepository(
        api = KtorFlightApi(client, baseUrl, parseDispatcher = Dispatchers.Default),
        store = if (failSavedWrites) FailingSavedWrites(store) else store,
    )
    return ExplorerController(
        repository = repository,
        main = Dispatchers.Main.immediate,
        compute = Dispatchers.Default,
        onClose = {
            client.close()
            driver.close()
        },
    )
}

/** Debug builds only: lets the failed save and remove path be seen on a real device. */
private class FailingSavedWrites(store: FlightStore) : FlightStore by store {
    override suspend fun saveFlight(flight: Flight, savedAt: Instant) = throw IllegalStateException("Simulated write failure")
    override suspend fun removeFlight(id: String) = throw IllegalStateException("Simulated write failure")
}
