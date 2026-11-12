package com.vknxworks.immichctrl.ui.local

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vknxworks.immichctrl.R
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.data.backup.LocalLibrary.Companion.toAsset
import com.vknxworks.immichctrl.data.backup.LocalMedia
import com.vknxworks.immichctrl.ui.components.CtrlSquareButton
import com.vknxworks.immichctrl.ui.components.CtrlTopBar
import com.vknxworks.immichctrl.ui.components.Load
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.MessageView
import com.vknxworks.immichctrl.ui.components.ProfileButton
import com.vknxworks.immichctrl.ui.components.RemoteImage
import com.vknxworks.immichctrl.ui.components.countLabel
import com.vknxworks.immichctrl.ui.components.rememberLoader
import com.vknxworks.immichctrl.ui.nav.AddAccountRoute
import com.vknxworks.immichctrl.ui.nav.LocalAlbumRoute
import com.vknxworks.immichctrl.ui.nav.LocalNavigator
import com.vknxworks.immichctrl.ui.photos.SelectionContext
import com.vknxworks.immichctrl.ui.photos.SourceGrid
import com.vknxworks.immichctrl.ui.photos.rememberList
import com.vknxworks.immichctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * "Use without a server": ImmichCtrl as the phone's gallery, with no Immich account. The Photos and Albums tabs
 * show what's stored on the phone; the viewer, sharing and deleting work as for phone photos when signed in.
 */

private val MEDIA_PERMISSIONS = arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION)

/** Wordmark, "Sign in to Immich" and the profile picture (Settings), then the tab's title. */
@Composable
private fun PhoneHeader(title: String) {
    val nav = LocalNavigator.current
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 8.dp)) {
        CtrlTopBar(trailing = {
            CtrlSquareButton(Icons.Outlined.CloudUpload, stringResource(R.string.sign_in_to_immich), onClick = { nav.navigate(AddAccountRoute) })
            ProfileButton()
        })
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

/** Ask for the photos permission, or a short "allow access" screen when it isn't given. */
@Composable
private fun rememberMediaAccess(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val has = remember(tick) { LocalMedia.hasPermission(context) }
    return has to {
        com.vknxworks.immichctrl.data.security.expectAppReturn(context)
        launcher.launch(MEDIA_PERMISSIONS)
    }
}

@Composable
fun PhonePhotosScreen() {
    val context = LocalContext.current
    val (hasAccess, ask) = rememberMediaAccess()
    val source = rememberList("phone:all") {
        withContext(Dispatchers.IO) {
            if (!LocalMedia.hasPermission(context)) emptyList()
            else LocalMedia.items(context, null, emptySet(), true).sortedByDescending { it.takenMs }.map { it.toAsset() }.distinctBy { it.id }
        }
    }
    // Back from the camera or another app, or the permission was just given: look again.
    LaunchedEffect(hasAccess) { source.refresh(silent = true) }
    LifecycleResumeEffect(Unit) {
        source.refresh(silent = true)
        onPauseOrDispose { }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    SourceGrid(
        source = source,
        context = SelectionContext.LOCAL,
        topPadding = top,
        scrollerTopInset = 64.dp,
        selectable = false,
        empty = {
            if (!hasAccess) {
                MessageView(
                    Icons.Outlined.PhoneAndroid,
                    stringResource(R.string.allow_access_to_your_photos),
                    stringResource(R.string.phone_only_access_body),
                    actionLabel = stringResource(R.string.allow),
                    onAction = ask,
                )
            } else {
                MessageView(Icons.Outlined.PhotoLibrary, stringResource(R.string.no_photos_on_this_phone))
            }
        },
        header = {
            item(key = "phone-header") { PhoneHeader(stringResource(R.string.photos)) }
        },
    )
}

@Composable
fun PhoneAlbumsScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val (hasAccess, ask) = rememberMediaAccess()
    val albums = rememberLoader("phone-albums-$hasAccess") { withContext(Dispatchers.IO) { if (hasAccess) LocalMedia.albums(context) else emptyList() } }
    LifecycleResumeEffect(Unit) {
        albums.reload(silent = true)
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PhoneHeader(stringResource(R.string.albums))
        if (!hasAccess) {
            MessageView(
                Icons.Outlined.PhoneAndroid,
                stringResource(R.string.allow_access_to_your_photos),
                stringResource(R.string.phone_only_access_body),
                actionLabel = stringResource(R.string.allow),
                onAction = ask,
            )
            return@Column
        }
        when (val s = albums.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> MessageView(Icons.Outlined.PhotoLibrary, s.message)
            is Load.Ok -> if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.PhotoLibrary, stringResource(R.string.no_photos_on_this_phone))
            } else LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.value, key = { it.id }) { a ->
                    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable { nav.navigate(LocalAlbumRoute(a.id, a.name)) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
                            RemoteImage(a.coverUri?.toString(), Modifier.fillMaxSize(), placeholderIcon = Icons.Outlined.PhotoLibrary)
                        }
                        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                        Text(
                            countLabel(a.count, R.plurals.count_items),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
