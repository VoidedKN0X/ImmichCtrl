package com.vknxworks.immichctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.BuildConfig
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.security.expectAppReturn
import com.vknxworks.immichctrl.data.settings.SettingsBackups
import com.vknxworks.immichctrl.data.settings.SettingsFileException
import com.vknxworks.immichctrl.data.timeline.Sources
import com.vknxworks.immichctrl.ui.components.CtrlAlertDialog
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

/** A settings file that was opened and is waiting for the user's yes. */
private class PendingFile(val settings: JsonObject, val createdAt: String?, val changes: List<SettingsBackups.Change>)

/**
 * Settings › Back up and restore: save the app's settings to a JSON file in a place you pick, and read one
 * back. No sign-in details, no app lock; see [SettingsBackups] for exactly what is and isn't in the file.
 */
@Composable
fun SettingsFileGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingFile?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = SettingsBackups.export(c.settings.current(), BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")).use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.settings_file_saved else R.string.settings_file_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    (context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")).use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > SettingsBackups.MAX_BYTES) throw SettingsFileException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                }
                val parsed = SettingsBackups.parse(text)
                PendingFile(parsed.settings, parsed.createdAt, SettingsBackups.changes(c.settings.current(), parsed.settings))
            }
            result.onSuccess { file ->
                if (file.changes.isEmpty()) Toast.makeText(context, R.string.settings_file_nothing_to_change, Toast.LENGTH_LONG).show()
                else pending = file
            }.onFailure { e ->
                val msg = when {
                    e is SettingsFileException && e.newer -> R.string.settings_file_too_new
                    e is SettingsFileException -> R.string.settings_file_not_valid
                    else -> R.string.settings_file_read_failed
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Group(stringResource(R.string.settings_file_title), footer = stringResource(R.string.settings_file_footer)) {
        GroupRow(
            stringResource(R.string.settings_file_save),
            stringResource(R.string.settings_file_save_subtitle),
            icon = Icons.Outlined.FileDownload,
            onClick = {
                expectAppReturn(context)
                runCatching { save.launch("immichctrl-settings-${LocalDate.now()}.json") }
            },
        )
        GroupRow(
            stringResource(R.string.settings_file_restore),
            stringResource(R.string.settings_file_restore_subtitle),
            icon = Icons.Outlined.FileUpload,
            onClick = {
                expectAppReturn(context)
                runCatching { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
            },
        )
    }

    pending?.let { file ->
        CtrlAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_file_restore_title)) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        pluralStringResource(R.plurals.settings_file_restore_body, file.changes.size, file.changes.size, shortDate(file.createdAt) ?: "?"),
                    )
                    file.changes.forEach { ch ->
                        Column {
                            Text(stringResource(ch.field.label), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text)
                            Text(
                                stringResource(R.string.settings_file_from_to, valueText(ch.field, ch.from), valueText(ch.field, ch.to)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(stringResource(R.string.settings_file_restore_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val ok = runCatching { c.settings.update { cur -> SettingsBackups.apply(cur, file.settings) } }.isSuccess
                        if (ok) Sources.refreshAll()
                        Toast.makeText(context, if (ok) R.string.settings_file_restored else R.string.settings_file_read_failed, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.settings_file_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
}

/** A stored value the way the settings screens would say it: On/Off, the choice's name, a number, a count. */
@Composable
private fun valueText(field: SettingsBackups.Field, value: JsonElement): String = when {
    value is JsonNull -> stringResource(R.string.settings_file_value_none)
    value is JsonArray -> pluralStringResource(R.plurals.settings_file_value_albums, value.size, value.size)
    value is JsonPrimitive && !value.isString && value.booleanOrNull != null ->
        stringResource(if (value.booleanOrNull == true) R.string.settings_file_value_on else R.string.off)
    value is JsonPrimitive -> field.valueLabels[value.content]?.let { stringResource(it) } ?: value.content
    else -> value.toString()
}
