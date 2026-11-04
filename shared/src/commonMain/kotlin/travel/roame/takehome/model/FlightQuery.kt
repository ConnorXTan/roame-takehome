package travel.roame.takehome.model

import kotlinx.datetime.LocalDate

/**
 * Everything the user can narrow results by. A null or empty field means "no restriction",
 * so the default instance matches every flight.
 */
data class FlightQuery(
    val originText: String = "",
    val destinationText: String = "",
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
    val cabin: String? = null,
    val maxPoints: Int? = null,
    val programs: Set<String> = emptySet(),
) {
    val dateStartIso: String? get() = dateStart?.toString()
    val dateEndIso: String? get() = dateEnd?.toString()
    val maxPointsOrZero: Int get() = maxPoints ?: 0
}

/** What the filter UI can offer, derived from the whole cached snapshot rather than the filtered results. */
data class FilterOptions(
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    val cabins: List<String> = emptyList(),
    val programs: List<String> = emptyList(),
    val minPoints: Int = 0,
    val maxPoints: Int = 0,
) {
    val firstDateIso: String? get() = firstDate?.toString()
    val lastDateIso: String? get() = lastDate?.toString()
}

enum class FilterKind { ORIGIN, DESTINATION, DATES, CABIN, MAX_POINTS, PROGRAM }

/** One removable chip. Built in shared code so both apps describe a filter in the same words. */
data class ActiveFilter(
    val kind: FilterKind,
    val label: String,
    /** The specific program for [FilterKind.PROGRAM]; empty otherwise. */
    val value: String = "",
) {
    val id: String get() = "$kind:$value"
}

/** Every airport marker on the map: one per destination code, never per city (Tokyo has two). */
data class DestinationMarker(
    val code: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val flightCount: Int,
    val lowestPoints: Int,
)
