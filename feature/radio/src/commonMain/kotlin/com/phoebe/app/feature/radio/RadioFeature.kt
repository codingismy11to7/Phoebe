package com.phoebe.app.feature.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phoebe.app.domain.RadioCountry
import com.phoebe.app.domain.RadioDirectoryState
import com.phoebe.app.domain.RadioStation
import com.phoebe.app.domain.RadioStationSearchQuery
import com.phoebe.app.domain.RadioStationSource
import com.phoebe.app.feature.library.LibraryScrollIndexEntry
import com.phoebe.app.feature.library.LibraryScrollbarState
import com.phoebe.app.feature.library.LibrarySectionIndex
import com.phoebe.app.feature.library.LibrarySectionIndexMode
import com.phoebe.app.feature.library.rememberLibrarySectionIndexSelectionDispatcher
import com.phoebe.app.ui.ArtworkImage
import com.phoebe.app.ui.PhoebeIcon
import com.phoebe.app.ui.PhoebeIconView
import com.phoebe.app.ui.PhoebeUi
import com.phoebe.app.ui.SectionLabel
import kotlinx.coroutines.launch

@Immutable
data class RadioRouteState(
    val directory: RadioDirectoryState,
    val startingStationIds: Set<String> = emptySet(),
)

@Immutable
class RadioRouteActions(
    val onSearch: (RadioStationSearchQuery) -> Unit,
    val onLoadMore: () -> Unit,
    val onRefreshPopular: () -> Unit,
    val onPlay: (RadioStation) -> Unit,
    val onAddManualStation: (String, String) -> Unit,
    val onUpdateManualStation: (RadioStation, String, String) -> Unit,
    val onDeleteManualStation: (RadioStation) -> Unit,
)

@Composable
fun RadioRoute(
    state: RadioRouteState,
    actions: RadioRouteActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 36.dp, vertical = 28.dp),
    sectionIndexMode: LibrarySectionIndexMode = LibrarySectionIndexMode.DesktopScrollbar,
) {
    var queryText by remember(state.directory.searchQuery.text) { mutableStateOf(state.directory.searchQuery.text) }
    var editingStation by remember { mutableStateOf<RadioStation?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var countriesExpanded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val indexScrollDispatcher = rememberLibrarySectionIndexSelectionDispatcher()
    val searchText = queryText.trim()
    val hasTextSearch = searchText.isNotBlank()
    val recommendedStations = remember(state.directory.recommendedStations, searchText) {
        state.directory.recommendedStations.filter { station ->
            searchText.isBlank() || station.matchesSearch(searchText)
        }
    }
    val recommendedByCategory = remember(recommendedStations) {
        recommendedStations.groupBy { it.category ?: "Recommended Streams" }
    }
    val activeDirectoryStations = remember(state.directory.directoryStations, searchText) {
        if (searchText.isBlank()) {
            state.directory.directoryStations
        } else {
            state.directory.directoryStations.filter { it.matchesSearch(searchText) }
        }
    }
    val showCountries = state.directory.searchQuery.isBlank &&
        !hasTextSearch &&
        (state.directory.loading || state.directory.countries.isNotEmpty())
    val showRecommended = state.directory.searchQuery.isBlank || hasTextSearch
    val keepSectionIndexLabelsVisible = false
    val scrollbarState by remember(listState) {
        derivedStateOf {
            LibraryScrollbarState(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                visibleItemsCount = listState.layoutInfo.visibleItemsInfo.size,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
            )
        }
    }
    val revealIndex by remember(listState) {
        derivedStateOf {
            listState.isScrollInProgress
        }
    }
    val shouldLoadMore by remember(
        listState,
        state.directory.searchQuery,
        state.directory.loading,
        state.directory.loadingMore,
        state.directory.canLoadMore,
        activeDirectoryStations.size,
    ) {
        derivedStateOf {
            if (state.directory.searchQuery.isBlank ||
                state.directory.loading ||
                state.directory.loadingMore ||
                !state.directory.canLoadMore
            ) {
                false
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                totalItems > 0 && lastVisible >= totalItems - 8
            }
        }
    }
    val sectionAnchors = remember(
        state.directory.manualStations,
        state.directory.countries,
        countriesExpanded,
        showCountries,
        state.directory.searchQuery,
        hasTextSearch,
        activeDirectoryStations,
        state.directory.loadingMore,
        showRecommended,
        recommendedByCategory,
    ) {
        buildList {
            var anchorItemIndex = 0
            add("Top" to anchorItemIndex)
            anchorItemIndex += 2

            if (state.directory.manualStations.isNotEmpty()) {
                add("My" to anchorItemIndex)
                anchorItemIndex += 1 + state.directory.manualStations.size
            }

            if (showCountries) {
                add("Countries" to anchorItemIndex)
                anchorItemIndex += 1
                if (countriesExpanded) {
                    anchorItemIndex += state.directory.countries.size
                }
            }

            if (!state.directory.searchQuery.isBlank || hasTextSearch) {
                add("Results" to anchorItemIndex)
                anchorItemIndex += 1
            }
            if (state.directory.errorMessage != null && !state.directory.searchQuery.isBlank) {
                anchorItemIndex += 1
            }
            if ((!state.directory.searchQuery.isBlank || hasTextSearch) && !state.directory.loading && activeDirectoryStations.isEmpty() && (!showRecommended || recommendedStations.isEmpty())) {
                anchorItemIndex += 1
            }
            anchorItemIndex += activeDirectoryStations.size
            if (state.directory.loadingMore) {
                anchorItemIndex += 1
            }

            if (showRecommended && recommendedStations.isNotEmpty()) {
                recommendedByCategory.forEach { (category, stations) ->
                    add(category to anchorItemIndex)
                    anchorItemIndex += 1 + stations.size
                }
            }
        }
    }

    LaunchedEffect(shouldLoadMore, state.directory.searchQuery, activeDirectoryStations.size) {
        if (shouldLoadMore) actions.onLoadMore()
    }

    LaunchedEffect(state.directory.countries.isEmpty()) {
        if (state.directory.countries.isEmpty()) {
            actions.onRefreshPopular()
        }
    }

    Box(modifier.fillMaxSize().background(PhoebeUi.shellTop)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (sectionIndexMode != LibrarySectionIndexMode.MobileScrollbar) {
                item(contentType = "header") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Radio", color = PhoebeUi.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Text("Browse internet stations and save your own streams", color = PhoebeUi.secondaryText, fontSize = 13.sp)
                        }
                        FilledTonalButton(onClick = { showAddDialog = true }) {
                            PhoebeIconView(PhoebeIcon.Plus, tint = PhoebeUi.primaryText, modifier = Modifier.size(15.dp))
                            Text("Add", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            item(contentType = "search") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = "Search stations",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                actions.onSearch(
                                    RadioStationSearchQuery(
                                        text = queryText,
                                    ),
                                )
                            },
                        ) {
                            PhoebeIconView(PhoebeIcon.Search, tint = PhoebeUi.primaryText, modifier = Modifier.size(15.dp))
                            Text("Search", modifier = Modifier.padding(start = 8.dp))
                        }
                        if (sectionIndexMode == LibrarySectionIndexMode.MobileScrollbar) {
                            FilledTonalButton(onClick = { showAddDialog = true }) {
                                PhoebeIconView(PhoebeIcon.Plus, tint = PhoebeUi.primaryText, modifier = Modifier.size(15.dp))
                                Text("Add", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (state.directory.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PhoebeUi.accentLight)
                        }
                    }
                }
            }

            if (state.directory.manualStations.isNotEmpty()) {
                item(contentType = "manual-label") { SectionLabel("MY STATIONS", PhoebeUi.accentLight) }
                items(state.directory.manualStations, key = { it.id }, contentType = { "manual-station" }) { station ->
                    RadioStationRow(
                        station = station,
                        starting = station.id in state.startingStationIds,
                        onPlay = { actions.onPlay(station) },
                        onEdit = { editingStation = station },
                        onDelete = { actions.onDeleteManualStation(station) },
                    )
                }
            }

            if (showCountries) {
                item(contentType = "country-label") {
                    RadioCollapsibleSectionLabel(
                        label = "BROWSE BY COUNTRY",
                        expanded = countriesExpanded,
                        onClick = { countriesExpanded = !countriesExpanded },
                    )
                }
                if (countriesExpanded) {
                    items(state.directory.countries, key = { it.code }, contentType = { "country" }) { country ->
                        RadioCountryRow(
                            country = country,
                            onClick = {
                                queryText = ""
                                actions.onSearch(RadioStationSearchQuery(countryCode = country.code))
                            },
                        )
                    }
                }
            }

            if (!state.directory.searchQuery.isBlank || hasTextSearch) {
                item(contentType = "directory-label") {
                    SectionLabel("RESULTS", PhoebeUi.accentLight)
                }
            }
            state.directory.errorMessage?.takeUnless { state.directory.searchQuery.isBlank }?.let { message ->
                item(contentType = "error") {
                    Text(message, color = PhoebeUi.secondaryText, fontSize = 13.sp)
                }
            }
            if ((!state.directory.searchQuery.isBlank || hasTextSearch) && !state.directory.loading && activeDirectoryStations.isEmpty() && (!showRecommended || recommendedStations.isEmpty())) {
                item(contentType = "empty") {
                    Text("No stations found.", color = PhoebeUi.mutedText, fontSize = 13.sp)
                }
            }
            items(activeDirectoryStations, key = { it.id }, contentType = { "directory-station" }) { station ->
                RadioStationRow(
                    station = station,
                    starting = station.id in state.startingStationIds,
                    onPlay = { actions.onPlay(station) },
                    onEdit = null,
                    onDelete = null,
                )
            }
            if (state.directory.loadingMore) {
                item(contentType = "loading-more") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PhoebeUi.accentLight)
                    }
                }
            }

            if (showRecommended && recommendedStations.isNotEmpty()) {
                recommendedByCategory.forEach { (category, stations) ->
                    item(contentType = "recommended-label-$category") {
                        SectionLabel(category.uppercase(), PhoebeUi.accentLight)
                    }
                    items(stations, key = { it.id }, contentType = { "recommended-station" }) { station ->
                        RadioStationRow(
                            station = station,
                            starting = station.id in state.startingStationIds,
                            onPlay = { actions.onPlay(station) },
                            onEdit = null,
                            onDelete = null,
                        )
                    }
                }
            }
        }

        if (sectionAnchors.size > 1) {
            LibrarySectionIndex(
                entries = sectionAnchors.map { (label, index) ->
                    LibraryScrollIndexEntry(label = label, itemIndex = index)
                },
                onEntrySelected = { entry ->
                    indexScrollDispatcher.launch(scrollScope, key = entry.itemIndex) {
                        listState.scrollToItem(entry.itemIndex)
                    }
                },
                mode = sectionIndexMode,
                revealSignal = revealIndex,
                keepLabelsVisible = keepSectionIndexLabelsVisible,
                scrollbarState = scrollbarState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        top = contentPadding.calculateTopPadding() + 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
            )
        }
    }

    if (showAddDialog) {
        ManualStationDialog(
            title = "Add station",
            station = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, streamUrl ->
                actions.onAddManualStation(name, streamUrl)
                showAddDialog = false
            },
        )
    }
    editingStation?.let { station ->
        ManualStationDialog(
            title = "Edit station",
            station = station,
            onDismiss = { editingStation = null },
            onSave = { name, streamUrl ->
                actions.onUpdateManualStation(station, name, streamUrl)
                editingStation = null
            },
        )
    }
}

@Composable
private fun RadioCollapsibleSectionLabel(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label, PhoebeUi.accentLight)
        Text(
            text = if (expanded) "Hide" else "Show",
            color = PhoebeUi.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RadioCountryRow(
    country: RadioCountry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(country.name, color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(country.code, color = PhoebeUi.secondaryText, fontSize = 12.sp)
        }
        Text("${country.stationCount}", color = PhoebeUi.mutedText, fontSize = 12.sp)
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun RadioStationRow(
    station: RadioStation,
    starting: Boolean,
    onPlay: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .clickable(enabled = !starting, onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PhoebeUi.subtleFill),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkImage(
                seed = station.name,
                thumbUrl = station.faviconUrlOrFallback,
                modifier = Modifier.size(38.dp),
                radius = 8.dp,
                elevated = false,
            )
        }
        Column(Modifier.weight(1f)) {
            val subtitle = if (station.source == RadioStationSource.Recommended) {
                station.description?.takeIf { it.isNotBlank() } ?: station.displaySubtitle
            } else {
                station.displaySubtitle
            }
            Text(station.name, color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (station.source == RadioStationSource.Manual && onEdit != null && onDelete != null) {
            TextButton(onClick = onEdit) { Text("Edit", color = PhoebeUi.accentLight, fontSize = 12.sp) }
            TextButton(onClick = onDelete) { Text("Delete", color = PhoebeUi.mutedText, fontSize = 12.sp) }
        }
        if (starting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PhoebeUi.accentLight)
        } else {
            PhoebeIconView(PhoebeIcon.Play, tint = PhoebeUi.primaryText, modifier = Modifier.size(16.dp))
        }
    }
}

private fun RadioStation.matchesSearch(query: String): Boolean {
    val normalized = query.lowercase()
    return listOf(name, description, category, tags, countryCode, language, streamUrl, homepageUrl)
        .filterNotNull()
        .any { normalized in it.lowercase() }
}

@Composable
private fun ManualStationDialog(
    title: String,
    station: RadioStation?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(station?.id) { mutableStateOf(station?.name.orEmpty()) }
    var streamUrl by remember(station?.id) { mutableStateOf(station?.streamUrl.orEmpty()) }
    val isValid = name.isNotBlank() && streamUrl.isNotBlank()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PhoebeUi.panel)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(12.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            RadioTextField(name, { name = it }, "Name")
            RadioTextField(streamUrl, { streamUrl = it }, "Stream URL")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = PhoebeUi.secondaryText) }
                FilledTonalButton(
                    onClick = { onSave(name, streamUrl) },
                    enabled = isValid,
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun RadioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = PhoebeUi.primaryText, fontSize = 13.sp),
        cursorBrush = SolidColor(PhoebeUi.accentLight),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PhoebeUi.subtleFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isBlank()) Text(placeholder, color = PhoebeUi.mutedText, fontSize = 13.sp)
                inner()
            }
        },
    )
}
