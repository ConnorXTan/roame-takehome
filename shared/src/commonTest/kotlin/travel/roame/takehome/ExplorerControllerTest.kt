package travel.roame.takehome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.FlightApiException
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.FlightSnapshot
import travel.roame.takehome.presentation.ContentKind
import travel.roame.takehome.presentation.ExplorerController

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerControllerTest {
    private val london = flight("london", destination = "LHR", cabin = "Economy", points = 40_000, date = "2027-02-01")
    private val londonBusiness = flight("london-j", destination = "LHR", cabin = "Business", points = 90_000, date = "2027-03-01")
    private val paris = flight("paris", destination = "CDG", destinationCity = "Paris", cabin = "Economy", points = 50_000, date = "2027-04-01")
    private val flights = listOf(london, londonBusiness, paris)

    private fun TestScope.controller(
        api: FakeApi = FakeApi(flights),
        store: FakeStore = FakeStore(),
    ): ExplorerController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ExplorerController(FlightRepository(api, store, FixedClock), dispatcher, dispatcher)
    }

    private fun date(iso: String) = LocalDate.parse(iso)

    // Loading, errors and offline

    @Test
    fun startsLoadingThenShowsResults() = runTest {
        val controller = controller()
        assertEquals(ContentKind.LOADING, controller.state.value.content)
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(ContentKind.RESULTS, state.content)
        assertEquals(3, state.results.size)
        assertEquals(3, state.totalFlightCount)
        assertNull(state.failureMessage)
        controller.close()
    }

    @Test
    fun noCacheAndNoNetworkIsAnErrorWithARetryThatWorks() = runTest {
        val api = FakeApi(flights).apply { failUnreachable() }
        val controller = controller(api)
        advanceUntilIdle()
        assertEquals(ContentKind.ERROR, controller.state.value.content)
        assertNotNull(controller.state.value.failureMessage)

        api.failure = null
        controller.refresh()
        advanceUntilIdle()
        assertEquals(ContentKind.RESULTS, controller.state.value.content)
        assertNull(controller.state.value.failureMessage)
        controller.close()
    }

    @Test
    fun retryingShowsLoadingRatherThanALingeringError() = runTest {
        val api = FakeApi(flights).apply { failUnreachable() }
        val controller = controller(api)
        advanceUntilIdle()
        api.gate = CompletableDeferred()
        controller.refresh()
        runCurrent()
        assertEquals(ContentKind.LOADING, controller.state.value.content)
        assertTrue(controller.state.value.isRefreshing)
        api.gate?.complete(Unit)
        advanceUntilIdle()
        controller.close()
    }

    @Test
    fun cachedDataShowsAtOnceAndAFailedRefreshMarksItStale() = runTest {
        val store = FakeStore(FlightSnapshot(flights, FixedClock.now()))
        val api = FakeApi().apply { failUnreachable() }
        val controller = controller(api, store)
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(ContentKind.RESULTS, state.content)
        assertEquals(3, state.results.size)
        assertTrue(state.isStale)
        assertNotNull(state.lastUpdatedLabel)
        controller.close()
    }

    @Test
    fun aSuccessfulRefreshClearsTheStaleMark() = runTest {
        val api = FakeApi(flights).apply { failUnreachable() }
        val controller = controller(api, FakeStore(FlightSnapshot(flights, FixedClock.now())))
        advanceUntilIdle()
        assertTrue(controller.state.value.isStale)
        api.failure = null
        controller.refresh()
        advanceUntilIdle()
        assertFalse(controller.state.value.isStale)
        controller.close()
    }

    @Test
    fun aSuccessfulEmptyResponseIsNotAnError() = runTest {
        val controller = controller(FakeApi(emptyList()))
        advanceUntilIdle()
        assertEquals(ContentKind.NO_FLIGHTS, controller.state.value.content)
        assertNull(controller.state.value.failureMessage)
        assertFalse(controller.state.value.isStale)
        controller.close()
    }

    @Test
    fun aCachedEmptyResponseStaysEmptyRatherThanBecomingAnError() = runTest {
        val store = FakeStore(FlightSnapshot(emptyList(), FixedClock.now()))
        val controller = controller(FakeApi().apply { failUnreachable() }, store)
        advanceUntilIdle()
        assertEquals(ContentKind.NO_FLIGHTS, controller.state.value.content)
        assertTrue(controller.state.value.isStale)
        controller.close()
    }

    @Test
    fun searchingCachedResultsWorksOffline() = runTest {
        val store = FakeStore(FlightSnapshot(flights, FixedClock.now()))
        val controller = controller(FakeApi().apply { failUnreachable() }, store)
        advanceUntilIdle()
        controller.setDestinationText("paris")
        advanceUntilIdle()
        assertEquals(listOf("paris"), controller.state.value.results.map { it.id })
        controller.close()
    }

    // Filters

    @Test
    fun filtersThatExcludeEverythingAreNoMatchesNotNoFlights() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setOriginText("nowhere")
        advanceUntilIdle()
        assertEquals(ContentKind.NO_MATCHES, controller.state.value.content)
        controller.close()
    }

    @Test
    fun startsWithTheFullDateRangeAndNoActiveFilters() = runTest {
        val controller = controller()
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(date("2027-02-01"), state.options.firstDate)
        assertEquals(date("2027-04-01"), state.options.lastDate)
        assertEquals(emptyList(), state.activeFilters)
        assertEquals(3, state.results.size)
        controller.close()
    }

    @Test
    fun theQueryUpdatesWithoutWaitingForResults() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setOriginText("jfk")
        assertEquals("jfk", controller.state.value.query.originText)
        assertEquals("From jfk", controller.state.value.activeFilters.single().label)
        controller.close()
    }

    @Test
    fun resultsReflectOnlyTheLatestOfManyRapidChanges() = runTest {
        val controller = controller()
        advanceUntilIdle()
        listOf("p", "pa", "par", "pari", "paris", "lon", "london").forEach(controller::setDestinationText)
        controller.setCabin("Business")
        advanceUntilIdle()
        assertEquals(listOf("london-j"), controller.state.value.results.map { it.id })
        controller.close()
    }

    @Test
    fun movingTheStartPastTheEndDragsTheEndAlong() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setDateEnd(date("2027-02-20"))
        controller.setDateStart(date("2027-03-01"))
        val query = controller.state.value.query
        assertEquals(date("2027-03-01"), query.dateStart)
        assertEquals(date("2027-03-01"), query.dateEnd)
        controller.close()
    }

    @Test
    fun movingTheEndBeforeTheStartDragsTheStartAlong() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setDateStart(date("2027-03-01"))
        controller.setDateEnd(date("2027-02-20"))
        val query = controller.state.value.query
        assertEquals(date("2027-02-20"), query.dateStart)
        assertEquals(date("2027-02-20"), query.dateEnd)
        controller.close()
    }

    @Test
    fun controlsBackAtTheEdgeOfTheDataAreNotActiveFilters() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setDateStart(date("2027-02-01"))
        controller.setDateEnd(date("2027-04-01"))
        controller.setMaxPoints(90_000)
        assertEquals(emptyList(), controller.state.value.activeFilters)
        controller.close()
    }

    @Test
    fun removingAChipClearsJustThatFilter() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setCabin("Economy")
        controller.toggleProgram("Avios")
        controller.toggleProgram("Aeroplan")
        val chip = controller.state.value.activeFilters.first { it.value == "Avios" }
        controller.removeFilter(chip)
        val state = controller.state.value
        assertEquals(setOf("Aeroplan"), state.query.programs)
        assertEquals("Economy", state.query.cabin)
        controller.close()
    }

    @Test
    fun resetClearsSearchAndEveryFilter() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setOriginText("jfk")
        controller.setDestinationText("lon")
        controller.setCabin("Business")
        controller.setMaxPoints(50_000)
        controller.toggleProgram("Avios")
        controller.setDateStart(date("2027-03-01"))
        advanceUntilIdle()
        controller.resetFilters()
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(emptyList(), state.activeFilters)
        assertEquals(3, state.results.size)
        assertEquals("", state.query.originText)
        controller.close()
    }

    @Test
    fun theListAndMapAreBuiltFromTheSameResults() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.setCabin("Economy")
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(state.results.size, state.markers.sumOf { it.flightCount })
        assertEquals(setOf("LHR", "CDG"), state.markers.map { it.code }.toSet())
        controller.close()
    }

    // Selection

    @Test
    fun selectionSurvivesFiltersThatStillIncludeTheFlight() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectFlight("london")
        controller.setCabin("Economy")
        advanceUntilIdle()
        assertEquals("london", controller.state.value.selectedFlight?.id)
        controller.close()
    }

    @Test
    fun selectionClearsWhenFiltersExcludeTheFlight() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectFlight("london")
        controller.setCabin("Business")
        advanceUntilIdle()
        assertNull(controller.state.value.selectedFlight)

        // It must stay cleared when the filter is lifted, not spring back.
        controller.setCabin(null)
        advanceUntilIdle()
        assertNull(controller.state.value.selectedFlight)
        controller.close()
    }

    @Test
    fun selectingAFlightOutsideTheResultsDoesNothing() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectFlight("missing")
        assertNull(controller.state.value.selectedFlight)
        controller.close()
    }

    @Test
    fun selectionPicksUpNewDetailsAfterARefresh() = runTest {
        val api = FakeApi(flights)
        val controller = controller(api)
        advanceUntilIdle()
        controller.selectFlight("london")
        api.flights = listOf(london.copy(points = 12_000))
        controller.refresh()
        advanceUntilIdle()
        assertEquals(12_000, controller.state.value.selectedFlight?.points)
        controller.close()
    }

    @Test
    fun aMarkerOpensItsFlightsCheapestFirst() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectDestination("LHR")
        val state = controller.state.value
        assertEquals("LHR", state.selectedDestination?.code)
        assertEquals(listOf("london", "london-j"), state.destinationFlights.map { it.id })
        controller.close()
    }

    @Test
    fun closingAFlightReturnsToItsDestination() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectDestination("LHR")
        controller.selectFlight("london")
        controller.selectFlight(null)
        val state = controller.state.value
        assertNull(state.selectedFlight)
        assertEquals("LHR", state.selectedDestination?.code)
        controller.close()
    }

    @Test
    fun anOpenDestinationClosesWhenFiltersRemoveItsFlights() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectDestination("CDG")
        controller.setCabin("Business")
        advanceUntilIdle()
        assertNull(controller.state.value.selectedDestination)
        assertEquals(emptyList(), controller.state.value.destinationFlights)
        controller.close()
    }

    @Test
    fun anOpenDestinationTracksItsFilteredFlights() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.selectDestination("LHR")
        controller.setCabin("Business")
        advanceUntilIdle()
        assertEquals(listOf("london-j"), controller.state.value.destinationFlights.map { it.id })
        assertEquals(1, controller.state.value.selectedDestination?.flightCount)
        controller.close()
    }

    // Saved flights

    @Test
    fun savingShowsInBothSavedStateAndIds() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.save(london)
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(listOf("london"), state.saved.map { it.id })
        assertTrue(state.isSaved("london"))
        assertFalse(state.isPending("london"))
        controller.close()
    }

    @Test
    fun savedFlightsIgnoreDiscoveryFilters() = runTest {
        val controller = controller()
        advanceUntilIdle()
        controller.save(london)
        controller.setDestinationText("nowhere")
        advanceUntilIdle()
        assertEquals(ContentKind.NO_MATCHES, controller.state.value.content)
        assertEquals(listOf("london"), controller.state.value.saved.map { it.id })
        controller.close()
    }

    @Test
    fun aFailedSaveDoesNotAppearSavedAndSaysSo() = runTest {
        val store = FakeStore().apply { failSavedWrites = true }
        val controller = controller(store = store)
        advanceUntilIdle()
        controller.save(london)
        advanceUntilIdle()
        val state = controller.state.value
        assertFalse(state.isSaved("london"))
        assertEquals(emptyList(), state.saved)
        assertFalse(state.isPending("london"))
        assertTrue(assertNotNull(state.message).text.contains("Couldn't save"))
        assertTrue(state.message!!.canRetry)
        controller.close()
    }

    @Test
    fun aFailedRemoveStaysSavedAndSaysSo() = runTest {
        val store = FakeStore().apply { saved.value = listOf(london) }
        val controller = controller(store = store)
        advanceUntilIdle()
        store.failSavedWrites = true
        controller.remove(london)
        advanceUntilIdle()
        val state = controller.state.value
        assertTrue(state.isSaved("london"))
        assertTrue(assertNotNull(state.message).text.contains("Couldn't remove"))
        controller.close()
    }

    @Test
    fun aSavedFlightDoesNotFlipBeforeTheWriteCommits() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore().apply { writeGate = gate }
        val controller = controller(store = store)
        advanceUntilIdle()
        controller.save(london)
        runCurrent()
        assertTrue(controller.state.value.isPending("london"))
        assertFalse(controller.state.value.isSaved("london"))
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(controller.state.value.isSaved("london"))
        controller.close()
    }

    @Test
    fun doubleTappingSaveWritesOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore().apply { writeGate = gate }
        val controller = controller(store = store)
        advanceUntilIdle()
        controller.save(london)
        controller.save(london)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, store.saveCalls)
        controller.close()
    }

    @Test
    fun retryingAFailedSaveWorks() = runTest {
        val store = FakeStore().apply { failSavedWrites = true }
        val controller = controller(store = store)
        advanceUntilIdle()
        controller.save(london)
        advanceUntilIdle()
        store.failSavedWrites = false
        controller.retryMessage()
        advanceUntilIdle()
        assertTrue(controller.state.value.isSaved("london"))
        assertNull(controller.state.value.message)
        controller.close()
    }

    @Test
    fun savedFlightsCanBeRemovedOffline() = runTest {
        val store = FakeStore(FlightSnapshot(flights, FixedClock.now())).apply { saved.value = listOf(london) }
        val controller = controller(FakeApi().apply { failUnreachable() }, store)
        advanceUntilIdle()
        controller.remove(london)
        advanceUntilIdle()
        assertEquals(emptyList(), controller.state.value.saved)
        controller.close()
    }

    @Test
    fun savedFlightsSurviveBeingDroppedFromTheApi() = runTest {
        val api = FakeApi(flights)
        val controller = controller(api)
        advanceUntilIdle()
        controller.save(paris)
        api.flights = listOf(london)
        controller.refresh()
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(listOf("london"), state.results.map { it.id })
        assertEquals(listOf("paris"), state.saved.map { it.id })
        controller.close()
    }

    @Test
    fun aRefreshFailureDoesNotTouchSavedFlights() = runTest {
        val api = FakeApi(flights)
        val controller = controller(api)
        advanceUntilIdle()
        controller.save(paris)
        advanceUntilIdle()
        api.failure = FlightApiException.Server(500)
        controller.refresh()
        advanceUntilIdle()
        assertEquals(listOf("paris"), controller.state.value.saved.map { it.id })
        controller.close()
    }

    // Lifecycle

    @Test
    fun watchDeliversUpdatesUntilCancelled() = runTest {
        val controller = controller()
        advanceUntilIdle()
        val seen = mutableListOf<String>()
        val subscription = controller.watch { seen += it.query.originText }
        runCurrent()
        controller.setOriginText("a")
        advanceUntilIdle()
        subscription.cancel()
        controller.setOriginText("b")
        advanceUntilIdle()
        assertEquals("a", seen.last())
        assertFalse("b" in seen)
        controller.close()
    }

    @Test
    fun closingReleasesResources() = runTest {
        var closed = false
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = ExplorerController(FlightRepository(FakeApi(), FakeStore(), FixedClock), dispatcher, dispatcher) { closed = true }
        advanceUntilIdle()
        controller.close()
        assertTrue(closed)
    }
}
