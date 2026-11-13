package com.vknxworks.immichctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.vknxworks.immichctrl.ui.theme.CtrlColors

/** The blurred backdrop shared by floating glass surfaces (tab bar, mini player). Null when not available. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Space taken at the bottom of the screen by floating bars. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the glass.
 */
val LocalBottomClearance = compositionLocalOf { 0.dp }

/**
 * Ctrl / Signal floating surface (viewer controls, selection bar, slideshow controls): the card surface at 92%
 * with a hairline border. No blur, sheen or shadow. The parameters are kept for existing call sites.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.glass(shape: Shape, elevation: Dp = 0.dp, tint: Color = CtrlColors.Surface): Modifier =
    this
        .clip(shape)
        .background(tint.copy(alpha = 0.92f))
        .border(1.dp, CtrlColors.ControlBorder, shape)
