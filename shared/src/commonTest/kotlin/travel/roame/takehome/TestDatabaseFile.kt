package travel.roame.takehome

import app.cash.sqldelight.db.SqlDriver

/** A SQLite file on disk that can be closed and reopened, to test what survives a relaunch. */
expect class TestDatabaseFile() {
    fun open(): SqlDriver
    fun delete()
}
