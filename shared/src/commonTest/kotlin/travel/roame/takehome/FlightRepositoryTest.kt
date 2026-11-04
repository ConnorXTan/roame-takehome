package travel.roame.takehome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.FlightApiException
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.FlightSnapshot
import travel.roame.takehome.data.RefreshFailure
import travel.roame.takehome.data.RefreshStatus

class FlightRepositoryTest {
    private val cached = FlightSnapshot(listOf(flight("old")), FixedClock.now())

    private fun repository(api: FakeApi, store: FakeStore) = FlightRepository(api, store, FixedClock)

    @Test
    fun aSuccessfulRefreshIsPersistedAndPublished() = runTest {
        val store = FakeStore()
        val repository = repository(FakeApi(listOf(flight("new"))), store)
        repository.refresh()
        assertEquals(listOf("new"), repository.snapshot.value?.flights?.map { it.id })
        assertEquals(listOf("new"), store.snapshot?.flights?.map { it.id })
        assertEquals(RefreshStatus.Idle, repository.status.value)
    }

    @Test
    fun aFailedRefreshPreservesExistingData() = runTest {
        val store = FakeStore(cached)
        val api = FakeApi().apply { failUnreachable() }
        val repository = repository(api, store)
        repository.loadCache()
        repository.refresh()
        assertEquals(listOf("old"), repository.snapshot.value?.flights?.map { it.id })
        assertEquals(listOf("old"), store.snapshot?.flights?.map { it.id })
        assertEquals(RefreshStatus.Failed(RefreshFailure.UNREACHABLE), repository.status.value)
    }

    @Test
    fun aBadResponseCannotOverwriteTheCache() = runTest {
        val store = FakeStore(cached)
        val api = FakeApi().apply { failure = FlightApiException.InvalidResponse(RuntimeException()) }
        val repository = repository(api, store)
        repository.loadCache()
        repository.refresh()
        assertEquals(listOf("old"), store.snapshot?.flights?.map { it.id })
        assertEquals(RefreshStatus.Failed(RefreshFailure.INVALID_RESPONSE), repository.status.value)
    }

    @Test
    fun aServerErrorIsReportedAsSuch() = runTest {
        val api = FakeApi().apply { failure = FlightApiException.Server(503) }
        val repository = repository(api, FakeStore())
        repository.refresh()
        assertEquals(RefreshStatus.Failed(RefreshFailure.SERVER), repository.status.value)
    }

    @Test
    fun aSuccessfulEmptyResponseReplacesTheCacheAndIsNotAFailure() = runTest {
        val store = FakeStore(cached)
        val repository = repository(FakeApi(emptyList()), store)
        repository.loadCache()
        repository.refresh()
        val snapshot = assertNotNull(repository.snapshot.value)
        assertEquals(emptyList(), snapshot.flights)
        assertEquals(RefreshStatus.Idle, repository.status.value)
    }

    @Test
    fun aFailureWithNoCacheLeavesNoSnapshot() = runTest {
        val repository = repository(FakeApi().apply { failUnreachable() }, FakeStore())
        repository.loadCache()
        repository.refresh()
        assertNull(repository.snapshot.value)
        assertEquals(RefreshStatus.Failed(RefreshFailure.UNREACHABLE), repository.status.value)
    }

    @Test
    fun whenTheCacheCannotBeWrittenTheScreenKeepsWhatIsOnDisk() = runTest {
        val store = FakeStore(cached).apply { failSnapshotWrites = true }
        val repository = repository(FakeApi(listOf(flight("new"))), store)
        repository.loadCache()
        repository.refresh()
        assertEquals(listOf("old"), repository.snapshot.value?.flights?.map { it.id })
        assertEquals(RefreshStatus.Failed(RefreshFailure.STORAGE), repository.status.value)
    }

    @Test
    fun anUnreadableCacheDoesNotCrashAndARefreshRecovers() = runTest {
        val store = FakeStore(cached).apply { failReads = true }
        val repository = repository(FakeApi(listOf(flight("new"))), store)
        repository.loadCache()
        assertEquals(RefreshStatus.Failed(RefreshFailure.STORAGE), repository.status.value)
        repository.refresh()
        assertEquals(listOf("new"), repository.snapshot.value?.flights?.map { it.id })
    }

    @Test
    fun overlappingRefreshesCollapseIntoOneRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeApi(listOf(flight("new"))).apply { this.gate = gate }
        val repository = repository(api, FakeStore())
        launch { repository.refresh() }
        launch { repository.refresh() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, api.calls)
    }

    @Test
    fun duplicateIdsInAResponseCollapse() = runTest {
        val repository = repository(FakeApi(listOf(flight("same"), flight("same", points = 1))), FakeStore())
        repository.refresh()
        assertEquals(1, repository.snapshot.value?.flights?.size)
    }

    @Test
    fun resultsAreSortedHoweverTheServerOrdersThem() = runTest {
        val api = FakeApi(listOf(flight("late", date = "2027-03-01"), flight("early", date = "2027-02-01")))
        val repository = repository(api, FakeStore())
        repository.refresh()
        assertEquals(listOf("early", "late"), repository.snapshot.value?.flights?.map { it.id })
    }
}
