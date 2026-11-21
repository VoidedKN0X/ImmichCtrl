package com.vknxworks.immichctrl.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.ApiException
import com.vknxworks.immichctrl.data.api.ApiKeyDto
import com.vknxworks.immichctrl.data.api.ApiPermissions
import com.vknxworks.immichctrl.data.api.AuthType
import com.vknxworks.immichctrl.data.api.NotificationDto
import com.vknxworks.immichctrl.data.api.SessionDto
import com.vknxworks.immichctrl.data.api.apiKeys
import com.vknxworks.immichctrl.data.api.createApiKey
import com.vknxworks.immichctrl.data.api.currentApiKey
import com.vknxworks.immichctrl.data.api.deleteApiKey
import com.vknxworks.immichctrl.data.api.deleteNotifications
import com.vknxworks.immichctrl.data.api.deleteOtherSessions
import com.vknxworks.immichctrl.data.api.deleteSession
import com.vknxworks.immichctrl.data.api.markNotifications
import com.vknxworks.immichctrl.data.api.notifications
import com.vknxworks.immichctrl.data.api.parseInstantMs
import com.vknxworks.immichctrl.data.api.sessions
import com.vknxworks.immichctrl.data.api.updateApiKey
import com.vknxworks.immichctrl.data.security.GuardedScreen
import com.vknxworks.immichctrl.data.security.copySensitive
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.ConfirmDialog
import com.vknxworks.immichctrl.ui.components.CtrlAlertDialog
import com.vknxworks.immichctrl.ui.components.CtrlDivider
import com.vknxworks.immichctrl.ui.components.CtrlDot
import com.vknxworks.immichctrl.ui.components.CtrlSwitch
import com.vknxworks.immichctrl.ui.components.ErrorView
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.TextInputDialog
import com.vknxworks.immichctrl.ui.components.ctrlFieldColors
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import com.vknxworks.immichctrl.ui.components.shortDate
import com.vknxworks.immichctrl.ui.components.toast
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.theme.CtrlColors
import com.vknxworks.immichctrl.ui.theme.CtrlShapes
import com.vknxworks.immichctrl.ui.theme.CtrlType
import com.vknxworks.immichctrl.ui.theme.Gutter

/** "5 minutes ago" for a server timestamp; null when it can't be read. */
internal fun relativeTime(iso: String?): String? {
    val ms = parseInstantMs(iso) ?: return null
    val now = System.currentTimeMillis()
    if (now - ms in 0 until 60_000L) return Strings.str(R.string.sync_just_now)
    return DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

// ---------------------------------------------------------------------------------------------
// Signed-in devices
// ---------------------------------------------------------------------------------------------

/** You › Signed-in devices: where the account is signed in, with sign-out for one device or all the others. */
@Composable
fun SessionsScreen() {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val viaApiKey = session?.account?.authType == AuthType.API_KEY
    val list = rememberLoader("sessions") { c.api.sessions() }
    var signingOut by remember { mutableStateOf<SessionDto?>(null) }
    var signingOutOthers by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.signed_in_devices))
        when (val s = list.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { list.reload() }
            is Load.Ok -> {
                val sorted = s.value.sortedWith(compareByDescending<SessionDto> { it.current }.thenByDescending { parseInstantMs(it.updatedAt) ?: 0L })
                val others = sorted.count { !it.current }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                    if (sorted.isEmpty()) {
                        Group(footer = stringResource(R.string.sessions_footer_api_key)) {
                            GroupRow(stringResource(R.string.sessions_none), icon = Icons.Outlined.Devices)
                        }
                    } else {
                        Group(footer = stringResource(if (viaApiKey) R.string.sessions_footer_api_key else R.string.sessions_footer)) {
                            sorted.forEach { d ->
                                GroupRow(
                                    sessionName(d),
                                    listOfNotNull(
                                        if (d.current) stringResource(R.string.session_this_device)
                                        else relativeTime(d.updatedAt)?.let { stringResource(R.string.session_last_active, it) },
                                        d.appVersion?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.session_app_version, it) },
                                        shortDate(d.createdAt)?.let { stringResource(R.string.session_signed_in_on, it) },
                                        shortDate(d.expiresAt)?.let { stringResource(R.string.session_expires, it) },
                                    ).joinToString(stringResource(R.string.list_separator_wide)),
                                    icon = if (d.current) Icons.Outlined.PhoneAndroid else Icons.Outlined.Devices,
                                    iconTint = if (d.current) CtrlColors.AccentText else CtrlColors.Muted,
                                    trailing = if (d.current) null else ({
                                        IconButton(onClick = { signingOut = d }) {
                                            Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.session_sign_out_device), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }),
                                )
                            }
                        }
                    }
                    if (others > 0) {
                        Group {
                            GroupRow(
                                stringResource(if (viaApiKey) R.string.session_sign_out_all else R.string.session_sign_out_others),
                                icon = Icons.AutoMirrored.Outlined.Logout,
                                iconTint = MaterialTheme.colorScheme.error,
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = { signingOutOthers = true },
                            )
                        }
                    }
                }
            }
        }
    }

    signingOut?.let { d ->
        ConfirmDialog(
            stringResource(R.string.session_sign_out_title, sessionName(d)),
            stringResource(R.string.session_sign_out_body),
            stringResource(R.string.sign_out),
            onDismiss = { signingOut = null },
        ) {
            signingOut = null
            c.runTask(Strings.str(R.string.session_signed_out)) {
                c.api.deleteSession(d.id)
                list.reload(silent = true)
            }
        }
    }
    if (signingOutOthers) {
        ConfirmDialog(
            stringResource(if (viaApiKey) R.string.session_sign_out_all_title else R.string.session_sign_out_others_title),
            stringResource(if (viaApiKey) R.string.session_sign_out_all_body else R.string.session_sign_out_others_body),
            stringResource(R.string.sign_out),
            onDismiss = { signingOutOthers = false },
        ) {
            signingOutOthers = false
            c.runTask(Strings.str(R.string.sessions_others_signed_out)) {
                c.api.deleteOtherSessions()
                list.reload(silent = true)
            }
        }
    }
}

@Composable
private fun sessionName(d: SessionDto): String {
    val parts = listOf(d.deviceType, d.deviceOS).map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) stringResource(R.string.session_unknown_device) else parts.joinToString(stringResource(R.string.list_separator_wide))
}

// ---------------------------------------------------------------------------------------------
// API keys
// ---------------------------------------------------------------------------------------------

/** You › API keys: the keys other apps and scripts use, with create, rename, change permissions and delete. */
@Composable
fun ApiKeysScreen() {
    val context = LocalContext.current
    val c = context.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val viaApiKey = session?.account?.authType == AuthType.API_KEY
    val keys = rememberLoader("api-keys") { c.api.apiKeys() }
    // The key this app itself signs in with (API-key accounts only), so it isn't deleted by accident.
    val own = rememberLoader("api-key-own:$viaApiKey") { if (viaApiKey) runCatching { c.api.currentApiKey().id }.getOrNull() else null }
    val ownId = own.value
    var menuFor by remember { mutableStateOf<ApiKeyDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ApiKeyDto?>(null) }
    var permissionsFor by remember { mutableStateOf<ApiKeyDto?>(null) }
    var deleting by remember { mutableStateOf<ApiKeyDto?>(null) }
    var secret by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.api_keys)) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, stringResource(R.string.api_key_new)) }
        }
        when (val s = keys.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { keys.reload() }
            is Load.Ok -> {
                if (s.value.isEmpty()) {
                    MessageView(
                        Icons.Outlined.Key,
                        stringResource(R.string.api_keys_empty_title),
                        stringResource(R.string.api_keys_empty_body),
                        actionLabel = stringResource(R.string.api_key_new),
                        onAction = { creating = true },
                    )
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                        Group(footer = stringResource(R.string.api_keys_footer)) {
                            s.value.sortedBy { it.name.lowercase() }.forEach { k ->
                                GroupRow(
                                    k.name.ifBlank { stringResource(R.string.api_key_unnamed) },
                                    listOfNotNull(
                                        if (k.id == ownId) stringResource(R.string.api_key_in_use) else null,
                                        permissionsLabel(k.permissions),
                                        shortDate(k.createdAt)?.let { stringResource(R.string.api_key_created_on, it) },
                                    ).joinToString(stringResource(R.string.list_separator_wide)),
                                    icon = Icons.Outlined.Key,
                                    iconTint = if (k.id == ownId) CtrlColors.AccentText else CtrlColors.Muted,
                                    showChevron = true,
                                    onClick = { menuFor = k },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { k ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = CtrlShapes.Sheet) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetTitle(k.name.ifBlank { stringResource(R.string.api_key_unnamed) }, permissionsLabel(k.permissions))
                ActionItem(Icons.Outlined.Edit, stringResource(R.string.rename)) { menuFor = null; renaming = k }
                ActionItem(Icons.Outlined.Tune, stringResource(R.string.api_key_change_permissions)) { menuFor = null; permissionsFor = k }
                ActionItem(Icons.Outlined.DeleteOutline, stringResource(R.string.api_key_delete), tint = MaterialTheme.colorScheme.error) { menuFor = null; deleting = k }
            }
        }
    }
    if (creating) {
        ApiKeyEditor(
            title = stringResource(R.string.api_key_new),
            confirm = stringResource(R.string.api_key_create),
            initialName = "",
            initialPermissions = listOf(ApiPermissions.ALL),
            askName = true,
            onDismiss = { creating = false },
        ) { name, permissions ->
            creating = false
            c.runTask {
                val made = c.api.createApiKey(name, permissions)
                keys.reload(silent = true)
                if (made.secret.isBlank()) c.toast(Strings.str(R.string.api_key_created)) else secret = made.secret
            }
        }
    }
    renaming?.let { k ->
        TextInputDialog(stringResource(R.string.rename), stringResource(R.string.name), stringResource(R.string.save), initial = k.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            c.runTask(Strings.str(R.string.api_key_saved)) {
                c.api.updateApiKey(k.id, name = name)
                keys.reload(silent = true)
            }
        }
    }
    permissionsFor?.let { k ->
        ApiKeyEditor(
            title = stringResource(R.string.api_key_change_permissions),
            confirm = stringResource(R.string.save),
            initialName = k.name,
            initialPermissions = k.permissions,
            askName = false,
            onDismiss = { permissionsFor = null },
        ) { _, permissions ->
            permissionsFor = null
            c.runTask(Strings.str(R.string.api_key_saved)) {
                c.api.updateApiKey(k.id, permissions = permissions)
                keys.reload(silent = true)
            }
        }
    }
    deleting?.let { k ->
        val isOwn = k.id == ownId
        ConfirmDialog(
            stringResource(R.string.api_key_delete_title, k.name.ifBlank { stringResource(R.string.api_key_unnamed) }),
            stringResource(if (isOwn) R.string.api_key_delete_own_body else R.string.api_key_delete_body),
            stringResource(R.string.lock_delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            c.runTask(Strings.str(R.string.api_key_deleted)) {
                c.api.deleteApiKey(k.id)
                keys.reload(silent = true)
            }
        }
    }
    secret?.let { value ->
        // The key is a password: keep it out of screenshots and away from other apps' overlays while it shows.
        GuardedScreen(sensitive = true)
        CtrlAlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.api_key_secret_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.api_key_secret_body))
                    SelectionContainer { Text(value, style = CtrlType.Mono, color = CtrlColors.Text) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copySensitive(context, value, Strings.str(R.string.api_keys))
                    c.toast(Strings.str(R.string.copied))
                }) { Text(stringResource(R.string.api_key_copy)) }
            },
            dismissButton = { TextButton(onClick = { secret = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun permissionsLabel(permissions: List<String>): String =
    if (ApiPermissions.isAll(permissions)) stringResource(R.string.api_key_all_permissions)
    else pluralStringResource(R.plurals.api_key_permissions, permissions.size, permissions.size)

/**
 * Name (when [askName]) and what the key may do: everything, or a ticked list of Immich's permissions.
 * Permissions the key already has that this app doesn't know stay ticked and are sent back unchanged.
 */
@Composable
private fun ApiKeyEditor(
    title: String,
    confirm: String,
    initialName: String,
    initialPermissions: List<String>,
    askName: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var all by remember { mutableStateOf(ApiPermissions.isAll(initialPermissions)) }
    val picked = remember { initialPermissions.filter { it != ApiPermissions.ALL }.toMutableStateList() }
    // Everything the server knows that this app's list doesn't have yet, so it can still be unticked.
    val groups = remember {
        val extra = initialPermissions.filter { it != ApiPermissions.ALL && it !in ApiPermissions.names }
        if (extra.isEmpty()) ApiPermissions.grouped
        else (ApiPermissions.names + extra).groupBy { ApiPermissions.groupOf(it) }.map { (g, l) -> g to l }
    }
    val canSave = (all || picked.isNotEmpty()) && (!askName || name.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = CtrlShapes.Sheet,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            SheetTitle(title, if (askName) null else initialName.ifBlank { null })
            if (askName) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = CtrlShapes.Field, colors = ctrlFieldColors(),
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { all = !all }.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.api_key_allow_everything), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(stringResource(R.string.api_key_allow_everything_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                CtrlSwitch(all, onCheckedChange = { all = it })
            }
            if (!all) {
                CtrlDivider()
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp)) {
                    groups.forEach { (group, list) ->
                        item(key = "g:$group") {
                            Text(
                                group.uppercase(),
                                style = CtrlType.Label,
                                color = CtrlColors.Muted,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp),
                            )
                        }
                        items(list, key = { it }) { p ->
                            val on = p in picked
                            Row(
                                Modifier.fillMaxWidth().clickable { if (on) picked.remove(p) else picked.add(p) }.padding(start = 24.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p, style = CtrlType.Mono, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Checkbox(on, onCheckedChange = { checked -> if (checked) { if (p !in picked) picked.add(p) } else picked.remove(p) })
                            }
                        }
                    }
                }
                Text(
                    if (picked.isEmpty()) stringResource(R.string.api_key_pick_one)
                    else pluralStringResource(R.plurals.api_key_permissions, picked.size, picked.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (picked.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            PillButton(
                confirm,
                onClick = { onSave(name.trim(), if (all) listOf(ApiPermissions.ALL) else picked.toList()) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------------------------

/** The server's inbox, or null when this Immich version doesn't have one. */
internal suspend fun loadNotifications(c: com.vknxworks.immichctrl.AppContainer): List<NotificationDto>? =
    try {
        c.api.notifications()
    } catch (e: ApiException) {
        // Immich before 1.133 has no notifications endpoint.
        if (e.code == 404) null else throw e
    }

/** You › Notifications: what the server has told this account (failed backups, album invitations, messages). */
@Composable
fun NotificationsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val inbox = rememberLoader("notifications") { loadNotifications(c) }
    var deletingAll by remember { mutableStateOf(false) }
    val entries = inbox.value.orEmpty().sortedByDescending { parseInstantMs(it.createdAt) ?: 0L }
    val unread = entries.filter { it.unread }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.notifications)) {
            if (unread.isNotEmpty()) IconButton(onClick = {
                val ids = unread.map { it.id }
                c.runTask(Strings.str(R.string.notifications_all_read)) {
                    c.api.markNotifications(ids)
                    inbox.reload(silent = true)
                }
            }) { Icon(Icons.Outlined.DoneAll, stringResource(R.string.notifications_mark_all_read)) }
            if (entries.isNotEmpty()) IconButton(onClick = { deletingAll = true }) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.notifications_delete_all)) }
        }
        when (val s = inbox.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { inbox.reload() }
            is Load.Ok -> when {
                s.value == null -> MessageView(Icons.Outlined.NotificationsNone, stringResource(R.string.notifications_unsupported_title), stringResource(R.string.notifications_unsupported_body))
                entries.isEmpty() -> MessageView(Icons.Outlined.NotificationsNone, stringResource(R.string.notifications_empty_title), stringResource(R.string.notifications_empty_body))
                else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    items(entries, key = { it.id }) { n ->
                        NotificationRow(
                            n,
                            onClick = {
                                if (n.unread) c.runTask {
                                    c.api.markNotifications(listOf(n.id))
                                    inbox.reload(silent = true)
                                }
                                n.albumId?.let { nav.album(it) }
                            },
                            onDelete = {
                                c.runTask {
                                    c.api.deleteNotifications(listOf(n.id))
                                    inbox.reload(silent = true)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (deletingAll) {
        ConfirmDialog(
            stringResource(R.string.notifications_delete_all_title),
            stringResource(R.string.notifications_delete_all_body),
            stringResource(R.string.lock_delete),
            onDismiss = { deletingAll = false },
        ) {
            deletingAll = false
            val ids = entries.map { it.id }
            c.runTask(Strings.str(R.string.notifications_deleted)) {
                // The server takes a limited list per request.
                ids.chunked(200).forEach { c.api.deleteNotifications(it) }
                inbox.reload(silent = true)
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationDto, onClick: () -> Unit, onDelete: () -> Unit) {
    val (icon: ImageVector, tint) = when (n.level) {
        "error" -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        "warning" -> Icons.Outlined.Warning to MaterialTheme.colorScheme.tertiary
        "success" -> Icons.Outlined.CheckCircle to CtrlColors.AccentText
        else -> (if (n.type == "SystemMessage") Icons.Outlined.Info else Icons.Outlined.Notifications) to CtrlColors.Muted
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Gutter, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.unread) {
                        CtrlDot(CtrlColors.Accent)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        n.title.ifBlank { stringResource(R.string.notifications) },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!n.description.isNullOrBlank()) {
                    Text(n.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                (relativeTime(n.createdAt) ?: shortDate(n.createdAt))?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = CtrlColors.Muted)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.notifications_delete_one), tint = CtrlColors.Muted) }
        }
        CtrlDivider(Modifier.padding(start = Gutter))
        Spacer(Modifier.height(0.dp))
    }
}
