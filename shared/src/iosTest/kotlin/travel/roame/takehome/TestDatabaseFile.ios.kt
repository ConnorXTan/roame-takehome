package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import travel.roame.takehome.db.FlightDatabase

actual class TestDatabaseFile actual constructor() {
    private val directory = NSTemporaryDirectory()
    private val name = "flights-${Random.nextLong()}.db"

    actual fun open(): SqlDriver = NativeSqliteDriver(
        schema = FlightDatabase.Schema,
        name = name,
        onConfiguration = { config ->
            config.copy(extendedConfig = DatabaseConfiguration.Extended(basePath = directory))
        },
    )

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete() {
        NSFileManager.defaultManager.removeItemAtPath("$directory/$name", null)
    }
}
