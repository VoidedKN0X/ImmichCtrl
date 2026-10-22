package com.vknxworks.immichctrl.data

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.immichctrl.data.security.expectAppReturn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android 13's notification permission, which backup and upload progress, offline album downloads and
 * the admin server alerts all need. ImmichCtrl asks for it once by itself; after that only when you
 * turn something on or tap Allow.
 */
object NotifyPermission {
    private const val PREFS = "notify_permission"
    private const val KEY_ASKED = "asked"

    private val _wanted = MutableStateFlow(0)

    /** Goes up whenever something starts that shows a notification (an upload, an offline album …). */
    val wanted: StateFlow<Int> = _wanted.asStateFlow()

    /** Something that shows a notification is starting: the app asks for the permission if it never did. */
    fun want() {
        _wanted.value++
    }

    /** ImmichCtrl may show notifications: the permission is granted and they aren't turned off. */
    fun allowed(context: Context): Boolean =
        !permissionMissing(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The Android 13+ runtime permission hasn't been granted. */
    fun permissionMissing(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Android's dialog was shown once, so ImmichCtrl never shows it again on its own. */
    fun asked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** Android still shows its dialog: the first time, and once more after a "Don't allow". */
    fun dialogAvailable(context: Context, activity: Activity?): Boolean =
        permissionMissing(context) && (
            !asked(context) ||
                activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
            )

    /** ImmichCtrl's page in Android's notification settings. */
    fun openSettings(context: Context) {
        expectAppReturn(context)
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
