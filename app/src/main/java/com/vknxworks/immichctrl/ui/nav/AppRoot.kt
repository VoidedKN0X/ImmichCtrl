package com.vknxworks.immichctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.immichctrl.container
import com.vknxworks.immichctrl.ui.auth.SignInScreen
import com.vknxworks.immichctrl.ui.backup.BackupScreen
import com.vknxworks.immichctrl.ui.components.LoadingView
import com.vknxworks.immichctrl.ui.components.LocalBottomClearance
import com.vknxworks.immichctrl.ui.components.LocalHazeState
import com.vknxworks.immichctrl.ui.library.AlbumScreen
import com.vknxworks.immichctrl.ui.library.AlbumsScreen
import com.vknxworks.immichctrl.ui.library.CollectionScreen
import com.vknxworks.immichctrl.ui.library.FoldersScreen
import com.vknxworks.immichctrl.ui.library.LibraryScreen
import com.vknxworks.immichctrl.ui.library.LockedScreen
import com.vknxworks.immichctrl.ui.library.MapScreen
import com.vknxworks.immichctrl.ui.library.PartnerScreen
import com.vknxworks.immichctrl.ui.library.PartnersScreen
import com.vknxworks.immichctrl.ui.library.PeopleScreen
import com.vknxworks.immichctrl.ui.library.PersonScreen
import com.vknxworks.immichctrl.ui.library.PickAssetsScreen
import com.vknxworks.immichctrl.ui.library.PlaceScreen
import com.vknxworks.immichctrl.ui.library.PlacesScreen
import com.vknxworks.immichctrl.ui.library.SharedLinksScreen
import com.vknxworks.immichctrl.ui.library.TagScreen
import com.vknxworks.immichctrl.ui.library.TagsScreen
import com.vknxworks.immichctrl.ui.photos.PhotosScreen
import com.vknxworks.immichctrl.ui.library.DuplicatesScreen
import com.vknxworks.immichctrl.ui.library.OfflineAlbumScreen
import com.vknxworks.immichctrl.ui.local.FreeUpSpaceScreen
import com.vknxworks.immichctrl.ui.local.LocalAlbumScreen
import com.vknxworks.immichctrl.ui.local.LocalAlbumsScreen
import com.vknxworks.immichctrl.ui.settings.LogsScreen
import com.vknxworks.immichctrl.ui.settings.NetworkingScreen
import com.vknxworks.immichctrl.ui.viewer.EditorScreen
import com.vknxworks.immichctrl.ui.viewer.ExternalViewScreen
import com.vknxworks.immichctrl.ui.viewer.MemoryScreen
import com.vknxworks.immichctrl.ui.viewer.SlideshowScreen
import com.vknxworks.immichctrl.ui.components.findActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.immichctrl.ui.components.PillButton
import com.vknxworks.immichctrl.ui.search.SearchScreen
import com.vknxworks.immichctrl.ui.settings.AccountsScreen
import com.vknxworks.immichctrl.ui.settings.AdminScreen
import com.vknxworks.immichctrl.ui.settings.SettingsScreen
import com.vknxworks.immichctrl.ui.settings.YouScreen
import com.vknxworks.immichctrl.ui.viewer.ViewerScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val ready by c.sessions.ready.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !ready -> LoadingView()
            active == null -> SignInScreen(onDone = {}, onCancel = null)
            else -> key(active!!.account.key) { MainScaffold() }
        }
        val settings by c.settings.settings.collectAsStateWithLifecycle()
        val locked by c.appLock.locked.collectAsStateWithLifecycle()
        val showLock = ready && active != null && settings.appLock && locked
        if (showLock) LockScreen()

        // Photos another app shared to ImmichCtrl are only uploaded once the user says so.
        val pending by c.pendingUploads.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val uploads = pending
        if (ready && !showLock && !uploads.isNullOrEmpty()) {
            val acc = active?.account
            if (acc == null) {
                LaunchedEffect(uploads) {
                    c.pendingUploads.value = null
                    android.widget.Toast.makeText(context, "Sign in to an Immich server first", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                com.vknxworks.immichctrl.ui.components.ConfirmDialog(
                    "Upload to Immich?",
                    "${com.vknxworks.immichctrl.ui.components.countLabel(uploads.size, "item")} shared from another app will be uploaded to ${acc.name.ifBlank { acc.email }} on ${acc.serverName}.",
                    "Upload",
                    destructive = false,
                    onDismiss = { c.pendingUploads.value = null },
                ) {
                    c.pendingUploads.value = null
                    (context.findActivity() as? com.vknxworks.immichctrl.MainActivity)?.uploadShared(uploads)
                }
            }
        }
    }
}

/** Covers the app until the phone's fingerprint, face or screen lock is given. */
@Composable
private fun LockScreen() {
    val context = LocalContext.current
    val c = context.container
    fun unlock() { context.findActivity()?.let { c.appLock.unlockWith(it) } }
    LaunchedEffect(Unit) { unlock() }
    // Nothing behind the cover may end up in a screenshot or screen recording.
    com.vknxworks.immichctrl.data.security.SecureScreen()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so nothing underneath can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("ImmichCtrl is locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            PillButton("Unlock", onClick = ::unlock)
        }
    }
}

private data class TopDest(val route: Any, val item: TabItem)

private val topDestinations = listOf(
    TopDest(PhotosRoute, TabItem("Photos", Icons.Outlined.Photo, Icons.Filled.Photo)),
    TopDest(SearchRoute, TabItem("Search", Icons.Outlined.Search, Icons.Filled.Search)),
    TopDest(LibraryRoute, TabItem("Library", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary)),
    TopDest(YouRoute, TabItem("You", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }
    val inViewer = dest?.hasRoute(ViewerRoute::class) == true
    val hideTabs = remember { mutableStateOf(false) }

    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val readOnly = settings.readOnly

    // Widgets, notifications and other apps ask for something to be opened.
    val openRequest by c.openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        val req = openRequest ?: return@LaunchedEffect
        c.openRequests.value = null
        when {
            req.startsWith("memory:") -> navigator.navigate(MemoryRoute(req.removePrefix("memory:")))
            req.startsWith("asset:") -> req.removePrefix("asset:").let { id -> navigator.navigate(ViewerRoute("open:$id", 0, id)) }
            req == "admin" -> if (c.sessions.active.value?.account?.isAdmin == true && !readOnly) navigator.navigate(AdminRoute)
            req == "backup" -> if (!readOnly) navigator.navigate(BackupRoute)
        }
    }
    val external by c.externalUris.collectAsStateWithLifecycle()
    LaunchedEffect(external) {
        if (!external.isNullOrEmpty() && dest?.hasRoute(ExternalViewRoute::class) != true) navigator.navigate(ExternalViewRoute)
    }
    // Read-only mode keeps to the Photos tab.
    LaunchedEffect(readOnly, selectedIndex) { if (readOnly && selectedIndex > 0) navController.navigate(PhotosRoute) { popUpTo(0) } }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalHideTabs provides hideTabs) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp && !inViewer && !readOnly) {
                // Tablets and unfolded foldables: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, top ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) top.item.selectedIcon else top.item.icon, contentDescription = top.item.label) },
                                label = { Text(top.item.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(
                    navController = navController,
                    showTabs = selectedIndex >= 0 && !hideTabs.value && !readOnly,
                    selectedIndex = selectedIndex,
                    onSelect = ::selectTab,
                )
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under a floating glass island.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    showTabs: Boolean,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomClearance provides if (selectedIndex >= 0) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                AppNavHost(navController)
            }
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { if (it.height > 0) overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                GlassTabBar(
                    items = topDestinations.map { it.item },
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = PhotosRoute) {
        composable<PhotosRoute> { PhotosScreen() }
        composable<SearchRoute> { SearchScreen() }
        composable<LibraryRoute> { LibraryScreen() }
        composable<YouRoute> { YouScreen() }

        composable<ViewerRoute> {
            val r = it.toRoute<ViewerRoute>()
            ViewerScreen(r.source, r.index, r.assetId)
        }
        composable<AlbumsRoute> { AlbumsScreen(it.toRoute<AlbumsRoute>().shared) }
        composable<AlbumRoute> { AlbumScreen(it.toRoute<AlbumRoute>().id) }
        composable<PickAssetsRoute> { PickAssetsScreen(it.toRoute<PickAssetsRoute>().albumId) }
        composable<PeopleRoute> { PeopleScreen() }
        composable<PersonRoute> { PersonScreen(it.toRoute<PersonRoute>().id) }
        composable<PlacesRoute> { PlacesScreen() }
        composable<MapRoute> { MapScreen() }
        composable<PlaceRoute> { PlaceScreen(it.toRoute<PlaceRoute>().city) }
        composable<CollectionRoute> { CollectionScreen(it.toRoute<CollectionRoute>().kind) }
        composable<LockedRoute> { LockedScreen() }
        composable<SharedLinksRoute> { SharedLinksScreen() }
        composable<PartnersRoute> { PartnersScreen() }
        composable<PartnerRoute> {
            val r = it.toRoute<PartnerRoute>()
            PartnerScreen(r.userId, r.name)
        }
        composable<TagsRoute> { TagsScreen() }
        composable<TagRoute> {
            val r = it.toRoute<TagRoute>()
            TagScreen(r.id, r.name)
        }
        composable<FoldersRoute> { FoldersScreen(it.toRoute<FoldersRoute>().path) }
        composable<MemoryRoute> { MemoryScreen(it.toRoute<MemoryRoute>().id) }
        composable<BackupRoute> { BackupScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<AccountsRoute> { AccountsScreen() }
        composable<AdminRoute> { AdminScreen() }
        composable<LocalAlbumsRoute> { LocalAlbumsScreen() }
        composable<LocalAlbumRoute> {
            val r = it.toRoute<LocalAlbumRoute>()
            LocalAlbumScreen(r.bucketId, r.name)
        }
        composable<FreeUpSpaceRoute> { FreeUpSpaceScreen() }
        composable<ExternalViewRoute> { ExternalViewScreen() }
        composable<EditorRoute> { EditorScreen(it.toRoute<EditorRoute>().assetId) }
        composable<SlideshowRoute> {
            val r = it.toRoute<SlideshowRoute>()
            SlideshowScreen(r.source, r.index)
        }
        composable<DuplicatesRoute> { DuplicatesScreen() }
        composable<OfflineAlbumRoute> { OfflineAlbumScreen(it.toRoute<OfflineAlbumRoute>().albumId) }
        composable<NetworkingRoute> { NetworkingScreen() }
        composable<LogsRoute> { LogsScreen() }
        composable<AddAccountRoute> {
            SignInScreen(onDone = { navController.popBackStack(PhotosRoute, false) }, onCancel = { navController.popBackStack() })
        }
    }
}
