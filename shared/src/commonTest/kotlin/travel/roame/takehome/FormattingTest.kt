package travel.roame.takehome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import travel.roame.takehome.domain.compactPoints
import travel.roame.takehome.domain.formatPoints
import travel.roame.takehome.domain.lastUpdatedLabel
import travel.roame.takehome.domain.longDate
import travel.roame.takehome.domain.mediumDate
import travel.roame.takehome.domain.shortDate

class FormattingTest {
    @Test
    fun pointsGetThousandsSeparators() {
        assertEquals("950", formatPoints(950))
        assertEquals("39,000", formatPoints(39_000))
        assertEquals("133,000", formatPoints(133_000))
        assertEquals("1,000,000", formatPoints(1_000_000))
    }

    @Test
    fun compactPointsFitAMarker() {
        assertEquals("950", compactPoints(950))
        assertEquals("39k", compactPoints(39_000))
        assertEquals("12.5k", compactPoints(12_500))
    }

    @Test
    fun datesFormatWithoutATimeZone() {
        val date = LocalDate.parse("2027-02-01")
        assertEquals("Feb 1", shortDate(date))
        assertEquals("Mon, Feb 1", mediumDate(date))
        assertEquals("Mon, Feb 1, 2027", longDate(date))
    }

    @Test
    fun lastUpdatedUsesTwelveHourTime() {
        assertEquals("Jan 15 at 8:32 AM", lastUpdatedLabel(Instant.parse("2027-01-15T08:32:00Z"), TimeZone.UTC))
        assertEquals("Jan 15 at 12:05 AM", lastUpdatedLabel(Instant.parse("2027-01-15T00:05:00Z"), TimeZone.UTC))
        assertEquals("Jan 15 at 12:05 PM", lastUpdatedLabel(Instant.parse("2027-01-15T12:05:00Z"), TimeZone.UTC))
    }
}
