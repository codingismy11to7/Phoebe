package com.phoebe.app.ui

import com.phoebe.app.domain.CollectionFacet

data class CollectionMixSeed(
    val facet: CollectionFacet,
    val value: String,
)

sealed interface AppNavigationRequest {
    data object SignIn : AppNavigationRequest
    data object ServerPicker : AppNavigationRequest
    data object LibraryPicker : AppNavigationRequest
    data object Home : AppNavigationRequest
    data object Player : AppNavigationRequest
    data class PlaylistDetail(val playlistId: String) : AppNavigationRequest
}
