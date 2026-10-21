package com.vknxworks.immichctrl.data.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/**
 * Decides whether the window gets FLAG_SECURE (blank screenshots, screen recordings and casting of
 * the screen). It's on while something private is showing: the locked folder (and photos opened
 * from it) and the app-lock cover. The Recents thumbnail is always blank (see MainActivity).
 */
object ScreenPrivacy {
    private var holders by mutableIntStateOf(0)

    val secure: Boolean get() = holders > 0

    fun acquire() { holders++ }
    fun release() { holders = (holders - 1).coerceAtLeast(0) }
}

/** Keeps FLAG_SECURE on for as long as the calling screen is shown. */
@Composable
fun SecureScreen() {
    DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}

/** Copies text marked as sensitive, so Android hides it from the clipboard preview and keyboards' history. */
fun copySensitive(context: Context, text: String, label: String = Strings.str(R.string.app_name)) {
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/**
 * Whether a URI another app handed us is safe to read: only content:// from another app's provider.
 * file:// URIs and our own FileProvider would make the app read (and upload or show) its own
 * private files on another app's behalf.
 */
fun isForeignContentUri(context: Context, uri: Uri): Boolean {
    if (!uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return false
    // "content://0@<authority>" still reaches <authority> (the number is a user id), so look past it.
    val authority = uri.authority?.substringAfterLast('@')?.takeIf { it.isNotBlank() } ?: return false
    if (authority.startsWith(context.packageName)) return false
    // And whatever its name, a provider that belongs to this app is never "another app's".
    val owner = runCatching { context.packageManager.resolveContentProvider(authority, android.content.pm.PackageManager.ComponentInfoFlags.of(0))?.packageName }.getOrNull()
    return owner != context.packageName
}
