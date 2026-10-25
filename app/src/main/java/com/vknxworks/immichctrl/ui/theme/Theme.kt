package com.vknxworks.immichctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/** One Ctrl / Signal palette. [CtrlColors.Dark] and [CtrlColors.Light] are the two the app uses. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val cardBorder: Color,
    val controlBorder: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    val stopped: Color,
    /** The accent for text, icons, outlines and focus on this palette's backgrounds. */
    val accentText: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val switchTrackOff: Color,
    val switchKnobOff: Color,
    val progressTrack: Color,
    val pinEmpty: Color,
)

/**
 * Ctrl / Signal: the series look. One accent, hairline borders instead of tints and shadows, and a dark and a
 * light palette that follow the phone's dark theme setting. Change [Accent] to recolour the whole app.
 *
 * [Accent] and [OnAccent] are the same in both palettes (accent fills always carry near-black content). Every
 * other colour comes from the palette in [LocalCtrlColors], so it switches with the theme.
 */
object CtrlColors {
    /** The one accent colour, used as a FILL: hero cards, main buttons, the selected tab, switch tracks, PIN dots. */
    val Accent = Color(0xFFB48CFF)
    /** Content on an [Accent] fill. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        cardBorder = Color(0xFF232328),
        controlBorder = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        stopped = Color(0xFF8C8C94),
        accentText = Accent,
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF3A1A1C),
        switchTrackOff = Color(0xFF2A2A30),
        switchKnobOff = Color(0xFF8C8C94),
        progressTrack = Color(0xFF26262C),
        pinEmpty = Color(0xFF3A3A41),
    )

    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        cardBorder = Color(0xFFDEDED7),
        controlBorder = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        stopped = Color(0xFF74747C),
        // #B48CFF is too pale for text on white.
        accentText = Color(0xFF6A3BDB),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE4E4),
        switchTrackOff = Color(0xFFDCDCD5),
        switchKnobOff = Color(0xFF74747C),
        progressTrack = Color(0xFFE2E2DC),
        pinEmpty = Color(0xFFC9C9C1),
    )

    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.raised
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.cardBorder
    val ControlBorder: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.controlBorder
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.muted
    val Stopped: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.stopped
    /** The accent as text, icon, outline or focus colour: #B48CFF in dark, #6A3BDB in light. */
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.accentText
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.error
    val SwitchTrackOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchTrackOff
    val SwitchKnobOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchKnobOff
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.progressTrack
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.pinEmpty
}

/** The current Ctrl / Signal palette. Set by [ImmichCtrlTheme] (and [CtrlDarkTheme]). */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/** Kept for older call sites: the accent. */
val ImmichAccent = CtrlColors.Accent

private fun scheme(p: CtrlPalette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    // Material's "primary" is mostly text and icons (text buttons, checkboxes, links), so it's the readable accent.
    // Accent fills set CtrlColors.Accent themselves.
    primary = p.accentText,
    onPrimary = if (p.isDark) CtrlColors.OnAccent else Color.White,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = p.text,
    onSecondary = p.background,
    // Selected chips, the tablet rail's indicator and tonal buttons: accent fill, like every "on" state.
    secondaryContainer = CtrlColors.Accent,
    onSecondaryContainer = CtrlColors.OnAccent,
    tertiary = p.accentText,
    onTertiary = if (p.isDark) CtrlColors.OnAccent else Color.White,
    tertiaryContainer = p.raised,
    onTertiaryContainer = p.text,
    background = p.background,
    onBackground = p.text,
    surface = p.background,
    onSurface = p.text,
    surfaceVariant = p.raised,
    onSurfaceVariant = p.muted,
    surfaceTint = Color.Transparent,
    inverseSurface = p.text,
    inverseOnSurface = p.background,
    surfaceBright = p.surface,
    surfaceDim = p.background,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.surface,
    // Dialogs, date pickers and menus draw on surfaceContainerHigh: keep them on the card surface.
    surfaceContainerHigh = p.surface,
    surfaceContainerHighest = p.raised,
    outline = p.controlBorder,
    outlineVariant = p.cardBorder,
    scrim = if (p.isDark) Color(0xCC000000) else Color(0x66000000),
    error = p.error,
    onError = p.onError,
    errorContainer = p.errorContainer,
    onErrorContainer = p.error,
)

private val DarkScheme = scheme(CtrlColors.Dark)
private val LightScheme = scheme(CtrlColors.Light)

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
 * The app theme: Ctrl / Signal, dark or light following the phone's dark theme setting. There is no in-app theme
 * choice and no wallpaper colours; [choice] and [dynamic] are still accepted (and saved) so older settings keep
 * loading, but they don't change anything.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ImmichCtrlTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CtrlPaletteTheme(if (dark) CtrlColors.Dark else CtrlColors.Light, content)
}

/** Always the dark palette, whatever the phone's setting: for the photo viewer, slideshows and the editor. */
@Composable
fun CtrlDarkTheme(content: @Composable () -> Unit) = CtrlPaletteTheme(CtrlColors.Dark, content)

@Composable
private fun CtrlPaletteTheme(palette: CtrlPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(
            colorScheme = if (palette.isDark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
