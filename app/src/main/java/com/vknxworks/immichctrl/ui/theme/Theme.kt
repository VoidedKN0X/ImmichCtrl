package com.vknxworks.immichctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.data.settings.ThemeChoice

/**
 * Ctrl / Signal: the series look. Dark only, one accent, hairline borders instead of tints and shadows.
 * Change [Accent] to recolour the whole app.
 */
object CtrlColors {
    /** The one accent colour. Everything that's "on", selected or primary uses it. */
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    val CardBorder = Color(0xFF232328)
    val ControlBorder = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    val Stopped = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)

    val SwitchTrackOff = Color(0xFF2A2A30)
    val ProgressTrack = Color(0xFF26262C)
    val PinEmpty = Color(0xFF3A3A41)
}

/** Kept for older call sites: the accent. */
val ImmichAccent = CtrlColors.Accent

private val Signal = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    // Selected chips, the tablet rail's indicator and tonal buttons: accent, like every "on" state.
    secondaryContainer = CtrlColors.Accent,
    onSecondaryContainer = CtrlColors.OnAccent,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Background,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Background,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    // Dialogs, date pickers and menus draw on surfaceContainerHigh: keep them on the card surface.
    surfaceContainerHigh = CtrlColors.Surface,
    surfaceContainerHighest = CtrlColors.Raised,
    outline = CtrlColors.ControlBorder,
    outlineVariant = CtrlColors.CardBorder,
    scrim = Color(0xCC000000),
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF3A1A1C),
    onErrorContainer = CtrlColors.Error,
)

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk: all UI text and headings. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** JetBrains Mono: numbers, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 54, 700, -0.04),
    displayMedium = style(48, 48, 700, -0.04),
    displaySmall = style(40, 42, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(30, 34, 700, -0.03),
    headlineSmall = style(24, 30, 700, -0.02),
    titleLarge = style(20, 26, 600, -0.01),
    titleMedium = style(18, 24, 600),
    titleSmall = style(15, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500, 0.01),
)

/** Extra type the Material scale doesn't have. */
object CtrlType {
    /** Section headers: mono, uppercase, wide, muted. Uppercase the text yourself (or use [com.vknxworks.immichctrl.ui.components.CtrlLabel]). */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** Small mono data: IDs, counts, addresses, statuses. */
    val Mono = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** The "NAME/CTRL" wordmark. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Big numeric readout on a hero card. */
    val Readout = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** The hero title on a main screen. */
    val Hero = TextStyle(fontFamily = SpaceGrotesk, fontSize = 56.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em)
}

/** Corner radii of the series. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(16.dp)
    val Field = RoundedCornerShape(16.dp)
    val Badge = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
    val Tile = RoundedCornerShape(6.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    // Chips, menus and small controls.
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * The app theme. Ctrl / Signal is dark only: [choice] and [dynamic] are still accepted (and saved) so
 * older settings keep loading, but they no longer change the colours.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ImmichCtrlTheme(choice: ThemeChoice = ThemeChoice.DARK, dynamic: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Signal, typography = AppTypography, shapes = AppShapes, content = content)
}
