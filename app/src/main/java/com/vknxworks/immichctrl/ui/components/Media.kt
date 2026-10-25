package com.vknxworks.immichctrl.ui.components

import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.vknxworks.immichctrl.AppContainer
import com.vknxworks.immichctrl.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.ui.res.stringResource
import androidx.annotation.PluralsRes
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

// ---------- Images ----------

/** A thumbnail with its ThumbHash blur showing until it arrives. */
@Composable
fun AssetThumb(
    assetId: String,
    thumbhash: String?,
    modifier: Modifier = Modifier,
    size: String = "thumbnail",
    contentScale: ContentScale = ContentScale.Crop,
    /** A copy on the phone or saved offline; shown instead of downloading. */
    localUri: String? = null,
    /** Locked-folder photos: kept in memory only, never written to the image cache on disk. */
    isPrivate: Boolean = false,
) {
    val c = LocalContext.current.container
    val url = remember(assetId, size, localUri) { localUri ?: c.api.thumbnailUrl(assetId, size, thumbhash?.take(12)) }
    val blur = remember(thumbhash) { ThumbHash.bitmap(thumbhash)?.asImageBitmap() }
    val model = privateImageModel(url, isPrivate)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (blur != null) Image(blur, null, Modifier.fillMaxSize(), contentScale = contentScale)
        AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

/** Any server image (album cover, person face, avatar) with a quiet placeholder. */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector = Icons.Outlined.Image,
) {
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(placeholderIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (url == null) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            error = { fallback() },
            loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) },
        )
    }
}

@Composable
fun PersonFace(personId: String, updatedAt: String?, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    RemoteImage(remember(personId, updatedAt) { c.api.personThumbUrl(personId, updatedAt) }, modifier.clip(AvatarShape), placeholderIcon = Icons.Outlined.Person)
}

/** A user's profile picture, or their initial on their Immich avatar colour. */
@Composable
fun UserAvatar(userId: String, name: String, hasImage: Boolean, changedAt: String?, avatarColor: String?, modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    Box(modifier.clip(AvatarShape).background(colorForAvatar(avatarColor).copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = colorForAvatar(avatarColor))
        if (hasImage) {
            AsyncImage(model = c.api.profileImageUrl(userId, changedAt), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun colorForAvatar(name: String?): Color = when (name) {
    "pink" -> Color(0xFFF0A3C6)
    "red" -> Color(0xFFFF8A80)
    "yellow" -> Color(0xFFF2D27B)
    "blue" -> Color(0xFF8DB7FF)
    "green" -> Color(0xFF8BD8A4)
    "purple" -> Color(0xFFC9A6FF)
    "orange" -> Color(0xFFFFB37A)
    "gray" -> Color(0xFFB0B6C0)
    "amber" -> Color(0xFFF2C38B)
    else -> MaterialTheme.colorScheme.primary
}

// ---------- Formatting ----------

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return "%.1f %s".format(v, units[i])
}

/** "1 item", "2,345 photos": a count with its noun, from the plurals in strings.xml. */
fun countLabel(n: Int, @PluralsRes noun: Int): String = Strings.plural(noun, n, "%,d".format(n))

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private val dayYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val longFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")
private val shortDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "Today", "Yesterday", "Sat 12 Oct", or with the year when it isn't this year. */
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> Strings.str(R.string.today)
        date == today.minusDays(1) -> Strings.str(R.string.yesterday)
        date.year == today.year -> date.format(dayFmt)
        else -> date.format(dayYearFmt)
    }
}

fun longDate(date: LocalDate): String = date.format(longFmt)

fun shortDate(iso: String?): String? = iso?.let { s ->
    runCatching { Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}?.format(shortDateFmt)

/** "3 years ago" for memories. */
fun yearsAgo(year: Int?): String {
    if (year == null) return Strings.str(R.string.on_this_day)
    val n = LocalDate.now().year - year
    return when {
        n <= 0 -> Strings.str(R.string.this_year)
        n == 1 -> Strings.str(R.string.n_1_year_ago)
        else -> Strings.str(R.string.years_ago, n)
    }
}

fun daysUntil(iso: String?): Long? = iso?.let { runCatching { ChronoUnit.DAYS.between(Instant.now(), Instant.parse(it)) }.getOrNull() }

// ---------- Background work and dialogs ----------

/** Runs a server call that outlives the screen that started it, reporting failures as a toast. */
fun AppContainer.runTask(success: String? = null, block: suspend () -> Unit) {
    appScope.launch(Dispatchers.Main) {
        runCatching { block() }
            .onSuccess { if (success != null) Toast.makeText(context, success, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, it.friendlyMessage(), Toast.LENGTH_SHORT).show() }
    }
}

fun AppContainer.toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
fun ActionItem(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirm: String,
    initial: String = "",
    allowEmpty: Boolean = false,
    secret: Boolean = false,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else if (secret) KeyboardType.Password else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = CtrlShapes.Field, colors = ctrlFieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = allowEmpty || text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, destructive: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

/** Ctrl / Signal avatars and faces: rounded squares, not circles. */
val AvatarShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 32)

/** Small badge used for counts and durations over thumbnails: 9dp corners, mono digits. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(com.vknxworks.immichctrl.ui.theme.CtrlShapes.Badge).background(com.vknxworks.immichctrl.ui.theme.CtrlColors.Background.copy(alpha = 0.62f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = Color.White)
        if (text.isNotEmpty()) {
            if (icon != null) Spacer(Modifier.width(3.dp))
            Text(text, style = com.vknxworks.immichctrl.ui.theme.CtrlType.Mono.copy(fontSize = 10.sp), color = Color.White)
        }
    }
}

/** The thumbnail for any [com.vknxworks.immichctrl.data.api.Asset], wherever it lives. */
@Composable
fun AssetThumb(asset: com.vknxworks.immichctrl.data.api.Asset, modifier: Modifier = Modifier, size: String = "thumbnail") =
    AssetThumb(
        asset.id, asset.thumbhash, modifier, size,
        localUri = if (asset.isLocalOnly || asset.localUri?.startsWith("file:") == true) asset.localUri else null,
        isPrivate = asset.visibility == "locked",
    )

/** Image model for Coil: locked-folder images skip the disk cache, so they're never left on storage. */
@Composable
fun privateImageModel(url: String, isPrivate: Boolean): Any {
    if (!isPrivate) return url
    val ctx = coil3.compose.LocalPlatformContext.current
    return remember(url) {
        coil3.request.ImageRequest.Builder(ctx)
            .data(url)
            .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
            .build()
    }
}

/** The activity behind a Compose context (needed for fingerprint prompts). */
fun android.content.Context.findActivity(): androidx.fragment.app.FragmentActivity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is androidx.fragment.app.FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
