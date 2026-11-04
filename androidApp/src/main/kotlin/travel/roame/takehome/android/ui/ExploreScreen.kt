package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import travel.roame.takehome.domain.formatPoints
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.Flight
import travel.roame.takehome.presentation.ContentKind
import travel.roame.takehome.presentation.ExplorerController
import travel.roame.takehome.presentation.ExplorerState

private enum class DiscoveryMode { LIST, MAP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(state: ExplorerState, controller: ExplorerController, visible: Boolean) {
    var mode by rememberSaveable { mutableStateOf(DiscoveryMode.LIST) }
    var mapRequested by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current

    // New search or filters start from the top; left alone, the list would anchor to whichever keyed
    // row was visible and land mid-way through the new results. A plain refresh doesn't move it. This
    // lives here, not in the list, because the list leaves composition when nothing matches.
    var shownQuery by remember { mutableStateOf(state.query) }
    LaunchedEffect(state.resultsRevision) {
        if (state.query != shownQuery) {
            shownQuery = state.query
            listState.scrollToItem(0)
        }
    }

    fun switchTo(next: DiscoveryMode) {
        focus.clearFocus()
        if (next == DiscoveryMode.MAP) mapRequested = true
        mode = next
    }

    BackHandler(enabled = visible && (state.selectedFlight != null || state.selectedDestination != null)) {
        if (state.selectedFlight != null) controller.selectFlight(null) else controller.clearSelection()
    }

    Column(Modifier.fillMaxSize().alpha(if (visible) 1f else 0f)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).fillMaxWidth().background(Explorer.colors.surface))
        SearchHeader(state, controller, onOpenFilters = { showFilters = true }, mode = mode, onMode = ::switchTo)
        StatusBanner(state, onRetry = controller::refresh)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val showsMapBackdrop = mapRequested && state.content.hasData
            if (showsMapBackdrop) {
                // Kept alive beneath the list once opened, so returning to it keeps the camera and costs nothing.
                FlightMap(
                    state = state,
                    onMarkerTap = { code ->
                        focus.clearFocus()
                        controller.selectDestination(code)
                    },
                    onMapTap = {
                        focus.clearFocus()
                        controller.clearSelection()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            when {
                state.content == ContentKind.LOADING -> LoadingRows(Modifier.background(Explorer.colors.canvas).padding(top = 12.dp))
                state.content == ContentKind.ERROR -> LoadFailure(state, controller)
                state.content == ContentKind.NO_FLIGHTS -> PullableMessage(state, controller) {
                    MessageState(
                        icon = Icons.Filled.Inbox,
                        title = "No flights available",
                        body = "The flight service is working but has nothing to offer right now. Pull down to check again.",
                    )
                }
                state.content == ContentKind.NO_MATCHES && mode == DiscoveryMode.LIST -> MessageState(
                    icon = Icons.Filled.SearchOff,
                    title = "No flights match",
                    body = "Try a different airport, or loosen your filters.",
                    actionLabel = "Reset filters",
                    onAction = controller::resetFilters,
                    modifier = Modifier.background(Explorer.colors.canvas),
                )
                mode == DiscoveryMode.LIST -> ResultsList(state, controller, listState)
                else -> MapOverlays(state, controller, onResetFilters = controller::resetFilters)
            }

            if (mode == DiscoveryMode.LIST && state.content == ContentKind.RESULTS) {
                SelectedFlightOverlay(state, controller, Modifier.align(Alignment.BottomCenter)) {
                    TextAction("Show on map") { switchTo(DiscoveryMode.MAP) }
                }
            }
        }
    }

    if (showFilters) {
        FiltersSheet(state, controller, onDismiss = { showFilters = false })
    }
}

@Composable
private fun LoadFailure(state: ExplorerState, controller: ExplorerController) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasLocalNetworkAccess(context)) }
    // Coming back from Settings is the only way this changes, so re-check on resume and fetch if it did.
    LifecycleResumeEffect(Unit) {
        val nowHasAccess = hasLocalNetworkAccess(context)
        if (nowHasAccess && !hasAccess) controller.refresh()
        hasAccess = nowHasAccess
        onPauseOrDispose {}
    }
    if (!hasAccess) {
        // Denied, this looks exactly like a dead server, so say what is really wrong and how to fix it.
        MessageState(
            icon = Icons.Filled.WifiOff,
            title = "Local network access is off",
            body = "Flight Explorer talks to its API over your local network. Allow it in Settings and flights will load when you come back.",
            actionLabel = "Open Settings",
            onAction = { openAppSettings(context) },
            modifier = Modifier.background(Explorer.colors.canvas),
        )
    } else {
        MessageState(
            icon = Icons.Filled.CloudOff,
            title = "Couldn't load flights",
            body = state.failureMessage.orEmpty(),
            actionLabel = "Try again",
            onAction = controller::refresh,
            modifier = Modifier.background(Explorer.colors.canvas),
        )
    }
}

private val ContentKind.hasData: Boolean
    get() = this == ContentKind.RESULTS || this == ContentKind.NO_MATCHES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullableMessage(state: ExplorerState, controller: ExplorerController, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = controller::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) { item { Box(Modifier.fillParentMaxSize()) { content() } } }
    }
}

@Composable
private fun SearchHeader(
    state: ExplorerState,
    controller: ExplorerController,
    onOpenFilters: () -> Unit,
    mode: DiscoveryMode,
    onMode: (DiscoveryMode) -> Unit,
) {
    val colors = Explorer.colors
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(top = 10.dp, bottom = 10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = colors.canvas,
        ) {
            Column {
                PlaceField(
                    icon = Icons.Filled.FlightTakeoff,
                    label = "From",
                    placeholder = "City or airport",
                    value = state.query.originText,
                    latestValue = { controller.state.value.query.originText },
                    onValueChange = controller::setOriginText,
                    imeAction = ImeAction.Next,
                )
                HorizontalDivider(Modifier.padding(start = 44.dp), color = colors.hairline)
                PlaceField(
                    icon = Icons.Filled.FlightLand,
                    label = "To",
                    placeholder = "City or airport",
                    value = state.query.destinationText,
                    latestValue = { controller.state.value.query.destinationText },
                    onValueChange = controller::setDestinationText,
                    imeAction = ImeAction.Done,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(resultSummary(state), style = MaterialTheme.typography.titleSmall, color = colors.ink, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow(Modifier.width(124.dp).height(36.dp)) {
                val buttonColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.ink,
                    activeContentColor = colors.surface,
                    activeBorderColor = colors.ink,
                    inactiveContainerColor = colors.surface,
                    inactiveContentColor = colors.muted,
                    inactiveBorderColor = colors.hairline,
                )
                SegmentedButton(
                    selected = mode == DiscoveryMode.LIST,
                    onClick = { onMode(DiscoveryMode.LIST) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = buttonColors,
                    icon = {},
                    label = { Icon(Icons.AutoMirrored.Filled.List, "List view", Modifier.size(18.dp)) },
                )
                SegmentedButton(
                    selected = mode == DiscoveryMode.MAP,
                    onClick = { onMode(DiscoveryMode.MAP) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = buttonColors,
                    icon = {},
                    label = { Icon(Icons.Filled.Map, "Map view", Modifier.size(18.dp)) },
                )
            }
            IconButton(onClick = onOpenFilters) {
                val filterCount = state.activeFilters.count { it.kind != FilterKind.ORIGIN && it.kind != FilterKind.DESTINATION }
                BadgedBox(
                    badge = {
                        if (filterCount > 0) {
                            Badge(containerColor = colors.accent, contentColor = colors.onAccent) { Text(filterCount.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Filled.Tune, "Filters", tint = colors.ink)
                }
            }
        }
        if (state.activeFilters.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "reset") { TextAction("Reset", controller::resetFilters) }
                items(state.activeFilters, key = { it.id }) { filter ->
                    InputChip(
                        selected = false,
                        onClick = { controller.removeFilter(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Remove ${filter.label}", Modifier.size(14.dp)) },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = colors.accentSoft,
                            labelColor = colors.ink,
                            trailingIconColor = colors.muted,
                        ),
                        border = null,
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = colors.hairline)
}

private fun resultSummary(state: ExplorerState): String = when (state.content) {
    ContentKind.LOADING -> "Loading flights…"
    ContentKind.ERROR, ContentKind.NO_FLIGHTS -> "No flights"
    else -> {
        val count = state.results.size
        if (count == 1) "1 flight" else "${formatPoints(count)} flights"
    }
}

@Composable
private fun PlaceField(
    icon: ImageVector,
    label: String,
    placeholder: String,
    value: String,
    latestValue: () -> String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
) {
    val colors = Explorer.colors
    val focus = LocalFocusManager.current
    // Text lives locally so the caret never waits on a round trip through shared state. It follows
    // shared state only when that disagrees, which is what lets Reset and the chips clear the field.
    // That check reads the controller, not `value`: `value` trails by a frame, so comparing against
    // it would overwrite the field with an older string and lose keystrokes while typing quickly.
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        val latest = latestValue()
        if (latest != text) text = latest
    }

    Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("$label · $placeholder", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                    inner()
                }
            },
        )
        if (text.isNotEmpty()) {
            IconButton(
                onClick = {
                    text = ""
                    onValueChange("")
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Filled.Cancel, "Clear $label", tint = colors.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StatusBanner(state: ExplorerState, onRetry: () -> Unit) {
    val colors = Explorer.colors
    AnimatedVisibility(visible = state.isStale, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.fillMaxWidth().background(colors.warningSoft).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CloudOff, null, tint = colors.warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.isRefreshing) "Checking for new flights…" else "Offline · flights from ${state.lastUpdatedLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.warning,
                modifier = Modifier.weight(1f),
            )
            TextAction("Retry", onRetry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsList(state: ExplorerState, controller: ExplorerController, listState: LazyListState) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = controller::refresh,
        modifier = Modifier.fillMaxSize().background(Explorer.colors.canvas),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                // Keeps the last row reachable above the selection card.
                bottom = if (state.selectedFlight != null) 240.dp else 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.results, key = { it.id }, contentType = { "flight" }) { flight ->
                FlightRow(
                    flight = flight,
                    selected = flight.id == state.selectedFlight?.id,
                    saved = state.isSaved(flight.id),
                    pending = state.isPending(flight.id),
                    onClick = {
                        controller.selectFlight(if (flight.id == state.selectedFlight?.id) null else flight.id)
                    },
                    onToggleSaved = { toggleSaved(state, controller, flight) },
                )
            }
        }
    }
}

fun toggleSaved(state: ExplorerState, controller: ExplorerController, flight: Flight) {
    if (state.isSaved(flight.id)) controller.remove(flight) else controller.save(flight)
}

@Composable
private fun SelectedFlightOverlay(
    state: ExplorerState,
    controller: ExplorerController,
    modifier: Modifier = Modifier,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    // Remembers the last flight so the card has content while it animates away.
    var lastFlight by remember { mutableStateOf(state.selectedFlight) }
    state.selectedFlight?.let { lastFlight = it }
    AnimatedVisibility(
        visible = state.selectedFlight != null,
        modifier = modifier,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
    ) {
        lastFlight?.let { flight ->
            FlightCard(
                flight = flight,
                saved = state.isSaved(flight.id),
                pending = state.isPending(flight.id),
                onToggleSaved = { toggleSaved(state, controller, flight) },
                onClose = { controller.selectFlight(null) },
                modifier = Modifier.padding(12.dp),
                secondaryAction = secondaryAction,
            )
        }
    }
}

@Composable
private fun MapOverlays(state: ExplorerState, controller: ExplorerController, onResetFilters: () -> Unit) {
    val colors = Explorer.colors
    Box(Modifier.fillMaxSize()) {
        if (state.content == ContentKind.NO_MATCHES) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                shape = MaterialTheme.shapes.medium,
                color = colors.surface,
                shadowElevation = 6.dp,
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("No flights match your filters", style = MaterialTheme.typography.labelMedium, color = colors.ink)
                    Spacer(Modifier.width(4.dp))
                    TextAction("Reset", onResetFilters)
                }
            }
        }
        DestinationPanel(state, controller, Modifier.align(Alignment.BottomCenter))
        SelectedFlightOverlay(state, controller, Modifier.align(Alignment.BottomCenter))
    }
}

/** Slides up when a marker is tapped, so its flights can be browsed without leaving the map. */
@Composable
private fun DestinationPanel(state: ExplorerState, controller: ExplorerController, modifier: Modifier = Modifier) {
    val colors = Explorer.colors
    val destination = state.selectedDestination
    var last by remember { mutableStateOf(destination) }
    destination?.let { last = it }
    val scope = rememberCoroutineScope()
    val panelList = rememberLazyListState()
    LaunchedEffect(destination?.code) { scope.launch { panelList.scrollToItem(0) } }

    AnimatedVisibility(
        visible = destination != null && state.selectedFlight == null,
        modifier = modifier,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
    ) {
        val marker = last ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier.fillMaxWidth().height(340.dp).padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = colors.canvas,
            shadowElevation = 10.dp,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().background(colors.surface).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${marker.city} · ${marker.code}", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            "${marker.flightCount} ${if (marker.flightCount == 1) "flight" else "flights"} · from ${formatPoints(marker.lowestPoints)} pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                    IconButton(onClick = controller::clearSelection) {
                        Icon(Icons.Filled.Close, "Close ${marker.city} flights", tint = colors.muted)
                    }
                }
                HorizontalDivider(color = colors.hairline)
                LazyColumn(
                    state = panelList,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.destinationFlights, key = { it.id }) { flight ->
                        FlightRow(
                            flight = flight,
                            selected = false,
                            saved = state.isSaved(flight.id),
                            pending = state.isPending(flight.id),
                            onClick = { controller.selectFlight(flight.id) },
                            onToggleSaved = { toggleSaved(state, controller, flight) },
                        )
                    }
                }
            }
        }
    }
}
