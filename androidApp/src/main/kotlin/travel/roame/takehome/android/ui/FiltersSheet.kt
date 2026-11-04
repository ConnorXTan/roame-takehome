package travel.roame.takehome.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import travel.roame.takehome.domain.formatPoints
import travel.roame.takehome.domain.shortDate
import travel.roame.takehome.presentation.ExplorerController
import travel.roame.takehome.presentation.ExplorerState

private const val POINTS_STEP = 500
private const val MILLIS_PER_DAY = 86_400_000L

// The Material picker speaks UTC-midnight milliseconds. Going through epoch days, rather than any
// local time zone, keeps the selected calendar date exact wherever the device is.
private fun LocalDate.toPickerMillis(): Long = toEpochDays() * MILLIS_PER_DAY
private fun Long.toLocalDate(): LocalDate = LocalDate.fromEpochDays(this / MILLIS_PER_DAY)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(state: ExplorerState, controller: ExplorerController, onDismiss: () -> Unit) {
    val colors = Explorer.colors
    val options = state.options
    val query = state.query
    val first = options.firstDate
    val last = options.lastDate
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, color = colors.ink)

            SectionTitle("Departure dates")
            OutlinedButton(
                onClick = { showDatePicker = true },
                enabled = first != null,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, colors.hairline),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val start = (query.dateStart ?: first)?.let(::shortDate).orEmpty()
                val end = (query.dateEnd ?: last)?.let(::shortDate).orEmpty()
                Text("$start – $end", color = colors.ink, modifier = Modifier.weight(1f))
            }

            SectionTitle("Cabin")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Any", query.cabin == null) { controller.setCabin(null) }
                options.cabins.forEach { cabin ->
                    ChoiceChip(cabin, query.cabin == cabin) { controller.setCabin(cabin) }
                }
            }

            SectionTitle("Maximum points")
            PointsSlider(state, controller)

            SectionTitle("Loyalty programs")
            Text("Show flights from any selected program.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.programs.forEach { program ->
                    ChoiceChip(program, program in query.programs) { controller.toggleProgram(program) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = controller::resetFilters,
                    enabled = state.activeFilters.isNotEmpty(),
                    border = BorderStroke(1.dp, colors.hairline),
                ) { Text("Reset", color = colors.ink) }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.results.size == 1) "Show 1 flight" else "Show ${formatPoints(state.results.size)} flights",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker && first != null && last != null) {
        DateRangeDialog(state, first, last, controller) { showDatePicker = false }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = Explorer.colors.ink)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Explorer.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface,
            labelColor = colors.ink,
            selectedContainerColor = colors.ink,
            selectedLabelColor = colors.surface,
        ),
    )
}

@Composable
private fun PointsSlider(state: ExplorerState, controller: ExplorerController) {
    val colors = Explorer.colors
    val options = state.options
    if (options.maxPoints <= options.minPoints) {
        Text("Not enough flights to compare.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        return
    }
    // Dragging updates results live, and the thumb is local so it tracks the finger exactly.
    val limit = state.query.maxPoints ?: options.maxPoints
    var dragging by remember { mutableStateOf(false) }
    var thumb by remember { mutableFloatStateOf(limit.toFloat()) }
    val shown = if (dragging) thumb else limit.toFloat()

    Text(
        if (state.query.maxPoints == null && !dragging) "No limit" else "Up to ${formatPoints(shown.roundToInt())} pts",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.muted,
    )
    Slider(
        value = shown,
        onValueChange = { value ->
            dragging = true
            val snapped = ((value / POINTS_STEP).roundToInt() * POINTS_STEP).coerceIn(options.minPoints, options.maxPoints)
            thumb = snapped.toFloat()
            controller.setMaxPoints(snapped)
        },
        onValueChangeFinished = { dragging = false },
        valueRange = options.minPoints.toFloat()..options.maxPoints.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.hairline,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    state: ExplorerState,
    first: LocalDate,
    last: LocalDate,
    controller: ExplorerController,
    onDismiss: () -> Unit,
) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = (state.query.dateStart ?: first).toPickerMillis(),
        initialSelectedEndDateMillis = (state.query.dateEnd ?: last).toPickerMillis(),
        initialDisplayedMonthMillis = (state.query.dateStart ?: first).toPickerMillis(),
        yearRange = first.year..last.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis in first.toPickerMillis()..last.toPickerMillis()

            override fun isSelectableYear(year: Int) = year in first.year..last.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction("Apply") {
                val start = picker.selectedStartDateMillis?.toLocalDate() ?: first
                // A single tap selects one day, so an unset end means that same day rather than "open".
                val end = picker.selectedEndDateMillis?.toLocalDate() ?: start
                controller.setDateStart(start)
                controller.setDateEnd(end)
                onDismiss()
            }
        },
        dismissButton = { TextAction("Cancel", onDismiss) },
    ) {
        DateRangePicker(state = picker, modifier = Modifier.weight(1f), showModeToggle = false)
    }
}
