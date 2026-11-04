package travel.roame.takehome.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import travel.roame.takehome.domain.departureLabel
import travel.roame.takehome.domain.departureRowLabel
import travel.roame.takehome.domain.formatPoints
import travel.roame.takehome.model.Flight

/** origin ●- - ✈ - -● destination: the route connector, drawn the same in rows and on the card. */
@Composable
fun RouteConnector(modifier: Modifier = Modifier) {
    val color = Explorer.colors.accent
    Box(modifier.height(20.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val y = size.height / 2
            val radius = 3.dp.toPx()
            drawLine(
                color = color,
                start = Offset(radius, y),
                end = Offset(size.width - radius, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
            drawCircle(color, radius, Offset(radius, y))
            drawCircle(color, radius, Offset(size.width - radius, y))
        }
        Surface(color = Explorer.colors.surface, shape = CircleShape) {
            Icon(
                Icons.Filled.Flight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(2.dp).size(16.dp).rotate(90f),
            )
        }
    }
}

@Composable
fun SaveIconButton(saved: Boolean, pending: Boolean, routeLabel: String, onClick: () -> Unit) {
    val tint by animateColorAsState(if (saved) Explorer.colors.accent else Explorer.colors.muted, label = "saveTint")
    IconButton(onClick = onClick, enabled = !pending) {
        if (pending) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Explorer.colors.muted)
        } else {
            Icon(
                if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (saved) "Remove $routeLabel from saved" else "Save $routeLabel",
                tint = tint,
            )
        }
    }
}

@Composable
fun FlightRow(
    flight: Flight,
    selected: Boolean,
    saved: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Explorer.colors
    val border by animateColorAsState(if (selected) colors.accent else colors.hairline, label = "rowBorder")
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .border(if (selected) 1.5.dp else 1.dp, border, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flight.origin, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                RouteConnector(Modifier.width(44.dp).padding(horizontal = 6.dp))
                Text(flight.destination, style = MaterialTheme.typography.titleMedium, color = colors.ink)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "${flight.originCity} to ${flight.destinationCity}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${flight.departureRowLabel} · ${flight.cabin}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPoints(flight.points), style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Text(
                flight.program,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
            )
        }
        SaveIconButton(saved, pending, "${flight.origin} to ${flight.destination}", onToggleSaved)
    }
}

/** The selected flight's details, shared by the list, the map and the saved screen. */
@Composable
fun FlightCard(
    flight: Flight,
    saved: Boolean,
    pending: Boolean,
    onToggleSaved: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeDescription: String = "Close flight details",
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    val colors = Explorer.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    flight.departureLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, closeDescription, tint = colors.muted, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flight.origin, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
                RouteConnector(Modifier.weight(1f).padding(horizontal = 12.dp))
                Text(flight.destination, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            }
            Row {
                Text(flight.originCity, style = MaterialTheme.typography.bodySmall, color = colors.muted, modifier = Modifier.weight(1f))
                Text(flight.destinationCity, style = MaterialTheme.typography.bodySmall, color = colors.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatPoints(flight.points), style = MaterialTheme.typography.titleLarge, color = colors.ink)
                        Text(" pts", style = MaterialTheme.typography.bodySmall, color = colors.muted, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text("${flight.cabin} · ${flight.program}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                val label = if (saved) "Remove" else "Save"
                if (saved) {
                    OutlinedButton(
                        onClick = onToggleSaved,
                        enabled = !pending,
                        border = BorderStroke(1.dp, colors.hairline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                    ) { SaveButtonContent(pending, label) }
                } else {
                    Button(onClick = onToggleSaved, enabled = !pending) { SaveButtonContent(pending, label) }
                }
            }
            if (secondaryAction != null) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) { secondaryAction() }
            }
        }
    }
}

@Composable
private fun SaveButtonContent(pending: Boolean, label: String) {
    if (pending) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Explorer.colors.muted)
        Spacer(Modifier.width(8.dp))
    }
    Text(label, fontWeight = FontWeight.SemiBold)
}

/** Skeleton rows for the first load, so the layout doesn't jump when results arrive. */
@Composable
fun LoadingRows(modifier: Modifier = Modifier) {
    val colors = Explorer.colors
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp).semantics { contentDescription = "Loading flights" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(7) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surface)
                    .padding(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(colors.hairline))
                    Box(Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.hairline))
                    Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.hairline))
                }
                Box(Modifier.width(56.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(colors.hairline))
            }
        }
    }
}

/** Shared layout for the error and empty states. */
@Composable
fun MessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = Explorer.colors
    Column(
        modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = Explorer.colors.accent, fontWeight = FontWeight.SemiBold) }
}
