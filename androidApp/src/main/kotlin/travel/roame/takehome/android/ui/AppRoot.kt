package travel.roame.takehome.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import travel.roame.takehome.android.ExplorerViewModel

private enum class Tab { EXPLORE, SAVED }

@Composable
fun AppRoot(viewModel: ExplorerViewModel = viewModel()) {
    val controller = viewModel.controller
    val state by controller.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.EXPLORE) }
    val snackbar = remember { SnackbarHostState() }

    StartWithLocalNetworkAccess(controller)

    LaunchedEffect(state.message?.id) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = message.text,
            actionLabel = if (message.canRetry) "Retry" else null,
            withDismissAction = !message.canRetry,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) controller.retryMessage() else controller.dismissMessage()
    }

    Scaffold(
        containerColor = Explorer.colors.canvas,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Explorer.colors.surface, tonalElevation = androidx.compose.ui.unit.Dp.Hairline) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Explorer.colors.accent,
                    selectedTextColor = Explorer.colors.accent,
                    indicatorColor = Explorer.colors.accentSoft,
                    unselectedIconColor = Explorer.colors.muted,
                    unselectedTextColor = Explorer.colors.muted,
                )
                NavigationBarItem(
                    selected = tab == Tab.EXPLORE,
                    onClick = { tab = Tab.EXPLORE },
                    colors = itemColors,
                    icon = {
                        Icon(
                            if (tab == Tab.EXPLORE) Icons.Filled.TravelExplore else Icons.Outlined.TravelExplore,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Explore") },
                )
                NavigationBarItem(
                    selected = tab == Tab.SAVED,
                    onClick = { tab = Tab.SAVED },
                    colors = itemColors,
                    icon = {
                        BadgedBox(
                            badge = {
                                if (state.saved.isNotEmpty()) {
                                    Badge(containerColor = Explorer.colors.accent, contentColor = Explorer.colors.onAccent) {
                                        Text(state.saved.size.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                if (tab == Tab.SAVED) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text("Saved") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Both tabs stay composed so switching keeps scroll position, map camera and text input.
            ExploreScreen(state, controller, visible = tab == Tab.EXPLORE)
            if (tab == Tab.SAVED) SavedScreen(state, controller)
        }
    }
}
