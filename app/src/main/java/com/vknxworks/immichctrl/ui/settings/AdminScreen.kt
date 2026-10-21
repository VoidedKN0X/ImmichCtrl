package com.vknxworks.immichctrl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.api.QueueDto
import com.vknxworks.immichctrl.data.api.queueCommand
import com.vknxworks.immichctrl.data.api.queues
import com.vknxworks.immichctrl.data.api.serverAbout
import com.vknxworks.immichctrl.data.api.serverStats
import com.vknxworks.immichctrl.data.api.serverStorage
import com.vknxworks.immichctrl.data.api.setQueuePaused
import com.vknxworks.immichctrl.ui.components.ActionItem
import com.vknxworks.immichctrl.ui.components.Group
import com.vknxworks.immichctrl.ui.components.GroupRow
import com.vknxworks.immichctrl.ui.components.ScreenTopBar
import com.vknxworks.immichctrl.ui.components.SheetTitle
import com.vknxworks.immichctrl.ui.components.formatBytes
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.components.runTask
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

private val queueLabels = mapOf(
    "thumbnailGeneration" to Strings.str(R.string.thumbnails),
    "metadataExtraction" to Strings.str(R.string.metadata),
    "videoConversion" to Strings.str(R.string.video_conversion),
    "faceDetection" to Strings.str(R.string.face_detection),
    "facialRecognition" to Strings.str(R.string.facial_recognition),
    "smartSearch" to Strings.str(R.string.smart_search),
    "duplicateDetection" to Strings.str(R.string.duplicate_detection),
    "backgroundTask" to Strings.str(R.string.background_tasks),
    "storageTemplateMigration" to Strings.str(R.string.storage_template),
    "migration" to Strings.str(R.string.migration),
    "search" to Strings.str(R.string.search),
    "sidecar" to Strings.str(R.string.sidecar_files),
    "library" to Strings.str(R.string.external_libraries),
    "notifications" to Strings.str(R.string.notifications),
    "backupDatabase" to Strings.str(R.string.database_backup),
    "ocr" to Strings.str(R.string.text_recognition_ocr),
    "workflow" to Strings.str(R.string.workflows),
    "integrityCheck" to Strings.str(R.string.integrity_check),
    "editor" to Strings.str(R.string.editor),
)

/** For admins: what's on the server and the background jobs that process it. */
@Composable
fun AdminScreen() {
    val c = LocalContext.current.container
    val stats = rememberLoader("admin-stats") { c.api.serverStats() }
    val storage = rememberLoader("admin-storage") { c.api.serverStorage() }
    val about = rememberLoader("admin-about") { c.api.serverAbout() }
    val queues = rememberLoader("admin-queues") { c.api.queues() }
    var open by remember { mutableStateOf<QueueDto?>(null) }

    // Job counts change quickly; keep them fresh while the screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            queues.reload(silent = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.server_administration)) {
            IconButton(onClick = { stats.reload(silent = true); storage.reload(silent = true); queues.reload(silent = true) }) {
                Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh))
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            storage.value?.let { d ->
                Group(stringResource(R.string.disk)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.of_used_free, d.diskUse, d.diskSize, d.diskAvailable), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { (d.diskUsagePercentage / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            stats.value?.let { s ->
                Group(stringResource(R.string.library)) {
                    GroupRow("%,d photos".format(s.photos), formatBytes(s.usagePhotos), icon = Icons.Outlined.Photo)
                    GroupRow("%,d videos".format(s.videos), formatBytes(s.usageVideos), icon = Icons.Outlined.Videocam)
                    GroupRow(stringResource(R.string.total), formatBytes(s.usage), icon = Icons.Outlined.Storage)
                }
                if (s.usageByUser.isNotEmpty()) {
                    Group(stringResource(R.string.users)) {
                        s.usageByUser.sortedByDescending { it.usage }.forEach { u ->
                            GroupRow(
                                u.userName,
                                listOfNotNull(
                                    "%,d photos, %,d videos".format(u.photos, u.videos),
                                    u.quotaSizeInBytes?.let { Strings.str(R.string.usage_of_quota, formatBytes(u.usage), formatBytes(it)) } ?: formatBytes(u.usage),
                                ).joinToString(Strings.str(R.string.list_separator_wide)),
                                icon = Icons.Outlined.Person,
                            )
                        }
                    }
                }
            }
            Group(stringResource(R.string.jobs), footer = stringResource(R.string.tap_a_job_to_run_it_pause_it_or)) {
                val list = queues.value.orEmpty()
                if (list.isEmpty()) GroupRow(if (queues.value == null) stringResource(R.string.loading) else stringResource(R.string.no_job_queues))
                list.forEach { q ->
                    val st = q.statistics
                    GroupRow(
                        queueLabels[q.name] ?: q.name,
                        listOfNotNull(
                            if (q.isPaused) Strings.str(R.string.paused) else null,
                            if (st.active > 0) Strings.str(R.string.running, st.active) else null,
                            if (st.waiting > 0) Strings.str(R.string.waiting, st.waiting) else null,
                            if (st.delayed > 0) Strings.str(R.string.delayed, st.delayed) else null,
                            if (st.failed > 0) Strings.str(R.string.failed, st.failed) else null,
                        ).joinToString(Strings.str(R.string.list_separator_wide)).ifEmpty { Strings.str(R.string.idle) },
                        trailing = { Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { open = q },
                    )
                }
            }
            about.value?.let { a ->
                Group(stringResource(R.string.server)) {
                    GroupRow(stringResource(R.string.immich_2, a.version), if (a.licensed) stringResource(R.string.licensed) else null, icon = Icons.Outlined.Info)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    open?.let { q ->
        val label = queueLabels[q.name] ?: q.name
        ModalBottomSheet(onDismissRequest = { open = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                SheetTitle(label)
                fun act(done: String, block: suspend () -> Unit) {
                    open = null
                    c.runTask(done) { block(); queues.reload(silent = true) }
                }
                ActionItem(Icons.Outlined.Refresh, stringResource(R.string.run_for_what_s_missing)) { act(Strings.str(R.string.started, label)) { c.api.queueCommand(q.name, "start", force = false) } }
                ActionItem(Icons.Outlined.Refresh, stringResource(R.string.run_for_everything)) { act(Strings.str(R.string.started_for_everything, label)) { c.api.queueCommand(q.name, "start", force = true) } }
                if (q.isPaused) {
                    ActionItem(Icons.Outlined.Refresh, stringResource(R.string.resume)) { act(Strings.str(R.string.resumed, label)) { c.api.setQueuePaused(q.name, false) } }
                } else {
                    ActionItem(Icons.Outlined.Refresh, stringResource(R.string.pause)) { act(Strings.str(R.string.paused_2, label)) { c.api.setQueuePaused(q.name, true) } }
                }
                if (q.statistics.failed > 0) {
                    ActionItem(Icons.Outlined.Refresh, stringResource(R.string.clear_failed)) { act(Strings.str(R.string.cleared)) { c.api.queueCommand(q.name, "clear-failed") } }
                }
            }
        }
    }
}
