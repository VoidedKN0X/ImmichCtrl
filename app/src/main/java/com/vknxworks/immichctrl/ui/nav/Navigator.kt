package com.vknxworks.immichctrl.ui.nav

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** One place that knows where things open. */
class Navigator(val nav: NavHostController) {

    fun navigate(route: Any) = nav.navigate(route) { launchSingleTop = true }

    fun back() {
        nav.popBackStack()
    }

    fun viewer(source: String, index: Int, assetId: String) = navigate(ViewerRoute(source, index, assetId))

    fun album(id: String?) {
        if (!id.isNullOrBlank()) navigate(AlbumRoute(id))
    }

    fun person(id: String?) {
        if (!id.isNullOrBlank()) navigate(PersonRoute(id))
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

/** Screens set this while they show their own bottom bar (selection actions) in place of the tabs. */
val LocalHideTabs = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }
