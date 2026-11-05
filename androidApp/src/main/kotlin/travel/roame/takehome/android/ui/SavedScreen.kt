package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import travel.roame.takehome.presentation.ExplorerController
import travel.roame.takehome.presentation.ExplorerState

/**
 * Reads only [ExplorerState.saved], never the discovery results, so filters can't hide anything here.
 * Its selection is local for the same reason: a saved flight may not be in the discovery results at all.
 */
@Composable
fun SavedScreen(state: ExplorerState, controller: ExplorerController) {
    val colors = Explorer.colors
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.saved.firstOrNull { it.id == openId }
    BackHandler(enabled = open != null) { openId = null }

    // Explore stays composed underneath to keep its state, and being transparent doesn't stop it taking
    // input. Without this empty pointer handler, a tap here falls through and can save a hidden flight.
    Column(Modifier.fillMaxSize().background(colors.canvas).pointerInput(Unit) {}) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth().background(colors.surface))
        Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Saved", style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Text(
                if (state.saved.size == 1) "1 flight, available offline" else "${state.saved.size} flights, available offline",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        HorizontalDivider(color = colors.hairline)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.saved.isEmpty()) {
                MessageState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "No saved flights yet",
                    body = "Tap the bookmark on any flight to keep it here, even offline.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (open != null) 240.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.saved, key = { it.id }) { flight ->
                        FlightRow(
                            flight = flight,
                            selected = flight.id == openId,
                            saved = true,
                            pending = state.isPending(flight.id),
                            onClick = { openId = if (openId == flight.id) null else flight.id },
                            onToggleSaved = { controller.remove(flight) },
                        )
                    }
                }
            }
            if (open != null) {
                FlightCard(
                    flight = open,
                    saved = true,
                    pending = state.isPending(open.id),
                    onToggleSaved = { controller.remove(open) },
                    onClose = { openId = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}
