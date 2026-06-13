package com.phoebe.app.data

import com.phoebe.app.domain.RecentSearchItem
import com.phoebe.app.platform.PlatformStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private const val MaxRecentSearchItems = 8
private const val PrefsFile = "search_history.json"

@Serializable
private data class SearchHistorySnapshot(
    val items: List<RecentSearchItem> = emptyList(),
)

@SingleIn(AppScope::class)
@Inject
class SearchHistoryRepository(
    private val storage: PlatformStorage,
) {
    private val mutableItems = MutableStateFlow<List<RecentSearchItem>>(emptyList())
    val items: StateFlow<List<RecentSearchItem>> = mutableItems.asStateFlow()

    suspend fun restore() {
        val raw = storage.readText(PrefsFile) ?: return
        val parsed = runCatching {
            PhoebeDataJson.decodeFromString(SearchHistorySnapshot.serializer(), raw)
        }.getOrNull() ?: return
        val restored = parsed.items.entityHitsOnly().take(MaxRecentSearchItems)
        mutableItems.value = restored
        if (restored.size != parsed.items.size) {
            persist(restored)
        }
    }

    suspend fun prepend(item: RecentSearchItem) {
        if (item is RecentSearchItem.Query) return
        val updated = (listOf(item) + mutableItems.value.filterNot { it.key == item.key })
            .take(MaxRecentSearchItems)
        mutableItems.value = updated
        persist(updated)
    }

    suspend fun remove(item: RecentSearchItem) {
        val updated = mutableItems.value.filterNot { it.key == item.key }
        mutableItems.value = updated
        persist(updated)
    }

    suspend fun clear() {
        mutableItems.value = emptyList()
        withContext(Dispatchers.Default) {
            storage.delete(PrefsFile)
        }
    }

    fun resetInMemoryState() {
        mutableItems.value = emptyList()
    }

    private fun List<RecentSearchItem>.entityHitsOnly(): List<RecentSearchItem> =
        filterNot { it is RecentSearchItem.Query }

    private suspend fun persist(items: List<RecentSearchItem>) {
        val payload = PhoebeDataJson.encodeToString(
            SearchHistorySnapshot.serializer(),
            SearchHistorySnapshot(items),
        )
        withContext(Dispatchers.Default) {
            storage.writeText(PrefsFile, payload)
        }
    }
}
