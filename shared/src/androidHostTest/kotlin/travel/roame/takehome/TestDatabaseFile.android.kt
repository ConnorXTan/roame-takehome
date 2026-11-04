package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import travel.roame.takehome.db.FlightDatabase

actual class TestDatabaseFile actual constructor() {
    private val file = File.createTempFile("flights", ".db").also { it.delete() }

    actual fun open(): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties(), FlightDatabase.Schema)

    actual fun delete() {
        file.delete()
    }
}
