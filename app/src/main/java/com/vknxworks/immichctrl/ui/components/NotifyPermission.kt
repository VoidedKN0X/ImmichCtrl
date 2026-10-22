package com.vknxworks.immichctrl.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.data.NotifyPermission
import com.vknxworks.immichctrl.data.security.expectAppReturn

/** Asking for notifications from a screen. */
class NotifyRequester internal constructor(
    /** Android's dialog while it still shows, otherwise ImmichCtrl's notification settings. */
    val request: () -> Unit,
    /** Android's dialog, but only if ImmichCtrl never showed it before (the one automatic ask). */
    val askOnce: () -> Unit,
)

/**
 * [onResult] gets whether notifications can be shown after Android's dialog (not called after a trip to
 * Settings; screens check again on resume with [rememberNotificationsAllowed]).
 */
@Composable
fun rememberNotifyPermission(onResult: (Boolean) -> Unit = {}): NotifyRequester {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        latest(NotifyPermission.allowed(context))
    }
    return remember(launcher) {
        fun showDialog() {
            NotifyPermission.markAsked(context)
            expectAppReturn(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NotifyRequester(
            request = {
                when {
                    NotifyPermission.allowed(context) -> latest(true)
                    NotifyPermission.dialogAvailable(context, context.findActivity()) -> showDialog()
                    else -> NotifyPermission.openSettings(context)
                }
            },
            askOnce = {
                if (NotifyPermission.permissionMissing(context) && !NotifyPermission.asked(context)) showDialog()
            },
        )
    }
}

/** Whether notifications are allowed, checked again every time the screen comes back (e.g. from Settings). */
@Composable
fun rememberNotificationsAllowed(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotifyPermission.allowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = NotifyPermission.allowed(context)
        onPauseOrDispose { }
    }
    return allowed to { v: Boolean -> allowed = v }
}

/** Shown while Android blocks ImmichCtrl's notifications, with a way to allow them. */
@Composable
fun NotificationsBlockedRow(subtitle: String, onAllow: () -> Unit) {
    GroupRow(
        stringResource(R.string.notifications_are_off),
        subtitle,
        icon = Icons.Outlined.NotificationsOff,
        iconTint = MaterialTheme.colorScheme.error,
        onClick = onAllow,
        trailing = { TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) } },
    )
}
