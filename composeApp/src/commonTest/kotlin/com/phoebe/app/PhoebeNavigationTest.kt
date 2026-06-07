package com.phoebe.app

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.RecentlyAddedKind
import com.phoebe.app.ui.BrowseSection
import com.phoebe.app.ui.PhoebeNavigator
import com.phoebe.app.ui.PhoebeRoute
import com.phoebe.app.ui.PhoebeRouteResolution
import com.phoebe.app.ui.decodePhoebeRouteBackStack
import com.phoebe.app.ui.encodePhoebeRouteBackStack
import com.phoebe.app.ui.phoebeRouteSerializersModule
import com.phoebe.app.ui.resolvePhoebeRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

class PhoebeNavigationTest {
    private val json = Json {
        serializersModule = phoebeRouteSerializersModule
        classDiscriminator = "type"
    }

    @Test
    fun serializesAllRouteVariantsThroughNavKeyModule() {
        val entry = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre)
        val routes = listOf(
            PhoebeRoute.SignIn,
            PhoebeRoute.ServerPicker,
            PhoebeRoute.LibraryPicker,
            PhoebeRoute.Browse(BrowseSection.Home),
            PhoebeRoute.Browse(BrowseSection.Search),
            PhoebeRoute.Collections(entry),
            PhoebeRoute.CollectionItems(entry, "Dream pop"),
            PhoebeRoute.ArtistDetail("artist-1"),
            PhoebeRoute.AlbumDetail("album-1"),
            PhoebeRoute.SongDetail("track-1"),
            PhoebeRoute.Lyrics("track-1"),
            PhoebeRoute.Lyrics(),
            PhoebeRoute.RecentlyAdded(RecentlyAddedKind.Songs),
            PhoebeRoute.PlayHistory(PlayHistoryKind.MostPlayed),
            PhoebeRoute.FavoritePlaylists,
            PhoebeRoute.FavoriteArtists,
            PhoebeRoute.FavoriteAlbums,
            PhoebeRoute.PlaylistDetail("playlist-1"),
            PhoebeRoute.Player,
        )

        routes.forEach { route ->
            val encoded = json.encodeToString(PolymorphicSerializer(NavKey::class), route)
            val decoded = json.decodeFromString(PolymorphicSerializer(NavKey::class), encoded)

            assertEquals(route, decoded)
        }
    }

    @Test
    fun recentlyAddedRouteRoundTripsThroughSaveableBackStackJson() {
        val backStack = NavBackStack<PhoebeRoute>(PhoebeRoute.Browse(BrowseSection.Home)).apply {
            add(PhoebeRoute.RecentlyAdded(RecentlyAddedKind.Songs))
        }
        val decoded = decodePhoebeRouteBackStack(encodePhoebeRouteBackStack(backStack))

        assertEquals(
            listOf(
                PhoebeRoute.Browse(BrowseSection.Home),
                PhoebeRoute.RecentlyAdded(RecentlyAddedKind.Songs),
            ),
            decoded.toList(),
        )
    }

    @Test
    fun browseRootReplacementKeepsSingleRootRoute() {
        val navigator = PhoebeNavigator(PhoebeRoute.SignIn)

        navigator.handle(AppNavigationRequest.Home)
        navigator.openBrowse(BrowseSection.Library)

        assertEquals(listOf(PhoebeRoute.Browse(BrowseSection.Library)), navigator.routes)
    }

    @Test
    fun homeRequestDoesNotResetActiveBrowseSection() {
        val navigator = PhoebeNavigator(PhoebeRoute.Browse())

        navigator.openBrowse(BrowseSection.Playlists)
        navigator.handle(AppNavigationRequest.Home)

        assertEquals(listOf(PhoebeRoute.Browse(BrowseSection.Playlists)), navigator.routes)
    }

    @Test
    fun homeRequestDoesNotClearActiveBrowseDetailStack() {
        val navigator = PhoebeNavigator(PhoebeRoute.Browse())

        navigator.openBrowse(BrowseSection.Playlists)
        navigator.open(PhoebeRoute.PlaylistDetail("playlist-1"))
        navigator.handle(AppNavigationRequest.Home)

        assertEquals(
            listOf(
                PhoebeRoute.Browse(BrowseSection.Playlists),
                PhoebeRoute.PlaylistDetail("playlist-1"),
            ),
            navigator.routes,
        )
    }

    @Test
    fun homeRequestStillLeavesSetupFlow() {
        val navigator = PhoebeNavigator(PhoebeRoute.SignIn)

        navigator.handle(AppNavigationRequest.ServerPicker)
        navigator.handle(AppNavigationRequest.LibraryPicker)
        navigator.handle(AppNavigationRequest.Home)

        assertEquals(listOf(PhoebeRoute.Browse()), navigator.routes)
    }

    @Test
    fun collectionDrillDownPopReturnsToCollectionsRoute() {
        val navigator = PhoebeNavigator(PhoebeRoute.Browse())
        val entry = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre)

        navigator.open(PhoebeRoute.Collections(entry))
        navigator.open(PhoebeRoute.CollectionItems(entry, "Rock"))
        navigator.pop()

        assertEquals(PhoebeRoute.Collections(entry), navigator.currentRoute)
        assertEquals(
            listOf(PhoebeRoute.Browse(), PhoebeRoute.Collections(entry)),
            navigator.routes,
        )
    }

    @Test
    fun detailPushAndPopReturnsToBrowseRoot() {
        val navigator = PhoebeNavigator(PhoebeRoute.Browse())

        navigator.open(PhoebeRoute.ArtistDetail("artist-1"))
        assertEquals(PhoebeRoute.ArtistDetail("artist-1"), navigator.currentRoute)

        navigator.pop()

        assertEquals(listOf(PhoebeRoute.Browse()), navigator.routes)
    }

    @Test
    fun playerOpenAndClosePreservesPreviousRoute() {
        val navigator = PhoebeNavigator(PhoebeRoute.Browse())

        navigator.open(PhoebeRoute.SongDetail("track-1"))
        navigator.openPlayer()
        navigator.openPlayer()

        assertEquals(
            listOf(PhoebeRoute.Browse(), PhoebeRoute.SongDetail("track-1"), PhoebeRoute.Player),
            navigator.routes,
        )

        navigator.pop()

        assertEquals(PhoebeRoute.SongDetail("track-1"), navigator.currentRoute)
    }

    @Test
    fun setupFlowBackBehaviorUsesOwnedBackStack() {
        val navigator = PhoebeNavigator(PhoebeRoute.SignIn)

        navigator.handle(AppNavigationRequest.ServerPicker)
        navigator.handle(AppNavigationRequest.LibraryPicker)
        navigator.pop()

        assertEquals(listOf(PhoebeRoute.SignIn, PhoebeRoute.ServerPicker), navigator.routes)
    }

    @Test
    fun missingDomainObjectResolvesToFallback() {
        val resolution = resolvePhoebeRoute(
            route = PhoebeRoute.ArtistDetail("missing-artist"),
            catalog = CatalogSnapshot(),
            currentTrack = null,
        )

        assertIs<PhoebeRouteResolution.Missing>(resolution)
    }
}
