package travel.roame.takehome.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightsEnvelope

/** Why a refresh failed. These stay distinct so the UI can say something true about each. */
sealed class FlightApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unreachable(cause: Throwable) : FlightApiException("Could not reach the flight service: ${cause.message}", cause)
    class Server(val status: Int) : FlightApiException("Flight service responded with HTTP $status")
    class InvalidResponse(cause: Throwable) : FlightApiException("Flight service sent an unreadable response: ${cause.message}", cause)
}

interface FlightApi {
    /** Returns the flights, possibly none. Anything other than a well-formed 2xx throws [FlightApiException]. */
    suspend fun fetchFlights(): List<Flight>
}

class KtorFlightApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val parseDispatcher: CoroutineDispatcher,
) : FlightApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchFlights(): List<Flight> {
        val body = try {
            val response = client.get("${baseUrl.trimEnd('/')}/flights")
            if (!response.status.isSuccess()) throw FlightApiException.Server(response.status.value)
            response.bodyAsText()
        } catch (error: FlightApiException) {
            throw error
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            throw FlightApiException.Unreachable(error)
        }
        return withContext(parseDispatcher) {
            try {
                json.decodeFromString<FlightsEnvelope>(body).flights
            } catch (error: SerializationException) {
                throw FlightApiException.InvalidResponse(error)
            } catch (error: IllegalArgumentException) {
                // kotlinx-datetime reports a bad departureDate this way.
                throw FlightApiException.InvalidResponse(error)
            }
        }
    }
}

/** Bounded, so a server that accepts the connection and then hangs still ends in a visible failure. */
fun configureFlightClient(config: io.ktor.client.HttpClientConfig<*>) {
    config.install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}
