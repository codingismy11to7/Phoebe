package com.phoebe.app.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.phoebe.app.domain.CatalogSnapshot
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class SearchUiState(
    val catalog: CatalogSnapshot = CatalogSnapshot(),
    val catalogRefreshing: Boolean = false,
    val query: String = "",
    val results: SearchUiResults = SearchResultsFactory.EmptyResults,
)

@Inject
class SearchResultsFactory {
    fun create(catalog: CatalogSnapshot, query: String): SearchUiResults =
        deriveSearchUiResults(catalog, query)

    companion object {
        val EmptyResults = SearchUiResults(
            tracks = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            topArtist = null,
            topAlbum = null,
            topTrack = null,
        )
    }
}

@Inject
class SearchViewModel(
    private val resultsFactory: SearchResultsFactory,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    fun updateCatalog(catalog: CatalogSnapshot, catalogRefreshing: Boolean) {
        mutableState.update { current ->
            current.copy(
                catalog = catalog,
                catalogRefreshing = catalogRefreshing,
                results = resultsFactory.create(catalog, current.query),
            )
        }
    }

    fun onQuery(query: String) {
        mutableState.update { current ->
            current.copy(
                query = query,
                results = resultsFactory.create(current.catalog, query),
            )
        }
    }

    fun routeState(): SearchDesktopRouteState {
        val current = state.value
        return SearchDesktopRouteState(
            catalog = current.catalog,
            catalogRefreshing = current.catalogRefreshing,
            query = current.query,
        )
    }
}
