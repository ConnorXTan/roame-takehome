package travel.roame.takehome.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** One dated flight, exactly as GET /flights returns it. `id` is opaque: the fixture has more than one format. */
@Serializable
data class Flight(
    val id: String,
    val origin: String,
    val destination: String,
    val departureDate: LocalDate,
    val program: String,
    val points: Int,
    val cabin: String,
    val originCity: String,
    val destinationCity: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
)

@Serializable
data class FlightsEnvelope(val flights: List<Flight>)
