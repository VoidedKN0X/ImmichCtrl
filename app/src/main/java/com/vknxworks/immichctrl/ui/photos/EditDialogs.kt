package com.vknxworks.immichctrl.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.PlaceDto
import com.vknxworks.immichctrl.data.api.searchPlaces
import com.vknxworks.immichctrl.data.settings.ShareSize
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pick a date, then a time. Returns the moment as ISO 8601 with the phone's offset, which is what
 * Immich stores as "date taken".
 */
@Composable
fun DateTimeDialog(initial: LocalDateTime, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { step = 1 }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Time taken") },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(dateState.selectedDateMillis ?: 0L).atZone(ZoneOffset.UTC).toLocalDate()
                    val local = date.atTime(timeState.hour, timeState.minute)
                    val zoned = local.atZone(ZoneId.systemDefault())
                    onPick(zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Back") } },
        )
    }
}

/** Find a place by name, or type coordinates. */
@Composable
fun LocationDialog(onDismiss: () -> Unit, onPick: (lat: Double, lon: Double, label: String) -> Unit) {
    val c = LocalContext.current.container
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceDto>>(emptyList()) }
    var coords by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = runCatching { c.api.searchPlaces(query) }.getOrDefault(emptyList())
    }
    val parsed = coords.split(',').map { it.trim().toDoubleOrNull() }.takeIf { it.size == 2 && it.all { v -> v != null } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search for a place") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(results, key = { "${it.name}${it.latitude}${it.longitude}" }) { p ->
                        val label = listOfNotNull(p.name, p.admin1name, p.admin2name).joinToString(", ")
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p.latitude, p.longitude, p.name) }.padding(vertical = 10.dp),
                        )
                    }
                }
                OutlinedTextField(
                    coords, { coords = it },
                    label = { Text("Or coordinates: latitude, longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    val (lat, lon) = parsed!!
                    onPick(lat!!, lon!!, "%.5f, %.5f".format(Locale.US, lat, lon))
                },
            ) { Text("Use coordinates") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Original or smaller files when sharing; optionally remembered as the default. */
@Composable
fun ShareSizeDialog(onDismiss: () -> Unit, onPick: (large: Boolean, remember: Boolean) -> Unit) {
    var keep by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share as") },
        text = {
            Column {
                Text(ShareSize.ORIGINAL.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onPick(false, keep) }.padding(vertical = 12.dp))
                Text(ShareSize.LARGE.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onPick(true, keep) }.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { keep = !keep }) {
                    Checkbox(keep, { keep = it })
                    Text("Always use this", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Options for a new (or existing) shared link. */
data class LinkOptions(
    val description: String = "",
    /** Days until it stops working; null for never. */
    val expiresInDays: Int? = null,
    val password: String = "",
    val allowDownload: Boolean = true,
    val allowUpload: Boolean = false,
    val showMetadata: Boolean = true,
) {
    fun expiresAt(): String? = expiresInDays?.let { Instant.now().plusSeconds(it * 86_400L).toString() }
}

@Composable
fun SharedLinkDialog(
    title: String,
    initial: LinkOptions = LinkOptions(),
    allowUploadOption: Boolean,
    confirm: String = "Create link",
    onDismiss: () -> Unit,
    onConfirm: (LinkOptions) -> Unit,
) {
    var o by remember { mutableStateOf(initial) }
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(o.description, { o = o.copy(description = it) }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Stops working", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(null to "Never", 1 to "1 day", 7 to "7 days", 30 to "30 days").forEach { (d, label) ->
                        FilterChip(o.expiresInDays == d, onClick = { o = o.copy(expiresInDays = d) }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    o.password, { o = o.copy(password = it) },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { showPassword = !showPassword }) {
                            androidx.compose.material3.Icon(
                                if (showPassword) androidx.compose.material.icons.Icons.Outlined.VisibilityOff else androidx.compose.material.icons.Icons.Outlined.Visibility,
                                if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Allow downloads", o.allowDownload) { o = o.copy(allowDownload = it) }
                if (allowUploadOption) ToggleRow("Let people add photos", o.allowUpload) { o = o.copy(allowUpload = it) }
                ToggleRow("Show photo details", o.showMetadata) { o = o.copy(showMetadata = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(o) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
