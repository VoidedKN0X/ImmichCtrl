package com.vknxworks.immichctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.vknxworks.immichctrl.ui.nav.TabItem
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import com.vknxworks.immichctrl.ui.theme.CtrlType

// Ctrl / Signal building blocks, shared by every screen.

/** "IMMICH/CTRL": the series wordmark, the slash in the accent. */
@Composable
fun CtrlWordmark(name: String = "IMMICH", modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
    )
}

/** A section label: JetBrains Mono, uppercase, wide tracking, muted. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The main-screen top bar: wordmark on the left, [trailing] controls and the profile button on the right. No fill. */
@Composable
fun CtrlTopBar(
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CtrlWordmark(modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 44dp square control (back, close, quick actions): surface fill, 14dp corners, hairline border. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CtrlColors.Text,
    container: Color = CtrlColors.Surface,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Control)
            .background(container)
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}

/**
 * The 44dp square profile button: the picture (or [initials]) on the surface, a hairline border and a
 * 10dp accent square in the corner while signed in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CtrlAvatarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    connected: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    picture: @Composable () -> Unit,
) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { picture() }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

/** A secondary card: surface, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The one most important thing on a main screen: accent fill, near-black content, mono label row, giant number. */
@Composable
fun CtrlHeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    unit: String? = null,
    footer: String? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CtrlShapes.Hero)
            .background(CtrlColors.Accent)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = CtrlType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = CtrlColors.OnAccent, modifier = Modifier.weight(1f), maxLines = 1)
            if (trailingLabel != null) {
                Text(trailingLabel.uppercase(), style = CtrlType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.1.em), color = CtrlColors.OnAccent, maxLines = 1)
            }
        }
        Text(
            buildAnnotatedString {
                append(value)
                if (unit != null) withStyle(SpanStyle(fontSize = CtrlType.Readout.fontSize * 0.44f)) { append(unit) }
            },
            style = CtrlType.Readout,
            color = CtrlColors.OnAccent,
            maxLines = 1,
        )
        if (footer != null) Text(footer.uppercase(), style = CtrlType.Mono, color = CtrlColors.OnAccent.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** An 8dp square status dot with 2dp corners. */
@Composable
fun CtrlDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CtrlShapes.Dot).background(color))
}

/**
 * The series switch: a 52x32 rounded rectangle with a 24dp knob. On: accent track, near-black knob.
 * Off: dark track, grey knob. Announced as a switch.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val bias by animateFloatAsState(if (checked) 1f else -1f, label = "switch-knob")
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchTrackOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchKnobOff, label = "switch-knob-color")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCheckedChange(it)
                    }
                } else Modifier,
            )
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .align(BiasAlignment(bias, 0f))
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(knob),
        )
    }
}

/** 6dp progress bar, 3dp corners. Light fill by default; [accent] for the primary metric. */
@Composable
fun CtrlProgress(progress: () -> Float, modifier: Modifier = Modifier, accent: Boolean = false) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier.height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (accent) CtrlColors.Accent else CtrlColors.Text,
        trackColor = CtrlColors.ProgressTrack,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Indeterminate version of [CtrlProgress]. */
@Composable
fun CtrlProgress(modifier: Modifier = Modifier, accent: Boolean = true) {
    LinearProgressIndicator(
        modifier = modifier.height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = if (accent) CtrlColors.Accent else CtrlColors.Text,
        trackColor = CtrlColors.ProgressTrack,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
    )
}

/** Text field colours: surface fill, hairline border, accent border and cursor when focused. Use with [CtrlShapes.Field]. */
@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.AccentText,
    unfocusedBorderColor = CtrlColors.ControlBorder,
    disabledBorderColor = CtrlColors.CardBorder,
    cursorColor = CtrlColors.AccentText,
    focusedLabelColor = CtrlColors.AccentText,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedLeadingIconColor = CtrlColors.Muted,
    unfocusedLeadingIconColor = CtrlColors.Muted,
    focusedTrailingIconColor = CtrlColors.Muted,
    unfocusedTrailingIconColor = CtrlColors.Muted,
    focusedPlaceholderColor = CtrlColors.Muted,
    unfocusedPlaceholderColor = CtrlColors.Muted,
)

/** A plain list row: 52dp min, 1dp top divider, leading mono meta, title, trailing mono status with a square dot. */
@Composable
fun CtrlListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    status: String? = null,
    active: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(CtrlColors.Divider))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (meta != null) Text(meta, style = CtrlType.Mono, color = CtrlColors.Muted)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                val color = if (active) CtrlColors.AccentText else CtrlColors.Stopped
                Text(status.uppercase(), style = CtrlType.Label.copy(letterSpacing = 0.1.em), color = color)
                CtrlDot(color)
            }
        }
    }
}

/**
 * The floating bottom navigation: 64dp tall, 22dp corners, surface at 92%, hairline border.
 * The selected tab is an accent rectangle with icon and label; the others are muted icons.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Card)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(if (selected) CtrlColors.Accent else Color.Transparent, label = "tab-bg")
            val fg by animateColorAsState(if (selected) CtrlColors.OnAccent else CtrlColors.Muted, label = "tab-fg")
            Row(
                Modifier
                    .height(48.dp)
                    .then(if (selected) Modifier else Modifier.width(48.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    )
                    .semantics { contentDescription = item.label }
                    .padding(horizontal = if (selected) 18.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (selected) item.selectedIcon else item.icon, null, Modifier.size(if (selected) 18.dp else 20.dp), tint = fg)
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Space a floating [CtrlBottomBar] needs from the screen bottom, before the navigation bar inset. */
val CtrlBottomBarMargin: Dp = 28.dp

/** A hairline divider in the list colour. */
@Composable
fun CtrlDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(CtrlColors.Divider))
}

/** Square-ish placeholder fill for full-size boxes. */
@Composable
fun CtrlFill(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(CtrlColors.Surface))
}

/** Material's AlertDialog in the series style: card surface, 26dp corners, hairline border. Same parameters. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.CardBorder, CtrlShapes.Hero),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Hero,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
    )
}
