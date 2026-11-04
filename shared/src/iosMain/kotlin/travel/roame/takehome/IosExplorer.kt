package travel.roame.takehome

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import travel.roame.takehome.data.configureFlightClient
import travel.roame.takehome.db.FlightDatabase
import travel.roame.takehome.presentation.ExplorerController
import travel.roame.takehome.presentation.createController

fun createExplorerController(
    baseUrl: String,
    failSavedWrites: Boolean,
    databaseName: String,
): ExplorerController = createController(
    client = HttpClient(Darwin) { configureFlightClient(this) },
    driver = NativeSqliteDriver(FlightDatabase.Schema, databaseName),
    baseUrl = baseUrl,
    ioDispatcher = Dispatchers.IO,
    failSavedWrites = failSavedWrites,
)
