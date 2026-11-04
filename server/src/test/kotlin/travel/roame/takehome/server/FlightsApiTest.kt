package travel.roame.takehome.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlightsApiTest {
    private val fixture = File("fixtures/flights.json").readBytes()

    @Test
    fun `returns 200 with the fixture envelope unchanged`() = testApplication {
        application { flightsApi(fixture, Mode.NORMAL) }
        val response = client.get("/flights")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertContentEquals(fixture, response.bodyAsBytes())
    }

    @Test
    fun `empty mode is a successful response with no flights`() = testApplication {
        application { flightsApi(fixture, Mode.EMPTY) }
        val response = client.get("/flights")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"flights":[]}""", response.bodyAsText())
    }

    @Test
    fun `error mode fails with a 500`() = testApplication {
        application { flightsApi(fixture, Mode.ERROR) }
        assertEquals(HttpStatusCode.InternalServerError, client.get("/flights").status)
    }
}
