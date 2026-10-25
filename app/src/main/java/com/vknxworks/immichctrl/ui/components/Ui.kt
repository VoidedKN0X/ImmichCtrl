package com.vknxworks.immichctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.text.font.FontWeight
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.nav.YouRoute
import com.vknxworks.immichctrl.ui.theme.Gutter
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import com.vknxworks.immichctrl.R

// ---------- Top bars ----------

/** Top bar for pushed screens: the square back button and a title in Space Grotesk. No fill. */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlSquareButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = nav::back)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A main tab's header: the IMMICH/CTRL wordmark with your profile picture, then the tab's big title. */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(start = if (padded) Gutter else 0.dp, end = if (padded) Gutter else 0.dp, top = 12.dp, bottom = 8.dp)) {
        CtrlTopBar(trailing = {
            trailing()
            if (showProfile) ProfileButton()
        })
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

/**
 * The signed-in user's Immich picture in the series' square profile button: the picture when there is one,
 * otherwise the initial. Tapping it opens the You page (not in read-only mode).
 */
@Composable
fun ProfileButton() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val acc = session?.account ?: return
    CtrlAvatarButton(stringResource(R.string.you), onClick = { if (!settings.readOnly) nav.navigate(YouRoute) }) {
        ProfileAvatar(acc.userId, acc.name)
    }
}

/** The series profile picture: the Immich picture over the initial, filling its (square) parent. */
@Composable
fun ProfileAvatar(userId: String, name: String, modifier: Modifier = Modifier.fillMaxSize()) {
    val c = LocalContext.current.container
    Box(modifier.background(CtrlColors.Surface), contentAlignment = Alignment.Center) {
        val initial = name.trim().take(2).uppercase()
        if (initial.isEmpty()) {
            Icon(Icons.Outlined.AccountCircle, null, Modifier.size(22.dp), tint = CtrlColors.Text)
        } else {
            Text(initial, style = MaterialTheme.typography.labelLarge, color = CtrlColors.Text)
        }
        AsyncImage(
            model = c.api.profileImageUrl(userId, null),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

// ---------- Grouped lists (settings, profile, admin) ----------

/**
 * A card of related rows with a mono section label above it. Rows draw a hairline at their top; the first one
 * sits under the card's own border.
 */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = if (padded) Gutter else 0.dp, vertical = 8.dp)) {
        if (title != null) CtrlLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CtrlShapes.Card)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card),
        ) {
            // Shift the rows up by the first row's 1dp divider so it hides under the card's own border.
            Column(
                Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val hair = 1.dp.roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height - hair).coerceAtLeast(0)) { placeable.place(0, -hair) }
                },
                content = content,
            )
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = CtrlColors.Muted,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        CtrlDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else if (showChevron) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GroupSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    GroupRow(title, subtitle, icon, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        CtrlSwitch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    fallbackLabel: String = "",
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: fallbackLabel
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CtrlColors.Surface, shape = CtrlShapes.Sheet) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Buttons ----------

/** The one main action on a screen. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Stopped,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Circular secondary action with a short label underneath. */
@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(min = 64.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CtrlShapes.Button)
                .background(if (active) CtrlColors.Accent else CtrlColors.Surface)
                .border(1.dp, if (active) CtrlColors.Accent else CtrlColors.ControlBorder, CtrlShapes.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = if (active) CtrlColors.OnAccent else CtrlColors.Text)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) CtrlColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------- Text helpers ----------

/** Facts about an item laid out with space between them instead of separator characters. */
@Composable
fun MetaRow(parts: List<String>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val visible = parts.filter { it.isNotBlank() }
    if (visible.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = color) }
    }
}
