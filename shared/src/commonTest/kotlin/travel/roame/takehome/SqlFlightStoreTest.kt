package travel.roame.takehome

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.FlightSnapshot
import travel.roame.takehome.data.SqlFlightStore
import travel.roame.takehome.db.FlightDatabase

/** Runs the real SQL on each platform's real SQLite driver, against a database file on disk. */
class SqlFlightStoreTest {
    private val testDb = TestDatabaseFile()
    private var driver = testDb.open()
    private var store = storeOn(driver)

    private fun storeOn(driver: app.cash.sqldelight.db.SqlDriver) =
        SqlFlightStore(lazyOf(FlightDatabase(driver)), Dispatchers.Unconfined)

    /** Closes the connection and opens the file afresh, as relaunching the app would. */
    private fun reopen() {
        driver.close()
        driver = testDb.open()
        store = storeOn(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        testDb.delete()
    }

    private val t1 = Instant.fromEpochMilliseconds(1_000)
    private val t2 = Instant.fromEpochMilliseconds(2_000)

    @Test
    fun noSnapshotBeforeTheFirstFetch() = runTest {
        assertNull(store.loadSnapshot())
    }

    @Test
    fun aSnapshotRoundTripsEveryField() = runTest {
        val flight = flight("f", "BOS", "BKK", "2027-02-01", "Mileage Plan", 39_000, "Premium Economy", "Boston", "Bangkok", 13.6811, 100.747)
        store.replaceSnapshot(FlightSnapshot(listOf(flight), t1))
        val snapshot = assertNotNull(store.loadSnapshot())
        assertEquals(listOf(flight), snapshot.flights)
        assertEquals(t1, snapshot.fetchedAt)
    }

    @Test
    fun cachedResultsSurviveReopening() = runTest {
        store.replaceSnapshot(FlightSnapshot(listOf(flight("a"), flight("b")), t1))
        reopen()
        assertEquals(setOf("a", "b"), assertNotNull(store.loadSnapshot()).flights.map { it.id }.toSet())
    }

    @Test
    fun savedFlightsSurviveReopening() = runTest {
        store.saveFlight(flight("a"), t1)
        reopen()
        assertEquals(listOf("a"), store.observeSaved().first().map { it.id })
    }

    @Test
    fun aNewSnapshotReplacesTheOldOne() = runTest {
        store.replaceSnapshot(FlightSnapshot(listOf(flight("old")), t1))
        store.replaceSnapshot(FlightSnapshot(listOf(flight("new")), t2))
        val snapshot = assertNotNull(store.loadSnapshot())
        assertEquals(listOf("new"), snapshot.flights.map { it.id })
        assertEquals(t2, snapshot.fetchedAt)
    }

    @Test
    fun anEmptySnapshotIsDistinctFromNoSnapshot() = runTest {
        store.replaceSnapshot(FlightSnapshot(emptyList(), t1))
        reopen()
        val snapshot = assertNotNull(store.loadSnapshot())
        assertEquals(emptyList(), snapshot.flights)
    }

    @Test
    fun savingTheSameFlightAgainUpdatesWithoutDuplicating() = runTest {
        store.saveFlight(flight("a", points = 50_000), t1)
        store.saveFlight(flight("a", points = 45_000, cabin = "Business"), t2)
        val saved = store.observeSaved().first()
        assertEquals(1, saved.size)
        assertEquals(45_000, saved.single().points)
        assertEquals("Business", saved.single().cabin)
    }

    @Test
    fun savingAgainKeepsAFlightsPlaceInTheList() = runTest {
        store.saveFlight(flight("first"), t1)
        store.saveFlight(flight("second"), t2)
        store.saveFlight(flight("first", points = 1_000), Instant.fromEpochMilliseconds(3_000))
        assertEquals(listOf("second", "first"), store.observeSaved().first().map { it.id })
    }

    @Test
    fun savedFlightsListNewestFirst() = runTest {
        store.saveFlight(flight("older"), t1)
        store.saveFlight(flight("newer"), t2)
        assertEquals(listOf("newer", "older"), store.observeSaved().first().map { it.id })
    }

    @Test
    fun savedFlightsSurviveBeingDroppedFromALaterResponse() = runTest {
        store.replaceSnapshot(FlightSnapshot(listOf(flight("a"), flight("b")), t1))
        store.saveFlight(flight("a"), t1)
        store.replaceSnapshot(FlightSnapshot(listOf(flight("b")), t2))
        assertEquals(listOf("a"), store.observeSaved().first().map { it.id })
    }

    @Test
    fun aRefreshUpdatesTheSavedCopyOfAFlightStillInTheResponse() = runTest {
        store.saveFlight(flight("a", points = 50_000), t1)
        store.replaceSnapshot(FlightSnapshot(listOf(flight("a", points = 42_000)), t2))
        assertEquals(42_000, store.observeSaved().first().single().points)
    }

    @Test
    fun aRefreshNeverCreatesSavedFlights() = runTest {
        store.replaceSnapshot(FlightSnapshot(listOf(flight("a")), t1))
        assertEquals(emptyList(), store.observeSaved().first())
    }

    @Test
    fun removingDeletesOnlyThatFlight() = runTest {
        store.saveFlight(flight("a"), t1)
        store.saveFlight(flight("b"), t2)
        store.removeFlight("a")
        assertEquals(listOf("b"), store.observeSaved().first().map { it.id })
    }

    @Test
    fun removingAFlightThatIsNotSavedIsHarmless() = runTest {
        store.removeFlight("missing")
        assertEquals(emptyList(), store.observeSaved().first())
    }

    /** Dropping a table makes real SQLite fail partway through a write, with no fake involved. */
    private fun breakSavedTable() {
        driver.execute(null, "DROP TABLE saved_flight", 0)
    }

    @Test
    fun aFailureMidRefreshRollsBackToTheOldSnapshot() = runTest {
        store.replaceSnapshot(FlightSnapshot(listOf(flight("old")), t1))
        breakSavedTable()
        // The cache is cleared before the saved-copy update fails, so only the transaction can save it.
        assertFailsWith<Exception> { store.replaceSnapshot(FlightSnapshot(listOf(flight("new")), t2)) }
        val snapshot = assertNotNull(store.loadSnapshot())
        assertEquals(listOf("old"), snapshot.flights.map { it.id })
        assertEquals(t1, snapshot.fetchedAt)
    }

    @Test
    fun aSaveThatFailsThrowsRatherThanPretending() = runTest {
        breakSavedTable()
        assertFailsWith<Exception> { store.saveFlight(flight("a"), t1) }
    }

    @Test
    fun aRemoveThatFailsThrowsRatherThanPretending() = runTest {
        breakSavedTable()
        assertFailsWith<Exception> { store.removeFlight("a") }
    }
}
