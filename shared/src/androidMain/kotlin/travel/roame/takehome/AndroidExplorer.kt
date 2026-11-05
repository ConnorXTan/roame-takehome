package travel.roame.takehome

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import travel.roame.takehome.data.configureFlightClient
import travel.roame.takehome.db.FlightDatabase
import travel.roame.takehome.presentation.ExplorerController
import travel.roame.takehome.presentation.createController

fun createExplorerController(
    context: Context,
    baseUrl: String,
    failSavedWrites: Boolean = false,
): ExplorerController = createController(
    client = HttpClient(OkHttp) { configureFlightClient(this) },
    createDriver = { AndroidSqliteDriver(FlightDatabase.Schema, context.applicationContext, "flights.db") },
    baseUrl = baseUrl,
    ioDispatcher = Dispatchers.IO,
    failSavedWrites = failSavedWrites,
)
