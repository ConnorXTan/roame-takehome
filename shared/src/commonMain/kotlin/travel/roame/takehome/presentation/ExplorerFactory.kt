package travel.roame.takehome.presentation

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.KtorFlightApi
import travel.roame.takehome.data.SqlFlightStore
import travel.roame.takehome.db.FlightDatabase

/** Wires the real graph. Platforms supply only the things that differ: the HTTP engine and the SQLite driver. */
internal fun createController(
    client: HttpClient,
    driver: SqlDriver,
    baseUrl: String,
    ioDispatcher: CoroutineDispatcher,
): ExplorerController {
    val repository = FlightRepository(
        api = KtorFlightApi(client, baseUrl, parseDispatcher = Dispatchers.Default),
        store = SqlFlightStore(FlightDatabase(driver), ioDispatcher),
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
