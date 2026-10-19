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

private val queueLabels = mapOf(
    "thumbnailGeneration" to "Thumbnails",
    "metadataExtraction" to "Metadata",
    "videoConversion" to "Video conversion",
    "faceDetection" to "Face detection",
    "facialRecognition" to "Facial recognition",
    "smartSearch" to "Smart search",
    "duplicateDetection" to "Duplicate detection",
    "backgroundTask" to "Background tasks",
    "storageTemplateMigration" to "Storage template",
    "migration" to "Migration",
    "search" to "Search",
    "sidecar" to "Sidecar files",
    "library" to "External libraries",
    "notifications" to "Notifications",
    "backupDatabase" to "Database backup",
    "ocr" to "Text recognition (OCR)",
    "workflow" to "Workflows",
    "integrityCheck" to "Integrity check",
    "editor" to "Editor",
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
        ScreenTopBar("Server administration") {
            IconButton(onClick = { stats.reload(silent = true); storage.reload(silent = true); queues.reload(silent = true) }) {
                Icon(Icons.Outlined.Refresh, "Refresh")
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            storage.value?.let { d ->
                Group("Disk") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("${d.diskUse} of ${d.diskSize} used  ·  ${d.diskAvailable} free", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { (d.diskUsagePercentage / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            stats.value?.let { s ->
                Group("Library") {
                    GroupRow("%,d photos".format(s.photos), formatBytes(s.usagePhotos), icon = Icons.Outlined.Photo)
                    GroupRow("%,d videos".format(s.videos), formatBytes(s.usageVideos), icon = Icons.Outlined.Videocam)
                    GroupRow("Total", formatBytes(s.usage), icon = Icons.Outlined.Storage)
                }
                if (s.usageByUser.isNotEmpty()) {
                    Group("Users") {
                        s.usageByUser.sortedByDescending { it.usage }.forEach { u ->
                            GroupRow(
                                u.userName,
                                listOfNotNull(
                                    "%,d photos, %,d videos".format(u.photos, u.videos),
                                    formatBytes(u.usage) + (u.quotaSizeInBytes?.let { " of ${formatBytes(it)}" } ?: ""),
                                ).joinToString("  ·  "),
                                icon = Icons.Outlined.Person,
                            )
                        }
                    }
                }
            }
            Group("Jobs", footer = "Tap a job to run it, pause it or clear failures.") {
                val list = queues.value.orEmpty()
                if (list.isEmpty()) GroupRow(if (queues.value == null) "Loading…" else "No job queues")
                list.forEach { q ->
                    val st = q.statistics
                    GroupRow(
                        queueLabels[q.name] ?: q.name,
                        listOfNotNull(
                            if (q.isPaused) "Paused" else null,
                            if (st.active > 0) "${st.active} running" else null,
                            if (st.waiting > 0) "${st.waiting} waiting" else null,
                            if (st.delayed > 0) "${st.delayed} delayed" else null,
                            if (st.failed > 0) "${st.failed} failed" else null,
                        ).joinToString("  ·  ").ifEmpty { "Idle" },
                        trailing = { Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { open = q },
                    )
                }
            }
            about.value?.let { a ->
                Group("Server") {
                    GroupRow("Immich ${a.version}", if (a.licensed) "Licensed" else null, icon = Icons.Outlined.Info)
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
                ActionItem(Icons.Outlined.Refresh, "Run for what's missing") { act("$label started") { c.api.queueCommand(q.name, "start", force = false) } }
                ActionItem(Icons.Outlined.Refresh, "Run for everything") { act("$label started for everything") { c.api.queueCommand(q.name, "start", force = true) } }
                if (q.isPaused) {
                    ActionItem(Icons.Outlined.Refresh, "Resume") { act("$label resumed") { c.api.setQueuePaused(q.name, false) } }
                } else {
                    ActionItem(Icons.Outlined.Refresh, "Pause") { act("$label paused") { c.api.setQueuePaused(q.name, true) } }
                }
                if (q.statistics.failed > 0) {
                    ActionItem(Icons.Outlined.Refresh, "Clear failed") { act("Cleared") { c.api.queueCommand(q.name, "clear-failed") } }
                }
            }
        }
    }
}
