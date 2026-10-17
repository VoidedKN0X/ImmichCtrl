package com.vknxworks.immichctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object PhotosRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object YouRoute

/** Full-screen viewer, paging through the grid it was opened from ([source] key, see Sources). */
@Serializable data class ViewerRoute(val source: String, val index: Int, val assetId: String)

@Serializable data class AlbumsRoute(val shared: Boolean)
@Serializable data class AlbumRoute(val id: String)
@Serializable data class PickAssetsRoute(val albumId: String)
@Serializable data object PeopleRoute
@Serializable data class PersonRoute(val id: String)
@Serializable data object PlacesRoute
@Serializable data object MapRoute
@Serializable data class PlaceRoute(val city: String)

/** kind: favorites, archive, trash, videos, recent */
@Serializable data class CollectionRoute(val kind: String)
@Serializable data object LockedRoute
@Serializable data object SharedLinksRoute
@Serializable data object PartnersRoute
@Serializable data class PartnerRoute(val userId: String, val name: String)
@Serializable data object TagsRoute
@Serializable data class TagRoute(val id: String, val name: String)
@Serializable data class FoldersRoute(val path: String = "")
@Serializable data class MemoryRoute(val id: String)

@Serializable data object BackupRoute
@Serializable data object SettingsRoute
@Serializable data object AccountsRoute
@Serializable data object AddAccountRoute
@Serializable data object AdminRoute
