package travel.roame.takehome

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import travel.roame.takehome.data.FlightApiException
import travel.roame.takehome.data.KtorFlightApi

class FlightApiTest {
    private val record = """
        {"id":"fx-BOS-BKK-20270201","origin":"BOS","destination":"BKK","departureDate":"2027-02-01",
         "program":"Mileage Plan","points":39000,"cabin":"Economy","originCity":"Boston",
         "destinationCity":"Bangkok","originLatitude":42.36197,"originLongitude":-71.0079,
         "destinationLatitude":13.6811,"destinationLongitude":100.747}
    """.trimIndent()

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK) = KtorFlightApi(
        client = HttpClient(MockEngine { respond(body, status, headersOf("Content-Type", "application/json")) }),
        baseUrl = "http://test/",
        parseDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun parsesTheFlightsEnvelope() = runTest {
        val flights = api("""{"flights":[$record]}""").fetchFlights()
        assertEquals("fx-BOS-BKK-20270201", flights.single().id)
        assertEquals(39_000, flights.single().points)
        assertEquals("2027-02-01", flights.single().departureDate.toString())
    }

    @Test
    fun requestsTheFlightsPath() = runTest {
        var requested = ""
        val api = KtorFlightApi(
            HttpClient(MockEngine { request ->
                requested = request.url.toString()
                respond("""{"flights":[]}""", HttpStatusCode.OK)
            }),
            "http://test/",
            Dispatchers.Unconfined,
        )
        api.fetchFlights()
        assertEquals("http://test/flights", requested)
    }

    @Test
    fun anEmptyListIsASuccessNotAFailure() = runTest {
        assertTrue(api("""{"flights":[]}""").fetchFlights().isEmpty())
    }

    @Test
    fun unknownFieldsAreIgnored() = runTest {
        val body = """{"flights":[${record.dropLast(1)},"airline":"new"}],"page":1}"""
        assertEquals(1, api(body).fetchFlights().size)
    }

    @Test
    fun aServerErrorIsReportedWithItsStatus() = runTest {
        val error = assertFailsWith<FlightApiException.Server> {
            api("oops", HttpStatusCode.InternalServerError).fetchFlights()
        }
        assertEquals(500, error.status)
    }

    @Test
    fun malformedJsonIsAnInvalidResponse() = runTest {
        assertFailsWith<FlightApiException.InvalidResponse> { api("""{"flights":[{"id":""").fetchFlights() }
    }

    @Test
    fun aMissingEnvelopeIsAnInvalidResponseNotAnEmptyList() = runTest {
        assertFailsWith<FlightApiException.InvalidResponse> { api("{}").fetchFlights() }
    }

    @Test
    fun aNullFlightsFieldIsAnInvalidResponse() = runTest {
        assertFailsWith<FlightApiException.InvalidResponse> { api("""{"flights":null}""").fetchFlights() }
    }

    @Test
    fun aBadDepartureDateIsAnInvalidResponse() = runTest {
        val body = """{"flights":[${record.replace("2027-02-01", "soon")}]}"""
        assertFailsWith<FlightApiException.InvalidResponse> { api(body).fetchFlights() }
    }

    @Test
    fun aConnectionFailureIsUnreachable() = runTest {
        val api = KtorFlightApi(
            HttpClient(MockEngine { throw IOException("connection refused") }),
            "http://test",
            Dispatchers.Unconfined,
        )
        assertFailsWith<FlightApiException.Unreachable> { api.fetchFlights() }
    }
}
