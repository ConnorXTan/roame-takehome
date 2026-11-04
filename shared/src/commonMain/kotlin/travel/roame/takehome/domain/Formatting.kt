package travel.roame.takehome.domain

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import travel.roame.takehome.model.Flight

// Flight dates are calendar dates with no time zone. Formatting them here, without ever going through
// an instant, is what stops a device west of UTC from showing the day before.

private fun Month.short(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
private fun DayOfWeek.short(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** "Feb 1" */
fun shortDate(date: LocalDate): String = "${date.month.short()} ${date.day}"

/** "Mon, Feb 1, 2027" */
fun longDate(date: LocalDate): String =
    "${date.dayOfWeek.short()}, ${shortDate(date)}, ${date.year}"

/** "39,000" */
fun formatPoints(points: Int): String =
    points.toString().reversed().chunked(3).joinToString(",").reversed()

/** "39k", "12.5k", "950": short enough for a map marker. */
fun compactPoints(points: Int): String = when {
    points < 1_000 -> points.toString()
    points % 1_000 == 0 -> "${points / 1_000}k"
    else -> "${points / 1_000}.${(points % 1_000) / 100}k"
}

/** "Nov 4 at 3:32 PM" in the device's time zone. */
fun lastUpdatedLabel(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = instant.toLocalDateTime(zone)
    val hour = (local.hour % 12).let { if (it == 0) 12 else it }
    val minute = local.minute.toString().padStart(2, '0')
    val meridiem = if (local.hour < 12) "AM" else "PM"
    return "${shortDate(local.date)} at $hour:$minute $meridiem"
}

/** Swift-friendly accessors, so the iOS app never has to touch kotlinx-datetime types. */
val Flight.departureLabel: String get() = longDate(departureDate)
val Flight.departureShortLabel: String get() = shortDate(departureDate)
val Flight.pointsLabel: String get() = formatPoints(points)
