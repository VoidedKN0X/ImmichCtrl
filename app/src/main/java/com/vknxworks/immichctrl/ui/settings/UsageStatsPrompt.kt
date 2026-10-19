package com.vknxworks.immichctrl.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.immichctrl.container
import kotlinx.coroutines.launch

/** What's shared, in the words used by the first-run question and the Settings footer. */
const val USAGE_STATS_DESCRIPTION =
    "Which screens you open, whether you use backup, search, sharing and albums (with a rough number of " +
        "photos, never the exact one), the app version, Android version, phone model and language. Never your " +
        "server address, account, photos, album, people or place names, locations or what you search for. " +
        "Sent to VKNXWorks' own server, not to any analytics company."

/** Asked once, after signing in. Nothing is sent until the user says yes; they can change it in Settings. */
@Composable
fun UsageStatsPrompt() {
    val c = LocalContext.current.container
    if (!c.telemetry.available) return
    // The stored values, not the placeholder defaults, so the question doesn't flash for people who answered it.
    val stored by c.settings.stored.collectAsStateWithLifecycle(initialValue = null)
    val s = stored ?: return
    if (s.usageStatsAsked) return
    val scope = rememberCoroutineScope()
    fun answer(share: Boolean) {
        scope.launch { c.settings.update { it.copy(usageStats = share, usageStatsAsked = true) } }
    }
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Help improve ImmichCtrl?") },
        text = {
            Text(
                "Share anonymous usage stats so I can see which features people use and which Android " +
                    "versions to support.\n\n$USAGE_STATS_DESCRIPTION\n\nYou can change this any time in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text("Share stats") } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text("No thanks") } },
    )
}
