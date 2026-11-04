package travel.roame.takehome.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The same tokens as iosApp/Sources/Theme.swift, so the two apps read as one product. */
@Immutable
data class ExplorerColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val warning: Color,
    val warningSoft: Color,
)

val LightExplorerColors = ExplorerColors(
    canvas = Color(0xFFF5F5F1),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF12202E),
    muted = Color(0xFF5E6B78),
    hairline = Color(0xFFE3E3DD),
    accent = Color(0xFFE5482A),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFCE9E4),
    warning = Color(0xFF8A5A00),
    warningSoft = Color(0xFFFBF0D9),
)

val DarkExplorerColors = ExplorerColors(
    canvas = Color(0xFF0E141B),
    surface = Color(0xFF18202A),
    ink = Color(0xFFF1F3F5),
    muted = Color(0xFF9AA6B2),
    hairline = Color(0xFF2B3440),
    accent = Color(0xFFFF7050),
    onAccent = Color(0xFF2A0E06),
    accentSoft = Color(0xFF3A211B),
    warning = Color(0xFFF2C46B),
    warningSoft = Color(0xFF332A17),
)

val LocalExplorerColors = staticCompositionLocalOf { LightExplorerColors }

object Explorer {
    val colors: ExplorerColors
        @Composable get() = LocalExplorerColors.current
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun FlightExplorerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkExplorerColors else LightExplorerColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondaryContainer = colors.accentSoft, onSecondaryContainer = colors.accent,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceContainer = colors.surface, surfaceContainerLow = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceVariant = colors.canvas, onSurfaceVariant = colors.muted,
            outline = colors.hairline, outlineVariant = colors.hairline,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondaryContainer = colors.accentSoft, onSecondaryContainer = colors.accent,
            background = colors.canvas, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceContainer = colors.surface, surfaceContainerLow = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceVariant = colors.canvas, onSurfaceVariant = colors.muted,
            outline = colors.hairline, outlineVariant = colors.hairline,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExplorerColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
