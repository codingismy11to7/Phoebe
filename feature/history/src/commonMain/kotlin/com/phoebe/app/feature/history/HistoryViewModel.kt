package com.phoebe.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoebe.app.data.PlayHistorySnapshot
import com.phoebe.app.data.PlayHistoryRankedEntries
import com.phoebe.app.data.derivationKey
import com.phoebe.app.data.playHistoryRows
import com.phoebe.app.data.rankedEntries
import com.phoebe.app.data.trackIndexKey
import com.phoebe.app.data.withRankedEntries
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.Track
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayHistoryRouteState(
    val kind: PlayHistoryKind,
    val catalog: CatalogSnapshot,
    val playHistory: PlayHistorySnapshot,
    val resolvedTracksById: Map<String, Track>,
    val nowPlaying: HistoryNowPlayingState,
    val loadRankedEntries: suspend (PlayHistoryKind, Int) -> PlayHistoryRankedEntries = { requestedKind, limit ->
        playHistory.rankedEntries(requestedKind, limit)
    },
    val queryLimit: Int = PlayHistoryFullListCapacity,
)

@Inject
class PlayHistoryViewModel : ViewModel() {
    private val mutableRouteState = MutableStateFlow<PlayHistoryRouteState?>(null)
    private val mutableUiState = MutableStateFlow(PlayHistoryUiState())
    private var deriveJob: Job? = null
    private var lastDerivationKey: String? = null

    val routeState: StateFlow<PlayHistoryRouteState?> = mutableRouteState.asStateFlow()
    val uiState: StateFlow<PlayHistoryUiState> = mutableUiState.asStateFlow()

    fun update(state: PlayHistoryRouteState) {
        mutableRouteState.value = state
        val derivationKey = buildString {
            append(state.kind.name)
            append(':')
            append(state.catalog.trackIndexKey())
            append(':')
            append(state.playHistory.derivationKey())
            append(':')
            append(state.queryLimit)
            append(':')
            state.resolvedTracksById.keys.sorted().forEach { append(it).append(',') }
        }
        if (derivationKey == lastDerivationKey) return
        lastDerivationKey = derivationKey
        val previousRows = mutableUiState.value.rows
        mutableUiState.value = PlayHistoryUiState(
            rows = previousRows,
            rankedTotal = state.rankedTotal(),
        )
        deriveJob?.cancel()
        deriveJob = viewModelScope.launch {
            val rankedEntries = runCatching {
                state.loadRankedEntries(state.kind, state.queryLimit)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                state.playHistory.rankedEntries(state.kind, state.queryLimit)
            }
            val rows = withContext(Dispatchers.Default) {
                playHistoryRows(
                    kind = state.kind,
                    catalog = state.catalog,
                    playHistory = state.playHistory.withRankedEntries(rankedEntries),
                    resolvedTracksById = state.resolvedTracksById,
                    queryLimit = rankedEntries.entryCount,
                )
            }
            mutableUiState.value = PlayHistoryUiState(
                rows = rows,
                rankedTotal = rankedEntries.totalCount,
            )
        }
    }

    private fun PlayHistoryRouteState.rankedTotal(): Int =
        when (kind) {
            PlayHistoryKind.MostPlayed -> playHistory.topMostPlayed.size
            PlayHistoryKind.RecentlyPlayed -> playHistory.topRecentlyPlayed.size
        }
}

const val PlayHistoryFullListCapacity = 25_000
