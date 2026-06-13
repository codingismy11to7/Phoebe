package com.phoebe.app.di

import com.phoebe.app.feature.history.PlayHistoryViewModel
import com.phoebe.app.feature.search.SearchResultsFactory
import com.phoebe.app.feature.search.SearchViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class RouteViewModelFactory(
    private val searchResultsFactory: SearchResultsFactory,
) {
    fun search(): SearchViewModel = SearchViewModel(searchResultsFactory)

    fun playHistory(): PlayHistoryViewModel = PlayHistoryViewModel()
}
